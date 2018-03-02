package com.omarea.vboot

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TabHost
import com.omarea.shell.SysUtils
import com.omarea.shell.units.TopTasksUnit
import com.omarea.ui.ProgressBarDialog
import com.omarea.ui.task_adapter
import kotlinx.android.synthetic.main.layout_task.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap


class FragmentTasks : Fragment() {
    private lateinit var processBarDialog: ProgressBarDialog
    internal lateinit var view: View
    internal lateinit var myHandler: Handler
    var refresh = true
    var kernel = false
    var process: Process? = null
    var isNew = false

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        view = inflater!!.inflate(R.layout.layout_task, container, false)

        myHandler = object : android.os.Handler() {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                if (msg.what == 0) {
                    process = msg.obj as Process
                } else if (msg.what == -1) {
                    this.post {
                        processBarDialog.hideDialog()
                        android.widget.Toast.makeText(context, "获取进程信息出错，可能不支持你的系统", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else if(msg.what == -2) {
                    this.post {
                        if (isNew) {
                            processBarDialog.hideDialog()
                            android.widget.Toast.makeText(context, "获取进程信息出错，可能不支持你的系统", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            isNew = true
                            getSwaps()
                        }
                    }
                } else if (msg.what == 1) {
                    var txt = msg.obj.toString()
                    txt = txt.replace("\t\t", "\t").replace("\t", " ")
                    while (txt.contains("  ")) {
                        txt = txt.replace("  ", " ")
                    }
                    var list = ArrayList<HashMap<String, String>>()
                    var rows = txt.split("\n").toMutableList()
                    if (rows.size < 1) {
                        return
                    }
                    var tr = rows[0].split(" ").toMutableList()
                    var pidIndex = tr.indexOf("PID")

                    var typeIndex = tr.indexOf("USER")
                    if (typeIndex < 0) {
                        typeIndex = tr.indexOf("UID")
                    }

                    var cpuIndex = tr.indexOf("CPU%")
                    if (cpuIndex < 0) {
                        cpuIndex = tr.indexOf("S[%CPU]")
                    }

                    var nameIndex = tr.indexOf("Name")
                    if (nameIndex < 0) {
                        nameIndex = tr.indexOf("ARGS")
                    }

                    if (pidIndex < 0 || typeIndex < 0 || cpuIndex < 0 || nameIndex < 0) {
                        return
                    }

                    for (i in 0..rows.size - 1) {
                        var tr = LinkedHashMap<String, String>()
                        var params = rows[i].split(" ").toMutableList()
                        if (params.size > 4) {
                            tr.put("itemPid", params[pidIndex])
                            if (!kernel && i != 0 && params[typeIndex].indexOf("u") != 0) {
                                continue
                            }
                            if (params[nameIndex] == "top") {
                                continue
                            }
                            tr.put("itemType", params[typeIndex])
                            tr.put("itemCpu", params[cpuIndex])
                            tr.put("itemName", params[nameIndex])

                            list.add(tr)
                        }
                    }

                    myHandler.post {
                        if (processBarDialog.isDialogShow() || refresh) {
                            var datas = task_adapter(context, list)
                            list_tasks.setAdapter(datas)
                            processBarDialog.hideDialog()
                        }
                    }
                }
            }
        }

        val tabHost = view.findViewById(R.id.tabhost_task) as TabHost
        tabHost.setup()
        tabHost.addTab(tabHost.newTabSpec("tab_0").setContent(R.id.tab_tasks_user).setIndicator("用户"))
        tabHost.addTab(tabHost.newTabSpec("tab_1").setContent(R.id.tab_tasks_system).setIndicator("系统"))
        tabHost.currentTab = 0

        return view
    }

    internal var getSwaps = {
        Thread({
            TopTasksUnit.executeCommandWithOutput(myHandler, false)
        }).start()
    }

    override fun onResume() {
        super.onResume()
        processBarDialog.showDialog()

        getSwaps()
        getTasks()
    }

    private fun getTasks() {
        val runningAppsInfo = ArrayList<RunningAppProcessInfo>()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = am.getRunningServices(1000)
        for (service in runningServices) {
            val pkgName = service.process.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            try {
                val item = RunningAppProcessInfo()
                item.pkgList = arrayOf(pkgName)
                item.pid = service.pid
                item.processName = service.process
                item.uid = service.uid
                runningAppsInfo.add(item)
            } catch (e: PackageManager.NameNotFoundException) {
            }
        }

        val list = ArrayList<HashMap<String, String>>()
        for (app in runningAppsInfo) {
            val tr = HashMap<String, String>()
            tr.put("itemPid", app.pid.toString())
            tr.put("itemCpu", "")
            tr.put("itemType", "")
            tr.put("itemName", app.processName)

            list.add(tr)
        }
        list.sortBy { item -> item["itemName"] }
        tasks_user.adapter = task_adapter(context, list)
    }

    override fun onPause() {
        if (process != null) {
            process!!.destroy()
            process = null
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (process != null) {
            process!!.destroy()
            process = null
        }
        super.onDestroy()
    }

    fun killProcess(pid: String) {
        SysUtils.executeRootCommand(mutableListOf("kill " + pid))
        processBarDialog.showDialog()
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        processBarDialog = ProgressBarDialog(this.context)
        checkbox_refresh.isChecked = this.refresh
        checkbox_refresh.setOnCheckedChangeListener({ _, isChecked ->
            this.refresh = isChecked
            if (isChecked) {
                processBarDialog.showDialog()
            } else {
                processBarDialog.hideDialog()
            }
        })
        checkbox_kernel.isChecked = this.kernel
        checkbox_kernel.setOnCheckedChangeListener({ _, isChecked ->
            this.kernel = isChecked
            processBarDialog.showDialog()
        })
        tasks_user.setOnItemClickListener { _, dialogView, position, _ ->
            val adapter = tasks_user.adapter as task_adapter
            val item = adapter.getItem(position)
            if (item.get("itemName") == "com.omarea.vboot") {
                Snackbar.make(dialogView, "你这是要我自杀啊！！！", Snackbar.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            AlertDialog.Builder(context).setTitle("结束" + item.get("itemName") + "?").setMessage("确定要强行停止这个任务吗，这可能导致数据丢失，甚至系统崩溃需要重启！")
                    .setNegativeButton(
                            "确定",
                            { _, _ ->
                                killProcess(item.get("itemPid").toString())
                            }
                    )
                    .setNeutralButton("取消",
                            { _, _ ->
                            })
                    .create().show()
        }
        list_tasks.setOnItemClickListener { _, dialogView, position, _ ->
            val adapter = list_tasks.adapter as task_adapter
            val item = adapter.getItem(position)
            if (item.get("itemName") == "com.omarea.vboot") {
                Snackbar.make(dialogView, "你这是要我自杀啊！！！", Snackbar.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            AlertDialog.Builder(context).setTitle("结束" + item.get("itemName") + "?").setMessage("确定要强行停止这个任务吗，这可能导致数据丢失，甚至系统崩溃需要重启！")
                    .setNegativeButton(
                            "确定",
                            { _, _ ->
                                killProcess(item.get("itemPid").toString())
                            }
                    )
                    .setNeutralButton("取消",
                            { _, _ ->
                            })
                    .create().show()
        }
    }

    companion object {
        fun Create(): Fragment {
            val fragment = FragmentTasks()
            return fragment
        }
    }
}// Required empty public constructor