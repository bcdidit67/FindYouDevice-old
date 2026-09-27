package com.mimo.findyoudevice.old

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * 网络类型判断（API 21 兼容）。
 *
 * 背景：ConnectivityManager.getActiveNetwork() / getNetworkCapabilities() 需要 API 23，
 * 在 Android 5.0/5.1 上直接调用会抛 NoSuchMethodError（崩溃）。本类提供两条路径：
 *
 *  - API 23+：使用 NetworkCapabilities（精确区分 WiFi / 蜂窝 / 以太 / 计量）
 *  - API 21-22：使用已废弃但可用的 NetworkInfo（TYPE_WIFI / TYPE_MOBILE）+ 反射调用
 *    没有的成员一律不触碰，避免 NoSuchMethodError
 */
object NetworkCompat {

    data class NetState(
        val available: Boolean,
        val isWifi: Boolean,
        val isCellular: Boolean,
        val isEthernet: Boolean,
        /** 计量网络（移动流量 / 收费热点）；API21-22 近似判断 */
        val metered: Boolean,
    )

    fun current(context: Context): NetState = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            currentModern(context)
        } else {
            currentLegacy(context)
        }
    }.getOrElse { NetState(false, false, false, false, false) }

    // ---------------- API 23+（由 current() 做版本守卫） ----------------
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private fun currentModern(context: Context): NetState {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return NetState(false, false, false, false, false)
        val net = cm.activeNetwork ?: return NetState(false, false, false, false, false)
        val caps = cm.getNetworkCapabilities(net)
            ?: return NetState(false, false, false, false, false)
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val eth = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return NetState(true, wifi, cell, eth, metered)
    }

    // ---------------- API 21-22（不使用任何 API23+ 方法） ----------------
    @Suppress("DEPRECATION")
    private fun currentLegacy(context: Context): NetState {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return NetState(false, false, false, false, false)
        // getAllNetworkInfo() 是 API 1 起就有的（API 23 废弃但可用），不触碰 getActiveNetwork
        val all: Array<android.net.NetworkInfo> = try {
            cm.allNetworkInfo ?: emptyArray()
        } catch (e: Throwable) {
            emptyArray()
        }
        var isWifi = false
        var isCell = false
        var isEth = false
        var connected = false
        for (info in all) {
            if (info == null || !info.isConnected) continue
            connected = true
            when (info.type) {
                ConnectivityManager.TYPE_WIFI -> isWifi = true
                ConnectivityManager.TYPE_MOBILE -> isCell = true
                ConnectivityManager.TYPE_ETHERNET -> isEth = true
            }
        }
        // 老 API 无法精确判断 METERED：近似处理——WiFi 视为非计量，蜂窝视为计量
        val metered = isCell && !isWifi
        return NetState(connected, isWifi, isCell, isEth, metered)
    }

    /** 供 UI 顶部状态显示 */
    fun label(context: Context): String {
        val s = current(context)
        return when {
            s.isWifi -> "WiFi"
            s.isCellular -> "流量"
            s.isEthernet -> "有线"
            s.available -> "网络"
            else -> "无网络"
        }
    }
}
