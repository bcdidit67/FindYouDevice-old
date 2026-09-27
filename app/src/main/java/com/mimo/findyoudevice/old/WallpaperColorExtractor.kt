package com.mimo.findyoudevice.old

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import java.io.InputStream

/**
 * 极轻量壁纸主色提取（面向 2GB/MT6735 老机型优化）。
 *
 * 算法（三步）：
 *  1. 极速采样：解码后立即缩放到 40x40（1600 px），随即 recycle()，避免 OOM；
 *  2. 提取候选主色：逐像素 RGB->HSV；过滤 S<0.2（灰白黑）与 V<0.2 / V>0.9（过暗过亮）；
 *     剩余像素按色相 H 每 30° 分桶（12 桶），取像素最多的桶 -> 主色相 H；
 *  3. 生成 WP 三色：背景(H,0.8,0.05) / 磁贴亮(H,0.9,0.7) / 磁贴暗(H,0.9,0.5)。
 *
 * 性能：全程约 10ms 内（1600 像素），仅在首启或壁纸变化时执行一次，结果缓存于 Prefs。
 */
object WallpaperColorExtractor {

    private const val THUMB = 40
    private const val BUCKETS = 12

    /** 默认强调色（蓝色）——提取失败或纯色壁纸时兜底 */
    const val DEFAULT_HUE = 210f

    data class ThemeColors(
        val hue: Float,
        val background: Int,
        /** 磁贴档位 A（WP8 主色，最亮，用于 2x2 大磁贴） */
        val tileBright: Int,
        /** 磁贴档位 B（稍暗，用于 2x1 / 1x1） */
        val tileDark: Int,
        /** 磁贴档位 C（更暗，用于次级磁贴，形成层次） */
        val tileDeep: Int,
        /** 半透明档（透出壁纸） */
        val tileTranslucent: Int,
        /** 强调色（Tab 选中 / 高亮文字） */
        val accent: Int,
    )

    /** 从系统壁纸提取主题色 */
    fun fromSystemWallpaper(context: Context): ThemeColors? {
        return try {
            val wm = android.app.WallpaperManager.getInstance(context)
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.getDrawable()?.let { drawableToBitmap(it) }
            } else {
                @Suppress("DEPRECATION")
                wm.drawable?.let { drawableToBitmap(it) }
            }
            bmp?.let { extractFrom(it) }
        } catch (e: Throwable) {
            null
        }
    }

    /** 从自定义图片 Uri 提取主题色 */
    fun fromUri(context: Context, uri: Uri): ThemeColors? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { s ->
                BitmapFactory.decodeStream(s, null, opts)
            }
            // 计算采样率，避免大图 OOM
            var sample = 1
            val maxDim = maxOf(opts.outWidth, opts.outHeight)
            while (maxDim / sample > THUMB * 4) sample *= 2

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
            }
            val bmp = context.contentResolver.openInputStream(uri)?.use { s ->
                BitmapFactory.decodeStream(s, null, decodeOpts)
            }
            bmp?.let { extractFrom(it) }
        } catch (e: Throwable) {
            null
        }
    }

    /** 核心提取：缩放 -> 过滤 -> 分桶 */
    fun extractFrom(src: Bitmap): ThemeColors {
        // 1. 极速采样
        val thumb = Bitmap.createScaledBitmap(src, THUMB, THUMB, false)
        if (thumb !== src) src.recycle()

        // 2. 过滤 + 色相分桶
        val buckets = IntArray(BUCKETS)
        val hsv = FloatArray(3)
        for (y in 0 until thumb.height) {
            for (x in 0 until thumb.width) {
                val px = thumb.getPixel(x, y)
                Color.colorToHSV(px, hsv)
                val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
                if (s < 0.2f) continue          // 灰白黑
                if (v < 0.2f || v > 0.9f) continue // 过暗/过亮
                val idx = ((h / 30f).toInt().coerceIn(0, BUCKETS - 1))
                buckets[idx]++
            }
        }
        thumb.recycle()

        var bestIdx = -1
        var bestCount = 0
        for (i in buckets.indices) {
            if (buckets[i] > bestCount) {
                bestCount = buckets[i]
                bestIdx = i
            }
        }
        // 无有效主色（纯色壁纸/黑白图）-> 默认蓝
        val hue = if (bestIdx < 0 || bestCount == 0) DEFAULT_HUE else bestIdx * 30f + 15f
        return buildTheme(hue)
    }

    /**
     * 按主色相生成 WP8 磁贴墙配色（多档明度，形成"磁贴墙"层次）。
     * 采用用户给定公式的扩展版：保持 S=0.9 高饱和，仅用 V 分档。
     */
    fun buildTheme(hue: Float): ThemeColors {
        val background = Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.06f))   // 极暗主色背景
        val bright = Color.HSVToColor(floatArrayOf(hue, 0.95f, 0.78f))        // 档 A：大磁贴（最亮）
        val dark = Color.HSVToColor(floatArrayOf(hue, 0.95f, 0.55f))          // 档 B：中磁贴
        val deep = Color.HSVToColor(floatArrayOf(hue, 0.95f, 0.38f))          // 档 C：小磁贴（更深）
        val translucent = Color.HSVToColor(0xE0, floatArrayOf(hue, 0.95f, 0.70f)) // 半透明
        val accent = Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.95f))        // 强调（Tab 选中）
        return ThemeColors(hue, background, bright, dark, deep, translucent, accent)
    }

    /** 纯色背景模式：由用户选择的主色生成 */
    fun fromSolidColor(hue: Float): ThemeColors = buildTheme(hue)

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap? {
        return try {
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else THUMB * 8
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else THUMB * 8
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        } catch (e: Throwable) {
            null
        }
    }
}
