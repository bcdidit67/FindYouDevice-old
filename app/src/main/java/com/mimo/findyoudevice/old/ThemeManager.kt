package com.mimo.findyoudevice.old

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import com.mimo.findyoudevice.old.WallpaperColorExtractor.ThemeColors

/**
 * 主题管理：统一产出当前生效的三色与磁贴 Drawable。
 *
 * 背景来源三选一（Prefs.KEY_BG_MODE）：
 *  0 = 跟随系统壁纸（动态提取主色）
 *  1 = 纯色（用户选定色相）
 *  2 = 自定义图片（SAF 选择，动态提取主色）
 *
 * 性能约定：颜色仅在 初始化 / 设置变更 时计算一次并缓存于 Prefs；
 * 其余时刻直接读缓存（O(1)），绝不在 onResume/onDraw 重算。
 */
object ThemeManager {

    const val BG_SYSTEM = 0
    const val BG_SOLID = 1
    const val BG_IMAGE = 2

    /** 可供用户选择的固定色相（纯色模式 / 手动覆盖） */
    val PRESET_HUES = listOf(
        "亮蓝" to 210f,
        "亮橙" to 30f,
        "青绿" to 170f,
        "品红" to 320f,
        "黄绿" to 75f,
    )

    @Volatile
    private var cached: ThemeColors? = null

    /** 读取缓存；无缓存时按当前设置计算一次（幂等、可安全重复调用；永不抛异常） */
    fun colors(context: Context): ThemeColors {
        cached?.let { return it }
        return runCatching { computeColors(context) }.getOrElse {
            // 任何异常（权限/壁纸读取/解码失败）一律回退默认蓝，保证不崩溃
            WallpaperColorExtractor.fromSolidColor(WallpaperColorExtractor.DEFAULT_HUE).also { t ->
                cached = t
            }
        }
    }

    private fun computeColors(context: Context): ThemeColors {
        val ctx = context.applicationContext
        val dynamicOn = Prefs.getDynamicColor(ctx)
        val bgMode = Prefs.getBgMode(ctx)

        val theme: ThemeColors = when {
            bgMode == BG_SOLID || !dynamicOn -> {
                // 纯色模式 / 关闭动态取色 -> 使用用户色相（或默认蓝）
                WallpaperColorExtractor.fromSolidColor(Prefs.getManualHue(ctx))
            }
            bgMode == BG_IMAGE -> {
                val uri = Prefs.getBgImageUri(ctx)
                val t = uri?.let { WallpaperColorExtractor.fromUri(ctx, it) }
                t ?: WallpaperColorExtractor.fromSolidColor(WallpaperColorExtractor.DEFAULT_HUE)
            }
            else -> {
                val t = WallpaperColorExtractor.fromSystemWallpaper(ctx)
                t ?: WallpaperColorExtractor.fromSolidColor(WallpaperColorExtractor.DEFAULT_HUE)
            }
        }
        cached = theme
        Prefs.setCachedHue(ctx, theme.hue)
        return theme
    }

    /** 强制重新计算（设置变更 / 壁纸变化 / 换图后调用；永不抛异常） */
    fun invalidate(context: Context): ThemeColors {
        cached = null
        return colors(context)
    }

    /** 安全读取颜色（供 UI 层调用，任何异常回退默认） */
    fun safeColors(context: Context): ThemeColors = runCatching { colors(context) }
        .getOrElse { WallpaperColorExtractor.fromSolidColor(WallpaperColorExtractor.DEFAULT_HUE) }

    /** 启动时：仅在无缓存 或 系统壁纸变化时重算（省电省 CPU；永不抛异常） */
    fun ensureFresh(context: Context) {
        runCatching {
            val ctx = context.applicationContext
            val curId = currentWallpaperId(ctx)
            val lastId = Prefs.getLastWallpaperId(ctx)
            if (cached == null || curId != lastId) {
                Prefs.setLastWallpaperId(ctx, curId)
                invalidate(ctx)
            }
        }
    }

    // ---------------- Drawable 工厂（替代硬编码 XML） ----------------
    fun backgroundDrawable(context: Context): Drawable =
        ColorDrawable(colors(context).background)

    /** 磁贴亮色（大磁贴 / 选中态） */
    fun tileBrightDrawable(context: Context): Drawable =
        ColorDrawable(colors(context).tileBright)

    /** 磁贴暗色（小磁贴 / 次级） */
    fun tileDarkDrawable(context: Context): Drawable =
        ColorDrawable(colors(context).tileDark)

    /**
     * 磁贴半透明（明显透出壁纸）。
     * [backgroundVisible] = 背景是否为图片/壁纸；纯色背景下退回不透明暗色，避免"糊成一团"。
     */
    fun tileTranslucentDrawable(context: Context): Drawable {
        val c = colors(context)
        val bgMode = Prefs.getBgMode(context)
        val imageBackdrop = bgMode == BG_IMAGE || (bgMode == BG_SYSTEM && Prefs.isTransparentTiles(context))
        return ColorDrawable(if (imageBackdrop) c.tileTranslucent else c.tileDark)
    }

    /**
     * 系统壁纸标识：用壁纸尺寸+内存地址的哈希近似代替（兼容 API21，无高版本 API 依赖）。
     * 仅用于判断“壁纸是否变化”，无需精确值。
     */
    private fun currentWallpaperId(context: Context): Int = try {
        @Suppress("DEPRECATION")
        val d = android.app.WallpaperManager.getInstance(context).drawable
        if (d == null) -1 else (d.intrinsicWidth * 31 + d.intrinsicHeight * 17 + System.identityHashCode(d))
    } catch (e: Throwable) {
        -1
    }
}
