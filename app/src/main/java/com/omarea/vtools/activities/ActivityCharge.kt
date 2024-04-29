package com.omarea.vtools.activities

import android.annotation.SuppressLint
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.omarea.data.GlobalStatus
import com.omarea.store.ChargeSpeedStore
import com.omarea.vtools.R
import com.omarea.vtools.databinding.ActivityChargeBinding
import com.omarea.vtools.dialogs.DialogElectricityUnit
import java.util.*

class ActivityCharge : ActivityBase() {
    private lateinit var binding:ActivityChargeBinding
    private lateinit var storage: ChargeSpeedStore
    private var timer: Timer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChargeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setBackArrow()

        storage = ChargeSpeedStore(this)
        binding.electricityAdjUnit.setOnClickListener {
            DialogElectricityUnit().showDialog(this)
        }
    }

    override fun onResume() {
        super.onResume()
        title = getString(R.string.menu_charge)
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    updateUI()
                }
            }, 0, 1000)
        }
    }

    override fun onPause() {
        timer?.cancel()
        timer = null
        super.onPause()
    }

    private val sumInfo: String
        get() {
            val sum = storage.sum
            var sumInfo = ""
            if (sum != 0) {
                sumInfo = getString(R.string.battery_status_sum).format((if (sum > 0) ("+" + sum) else (sum.toString())))
            }
            return sumInfo
        }

    private val hander = Handler(Looper.getMainLooper())
    @SuppressLint("SetTextI18n")
    private fun updateUI() {
        hander.post {
            binding.viewSpeed.invalidate()
            binding.viewTime.invalidate()
            binding.viewTemperature.invalidate()

            binding.chargeState.text = (when (GlobalStatus.batteryStatus) {
                BatteryManager.BATTERY_STATUS_DISCHARGING -> {
                    getString(R.string.battery_status_discharging)
                }
                BatteryManager.BATTERY_STATUS_CHARGING -> {
                    getString(R.string.battery_status_charging)
                }
                BatteryManager.BATTERY_STATUS_FULL -> {
                    getString(R.string.battery_status_full)
                }
                BatteryManager.BATTERY_STATUS_UNKNOWN -> {
                    getString(R.string.battery_status_unknown)
                }
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> {
                    getString(R.string.battery_status_not_charging)
                }
                else -> getString(R.string.battery_status_unknown)
            }) + sumInfo
        }
    }
}
