package com.mimo.findyoudevice.old

import android.app.Application

/**
 * 应用入口：初始化轻量存储（SP+JSON）与网络自动控制。
 * 老版无 Room；对应逻辑由 [DeviceStore] 承担。
 */
class MainApplication : Application() {

    companion object {
        @Volatile
        lateinit var instance: MainApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        DeviceStore.init(this)
        NetworkAutoControl.register(this)
    }
}
