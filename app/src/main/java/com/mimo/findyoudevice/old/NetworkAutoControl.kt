package com.mimo.findyoudevice.old

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * 网络场景自动控制（API 21 兼容）：
 *  - 连接 WiFi（非计量）→ 自动开启服务（仅当用户此前希望开启）
 *  - 连接移动流量 → 自动关闭服务
 *  - 连接热点 / 计量网络 → 自动关闭服务
 *
 * ⚠️ 兼容性：所有网络状态判断走 [NetworkCompat]，
 * 避免在 Android 5.x 上调用 API23+ 的 getActiveNetwork()（会 NoSuchMethodError 崩溃）。
 */
object NetworkAutoControl {

    private var registered = false
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastDecisionMs = 0L

    fun register(context: Context) {
        if (registered) return
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = schedule(app)
            override fun onLost(network: Network) = schedule(app)
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = schedule(app)
        }
        // registerNetworkCallback(NetworkRequest, cb) 自 API 21 起可用
        val ok = runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, cb)
            true
        }.getOrDefault(false)
        if (ok) {
            callback = cb
            registered = true
            schedule(app)
        }
    }

    fun unregister(context: Context) {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        callback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
        callback = null
        registered = false
    }

    /** 延迟 400ms 后在主线程评估（等待网络栈就绪；500ms 防抖去重） */
    private fun schedule(app: Context) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ evaluate(app) }, 400)
    }

    /** 核心决策：按当前默认网络类型与设置开关，自动开/关服务（永不抛异常） */
    fun evaluate(context: Context) {
        runCatching {
            val app = context.applicationContext
            val now = System.currentTimeMillis()
            if (now - lastDecisionMs < 500) return
            lastDecisionMs = now

            // ★ 走 NetworkCompat：API21 用 NetworkInfo，API23+ 用 NetworkCapabilities
            val s = NetworkCompat.current(app)
            val serviceRunning = Prefs.sp(app).getBoolean(HostService.KEY_WEB_RUNNING, false)

            when {
                // ① 自动开：WiFi（非计量）+ 用户希望开启 + 服务未运行
                s.isWifi && !s.metered &&
                    Prefs.getAutoStartWifi(app) && Prefs.getUserWantWeb(app) && !serviceRunning -> {
                    HostService.start(app)
                }
                // ② 自动关：移动流量
                s.isCellular && Prefs.getAutoStopCellular(app) && serviceRunning -> {
                    HostService.stop(app)
                }
                // ③ 自动关：热点 / 计量网络
                (s.isWifi || s.isEthernet) && s.metered &&
                    Prefs.getAutoStopMetered(app) && serviceRunning -> {
                    HostService.stop(app)
                }
            }
        }
    }
}
