package com.mimo.findyoudevice.old

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.mimo.findyoudevice.old.databinding.ActivityMainBinding

/**
 * FindYouDevice-old 主界面：顶部大字号 Tab（设备 / 设置 / 关于）+ ViewPager2。
 *
 * 主题：背景与磁贴颜色由 [ThemeManager] 动态生成（跟随壁纸 / 纯色 / 自定义图片），
 * 仅在首启或设置变更时计算一次并缓存。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: MetroPagerAdapter

    private val tabIds = intArrayOf(R.id.tabDevices, R.id.tabSettings, R.id.tabAbout)
    private val hints = arrayOf("管理与查找局域网设备", "外观与服务设置", "版本与联系方式")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.ensureFresh(this) // 启动时一次性准备主题（含换壁纸检测）
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pagerAdapter = MetroPagerAdapter(this)
        binding.pager.adapter = pagerAdapter
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = renderTabs(position)
        })

        tabIds.forEachIndexed { idx, id ->
            findViewById<TextView>(id).setOnClickListener {
                if (binding.pager.currentItem != idx) binding.pager.setCurrentItem(idx, true)
            }
        }
        renderTabs(0)
        applyThemeToAll()
    }

    /** 切换 Tab 后刷新（颜色跟随主题） */
    fun applyThemeToAll() {
        binding.root.background = buildBackground()
        pagerAdapter.notifyDataSetChanged()
    }

    /** 构建背景：纯色 / 自定义图片 / 系统壁纸（后二者上叠主题深色遮罩，保证文字可读） */
    private fun buildBackground(): Drawable {
        val mode = Prefs.getBgMode(this)
        val overlay = ColorDrawable(ThemeManager.colors(this).background).apply {
            alpha = if (mode == ThemeManager.BG_SOLID) 255 else 200
        }
        val image: Drawable? = when (mode) {
            ThemeManager.BG_IMAGE -> Prefs.getBgImageUri(this)?.let { uri ->
                runCatching {
                    contentResolver.openInputStream(uri)?.use { BitmapDrawable(resources, BitmapFactory.decodeStream(it)) }
                }.getOrNull()
            }
            ThemeManager.BG_SYSTEM -> runCatching {
                @Suppress("DEPRECATION")
                android.app.WallpaperManager.getInstance(this).drawable
            }.getOrNull()
            else -> null
        }
        return if (image == null) overlay
        else LayerDrawable(arrayOf(image, overlay))
    }

    /** Tab 选中=白色 / 未选中=灰色，并更新下方提示小字 */
    private fun renderTabs(selected: Int) {
        tabIds.forEachIndexed { idx, id ->
            findViewById<TextView>(id).setTextColor(
                if (idx == selected) 0xFFFFFFFF.toInt() else 0xFF888888.toInt()
            )
        }
        findViewById<TextView>(R.id.tabHint).text = hints.getOrElse(selected) { "" }
    }

    companion object {
        const val SP_NAME = "fyd_prefs"
        const val KEY_MODE = "mode"
        const val MODE_HOST = "host"
        const val MODE_CLIENT = "client"
    }
}
