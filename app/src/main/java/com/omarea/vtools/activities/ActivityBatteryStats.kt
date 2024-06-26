package com.omarea.vtools.activities

import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.omarea.store.BatteryHistoryStore
import com.omarea.ui.AdapterBatteryStats
import com.omarea.vtools.R
import com.omarea.vtools.databinding.ActivityBatteryStatsBinding
import java.util.*
import kotlin.math.abs


class ActivityBatteryStats : ActivityBase() {
    private lateinit var binding:ActivityBatteryStatsBinding
    private lateinit var storage: BatteryHistoryStore
    private var timer: Timer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatteryStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setBackArrow()
        onViewCreated()
    }

    private fun onViewCreated() {
        storage = BatteryHistoryStore(context)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.delete, menu)
        return true
    }

    //右上角菜单
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_delete -> {
                BatteryHistoryStore(context).clearData()
                Toast.makeText(context, "统计记录已清理", Toast.LENGTH_SHORT).show()
                loadData()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        loadData()
        title = getString(R.string.menu_battery_stats)
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    updateMaxState()
                }
            }, 0, 200)
        }
    }

    override fun onPause() {
        timer?.cancel()
        timer = null
        super.onPause()
    }

    private val hander = Handler(Looper.getMainLooper())
    private fun updateMaxState() {
        // 峰值设置
        val maxInput = abs(storage.getMaxIO(BatteryManager.BATTERY_STATUS_CHARGING))
        val maxOutput = abs(storage.getMinIO(BatteryManager.BATTERY_STATUS_DISCHARGING))
        val maxTemperature = abs(storage.getMaxTemperature())
        var batteryInputMax = 10000
        var batteryOutputMax = 3000
        var batteryTemperatureMax = 60

        if (maxInput > batteryInputMax) {
            batteryInputMax = maxInput
        }
        if (maxOutput > batteryOutputMax) {
            batteryOutputMax = maxOutput
        }
        if (maxTemperature > batteryTemperatureMax) {
            batteryTemperatureMax = maxTemperature
        }

        hander.post {
            try {
                binding.batteryMaxOutput.setData(batteryOutputMax.toFloat(), batteryOutputMax - maxOutput.toFloat())
                binding.batteryMaxOutputText.text = maxOutput.toString() + " mA"
                binding.batteryMaxIntput.setData(batteryInputMax.toFloat(), batteryInputMax - maxInput.toFloat())
                binding.batteryMaxIntputText.text = maxInput.toString() + " mA"
                if (maxTemperature < 0) {
                    binding.batteryMaxTemperature.setData(batteryTemperatureMax.toFloat(), batteryTemperatureMax.toFloat())
                } else {
                    binding.batteryMaxTemperature.setData(batteryTemperatureMax.toFloat(), batteryTemperatureMax - maxTemperature.toFloat())
                }
                binding.batteryMaxTemperatureText.text = maxTemperature.toString() + "°C"
            } catch (ex: Exception) {
                timer?.cancel()
                timer = null
            }
        }
    }

    private fun loadData() {
        val storage = BatteryHistoryStore(context)
        val data = storage.getAvgData(BatteryManager.BATTERY_STATUS_DISCHARGING)

        val sampleTime = 6
        binding.batteryStats.adapter = AdapterBatteryStats(context, (data.filter {
            // 仅显示运行时间超过2分钟的应用数据，避免误差过大
            (it.count * sampleTime) > 120
        }), sampleTime)
    }
}
