package com.omarea.vboot

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.KeyEvent
import android.view.View
import android.widget.SimpleAdapter
import android.widget.Spinner
import android.widget.Switch
import com.omarea.shared.Consts
import com.omarea.shared.ServiceHelper
import com.omarea.shared.SpfConfig
import com.omarea.shell.Platform
import com.omarea.ui.ProgressBarDialog
import kotlinx.android.synthetic.main.activity_accessibility_key_event_settings.*
import java.io.File
import java.util.ArrayList
import java.util.HashMap

class ActivityAccessibilityKeyEventSettings : AppCompatActivity() {
    private lateinit var spf: SharedPreferences
    private var myHandler = Handler()

    override fun onPostResume() {
        super.onPostResume()
        delegate.onPostResume()
    }

    @SuppressLint("ApplySharedPref")
    override fun onCreate(savedInstanceState: Bundle?) {
        spf = getSharedPreferences(SpfConfig.KEY_EVENT_SPF, Context.MODE_PRIVATE)
        if (getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE).getBoolean(SpfConfig.GLOBAL_SPF_NIGHT_MODE, false))
            this.setTheme(R.style.AppTheme_NoActionBarNight)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessibility_key_event_settings)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        setSwitchClick()
    }

    @SuppressLint("ApplySharedPref")
    fun setSwitchClick() {
        val accessibilityEnabled = Settings.Secure.getInt(getApplicationContext().getContentResolver(), android.provider.Settings.Secure.ACCESSIBILITY_ENABLED)
        if (accessibilityEnabled === 1) {
            val settingValue = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }
        val tags = arrayListOf("3", "4", "82", "187")
        val listItem = ArrayList<HashMap<String, Any>>().apply {
            add(HashMap<String, Any>().apply {
                put("text", "默认")
                put("key", Int.MIN_VALUE)
            })
            add(HashMap<String, Any>().apply {
                put("text", "返回")
                put("key", AccessibilityService.GLOBAL_ACTION_BACK)
            })
            add(HashMap<String, Any>().apply {
                put("text", "主页")
                put("key", AccessibilityService.GLOBAL_ACTION_HOME)
            })
            /*
            add(HashMap<String, Any>().apply {
                put("text", "菜单")
                put("key", AccessibilityService.)
            })
            */
            add(HashMap<String, Any>().apply {
                put("text", "最近任务")
                put("key", AccessibilityService.GLOBAL_ACTION_RECENTS)
            })
            add(HashMap<String, Any>().apply {
                put("text", "电源界面")
                put("key", AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add(HashMap<String, Any>().apply {
                    put("text", "分屏")
                        put("key", AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                })
            }
        }
        val adapter = SimpleAdapter(this, listItem,
                R.layout.text_item,
                arrayOf("text"),
                intArrayOf(R.id.text))
        for (tag in tags) {
            val switch = key_sets.findViewWithTag<Switch>(tag)
            val click = key_sets.findViewWithTag<Spinner>(switch.tag.toString() + "_click")
            val longClick = key_sets.findViewWithTag<Spinner>(switch.tag.toString() + "_long_click")
            click.adapter = adapter
            longClick.adapter = adapter
            switch.setOnClickListener {
                if(switch.isChecked) {
                    KeyEvent.KEYCODE_HOME
                    val clickValue = (click.selectedItem as HashMap<*, *>).get("key") as Int
                    val longClickValue = (longClick.selectedItem as HashMap<*, *>).get("key") as Int
                    spf.edit()
                            .putInt(switch.tag.toString() + "_click", clickValue)
                            .putInt(switch.tag.toString() + "_long_click", longClickValue)
                            .commit()
                } else {
                    spf.edit()
                            .remove(switch.tag.toString() + "_click")
                            .remove(switch.tag.toString() + "_long_click")
                            .commit()
                }
            }
        }
    }

    public override fun onPause() {
        super.onPause()
    }
}
