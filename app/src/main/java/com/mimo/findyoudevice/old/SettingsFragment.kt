package com.mimo.findyoudevice.old

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.mimo.findyoudevice.old.databinding.PageSettingsBinding

/**
 * 设置页（Metro）：
 *  - 背景来源（跟随壁纸 / 纯色 / 自定义图片）—— 独立控制项
 *  - 强调色（独立于背景色，可单独选择）
 *  - 动态取色 / 半透明磁贴
 *  - 服务自动控制（WiFi 自动开 / 流量与热点自动关）
 *  - 主机配置（密码 / 闪光灯节点）
 *  - 联系与项目
 */
class SettingsFragment : Fragment() {

    private var _b: PageSettingsBinding? = null
    private val b get() = _b!!

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            Prefs.setBgImageUri(requireContext(), uri)
            Prefs.setBgMode(requireContext(), ThemeManager.BG_IMAGE)
            ThemeManager.invalidate(requireContext())
            applyAll()
            Toast.makeText(requireContext(), "已应用自定义背景图", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = PageSettingsBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        // ---------- 背景来源（三选一，独立） ----------
        b.rowBgSystem.setOnClickListener {
            Prefs.setBgMode(ctx, ThemeManager.BG_SYSTEM)
            ThemeManager.invalidate(ctx); applyAll()
            Toast.makeText(ctx, "背景：跟随系统壁纸", Toast.LENGTH_SHORT).show()
        }
        b.rowBgSolid.setOnClickListener {
            Prefs.setBgMode(ctx, ThemeManager.BG_SOLID)
            showHuePicker(title = "选择背景色") { hue ->
                Prefs.setManualHue(ctx, hue)
                ThemeManager.invalidate(ctx); applyAll()
            }
        }
        b.rowBgImage.setOnClickListener { pickImage.launch(arrayOf("image/*")) }

        // ---------- 强调色（独立于背景色） ----------
        b.rowHue.setOnClickListener {
            showHuePicker(title = "选择强调色") { hue ->
                Prefs.setAccentCustom(ctx, true)
                Prefs.setAccentHue(ctx, hue)
                applyAll()
            }
        }

        // ---------- 开关 ----------
        b.swDynamic.isChecked = Prefs.getDynamicColor(ctx)
        b.swDynamic.setOnCheckedChangeListener { _, on ->
            Prefs.setDynamicColor(ctx, on)
            ThemeManager.invalidate(ctx); applyAll()
        }
        b.swTransparent.isChecked = Prefs.isTransparentTiles(ctx)
        b.swTransparent.setOnCheckedChangeListener { _, on ->
            Prefs.setTransparentTiles(ctx, on); applyAll()
        }
        b.swAutoWifi.isChecked = Prefs.getAutoStartWifi(ctx)
        b.swAutoWifi.setOnCheckedChangeListener { _, on ->
            Prefs.setAutoStartWifi(ctx, on); NetworkAutoControl.evaluate(ctx)
        }
        b.swAutoCell.isChecked = Prefs.getAutoStopCellular(ctx)
        b.swAutoCell.setOnCheckedChangeListener { _, on ->
            Prefs.setAutoStopCellular(ctx, on); NetworkAutoControl.evaluate(ctx)
        }
        b.swAutoMetered.isChecked = Prefs.getAutoStopMetered(ctx)
        b.swAutoMetered.setOnCheckedChangeListener { _, on ->
            Prefs.setAutoStopMetered(ctx, on); NetworkAutoControl.evaluate(ctx)
        }

        // ---------- 主机配置 ----------
        b.btnSavePwd.setOnClickListener {
            val raw = b.etPassword.text?.toString()?.trim().orEmpty()
            Prefs.setPasswordHash(ctx, if (raw.isEmpty()) "" else Prefs.sha256(raw))
            b.etPassword.setText("")
            Toast.makeText(ctx, if (raw.isEmpty()) "已清除密码" else "密码已保存", Toast.LENGTH_SHORT).show()
        }
        b.btnSaveFlash.setOnClickListener {
            Prefs.setFlashPath(ctx, b.etFlash.text?.toString()?.trim().orEmpty())
            Toast.makeText(ctx, "闪光灯节点已保存", Toast.LENGTH_SHORT).show()
        }
        b.etFlash.setText(Prefs.getFlashPath(ctx))

        // ---------- 联系与项目 ----------
        b.rowProjectSet.setOnClickListener {
            openUrl(ctx, "https://github.com/bcdidit67/FindYouDevice-old")
        }
        b.rowQqSet.setOnClickListener { copyText(ctx, "QQ 号", "3891605032") }
        b.rowMailSet.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("mailto:fxxkhw676767@outlook.com")))
            }.onFailure {
                Toast.makeText(ctx, "邮箱：fxxkhw676767@outlook.com", Toast.LENGTH_LONG).show()
            }
        }

        refreshLabels()
    }

    /** 颜色选择器（背景色 / 强调色共用） */
    private fun showHuePicker(title: String, onPick: (Float) -> Unit) {
        val ctx = requireContext()
        val names = ThemeManager.PRESET_HUES.map { it.first }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setItems(names) { _, which ->
                onPick(ThemeManager.PRESET_HUES[which].second)
                refreshLabels()
                Toast.makeText(ctx, "$title：${names[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** 刷新"当前：xxx"标签 */
    private fun refreshLabels() {
        val bb = _b ?: return
        val ctx = requireContext()
        bb.tvBgCurrent.text = when (Prefs.getBgMode(ctx)) {
            ThemeManager.BG_SOLID -> "当前：纯色背景"
            ThemeManager.BG_IMAGE -> "当前：自定义图片"
            else -> "当前：跟随系统壁纸"
        }
        val hueName = { h: Float ->
            ThemeManager.PRESET_HUES.minByOrNull { kotlin.math.abs(it.second - h) }?.first ?: "自定义"
        }
        bb.tvHue.text = if (Prefs.isAccentCustom(ctx)) {
            "当前：${hueName(Prefs.getAccentHue(ctx))}（自定义）"
        } else {
            "当前：跟随提取色（H=${Prefs.getCachedHue(ctx).toInt()}°）"
        }
    }

    /** 通知全局刷新主题 */
    private fun applyAll() {
        refreshLabels()
        (activity as? MainActivity)?.applyThemeToAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
