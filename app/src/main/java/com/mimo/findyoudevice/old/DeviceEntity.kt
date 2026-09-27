package com.mimo.findyoudevice.old

/**
 * 客户端"已添加的主机设备"数据模型。
 * 老版无 Room：纯数据类，持久化由 [DeviceStore]（SP + JSON）负责。
 */
data class DeviceEntity(
    val uid: Long = 0L,
    /** 用户自定义"易记名称" */
    val alias: String,
    /** 主机局域网 IPv4 地址 */
    val ip: String,
    /** 主机型号（扫描 /info 自动获取，手动添加可空） */
    val model: String? = null,
    /** 上次被查找时间戳（epoch ms），0 = 从未 */
    val lastFindTime: Long = 0L,
    /** 在线/离线标识（最近一次探测结果） */
    val isOnline: Boolean = false,
)
