package com.mimo.findyoudevice.old

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设备交互逻辑（自 主仓库 ClientFragment 搬运，弹窗全部 Metro 化）。
 * 依赖：LanScanner / DeviceStore / Prefs / HostService。
 */
object DeviceActions {

    /** 防重入（查找流程进行中） */
    private var busy = false

    // ------------------------------------------------------------------
    // Metro 通用弹窗
    // ------------------------------------------------------------------
    private fun newDialog(activity: Activity, layoutId: Int): Dialog {
        val dlg = Dialog(activity)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(layoutId)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dlg
    }

    private fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------
    // 设备操作弹窗
    // ------------------------------------------------------------------
    fun showDeviceActions(activity: AppCompatActivity, device: DeviceEntity) {
        val dlg = newDialog(activity, R.layout.dialog_device_actions)
        dlg.findViewById<TextView>(R.id.tvDlgTitle).text = device.alias
        dlg.findViewById<TextView>(R.id.tvDlgSub).text =
            device.ip +
                (device.model?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                " · 上次被查找：" + stampShort(device.lastFindTime)

        dlg.findViewById<TextView>(R.id.rowFind).setOnClickListener {
            dlg.dismiss(); unlockDevice(activity, device)
        }
        dlg.findViewById<TextView>(R.id.rowStop).setOnClickListener {
            dlg.dismiss(); stopRemoteDevice(activity, device)
        }
        dlg.findViewById<TextView>(R.id.rowEdit).setOnClickListener {
            dlg.dismiss(); showAddDialog(activity, device)
        }
        dlg.findViewById<TextView>(R.id.rowDelete).setOnClickListener {
            dlg.dismiss(); confirmDelete(activity, device)
        }
        val sw = dlg.findViewById<Switch>(R.id.swLockFind)
        sw.isChecked = Prefs.getDeviceLock(activity, device.ip)
        sw.setOnCheckedChangeListener { _, checked ->
            Prefs.setDeviceLock(activity, device.ip, checked)
        }
        dlg.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dlg.dismiss() }
        dlg.show()
    }

    // ------------------------------------------------------------------
    // 添加 / 编辑
    // ------------------------------------------------------------------
    fun showAddDialog(activity: AppCompatActivity, existing: DeviceEntity?) {
        val isEdit = existing != null
        val dlg = newDialog(activity, R.layout.dialog_add_device)
        dlg.findViewById<TextView>(R.id.tvDlgTitle).text =
            if (isEdit) "编辑别名" else "手动添加设备"

        val etAlias = dlg.findViewById<EditText>(R.id.etAlias)
        val etIp = dlg.findViewById<EditText>(R.id.etIp)
        if (isEdit) {
            etAlias.setText(existing!!.alias)
            etIp.setText(existing.ip)
            etIp.visibility = View.GONE
        }
        val btnOk = dlg.findViewById<TextView>(R.id.btnOk)
        btnOk.text = if (isEdit) "保存" else "添加"
        btnOk.setOnClickListener {
            val alias = etAlias.text?.toString()?.trim().orEmpty()
            val ip = etIp.text?.toString()?.trim().orEmpty()
            if (alias.isEmpty()) {
                toast(activity, "请填写易记名称")
                return@setOnClickListener
            }
            if (!isEdit && !isValidIp(ip)) {
                toast(activity, "IP 地址不合法")
                return@setOnClickListener
            }
            if (isEdit) DeviceStore.update(existing!!.copy(alias = alias))
            else DeviceStore.insert(DeviceEntity(alias = alias, ip = ip))
            dlg.dismiss()
        }
        dlg.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dlg.dismiss() }
        dlg.show()
    }

    // ------------------------------------------------------------------
    // 查找 / 停止（先探活 → 带记忆密码触发 → 401 才询问密码）
    // ------------------------------------------------------------------
    fun unlockDevice(activity: AppCompatActivity, device: DeviceEntity) {
        if (busy) return
        busy = true
        performUnlock(
            activity, device,
            Prefs.getDevicePassword(activity, device.ip),
            Prefs.getDeviceLock(activity, device.ip),
            false,
        )
    }

    private fun performUnlock(
        activity: AppCompatActivity,
        device: DeviceEntity,
        password: String,
        lock: Boolean,
        fromDialog: Boolean,
    ) {
        activity.lifecycleScope.launch {
            val up = LanScanner.isReachable(device.ip)
            if (!up) {
                DeviceStore.updateOnline(device.uid, false)
                Toast.makeText(
                    activity,
                    "主机离线：无法连接 ${device.ip}:${HostService.DEFAULT_PORT}",
                    Toast.LENGTH_LONG
                ).show()
                busy = false
                return@launch
            }
            DeviceStore.updateOnline(device.uid, true)
            val ok = withContext(Dispatchers.IO) { LanScanner.triggerFind(device.ip, password, lock) }
            if (ok) {
                DeviceStore.updateLastFind(device.uid, System.currentTimeMillis())
                busy = false
                Toast.makeText(
                    activity,
                    if (lock) "已触发锁定查找：主机持续响铃/闪光，直到在任一客户端/网页点“停止查找”"
                    else "已触发查找：主机响铃/闪光约 15 秒后自动停止",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val stillUp = LanScanner.isReachable(device.ip)
            if (!stillUp) {
                DeviceStore.updateOnline(device.uid, false)
                busy = false
                Toast.makeText(activity, "查找失败：主机已离线", Toast.LENGTH_LONG).show()
            } else if (fromDialog) {
                toast(activity, "密码错误：主机拒绝查找请求，请重新输入")
                showPasswordDialog(activity, device, password, forStop = false)
            } else {
                showPasswordDialog(activity, device, password, forStop = false)
            }
        }
    }

    fun stopRemoteDevice(activity: AppCompatActivity, device: DeviceEntity) {
        if (busy) return
        busy = true
        performStop(activity, device, Prefs.getDevicePassword(activity, device.ip), false)
    }

    private fun performStop(activity: AppCompatActivity, device: DeviceEntity, password: String, fromDialog: Boolean) {
        activity.lifecycleScope.launch {
            val up = LanScanner.isReachable(device.ip)
            if (!up) {
                DeviceStore.updateOnline(device.uid, false)
                Toast.makeText(activity, "主机离线：无法停止 ${device.ip}", Toast.LENGTH_LONG).show()
                busy = false
                return@launch
            }
            DeviceStore.updateOnline(device.uid, true)
            val ok = withContext(Dispatchers.IO) { LanScanner.stopFind(device.ip, password) }
            if (ok) {
                busy = false
                toast(activity, "已停止查找")
                return@launch
            }
            val stillUp = LanScanner.isReachable(device.ip)
            if (!stillUp) {
                DeviceStore.updateOnline(device.uid, false)
                busy = false
                Toast.makeText(activity, "停止失败：主机已离线", Toast.LENGTH_LONG).show()
            } else if (fromDialog) {
                toast(activity, "密码错误：主机拒绝停止请求，请重新输入")
                showPasswordDialog(activity, device, password, forStop = true)
            } else {
                showPasswordDialog(activity, device, password, forStop = true)
            }
        }
    }

    /** 密码输入弹窗（正确后自动记忆） */
    private fun showPasswordDialog(activity: AppCompatActivity, device: DeviceEntity, prefill: String, forStop: Boolean) {
        val dlg = newDialog(activity, R.layout.dialog_password)
        val verb = if (forStop) "停止查找" else "查找"
        dlg.findViewById<TextView>(R.id.tvDlgTitle).text = "$verb · ${device.alias}"
        dlg.findViewById<TextView>(R.id.tvDlgMsg).text =
            "主机 ${device.ip} 要求密码后才允许$verb（设置一次后将自动记忆，无需重复输入）"
        val et = dlg.findViewById<EditText>(R.id.etPassword)
        et.setText(prefill)
        et.setSelection(et.text.length)
        dlg.findViewById<TextView>(R.id.btnOk).setOnClickListener {
            val pw = et.text?.toString()?.trim().orEmpty()
            Prefs.setDevicePassword(activity, device.ip, pw)
            busy = false
            dlg.dismiss()
            if (forStop) performStop(activity, device, pw, true)
            else {
                val lock = Prefs.getDeviceLock(activity, device.ip)
                performUnlock(activity, device, pw, lock, true)
            }
        }
        dlg.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dlg.dismiss() }
        dlg.setOnDismissListener { busy = false }
        dlg.show()
    }

    // ------------------------------------------------------------------
    // 删除
    // ------------------------------------------------------------------
    private fun confirmDelete(activity: AppCompatActivity, device: DeviceEntity) {
        val dlg = newDialog(activity, R.layout.dialog_confirm)
        dlg.findViewById<TextView>(R.id.tvDlgTitle).text = "删除设备"
        dlg.findViewById<TextView>(R.id.tvDlgMsg).text = "确定从列表删除「${device.alias}」？"
        val btnOk = dlg.findViewById<TextView>(R.id.btnOk)
        btnOk.text = "删除"
        btnOk.setOnClickListener {
            DeviceStore.deleteByUid(device.uid)
            dlg.dismiss()
        }
        dlg.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dlg.dismiss() }
        dlg.show()
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------
    fun isValidIp(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        return parts.all { seg ->
            seg.isNotEmpty() && seg.length <= 3 && seg.all { it.isDigit() } &&
                (seg.toIntOrNull() ?: 256) in 0..255
        }
    }

    private fun stampShort(t: Long): String =
        if (t <= 0L) "从未"
        else java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(t))
}
