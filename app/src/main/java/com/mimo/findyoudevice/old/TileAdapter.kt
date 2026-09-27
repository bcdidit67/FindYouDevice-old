package com.mimo.findyoudevice.old

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mimo.findyoudevice.old.databinding.ItemTileBinding

/**
 * Metro 磁贴数据。
 * - [accent] 磁贴色档位：A=大磁贴（最亮）/ B=中 / C=小（更深），组合出 WP8 的"磁贴墙"层次
 * - [spanSize] 占列数（2 列网格；2 = 通栏）
 * - [rowSpan] 占行数（用于 2x2 大磁贴）
 */
data class Tile(
    val id: String,
    var title: String,
    val iconRes: Int,
    var status: String = "",
    val spanSize: Int = 1,
    val rowSpan: Int = 1,
    var accent: Accent = Accent.A,
) {
    enum class Accent { A, B, C }
}

/**
 * Metro 磁贴适配器（WP8 风格）。
 * 颜色 100% 来自 [ThemeManager] 动态生成的纯色 Drawable——不再有任何 XML 固定色。
 */
class TileAdapter(
    private val tiles: MutableList<Tile>,
    private val onClick: (Tile) -> Unit,
) : RecyclerView.Adapter<TileAdapter.VH>() {

    /** 是否使用半透明磁贴（透出壁纸） */
    var useTranslucent: Boolean = false

    inner class VH(val b: ItemTileBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = tiles[position]
        val b = holder.b
        val ctx = b.root.context

        b.ivTileIcon.setImageResource(t.iconRes)
        b.tvTileTitle.text = t.title
        b.tvTileStatus.text = t.status

        // === WP8 磁贴着色：三档明度 + 可选半透明 ===
        val bg = when {
            useTranslucent -> ThemeManager.tileTranslucentDrawable(ctx)
            t.accent == Tile.Accent.A -> ThemeManager.tileBrightDrawable(ctx)
            t.accent == Tile.Accent.C -> ThemeManager.tileDeepDrawable(ctx)
            else -> ThemeManager.tileDarkDrawable(ctx)
        }
        b.root.background = bg
        // 大磁贴：图标更大
        val big = t.spanSize >= 2 && t.rowSpan >= 2
        val iconPx = if (big) 44 else 26
        val lp = b.ivTileIcon.layoutParams
        lp.width = (iconPx * ctx.resources.displayMetrics.density).toInt()
        lp.height = lp.width
        b.ivTileIcon.layoutParams = lp
        b.tvTileTitle.textSize = if (big) 22f else 17f

        b.root.setOnClickListener { onClick(t) }
    }

    override fun getItemCount(): Int = tiles.size
}
