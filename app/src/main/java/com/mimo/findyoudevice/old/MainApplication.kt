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
        // 全局兜底：捕获主线程未处理异常，避免"一点开就闪退"且无提示
        runCatching {
            Thread.setDefaultUncaughtExceptionHandler { _, e ->
                runCatching {
                    val sw = java.io.StringWriter()
                    e.printStackTrace(java.io.PrintWriter(sw))
                    val f = java.io.File(getExternalFilesDir(null), "crash.log")
                    f.appendText("\n=== ${System.currentTimeMillis()} ===\n$sw\n")
                }
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        runCatching { DeviceStore.init(this) }
        runCatching { NetworkAutoControl.register(this) }
    }
}
