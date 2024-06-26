package com.omarea.vtools.activities

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.omarea.Scene
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ThemeMode
import com.omarea.permissions.Busybox
import com.omarea.permissions.CheckRootStatus
import com.omarea.permissions.WriteSettings
import com.omarea.store.SpfConfig
import com.omarea.vtools.R
import com.omarea.vtools.databinding.ActivityStartSplashBinding
import java.util.*

class ActivityStartSplash : Activity() {
    companion object {
        public var finished = false
    }

    private lateinit var binding:ActivityStartSplashBinding
    private lateinit var globalSPF: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        globalSPF = getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        val themeMode = ThemeSwitch.switchTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityStartSplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateThemeStyle(themeMode)

        checkPermissions()
    }

    /**
     * 协议 同意与否
     */
    private fun initContractAction() {
        val view = layoutInflater.inflate(R.layout.dialog_danger_agreement, null)
        val dialog = DialogHelper.customDialog(this, view, false)
        val btnConfirm = view.findViewById<Button>(R.id.btn_confirm)
        val agreement = view.findViewById<CompoundButton>(R.id.agreement)
        val timer = Timer()
        var timeout = 5
        var clickItems = 0
        timer.schedule(object : TimerTask() {
            override fun run() {
                Scene.post {
                    if (timeout > 0) {
                        timeout --
                        btnConfirm.text = timeout.toString() + "s"
                    } else {
                        timer.cancel()
                        btnConfirm.text = "同意继续"
                    }
                }
            }
        }, 0, 1000)
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            timer.cancel()
            dialog.dismiss()
            finish()
        }
        btnConfirm.setOnClickListener {
            if (!agreement.isChecked) {
                return@setOnClickListener
            }
            if (timeout > 0 && clickItems < 10) { // 连点10次允许跳过倒计时
                clickItems++
                return@setOnClickListener
            }

            timer.cancel()
            dialog.dismiss()
            globalSPF.edit().putBoolean(SpfConfig.GLOBAL_SPF_CONTRACT, true).apply()
            checkPermissions()
        }
    }

    /**
     * 界面主题样式调整
     */
    private fun updateThemeStyle(themeMode: ThemeMode) {
        if (themeMode.isDarkMode) {
            binding.splashRoot.setBackgroundColor(Color.argb(255, 0, 0, 0))
            getWindow().setNavigationBarColor(Color.argb(255, 0, 0, 0))
        } else {
            // getWindow().setNavigationBarColor(getColorAccent())
            binding.splashRoot.setBackgroundColor(Color.argb(255, 255, 255, 255))
            getWindow().setNavigationBarColor(Color.argb(255, 255, 255, 255))
        }

        //  得到当前界面的装饰视图
        if (Build.VERSION.SDK_INT >= 21) {
            val decorView = getWindow().getDecorView();
            //让应用主题内容占用系统状态栏的空间,注意:下面两个参数必须一起使用 stable 牢固的
            val option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            decorView.setSystemUiVisibility(option);
            //设置状态栏颜色为透明
            getWindow().setStatusBarColor(Color.TRANSPARENT)
        }
    }

    private fun getColorAccent(): Int {
        val typedValue = TypedValue()
        this.theme.resolveAttribute(R.attr.colorAccent, typedValue, true)
        return typedValue.data
    }

    /**
     * 开始检查必需权限
     */
    private fun checkPermissions() {
        checkRoot()
    }

    private inner class CheckFileWirte : Runnable {
        override fun run() {
            binding.startStateText.text = "检查并获取必需权限……"
            hasRoot = true

            checkFileWrite(InstallBusybox())
        }

    }

    private inner class InstallBusybox() : Runnable {
        override fun run() {
            binding.startStateText.text = "检查Busybox是否安装..."
            Busybox(this@ActivityStartSplash).forceInstall(BusyboxInstalled(this@ActivityStartSplash))
        }

    }

    private class BusyboxInstalled(private val context: ActivityStartSplash) : Runnable {
        override fun run() {
            context.startToFinish()
        }

    }

    private fun checkPermission(permission: String): Boolean = PermissionChecker.checkSelfPermission(this.applicationContext, permission) == PermissionChecker.PERMISSION_GRANTED

    /**
     * 检查权限 主要是文件读写权限
     */
    private fun checkFileWrite(next: Runnable) {
        Thread {
            CheckRootStatus.grantPermission(this)
            if (!(checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) && checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    ActivityCompat.requestPermissions(
                            this@ActivityStartSplash,
                            arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                                    Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Manifest.permission.WAKE_LOCK
                            ),
                            0x11
                    )
                } else {
                    ActivityCompat.requestPermissions(
                            this@ActivityStartSplash,
                            arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                                    Manifest.permission.WAKE_LOCK
                            ),
                            0x11
                    )
                }
            }
            myHandler.post {
                val writeSettings = WriteSettings()
                if (!writeSettings.getPermission(applicationContext)) {
                    writeSettings.setPermission(applicationContext)
                }
                next.run()
            }
        }.start()
    }

    private var hasRoot = false
    private var myHandler = Handler(Looper.getMainLooper())

    private fun checkRoot() {
        val disableSeLinux = globalSPF.getBoolean(SpfConfig.GLOBAL_SPF_DISABLE_ENFORCE, false)
        CheckRootStatus(this, Runnable {
            if (globalSPF.getBoolean(SpfConfig.GLOBAL_SPF_CONTRACT, false)) {
                CheckFileWirte().run()
            } else {
                initContractAction()
            }
        }, disableSeLinux, InstallBusybox()).forceGetRoot()
    }

    /**
     * 启动完成
     */
    private fun startToFinish() {
        binding.startStateText.text = "启动完成！"

        val intent = Intent(this.applicationContext, ActivityMain::class.java)
        startActivity(intent)
        finished = true
        finish()
    }
}