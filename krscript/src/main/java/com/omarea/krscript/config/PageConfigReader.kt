package com.omarea.krscript.config

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.util.Log
import android.util.Xml
import android.widget.Toast
import com.omarea.common.shared.FileWrite
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.RootFile
import com.omarea.krscript.executor.ExtractAssets
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.*
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Created by Hello on 2018/04/01.
 */
class PageConfigReader {
    private var context: Context
    private var pageConfig: String = ""
    // 读取pageConfig时自动获得
    private var pageConfigAbsPath: String = ""
    private var pageConfigStream: InputStream? = null
    private var parentDir: String = ""

    constructor(context: Context, pageConfig: String) {
        this.context = context;
        this.pageConfig = pageConfig;
    }

    constructor(context: Context, pageConfig: String, parentDir: String) {
        this.context = context;
        this.pageConfig = pageConfig;
        this.parentDir = parentDir;
    }

    constructor(context: Context, pageConfigStream: InputStream) {
        this.context = context;
        this.pageConfigStream = pageConfigStream;
    }

    private val ASSETS_FILE = "file:///android_asset/"

    private fun tryOpenDiskFile(filePath: String): FileInputStream? {
        try {
            File(filePath).run {
                if (exists() && canRead()) {
                    pageConfigAbsPath = absolutePath
                    return inputStream()
                }
            }

            if (!filePath.startsWith("/")) {
                if (parentDir.isNotEmpty()) {
                    val relativePath = when {
                        !parentDir.endsWith("/") -> parentDir + "/"
                        else -> parentDir
                    } + filePath

                    File(relativePath).run {
                        if (exists() && canRead()) {
                            pageConfigAbsPath = absolutePath
                            return inputStream()
                        }
                    }
                }

                val privatePath = FileWrite.getPrivateFileDir(context) + filePath
                File(privatePath).run {
                    if (exists() && canRead()) {
                        pageConfigAbsPath = absolutePath
                        return inputStream()
                    }
                }
            }

            val parent = when {
                !parentDir.endsWith("/") -> parentDir + "/"
                else -> parentDir
            }

            var relativePath: String? = null
            if (parentDir.isNotEmpty() && !filePath.startsWith("/")) {
                relativePath = when {
                    !parentDir.endsWith("/") -> parentDir + "/"
                    else -> parentDir
                } + filePath
            }

            when {
                RootFile.fileExists(filePath) -> filePath
                relativePath != null && RootFile.fileExists(relativePath) -> relativePath
                else -> null
            }.run {
                val cachePath = FileWrite.getPrivateFilePath(context, "kr-script/outside_page.xml")
                KeepShellPublic.doCmdSync("cp -f \"$this\" \"$cachePath\"")
                File(cachePath).run {
                    if (exists() && canRead()) {
                        return inputStream()
                    }
                }
            }
        } catch (ex: java.lang.Exception) {
        }
        return null
    }

    fun getConfig(filePath: String): InputStream? {
        try {
            if (filePath.startsWith(ASSETS_FILE)) {
                return context.assets.open(filePath.substring(ASSETS_FILE.length))
            } else {
                val fileInputStream = tryOpenDiskFile(filePath)
                if (fileInputStream != null) {
                    return fileInputStream
                } else {
                    return context.assets.open(filePath)
                }
            }
        } catch (ex: Exception) {
            return null
        }
    }

    fun readConfigXml(): ArrayList<ConfigItemBase>? {
        if (pageConfigStream != null) {
            return readConfigXml(pageConfigStream!!)
        } else {
            try {
                val fileInputStream = getConfig(pageConfig) ?: return ArrayList()
                return readConfigXml(fileInputStream)
            } catch (ex: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "解析配置文件失败\n" + ex.message, Toast.LENGTH_LONG).show()
                }
                Log.e("KrConfig Fail！", "" + ex.message)
            }

        }
        return null
    }

    private fun readConfigXml(fileInputStream: InputStream): ArrayList<ConfigItemBase>? {
        try {
            val parser = Xml.newPullParser()// 获取xml解析器
            parser.setInput(fileInputStream, "utf-8")// 参数分别为输入流和字符编码
            var type = parser.eventType
            val mainList: ArrayList<ConfigItemBase> = ArrayList()
            var action: ActionInfo? = null
            var switch: SwitchInfo? = null
            var picker: PickerInfo? = null
            var group: GroupInfo? = null
            var page: PageInfo? = null
            var text: TextInfo? = null
            var isRootNode = true
            while (type != XmlPullParser.END_DOCUMENT) { // 如果事件不等于文档结束事件就继续循环
                when (type) {
                    XmlPullParser.START_TAG -> {
                        if ("group" == parser.name) {
                            if (group != null && group.supported) {
                                mainList.add(group)
                            }
                            group = groupNode(parser)
                        } else if (group != null && !group.supported) {
                            // 如果 group.supported !- true 跳过group内所有项
                        } else {
                            if ("page" == parser.name) {
                                if (!isRootNode) {
                                    page = mainNode(PageInfo(pageConfigAbsPath), parser) as PageInfo?
                                    if (page != null) {
                                        page = pageNode(page, parser)
                                    }
                                }
                            } else if ("action" == parser.name) {
                                action = mainNode(ActionInfo(), parser) as ActionInfo?
                            } else if ("switch" == parser.name) {
                                switch = mainNode(SwitchInfo(), parser) as SwitchInfo?
                            } else if ("picker" == parser.name) {
                                picker = mainNode(PickerInfo(), parser) as PickerInfo?
                                if (picker != null) {
                                    pickerNode(picker, parser)
                                }
                            } else if ("text" == parser.name) {
                                text = mainNode(TextInfo(), parser) as TextInfo?
                            } else if (page != null) {
                                tagStartInPage(page, parser)
                            } else if (action != null) {
                                tagStartInAction(action, parser)
                            } else if (switch != null) {
                                tagStartInSwitch(switch, parser)
                            } else if (picker != null) {
                                tagStartInPicker(picker, parser)
                            } else if (text != null) {
                                tagStartInText(text, parser)
                            } else if ("resource" == parser.name) {
                                resourceNode(parser)
                            }
                        }
                        isRootNode = false
                    }
                    XmlPullParser.END_TAG ->
                        if ("group" == parser.name) {
                            if (group != null && group.supported) {
                                mainList.add(group)
                            }
                            group = null
                        } else if (group != null) {
                            if ("page" == parser.name) {
                                tagEndInPage(page, parser)
                                if (page != null) {
                                    group.children.add(page)
                                }
                                page = null
                            } else if ("action" == parser.name) {
                                tagEndInAction(action, parser)
                                if (action != null) {
                                    group.children.add(action)
                                }
                                action = null
                            } else if ("switch" == parser.name) {
                                tagEndInSwitch(switch, parser)
                                if (switch != null) {
                                    group.children.add(switch)
                                }
                                switch = null
                            } else if ("picker" == parser.name) {
                                tagEndInPicker(picker, parser)
                                if (picker != null) {
                                    group.children.add(picker)
                                }
                                picker = null
                            } else if ("text" == parser.name) {
                                tagEndInText(text, parser)
                                if (text != null) {
                                    group.children.add(text)
                                }
                                text = null
                            }
                        } else {
                            if ("page" == parser.name) {
                                tagEndInPage(page, parser)
                                if (page != null) {
                                    mainList.add(page)
                                }
                                page = null
                            } else if ("action" == parser.name) {
                                tagEndInAction(action, parser)
                                if (action != null) {
                                    mainList.add(action)
                                }
                                action = null
                            } else if ("switch" == parser.name) {
                                tagEndInSwitch(switch, parser)
                                if (switch != null) {
                                    mainList.add(switch)
                                }
                                switch = null
                            } else if ("picker" == parser.name) {
                                tagEndInPicker(picker, parser)
                                if (picker != null) {
                                    mainList.add(picker)
                                }
                                picker = null
                            } else if ("text" == parser.name) {
                                tagEndInText(text, parser)
                                if (text != null) {
                                    mainList.add(text)
                                }
                                text = null
                            }
                        }
                }
                type = parser.next()// 继续下一个事件
            }

            return mainList
        } catch (ex: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "解析配置文件失败\n" + ex.message, Toast.LENGTH_LONG).show()
            }
            Log.e("KrConfig Fail！", "" + ex.message)
        }

        return null
    }

    private var actionParamInfos: ArrayList<ActionParamInfo>? = null
    var actionParamInfo: ActionParamInfo? = null
    private fun tagStartInAction(action: ActionInfo, parser: XmlPullParser) {
        if ("title" == parser.name) {
            action.title = parser.nextText()
        } else if ("desc" == parser.name) {
            descNode(action, parser)
        } else if ("summary" == parser.name) {
            summaryNode(action, parser)
        } else if ("script" == parser.name || "set" == parser.name || "setstate" == parser.name) {
            action.setState = parser.nextText().trim()
        } else if ("param" == parser.name) {
            if (actionParamInfos == null) {
                actionParamInfos = ArrayList()
            }
            actionParamInfo = ActionParamInfo()
            val actionParamInfo = actionParamInfo!!
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                val attrValue = parser.getAttributeValue(i)
                when {
                    attrName == "name" -> actionParamInfo.name = attrValue
                    attrName == "label" -> actionParamInfo.label = attrValue
                    attrName == "title" -> actionParamInfo.title = attrValue
                    attrName == "desc" -> actionParamInfo.desc = attrValue
                    attrName == "value" -> actionParamInfo.value = attrValue
                    attrName == "type" -> actionParamInfo.type = attrValue.toLowerCase().trim { it <= ' ' }
                    attrName == "readonly" -> {
                        val value = attrValue.toLowerCase().trim { it <= ' ' }
                        actionParamInfo.readonly = (value == "readonly" || value == "true" || value == "1")
                    }
                    attrName == "maxlength" -> actionParamInfo.maxLength = Integer.parseInt(attrValue)
                    attrName == "min" -> actionParamInfo.min = Integer.parseInt(attrValue)
                    attrName == "max" -> actionParamInfo.max = Integer.parseInt(attrValue)
                    attrName == "required" -> actionParamInfo.required = attrValue == "true" || attrValue == "1" || attrValue == "required"
                    attrName == "value-sh" || attrName == "value-su" -> {
                        val script = attrValue
                        actionParamInfo.valueShell = script
                    }
                    attrName == "options-sh" || attrName == "options-su" -> {
                        if (actionParamInfo.options == null)
                            actionParamInfo.options = ArrayList<ActionParamInfo.ActionParamOption>()
                        val script = attrValue
                        actionParamInfo.optionsSh = script
                    }
                    attrName == "support" || attrName == "visible" -> {
                        if (executeResultRoot(context, attrValue) != "1") {
                            actionParamInfo.supported = false
                        }
                    }
                    attrName == "multiple" -> {
                        actionParamInfo.multiple = attrValue == "multiple" || attrValue == "true" || attrValue == "1"
                    }
                }
            }
            if (actionParamInfo.supported && actionParamInfo.name != null && actionParamInfo.name!!.isNotEmpty()) {
                actionParamInfos!!.add(actionParamInfo)
            }
        } else if (actionParamInfo != null && "option" == parser.name) {
            val actionParamInfo = actionParamInfo!!
            if (actionParamInfo.options == null) {
                actionParamInfo.options = ArrayList()
            }
            val option = ActionParamInfo.ActionParamOption()
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                if (attrName == "val" || attrName == "value") {
                    option.value = parser.getAttributeValue(i)
                }
            }
            option.desc = parser.nextText()
            if (option.value == null)
                option.value = option.desc
            actionParamInfo.options!!.add(option)
        } else if ("resource" == parser.name) {
            resourceNode(parser)
        }
    }

    private fun tagEndInPage(page: PageInfo?, parser: XmlPullParser) {
    }

    private fun tagEndInAction(action: ActionInfo?, parser: XmlPullParser) {
        if (action != null) {
            if (action.setState == null)
                action.setState = ""
            action.params = actionParamInfos

            actionParamInfos = null
        }
    }

    private fun tagStartInPage(info: PageInfo, parser: XmlPullParser) {
        when {
            "title" == parser.name -> info.title = parser.nextText()
            "desc" == parser.name -> descNode(info, parser)
            "summary" == parser.name -> summaryNode(info, parser)
            "resource" == parser.name -> resourceNode(parser)
            "html" == parser.name -> info.onlineHtmlPage = parser.nextText()
            "config" == parser.name -> info.pageConfigPath = parser.nextText()
            ("status" == parser.name || "status-bar" == parser.name) -> info.statusBar = parser.nextText()
        }
    }

    private fun tagStartInSwitch(switchInfo: SwitchInfo, parser: XmlPullParser) {
        when {
            "title" == parser.name -> switchInfo.title = parser.nextText()
            "desc" == parser.name -> descNode(switchInfo, parser)
            "summary" == parser.name -> summaryNode(switchInfo, parser)
            "get" == parser.name || "getstate" == parser.name -> switchInfo.getState = parser.nextText()
            "set" == parser.name || "setstate" == parser.name -> switchInfo.setState = parser.nextText()
            "resource" == parser.name -> resourceNode(parser)
        }
    }

    private fun groupNode(parser: XmlPullParser): GroupInfo {
        val groupInfo = GroupInfo()
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            val attrValue = parser.getAttributeValue(i)
            if (attrName == "title") {
                groupInfo.separator = attrValue
            } else if (attrName == "support" || attrName == "visible") {
                groupInfo.supported = executeResultRoot(context, attrValue) == "1"
            }
        }
        return groupInfo
    }

    private fun mainNode(configItemBase: ConfigItemBase, parser: XmlPullParser): ConfigItemBase? {
        for (i in 0 until parser.attributeCount) {
            val attrValue = parser.getAttributeValue(i)
            when (parser.getAttributeName(i)) {
                "key", "index", "id" -> configItemBase.key = attrValue
                "title" -> configItemBase.title = attrValue
                "desc" -> configItemBase.desc = attrValue
                "confirm" -> configItemBase.confirm = (attrValue == "confirm" || attrValue == "true" || attrValue == "1")
                "auto-off" -> configItemBase.autoOff = (attrValue == "auto-off" || attrValue == "true" || attrValue == "1")
                "interruptible", "interruptable" -> configItemBase.interruptable = (attrValue.isEmpty() || attrValue == "interruptable" || attrValue == "interruptable" || attrValue == "true" || attrValue == "1")
                "support", "visible" -> {
                    if (executeResultRoot(context, attrValue) != "1") {
                        return null
                    }
                }
                "desc-sh" -> {
                    configItemBase.descSh = parser.getAttributeValue(i)
                    configItemBase.desc = executeResultRoot(context, configItemBase.descSh)
                }
                "summary" -> {
                    configItemBase.summary = parser.getAttributeValue(i)
                }
                "summary-sh" -> {
                    configItemBase.summarySh = parser.getAttributeValue(i)
                    configItemBase.summary = executeResultRoot(context, configItemBase.summarySh)
                }
                "reload", "reload-page" -> {
                    if (attrValue == "reload-page" || attrValue == "reload" || attrValue == "page" || attrValue == "true" || attrValue == "1") {
                        configItemBase.reloadPage = true
                    }
                }
                "bg-task", "background-task", "async-task" -> {
                    if (attrValue == "async-task" || attrValue == "async" || attrValue == "bg-task" || attrValue == "background" || attrValue == "background-task" || attrValue == "true" || attrValue == "1") {
                        configItemBase.backgroundTask = true
                    }
                }
            }
        }
        return configItemBase
    }

    // TODO: 整理Title和Desc
    // TODO: 整理ReloadPage
    private fun pageNode(page: PageInfo, parser: XmlPullParser): PageInfo {
        for (attrIndex in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(attrIndex)
            val attrValue = parser.getAttributeValue(attrIndex)
            when (attrName) {
                "config" -> page.pageConfigPath = attrValue
                "html" -> page.onlineHtmlPage = attrValue
                "before-load", "before-read" -> page.beforeRead = attrValue
                "after-load", "after-read" -> page.afterRead = attrValue
                "load-ok", "load-success" -> page.loadSuccess = attrValue
                "load-fail", "load-error" -> page.loadFail = attrValue
                "config-sh" -> page.pageConfigSh = attrValue
            }
        }
        return page
    }

    private fun pickerNode(pickerInfo: PickerInfo, parser: XmlPullParser) {
        for (attrIndex in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(attrIndex)
            val attrValue = parser.getAttributeValue(attrIndex)
            when (attrName) {
                "options-sh", "options-su" -> {
                    if (pickerInfo.options == null)
                        pickerInfo.options = ArrayList()
                    pickerInfo.optionsSh = attrValue
                }
                "multiple" -> {
                    pickerInfo.multiple = attrValue == "multiple" || attrValue == "true" || attrValue == "1"
                }
            }
        }
    }

    private fun descNode(configItemBase: ConfigItemBase, parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == "su" || attrName == "sh" || attrName == "desc-sh") {
                configItemBase.descSh = parser.getAttributeValue(i)
                configItemBase.desc = executeResultRoot(context, configItemBase.descSh)
            }
        }
        if (configItemBase.desc.isEmpty())
            configItemBase.desc = parser.nextText()
    }

    private fun summaryNode(configItemBase: ConfigItemBase, parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == "su" || attrName == "sh" || attrName == "summary-sh") {
                configItemBase.summarySh = parser.getAttributeValue(i)
                configItemBase.summary = executeResultRoot(context, configItemBase.summarySh)
            }
        }
        if (configItemBase.summary.isEmpty())
            configItemBase.summary = parser.nextText()
    }

    private fun resourceNode(parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == "file") {
                val file = parser.getAttributeValue(i).trim()
                if (file.startsWith(ASSETS_FILE)) {
                    ExtractAssets(context).extractResource(file)
                }
            } else if (parser.getAttributeName(i) == "dir") {
                val file = parser.getAttributeValue(i).trim()
                if (file.startsWith(ASSETS_FILE)) {
                    ExtractAssets(context).extractResources(file)
                }
            }
        }
    }

    private fun tagEndInSwitch(switchInfo: SwitchInfo?, parser: XmlPullParser) {
        if (switchInfo != null) {
            if (switchInfo.getState == null) {
                switchInfo.getState = ""
            } else {
                val shellResult = executeResultRoot(context, switchInfo.getState)
                switchInfo.checked = shellResult != "error" && (shellResult == "1" || shellResult.toLowerCase() == "true")
            }
            if (switchInfo.setState == null) {
                switchInfo.setState = ""
            }
        }
    }

    private fun tagStartInText(textInfo: TextInfo, parser: XmlPullParser) {
        if ("title" == parser.name) {
            textInfo.title = parser.nextText()
        } else if ("desc" == parser.name) {
            descNode(textInfo, parser)
        } else if ("summary" == parser.name) {
            summaryNode(textInfo, parser)
        } else if ("slice" == parser.name) {
            rowNode(textInfo, parser)
        } else if ("resource" == parser.name) {
            resourceNode(parser)
        }
    }

    private fun rowNode(textInfo: TextInfo, parser: XmlPullParser) {
        val textRow = TextInfo.TextRow()
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i).toLowerCase()
            val attrValue = parser.getAttributeValue(i)
            try {
                when (attrName) {
                    "bold", "b" -> textRow.bold = (attrValue == "1" || attrValue == "true" || attrValue == "bold")
                    "italic", "i" -> textRow.italic = (attrValue == "1" || attrValue == "true" || attrValue == "italic")
                    "underline", "u" -> textRow.underline = (attrValue == "1" || attrValue == "true" || attrValue == "underline")
                    "foreground", "color" -> textRow.color = Color.parseColor(attrValue)
                    "bg", "background", "bgcolor" -> textRow.bgColor = Color.parseColor(attrValue)
                    "size" -> textRow.size = attrValue.toInt()
                    "break" -> textRow.breakRow = (attrValue == "1" || attrValue == "true" || attrValue == "break")
                    "link", "href" -> textRow.link = attrValue
                    "activity", "a", "intent" -> textRow.activity = attrValue
                    "script", "run" -> {
                        textRow.onClickScript = attrValue
                    }
                    "sh" -> {
                        textRow.dynamicTextSh = attrValue
                    }
                    "align" -> {
                        when (attrValue) {
                            "left" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                textRow.align = Layout.Alignment.ALIGN_LEFT
                            }
                            "right" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                textRow.align = Layout.Alignment.ALIGN_RIGHT
                            }
                            "center" -> textRow.align = Layout.Alignment.ALIGN_CENTER
                            "normal" -> textRow.align = Layout.Alignment.ALIGN_NORMAL
                        }
                    }
                }
            } catch (ex: Exception) {
            }
        }
        textRow.text = "" + parser.nextText()
        textInfo.rows.add(textRow)
    }

    private fun tagStartInPicker(pickerInfo: PickerInfo, parser: XmlPullParser) {
        if ("title" == parser.name) {
            pickerInfo.title = parser.nextText()
        } else if ("desc" == parser.name) {
            descNode(pickerInfo, parser)
        } else if ("summary" == parser.name) {
            summaryNode(pickerInfo, parser)
        } else if ("option" == parser.name) {
            if (pickerInfo.options == null) {
                pickerInfo.options = ArrayList()
            }
            val option = ActionParamInfo.ActionParamOption()
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                if (attrName == "val" || attrName == "value") {
                    option.value = parser.getAttributeValue(i)
                }
            }
            option.desc = parser.nextText()
            if (option.value == null)
                option.value = option.desc
            pickerInfo.options!!.add(option)
        } else if ("getstate" == parser.name || "get" == parser.name) {
            pickerInfo.getState = parser.nextText()
        } else if ("setstate" == parser.name || "set" == parser.name) {
            pickerInfo.setState = parser.nextText()
        } else if ("resource" == parser.name) {
            resourceNode(parser)
        }
    }

    private fun tagEndInPicker(pickerInfo: PickerInfo?, parser: XmlPullParser) {
        if (pickerInfo != null) {
            if (pickerInfo.getState == null) {
                pickerInfo.getState = ""
            } else {
                val shellResult = executeResultRoot(context, "" + pickerInfo.getState)
                pickerInfo.value = shellResult
            }
            if (pickerInfo.setState == null) {
                pickerInfo.setState = ""
            }
        }
    }

    private fun tagEndInText(textInfo: TextInfo?, parser: XmlPullParser) {
    }

    private fun executeResultRoot(context: Context, scriptIn: String): String {
        return ScriptEnvironmen.executeResultRoot(context, scriptIn);
    }
}