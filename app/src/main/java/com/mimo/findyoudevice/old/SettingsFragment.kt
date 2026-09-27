package com.mimo.findyoudevice.old

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.ViewGroup as VG
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.mimo.findyoudevice.old.databinding.PageSettingsBinding

/**
 * 设置页（Metro）：主题（动态取色 / 背景来源 / 固定颜色 / 半透明）+ 服务自动控制 + 主机配置 + 联系。
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
            refreshUi()
            (activity as? MainActivity)?.applyThemeToAll()
            Toast.makeText(requireContext(), "已应用自定义背景图", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = PageSettingsBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ---- 主题 ----
        b.swDynamic.isChecked = Prefs.getDynamicColor(requireContext())
        b.swDynamic.setOnCheckedChangeListener { _, on ->
            Prefs.setDynamicColor(requireContext(), on)
            ThemeManager.invalidate(requireContext())
            (activity as? MainActivity)?.applyThemeToAll()
        }
        b.swTransparent.isChecked = Prefs.isTransparentTiles(requireContext())
        b.swTransparent.setOnCheckedChangeListener { _, on ->
            Prefs.setTransparentTiles(requireContext(), on)
            (activity as? MainActivity)?.applyThemeToAll()
        }
        b.rowBgSystem.setOnClickListener {
            Prefs.setBgMode(requireContext(), ThemeManager.BG_SYSTEM)
            ThemeManager.invalidate(requireContext())
            (activity as? MainActivity)?.applyThemeToAll(); refreshUi()
            Toast.makeText(requireContext(), "背景：跟随系统壁纸", Toast.LENGTH_SHORT).show()
        }
        b.rowBgSolid.setOnClickListener {
            Prefs.setBgMode(requireContext(), ThemeManager.BG_SOLID)
            showHuePicker()
            refreshUi()
        }
        b.rowBgImage.setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }
        b.rowHue.setOnClickListener { showHuePicker() }

        // ---- 服务自动控制 ----
        b.swAutoWifi.isChecked = Prefs.getAutoStartWifi(requireContext())
        b.swAutoWifi.setOnCheckedChangeListener { _, on ->
            Prefs.setAutoStartWifi(requireContext(), on)
            NetworkAutoControl.evaluate(requireContext())
        }
        b.swAutoCell.isChecked = Prefs.getAutoStopCellular(requireContext())
        b.swAutoCell.setOnCheckedChangeListener { _, on ->
            Prefs.setAutoStopCellular(requireContext(), on)
            NetworkAutoControl.evaluate(requireContext())
        }
        b.swAutoMetered.isChecked = Prefs.getAutoStopMetered(requireContext())
        b.swAutoMetered.setOnCheckedChangeListener { _, on ->
            Prefs.setAutoStopMetered(requireContext(), on)
            NetworkAutoControl.evaluate(requireContext())
        }

        // ---- 主机配置 ----
        b.btnSavePwd.setOnClickListener {
            val raw = b.etPassword.text?.toString()?.trim().orEmpty()
            Prefs.setPasswordHash(requireContext(), if (raw.isEmpty()) "" else Prefs.sha256(raw))
            b.etPassword.setText("")
            Toast.makeText(requireContext(), if (raw.isEmpty()) "已清除密码" else "密码已保存", Toast.LENGTH_SHORT).show()
        }
        b.btnSaveFlash.setOnClickListener {
            Prefs.setFlashPath(requireContext(), b.etFlash.text?.toString()?.trim().orEmpty())
            Toast.makeText(requireContext(), "闪光灯节点已保存", Toast.LENGTH_SHORT).show()
        }
        b.etFlash.setText(Prefs.getFlashPath(requireContext()))

        refreshUi()
    }

    /** 固定颜色选择（预设色相 + 当前色相指示） */
    private fun showHuePicker() {
        val ctx = requireContext()
        val names = ThemeManager.PRESET_HUES.map { it.first }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("选择强调色")
            .setItems(names) { _, which ->
                val hue = ThemeManager.PRESET_HUES[which].second
                Prefs.setManualHue(ctx, hue)
                Prefs.setBgMode(ctx, ThemeManager.BG_SOLID)
                ThemeManager.invalidate(ctx)
                (activity as? MainActivity)?.applyThemeToAll()
                refreshUi()
                Toast.makeText(ctx, "强调色：${names[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun refreshUi() {
        val bb = _b ?: return
        val ctx = requireContext()
        val mode = Prefs.getBgMode(ctx)
        bb.tvBgMode.text = when (mode) {
            ThemeManager.BG_SOLID -> "当前：纯色"
            ThemeManager.BG_IMAGE -> "当前：自定义图片"
            else -> "当前：系统壁纸"
        }
        val hue = Prefs.getCachedHue(ctx)
        val name = ThemeManager.PRESET_HUES.minByOrNull { kotlin.math.abs(it.second - hue) }?.first ?: "自定义"
        bb.tvHue.text = "当前强调色：$name（H=${hue.toInt()}°）"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
