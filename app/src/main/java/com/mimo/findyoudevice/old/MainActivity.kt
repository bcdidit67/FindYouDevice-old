package com.mimo.findyoudevice.old

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * FindYouDevice-old 主入口（阶段1 空壳）。
 * Metro UI 磁贴主页将在阶段3实现。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    companion object {
        /** 持久化：SharedPreferences 文件名 */
        const val SP_NAME = "fyd_prefs"
        /** mode 键：存字符串 host | client */
        const val KEY_MODE = "mode"
        const val MODE_HOST = "host"
        const val MODE_CLIENT = "client"
    }
}
