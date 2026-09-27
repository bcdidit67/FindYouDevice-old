package com.mimo.findyoudevice.old

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.mimo.findyoudevice.old.databinding.PageDevicesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 设备页（WP 磁贴网格 + 设备列表）。
 * 磁贴颜色全部由 [ThemeManager] 动态生成（跟随壁纸/纯色/自定义图片）。
 */
class DevicesFragment : Fragment() {

    private var _b: PageDevicesBinding? = null
    private val b get() = _b!!
    private lateinit var tileAdapter: TileAdapter
    private lateinit var deviceAdapter: DeviceAdapter
    private val scanBusy = AtomicBoolean(false)

    private val tiles = mutableListOf(
        Tile("web", "Web 服务", R.drawable.ic_desktop, 0, status = "已关闭", spanSize = 2),
        Tile("scan", "扫描局域网", R.drawable.ic_search, 0),
        Tile("add", "手动添加", R.drawable.ic_add, 0),
        Tile("test", "测试报警", R.drawable.ic_bell, 0, status = ""),
        Tile("local", "本机状态", R.drawable.ic_target, 0, status = "…", spanSize = 2),
    )

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = PageDevicesBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTiles()
        setupDevices()
        applyDynamicColors()
    }

    override fun onResume() {
        super.onResume()
        applyDynamicColors()
        refreshStatus()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    /** 动态着色的核心：磁贴 Drawable 全部由代码生成 */
    private fun applyDynamicColors() {
        val ctx = requireContext()
        tiles.forEach { t ->
            t.colorRes = 0 // 由 adapter 使用 Drawable
        }
        tileAdapter.dynamicColor = true
        tileAdapter.notifyDataSetChanged()
        // 状态文字颜色随主题（浅灰）
        b.tvStatus.setTextColor(0xFFAAAAAA.toInt())
    }

    private fun setupTiles() {
        val lm = GridLayoutManager(requireContext(), 2)
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = tiles[position].spanSize
        }
        b.rvTiles.layoutManager = lm
        tileAdapter = TileAdapter(tiles) { onTile(it) }.apply {
            dynamicColor = true
            useTranslucent = Prefs.getBgMode(requireContext()) != ThemeManager.BG_SOLID &&
                Prefs.isTransparentTiles(requireContext())
        }
        b.rvTiles.adapter = tileAdapter
    }

    private fun setupDevices() {
        deviceAdapter = DeviceAdapter { d -> DeviceActions.showDeviceActions(requireActivity() as androidx.appcompat.app.AppCompatActivity, d) }
        b.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        b.rvDevices.adapter = deviceAdapter
        viewLifecycleOwner.lifecycleScope.launch {
            DeviceStore.flow.collect { list ->
                deviceAdapter.submit(list)
                b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun onTile(t: Tile) {
        val act = requireActivity() as androidx.appcompat.app.AppCompatActivity
        when (t.id) {
            "scan" -> startScan()
            "add" -> DeviceActions.showAddDialog(act, null)
            "web" -> toggleWebService()
            "test" -> testAlarm()
            "local" -> onLocalTile()
        }
    }

    private fun startScan() {
        if (!scanBusy.compareAndSet(false, true)) return
        Toast.makeText(requireContext(), "正在扫描…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { LanScanner.scan(requireContext()) }
                Toast.makeText(
                    requireContext(),
                    if (list.isEmpty()) "未发现主机，请确认同网段目标已开启 Web 服务"
                    else "发现 ${list.size} 台主机",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "扫描异常：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                scanBusy.set(false)
            }
        }
    }

    private fun toggleWebService() {
        val ctx = requireContext()
        val want = Prefs.sp(ctx).getBoolean(HostService.KEY_WEB_RUNNING, false)
        if (want) {
            HostService.stop(ctx)
            Prefs.sp(ctx).edit().putBoolean(HostService.KEY_WEB_RUNNING, false).apply()
            Toast.makeText(ctx, "Web 服务已停止", Toast.LENGTH_SHORT).show()
            refreshStatus()
        } else {
            Prefs.sp(ctx).edit().putBoolean(HostService.KEY_WEB_RUNNING, true).apply()
            HostService.start(ctx)
            Toast.makeText(ctx, "正在启动 Web 服务…", Toast.LENGTH_SHORT).show()
            handler.postDelayed({
                if (!isPortOpen(HostService.DEFAULT_PORT)) {
                    Prefs.sp(ctx).edit().putBoolean(HostService.KEY_WEB_RUNNING, false).apply()
                    Toast.makeText(ctx, "启动失败：端口未进入监听状态", Toast.LENGTH_LONG).show()
                }
                refreshStatus()
            }, 3000)
        }
    }

    private fun testAlarm() {
        if (AlarmController.isRunning) {
            Toast.makeText(requireContext(), "已有报警进行中，请先停止", Toast.LENGTH_SHORT).show()
            return
        }
        AlarmController.start(requireContext(), false, 12_000L)
        Toast.makeText(requireContext(), "测试报警（12 秒自动停止）", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun onLocalTile() {
        val ctx = requireContext()
        if (AlarmController.isRunning) {
            AlarmController.stop()
            Toast.makeText(ctx, "已停止本机报警", Toast.LENGTH_SHORT).show()
            refreshStatus()
            return
        }
        val running = Prefs.sp(ctx).getBoolean(HostService.KEY_WEB_RUNNING, false) &&
            isPortOpen(HostService.DEFAULT_PORT)
        val lan = lanIpv4()
        if (running && lan != null) {
            copyText(ctx, "本机地址", "http://$lan:${HostService.DEFAULT_PORT}")
        } else {
            Toast.makeText(ctx, "本机 Web 服务未开启：点击「Web 服务」磁贴启动", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshStatus() {
        val bb = _b ?: return
        val ctx = requireContext()
        val lan = lanIpv4()
        bb.tvStatus.text = if (lan.isNullOrBlank()) "IP: 无网络" else "IP: $lan"
        val wantWeb = Prefs.sp(ctx).getBoolean(HostService.KEY_WEB_RUNNING, false)
        val webRunning = wantWeb && isPortOpen(HostService.DEFAULT_PORT)
        tiles.find { it.id == "web" }?.let { t ->
            t.dynamic = if (webRunning) Tile.TileAccent.BRIGHT else Tile.TileAccent.DARK
            t.status = if (webRunning) "已开启" else "已关闭"
        }
        tiles.find { it.id == "local" }?.let { t ->
            t.dynamic = if (AlarmController.isRunning) Tile.TileAccent.BRIGHT else Tile.TileAccent.DARK
            t.status = when {
                AlarmController.isLocked -> "锁定报警中 · 点击停止"
                AlarmController.isRunning -> "报警中 · 点击停止"
                else -> "空闲 · 点击复制地址"
            }
        }
        tileAdapter.notifyDataSetChanged()
    }

    private fun isPortOpen(port: Int): Boolean = runCatching {
        val s = Socket()
        try {
            s.connect(InetSocketAddress("127.0.0.1", port), 600)
            true
        } finally {
            runCatching { s.close() }
        }
    }.getOrDefault(false)

    private fun lanIpv4(): String? = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { ni ->
            if (!ni.isUp || ni.isLoopback) return@forEach
            Collections.list(ni.inetAddresses).forEach { a ->
                if (a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress) {
                    return a.hostAddress
                }
            }
        }
        null
    }.getOrNull()

    override fun onDestroyView() {
        handler.removeCallbacks(tick)
        super.onDestroyView()
        _b = null
    }
}
