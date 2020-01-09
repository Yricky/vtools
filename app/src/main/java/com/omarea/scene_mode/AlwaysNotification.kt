package com.omarea.scene_mode

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.RemoteViews
import com.omarea.model.BatteryStatus
import com.omarea.store.BatteryHistoryStore
import com.omarea.store.SpfConfig
import com.omarea.utils.GlobalStatus
import com.omarea.vtools.R
import com.omarea.vtools.activities.ActivityQuickSwitchMode

/**
 * 常驻通知
 */
internal class AlwaysNotification(private var context: Context, notify: Boolean = false) : ModeSwitcher() {
    private var showNofity: Boolean = false
    private var notification: Notification? = null
    private var notificationManager: NotificationManager? = null
    private var batteryHistoryStore: BatteryHistoryStore? = null
    private var globalSPF = context.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
    private var batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private fun getAppName(packageName: String): CharSequence? {
        try {
            return context.packageManager.getPackageInfo(packageName, 0).applicationInfo.loadLabel(context.packageManager)
        } catch (ex: Exception) {
            return packageName
        }
    }

    private fun getBatteryIcon(capacity: Int): Int {
        if (capacity < 20)
            return R.drawable.b_0
        if (capacity < 30)
            return R.drawable.b_1
        if (capacity < 70)
            return R.drawable.b_2
        return R.drawable.b_3
    }

    //显示通知
    internal fun notify(saveLog:Boolean = false) {
        try {
            var currentMode = getCurrentPowerMode()
            if (currentMode.length == 0) {
                currentMode = ""
            }

            var currentApp = getCurrentPowermodeApp()
            if (currentApp.length == 0) {
                currentApp = context.packageName
            }

            notifyPowerModeChange(currentApp, currentMode, saveLog)
        } catch (ex: Exception) {
            Log.e("NotifyHelper", "" + ex.localizedMessage)
        }
    }

    private fun updateBatteryStatus() {
        // 电池电流
        val batteryCurrentNow = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        val batteryCurrentNowMA = (batteryCurrentNow / globalSPF.getInt(SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT, SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT_DEFAULT))

        // 电量
        val batteryCapacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        GlobalStatus.batteryCurrentNow = batteryCurrentNowMA;
        GlobalStatus.batteryCapacity = batteryCapacity;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 状态
            val batteryStatus = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            GlobalStatus.batteryStatus = batteryStatus;
        }
    }

    private fun notifyPowerModeChange(packageName: String, mode: String, saveLog: Boolean = false) {
        if (!showNofity) {
            return
        }

        var batteryImage: Bitmap? = null
        var batteryIO: String? = ""
        var batteryTemp = ""
        var modeImage = BitmapFactory.decodeResource(context.resources, getModImage(mode))

        try {
            updateBatteryStatus();

            batteryIO = "${GlobalStatus.batteryCurrentNow}mA"
            batteryTemp = "${GlobalStatus.batteryTemperature}°C"

            if (GlobalStatus.batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING) {
                batteryImage = BitmapFactory.decodeResource(context.resources, getBatteryIcon(GlobalStatus.batteryCapacity))
            } else {
                batteryImage = BitmapFactory.decodeResource(context.resources, R.drawable.b_4)
            }
            modeImage = BitmapFactory.decodeResource(context.resources, getModImage(mode))
        } catch (ex: Exception) {
            Log.e("NotifyHelper", "" + ex.message)
        }

        if (batteryHistoryStore == null) {
            batteryHistoryStore = BatteryHistoryStore(context)
        }

        if (saveLog && GlobalStatus.batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            val status = BatteryStatus()
            status.packageName = packageName
            status.mode = mode
            status.time = System.currentTimeMillis()
            status.temperature = GlobalStatus.batteryTemperature
            status.status = GlobalStatus.batteryStatus
            status.io = GlobalStatus.batteryCurrentNow.toInt()

            batteryHistoryStore!!.insertHistory(status)
        }

        val remoteViews = RemoteViews(context.packageName, R.layout.layout_notification)
        remoteViews.setTextViewText(R.id.notify_title, getAppName(packageName))
        remoteViews.setTextViewText(R.id.notify_text, getModName(mode))
        remoteViews.setTextViewText(R.id.notify_battery_text, "$batteryIO ${GlobalStatus.batteryCapacity}% $batteryTemp")
        if (modeImage != null) {
            remoteViews.setImageViewBitmap(R.id.notify_mode, modeImage)
        }
        if (batteryImage != null) {
            remoteViews.setImageViewBitmap(R.id.notify_battery_icon, batteryImage)
        }

        val intent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, ActivityQuickSwitchMode::class.java).putExtra("packageName", packageName),
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val icon = getModIcon(mode)
        notificationManager = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        var builder: NotificationCompat.Builder? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager!!.getNotificationChannel("vtool-long-time") == null) {
                notificationManager!!.createNotificationChannel(NotificationChannel("vtool-long-time", "常驻通知", NotificationManager.IMPORTANCE_LOW))
            }
            builder = NotificationCompat.Builder(context, "vtool-long-time")
        } else {
            builder = NotificationCompat.Builder(context)
        }
        notification =
                builder.setSmallIcon(if (false) R.drawable.fanbox else icon)
                        .setContent(remoteViews)
                        .setWhen(System.currentTimeMillis())
                        .setAutoCancel(true)
                        //.setDefaults(Notification.DEFAULT_SOUND)
                        .setContentIntent(intent)
                        .build()

        notification!!.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        notificationManager?.notify(0x100, notification)
    }

    //隐藏通知
    internal fun hideNotify() {
        if (notification != null) {
            notificationManager?.cancel(0x100)
            notification = null
            notificationManager = null
        }
    }

    internal fun setNotify(show: Boolean) {
        this.showNofity = show
        if (!show) {
            hideNotify()
        } else {
            notify()
        }
    }

    init {
        showNofity = notify
    }
}
