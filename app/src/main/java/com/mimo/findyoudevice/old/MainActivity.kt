package com.mimo.findyoudevice.old

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.mimo.findyoudevice.old.databinding.ActivityMainBinding
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
 * FindYouDevice-old 主界面（Metro / WP10 风格）。
 *
 * - 磁贴网格：扫描 / 添加 / Web 服务 / 测试报警 / 本机状态 / 设置 / 关于
 * - 设备列表：DeviceStore(SP+JSON) 数据源，点击弹出 Metro 操作面板
 * - 2 秒轻量轮询刷新状态；无阴影 / 无圆角 / 仅背景色切换
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tileAdapter: TileAdapter
    private lateinit var deviceAdapter: DeviceAdapter
    private val scanBusy = AtomicBoolean(false)

    private val tiles = mutableListOf(
        Tile("scan", "扫描局域网", R.drawable.ic_search, R.drawable.tile_blue),
        Tile("add", "手动添加", R.drawable.ic_add, R.drawable.tile_blue),
        Tile("web", "Web 服务", R.drawable.ic_desktop, R.drawable.tile_gray, status = "已关闭"),
        Tile("test", "测试报警", R.drawable.ic_bell, R.drawable.tile_orange),
        Tile("local", "本机状态", R.drawable.ic_target, R.drawable.tile_gray, status = "…", spanSize = 2),
        Tile("settings", "设置", R.drawable.ic_settings, R.drawable.tile_blue),
        Tile("about", "关于", R.drawable.ic_stat_bell, R.drawable.tile_orange),
    )

    private val handler = Handler(Looper.getMainLooper())
    private val statusTick = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupTiles()
        setupDevices()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        handler.removeCallbacks(statusTick)
        handler.post(statusTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusTick)
    }

    private fun setupTiles() {
        val lm = GridLayoutManager(this, 2)
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = tiles[position].spanSize
        }
        binding.rvTiles.layoutManager = lm
        tileAdapter = TileAdapter(tiles) { onTile(it) }
        binding.rvTiles.adapter = tileAdapter
    }

    private fun setupDevices() {
        deviceAdapter = DeviceAdapter { d -> DeviceActions.showDeviceActions(this, d) }
        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = deviceAdapter
        lifecycleScope.launch {
            DeviceStore.flow.collect { list ->
                deviceAdapter.submit(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun onTile(t: Tile) {
        when (t.id) {
            "scan" -> startScan()
            "add" -> DeviceActions.showAddDialog(this, null)
            "web" -> toggleWebService()
            "test" -> testAlarm()
            "local" -> onLocalTile()
            "settings" -> startActivity(Intent(this, SettingsActivity::class.java))
            "about" -> showAbout()
        }
    }

    // ---------------- 扫描局域网 ----------------
    private fun startScan() {
        if (!scanBusy.compareAndSet(false, true)) return
        Toast.makeText(this, "正在扫描…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) { LanScanner.scan(this@MainActivity) }
                Toast.makeText(
                    this@MainActivity,
                    if (count.isEmpty()) "未发现主机，请确认同网段目标已开启 Web 服务"
                    else "发现 ${count.size} 台主机",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "扫描异常：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                scanBusy.set(false)
            }
        }
    }

    // ---------------- Web 服务开关 ----------------
    private fun toggleWebService() {
        val ctx = this
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
                    Toast.makeText(ctx, "启动失败：端口未进入监听状态（可检查后台运行权限后重试）", Toast.LENGTH_LONG).show()
                }
                refreshStatus()
            }, 3000)
        }
    }

    // ---------------- 测试报警 ----------------
    private fun testAlarm() {
        if (AlarmController.isRunning) {
            Toast.makeText(this, "已有报警进行中，请先停止", Toast.LENGTH_SHORT).show()
            return
        }
        AlarmController.start(this, false, 12_000L)
        Toast.makeText(this, "测试报警（12 秒自动停止）", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    // ---------------- 本机状态磁贴：复制地址 / 停止报警 ----------------
    private fun onLocalTile() {
        if (AlarmController.isRunning) {
            AlarmController.stop()
            Toast.makeText(this, "已停止本机报警", Toast.LENGTH_SHORT).show()
            refreshStatus()
            return
        }
        val running = Prefs.sp(this).getBoolean(HostService.KEY_WEB_RUNNING, false) &&
            isPortOpen(HostService.DEFAULT_PORT)
        val lan = lanIpv4()
        if (running && lan != null) {
            copyText(this, "本机地址", "http://$lan:${HostService.DEFAULT_PORT}")
        } else {
            Toast.makeText(this, "本机 Web 服务未开启：可点击「Web 服务」磁贴启动", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- 状态刷新（2 秒轮询） ----------------
    private fun refreshStatus() {
        val lan = lanIpv4()
        binding.tvSub.text = if (lan.isNullOrBlank()) "IP: 无网络" else "IP: $lan"

        val wantWeb = Prefs.sp(this).getBoolean(HostService.KEY_WEB_RUNNING, false)
        val webRunning = wantWeb && isPortOpen(HostService.DEFAULT_PORT)
        tiles.find { it.id == "web" }?.let { t ->
            t.colorRes = if (webRunning) R.drawable.tile_blue else R.drawable.tile_gray
            t.status = if (webRunning) "已开启" else "已关闭"
        }
        tiles.find { it.id == "local" }?.let { t ->
            t.colorRes = if (AlarmController.isRunning) R.drawable.tile_orange else R.drawable.tile_gray
            t.status = when {
                AlarmController.isLocked -> "锁定报警中 · 点击停止"
                AlarmController.isRunning -> "报警中 · 点击停止"
                else -> "空闲 · 点击复制地址"
            }
        }
        tileAdapter.notifyDataSetChanged()
    }

    // ---------------- 关于 ----------------
    private fun showAbout() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(R.layout.dialog_confirm)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dlg.findViewById<TextView>(R.id.tvDlgTitle).text = "FindYouDevice-old v1.0.0"
        val msg = dlg.findViewById<TextView>(R.id.tvDlgMsg)
        msg.text =
            "局域网设备查找 · 老手机版（基于 FindYouDevice v1.1.1）\n\n" +
                "项目：github.com/bcdidit67/FindYouDevice-old\n" +
                "QQ：3891605032（点击复制）\n" +
                "邮箱：fxxkhw676767@outlook.com（点击撰写）"
        msg.setOnClickListener {
            copyText(this, "联系方式", "QQ 3891605032 / fxxkhw676767@outlook.com")
        }
        val btnOk = dlg.findViewById<TextView>(R.id.btnOk)
        btnOk.text = "项目地址"
        btnOk.setOnClickListener {
            openUrl(this, "https://github.com/bcdidit67/FindYouDevice-old")
            dlg.dismiss()
        }
        dlg.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dlg.dismiss() }
        dlg.show()
    }

    // ---------------- 工具 ----------------
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

    companion object {
        /** 持久化：SharedPreferences 文件名 */
        const val SP_NAME = "fyd_prefs"
        /** mode 键：存字符串 host | client（老版合并后仅作兼容保留） */
        const val KEY_MODE = "mode"
        const val MODE_HOST = "host"
        const val MODE_CLIENT = "client"
    }
}
