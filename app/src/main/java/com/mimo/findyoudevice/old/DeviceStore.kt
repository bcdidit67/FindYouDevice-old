package com.mimo.findyoudevice.old

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 设备列表轻量存储（SharedPreferences + JSON）：替代 Room。
 * 所有方法线程安全（synchronized）；变更后同步发布到 [flow]（RecyclerView 数据源）。
 */
object DeviceStore {

    private const val SP_NAME = "fyd_old_devices"
    private const val KEY_DEVICES = "devices_json"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _flow = MutableStateFlow<List<DeviceEntity>>(emptyList())
    val flow: StateFlow<List<DeviceEntity>> = _flow

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        prefs = p
        _flow.value = parse(p.getString(KEY_DEVICES, null))
    }

    @Synchronized
    fun getAllOnce(): List<DeviceEntity> = _flow.value

    @Synchronized
    fun getAllFlow(): StateFlow<List<DeviceEntity>> = flow

    @Synchronized
    fun findByIp(ip: String): DeviceEntity? = _flow.value.firstOrNull { it.ip == ip }

    @Synchronized
    fun findById(uid: Long): DeviceEntity? = _flow.value.firstOrNull { it.uid == uid }

    @Synchronized
    fun countByIp(ip: String): Int = _flow.value.count { it.ip == ip }

    /** 插入（uid 自动分配），返回新 uid */
    @Synchronized
    fun insert(device: DeviceEntity): Long {
        val list = _flow.value.toMutableList()
        val newUid = if (device.uid != 0L) device.uid else (list.maxOfOrNull { it.uid } ?: 0L) + 1
        list.add(device.copy(uid = newUid))
        persist(list)
        return newUid
    }

    @Synchronized
    fun update(device: DeviceEntity) {
        persist(_flow.value.map { if (it.uid == device.uid) device else it })
    }

    @Synchronized
    fun updateOnline(uid: Long, online: Boolean) {
        persist(_flow.value.map { if (it.uid == uid) it.copy(isOnline = online) else it })
    }

    @Synchronized
    fun updateOnlineByIp(ip: String, online: Boolean) {
        persist(_flow.value.map { if (it.ip == ip) it.copy(isOnline = online) else it })
    }

    @Synchronized
    fun updateLastFind(uid: Long, stamp: Long) {
        persist(_flow.value.map { if (it.uid == uid) it.copy(lastFindTime = stamp, isOnline = true) else it })
    }

    @Synchronized
    fun deleteByUid(uid: Long) {
        persist(_flow.value.filterNot { it.uid == uid })
    }

    @Synchronized
    fun deleteByIp(ip: String) {
        persist(_flow.value.filterNot { it.ip == ip })
    }

    @Synchronized
    fun deleteAll() {
        persist(emptyList())
    }

    private fun persist(list: List<DeviceEntity>) {
        _flow.value = list.toList()
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("uid", d.uid)
                put("alias", d.alias)
                put("ip", d.ip)
                put("model", d.model ?: JSONObject.NULL)
                put("lastFindTime", d.lastFindTime)
                put("isOnline", d.isOnline)
            })
        }
        prefs?.edit()?.putString(KEY_DEVICES, arr.toString())?.apply()
    }

    private fun parse(raw: String?): List<DeviceEntity> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DeviceEntity(
                    uid = o.optLong("uid"),
                    alias = o.optString("alias"),
                    ip = o.optString("ip"),
                    model = if (o.isNull("model")) null else o.optString("model"),
                    lastFindTime = o.optLong("lastFindTime"),
                    isOnline = o.optBoolean("isOnline"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
