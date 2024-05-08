package com.omarea.vtools.fragments

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.omarea.Scene
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.data.GlobalStatus
import com.omarea.library.shell.*
import com.omarea.model.CpuCoreInfo
import com.omarea.scene_mode.CpuConfigInstaller
import com.omarea.scene_mode.ModeSwitcher
import com.omarea.store.SpfConfig
import com.omarea.ui.AdapterCpuCores
import com.omarea.utils.AccessibleServiceHelper
import com.omarea.vtools.R
import com.omarea.vtools.databinding.FragmentHomeBinding
import com.omarea.vtools.dialogs.DialogElectricityUnit
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.collections.HashMap

class FragmentHome : androidx.fragment.app.Fragment() {
    private val modeSwitcher = ModeSwitcher()
    private lateinit var binding:FragmentHomeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return FragmentHomeBinding.inflate(inflater, container, false).also {
            binding = it
        }.root
    }

    private var CpuFrequencyUtil = CpuFrequencyUtils()
    private lateinit var globalSPF: SharedPreferences
    private var timer: Timer? = null

    private lateinit var spf: SharedPreferences
    private var myHandler = Handler(Looper.getMainLooper())
    private var cpuLoadUtils = CpuLoadUtils()
    private val memoryUtils = MemoryUtils()

    private suspend fun forceKSWAPD(mode: Int): String {
        return withContext(Dispatchers.Default) {
            SwapUtils(context!!).forceKswapd(mode)
        }
    }

    private suspend fun dropCaches() {
        return withContext(Dispatchers.Default) {
            KeepShellPublic.doCmdSync(
                    "sync\n" +
                            "echo 3 > /proc/sys/vm/drop_caches\n" +
                            "echo 1 > /proc/sys/vm/compact_memory")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activityManager = context!!.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        batteryManager = context!!.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        globalSPF = context!!.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        binding.btnPowersave.setOnClickListener {
            installConfig(ModeSwitcher.POWERSAVE)
        }
        binding.btnDefaultmode.setOnClickListener {
            installConfig(ModeSwitcher.BALANCE)
        }
        binding.btnGamemode.setOnClickListener {
            installConfig(ModeSwitcher.PERFORMANCE)
        }
        binding.btnFastmode.setOnClickListener {
            installConfig(ModeSwitcher.FAST)
        }

        if (!GlobalStatus.homeMessage.isNullOrEmpty()) {
            binding.homeMessage.visibility = View.VISIBLE
            binding.homeMessage.text = GlobalStatus.homeMessage
        }

        spf = context!!.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        binding.homeClearRam.setOnClickListener {
            binding.homeRaminfoText.text = getString(R.string.please_wait)
            GlobalScope.launch(Dispatchers.Main) {
                dropCaches()
                Scene.toast("缓存已清理...", Toast.LENGTH_SHORT)
            }
        }

        binding.homeClearSwap.setOnClickListener {
            binding.homeZramsizeText.text = getText(R.string.please_wait)
            GlobalScope.launch(Dispatchers.Main) {
                Scene.toast("开始回收少量内存(长按回收更多~)", Toast.LENGTH_SHORT)
                val result = forceKSWAPD(1)
                Scene.toast(result, Toast.LENGTH_SHORT)
            }
        }

        binding.homeClearSwap.setOnLongClickListener {
            binding.homeZramsizeText.text = getText(R.string.please_wait)
            GlobalScope.launch(Dispatchers.Main) {
                val result = forceKSWAPD(2)
                Scene.toast(result, Toast.LENGTH_SHORT)
            }
            true
        }

        binding.homeHelp.setOnClickListener {
            try {
                val uri = Uri.parse("http://vtools.omarea.com/")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context!!, "启动在线页面失败！", Toast.LENGTH_SHORT).show()
            }
        }

        binding.homeBatteryEdit.setOnClickListener {
            DialogElectricityUnit().showDialog(context!!)
        }

        // 点击CPU核心 查看详细参数
        binding.cpuCoreList.setOnItemClickListener { _, _, position, _ ->
            CpuFrequencyUtil.getCoregGovernorParams(position)?.run {
                val msg = StringBuilder()
                for (param in this) {
                    msg.append("\n")
                    msg.append(param.key)
                    msg.append("：")
                    msg.append(param.value)
                    msg.append("\n")
                }
                DialogHelper.alert(activity!!, "调度器参数", msg.toString())
            }
        }

        binding.homeDeviceName.text = when (Build.VERSION.SDK_INT) {
            34 -> "Android 14"
            33 -> "Android 13"
            32 -> "Android 12L"
            31 -> "Android 12"
            30 -> "Android 11"
            29 -> "Android 10"
            28 -> "Android 9"
            27 -> "Android 8.1"
            26 -> "Android 8.0"
            25 -> "Android 7.0"
            24 -> "Android 7.0"
            23 -> "Android 6.0"
            22 -> "Android 5.1"
            21 -> "Android 5.0"
            else -> ""
        }.let { "$it (${Build.VERSION.SDK_INT})".trim() }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        if (isDetached) {
            return
        }
        activity!!.title = getString(R.string.app_name)

        if (globalSPF.getBoolean(SpfConfig.HOME_QUICK_SWITCH, true) && (CpuConfigInstaller().dynamicSupport(Scene.context) || modeSwitcher.modeConfigCompleted())) {
            binding.powermodeToggles.visibility = View.VISIBLE
        } else {
            binding.powermodeToggles.visibility = View.GONE
        }

        setModeState()
        maxFreqs.clear()
        minFreqs.clear()
        stopTimer()
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    updateInfo()
                }
            }, 0, 1500)
        }
    }

    private var coreCount = -1
    private lateinit var batteryManager: BatteryManager
    private lateinit var activityManager: ActivityManager

    private var minFreqs = HashMap<Int, String>()
    private var maxFreqs = HashMap<Int, String>()
    fun format1(value: Double): String {
        var bd = BigDecimal(value)
        bd = bd.setScale(1, RoundingMode.HALF_UP)
        return bd.toString()
    }

    @SuppressLint("SetTextI18n")
    private fun updateRamInfo() {
        try {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            val totalMem = (info.totalMem / 1024 / 1024f).toInt()
            val availMem = (info.availMem / 1024 / 1024f).toInt()

            val swapInfo = KeepShellPublic.doCmdSync("free -m | grep Swap")
            var swapTotal = 0
            var swaoUse = 0
            if (swapInfo.contains("Swap")) {
                try {
                    val swapInfos = swapInfo.substring(swapInfo.indexOf(" "), swapInfo.lastIndexOf(" ")).trim()
                    if (Regex("[\\d]+[\\s]{1,}[\\d]{1,}").matches(swapInfos)) {
                        swapTotal = swapInfos.substring(0, swapInfos.indexOf(" ")).trim().toInt()
                        swaoUse = swapInfos.substring(swapInfos.indexOf(" ")).trim().toInt()
                    }
                } catch (ex: java.lang.Exception) {
                }
                // home_swapstate.text = swapInfo.substring(swapInfo.indexOf(" "), swapInfo.lastIndexOf(" ")).trim()
            }

            myHandler.post {
                binding.homeRaminfoText.text = "${((totalMem - availMem) * 100 / totalMem)}% (${totalMem / 1024 + 1}GB)"
                binding.homeRaminfo.setData(totalMem.toFloat(), availMem.toFloat())
                binding.homeSwapstateChat?.setData(swapTotal.toFloat(), (swapTotal - swaoUse).toFloat())
                binding.homeZramsizeText.text = (
                        if (swapTotal > 99) {
                            "${(swaoUse * 100.0 / swapTotal).toInt()}% (${format1(swapTotal / 1024.0)}GB)"
                        } else {
                            "${(swaoUse * 100.0 / swapTotal).toInt()}% (${swapTotal}MB)"
                        }
                        )
            }
        } catch (ex: Exception) {
        }
    }

    /**
     * dp转换成px
     */
    private fun dp2px(dpValue: Float): Int {
        val scale = context!!.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    private fun elapsedRealtimeStr(): String {
        val timer = SystemClock.elapsedRealtime() / 1000
        return String.format("%02d:%02d:%02d", timer / 3600, timer % 3600 / 60, timer % 60)
    }

    private var updateTick = 0

    private var batteryCurrentNow = 0L

    @SuppressLint("SetTextI18n")
    private fun updateInfo() {
        if (coreCount < 1) {
            coreCount = CpuFrequencyUtil.getCoreCount()
            myHandler.post {
                try {
                    binding.cpuCoreCount.text = "$coreCount 核心"
                } catch (ex: Exception) {
                }
            }
        }
        val cores = ArrayList<CpuCoreInfo>()
        for (coreIndex in 0 until coreCount) {
            val core = CpuCoreInfo(coreIndex)

            core.currentFreq = CpuFrequencyUtil.getCurrentFrequency("cpu$coreIndex")
            if (!maxFreqs.containsKey(coreIndex) || (core.currentFreq != "" && maxFreqs[coreIndex].isNullOrEmpty())) {
                maxFreqs[coreIndex] = CpuFrequencyUtil.getCurrentMaxFrequency("cpu$coreIndex")
            }
            core.maxFreq = maxFreqs[coreIndex]

            if (!minFreqs.containsKey(coreIndex) || (core.currentFreq != "" && minFreqs[coreIndex].isNullOrEmpty())) {
                minFreqs.put(coreIndex, CpuFrequencyUtil.getCurrentMinFrequency("cpu$coreIndex"))
            }
            core.minFreq = minFreqs[coreIndex]
            cores.add(core)
        }
        val loads = cpuLoadUtils.cpuLoad
        for (core in cores) {
            if (loads.containsKey(core.coreIndex)) {
                core.loadRatio = loads[core.coreIndex]!!
            }
        }

        val gpuFreq = GpuUtils.getGpuFreq() + "Mhz"
        val gpuLoad = GpuUtils.getGpuLoad()

        // 电池电流
        batteryCurrentNow = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // 电量
        val batteryCapacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        // 电池温度
        val temperature = GlobalStatus.updateBatteryTemperature()

        updateRamInfo()
        val memInfo = memoryUtils.memoryInfo

        myHandler.post {
            try {
                binding.homeSwapCached.text = "" + (memInfo.swapCached / 1024) + "MB"
                binding.homeBuffers.text = "" + (memInfo.buffers / 1024) + "MB"
                binding.homeDirty.text = "" + (memInfo.dirty / 1024) + "MB"

                binding.homeRunningTime.text = elapsedRealtimeStr()
                if (batteryCurrentNow != Long.MIN_VALUE && batteryCurrentNow != Long.MAX_VALUE) {
                    binding.homeBatteryNow.text = (batteryCurrentNow / globalSPF.getInt(SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT, SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT_DEFAULT)).toString() + "mA"
                } else {
                    binding.homeBatteryNow.text = "--"
                }
                binding.homeBatteryCapacity.text = "$batteryCapacity%"
                binding.homeBatteryTemperature.text = "${temperature}°C"

                binding.homeGpuFreq.text = gpuFreq
                binding.homeGpuLoad.text = "负载：$gpuLoad%"
                if (gpuLoad > -1) {
                    binding.homeGpuChat.setData(100.toFloat(), (100 - gpuLoad).toFloat())
                }
                if (loads.containsKey(-1)) {
                    binding.cpuCoreTotalLoad.text = "负载：" + loads[-1]!!.toInt().toString() + "%"
                    binding.homeCpuChat.setData(100.toFloat(), (100 - loads[-1]!!.toInt()).toFloat())
                }
                if (binding.cpuCoreList.adapter == null) {
                    val layoutParams = binding.cpuCoreList.layoutParams
                    if (cores.size < 6) {
                        layoutParams.height = dp2px(105 * 2F)
                        binding.cpuCoreList.numColumns = 2
                    } else if (cores.size > 12) {
                        layoutParams.height = dp2px(105 * 4F)
                    } else if (cores.size > 8) {
                        layoutParams.height = dp2px(105 * 3F)
                    } else {
                        layoutParams.height = dp2px(105 * 2F)
                    }
                    binding.cpuCoreList.layoutParams = layoutParams
                    binding.cpuCoreList.adapter = AdapterCpuCores(context!!, cores)
                } else {
                    (binding.cpuCoreList.adapter as AdapterCpuCores).setData(cores)
                }

            } catch (ex: Exception) {

            }
        }
        updateTick++
        if (updateTick > 5) {
            updateTick = 0
            minFreqs.clear()
            maxFreqs.clear()
        }
    }

    private fun setModeState() {
        binding.btnPowersave.alpha = 0.4f
        binding.btnDefaultmode.alpha = 0.4f
        binding.btnGamemode.alpha = 0.4f
        binding.btnFastmode.alpha = 0.4f
        when (ModeSwitcher.getCurrentPowerMode()) {
            ModeSwitcher.BALANCE -> {
                binding.btnDefaultmode.alpha = 1f
            }
            ModeSwitcher.PERFORMANCE -> {
                binding.btnGamemode.alpha = 1f
            }
            ModeSwitcher.POWERSAVE -> {
                binding.btnPowersave.alpha = 1f
            }
            ModeSwitcher.FAST -> {
                binding.btnFastmode.alpha = 1f
            }
        }
    }

    private fun stopTimer() {
        if (this.timer != null) {
            timer!!.cancel()
            timer = null
        }
    }

    override fun onPause() {
        stopTimer()
        super.onPause()
    }

    private fun toggleMode(modeSwitcher: ModeSwitcher, mode: String): Deferred<Unit> {
        return GlobalScope.async {
            context?.run {
                if (modeSwitcher.modeConfigCompleted()) {
                    modeSwitcher.executePowercfgMode(mode, packageName)
                } else {
                    CpuConfigInstaller().installOfficialConfig(context!!)
                    modeSwitcher.executePowercfgMode(mode, packageName)
                }
            }
        }
    }

    private fun installConfig(toMode: String) {
        val dynamic = AccessibleServiceHelper().serviceRunning(context!!) && spf.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL, SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL_DEFAULT)
        if (!dynamic && ModeSwitcher.getCurrentPowerMode() == toMode) {
            modeSwitcher.setCurrent("", "")
            globalSPF.edit().putString(SpfConfig.GLOBAL_SPF_POWERCFG, "").apply()
            myHandler.post {
                DialogHelper.confirmBlur(this.activity!!,
                        "提示",
                        "需要重启手机才能恢复默认调度，是否立即重启？",
                        {
                            KeepShellPublic.doCmdSync("sync\nsleep 1\nsvc power reboot || reboot")
                        },
                        null)
                setModeState()
            }
            return
        }

        val progressBarDialog = ProgressBarDialog(this.activity!!, "home-mode-switch")
        progressBarDialog.showDialog(getString(R.string.please_wait))

        GlobalScope.launch(Dispatchers.Main) {
            toggleMode(modeSwitcher, toMode).await()

            setModeState()
            maxFreqs.clear()
            minFreqs.clear()
            updateInfo()
            if (dynamic) {
                globalSPF.edit().putString(SpfConfig.GLOBAL_SPF_POWERCFG, "").apply()
                DialogHelper.alert(activity!!,
                        "提示",
                        "“场景模式-动态响应”已被激活，你手动选择的模式随时可能被覆盖。\n\n如果你需要长期使用手动控制，请前往“功能”菜单-“性能界面”界面关闭“动态响应”！")
            } else {
                globalSPF.edit().putString(SpfConfig.GLOBAL_SPF_POWERCFG, toMode).apply()
                if (!globalSPF.getBoolean(SpfConfig.GLOBAL_SPF_POWERCFG_FRIST_NOTIFY, false)) {
                    DialogHelper.confirm(activity!!,
                            "提示",
                            "如果你已允许Scene自启动，手机重启后，Scene还会自动激活刚刚选择的模式。\n\n如果需要恢复系统默认调度，请再次点击，然后重启手机！",
                            DialogHelper.DialogButton(getString(R.string.btn_confirm)),
                            DialogHelper.DialogButton(getString(R.string.btn_dontshow), {
                                globalSPF.edit().putBoolean(SpfConfig.GLOBAL_SPF_POWERCFG_FRIST_NOTIFY, true).apply()
                            })).setCancelable(false)
                }
            }

            progressBarDialog.hideDialog()
        }
    }
}
