package com.mimo.findyoudevice.old

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mimo.findyoudevice.old.databinding.ItemTileBinding

/**
 * Metro 磁贴数据。
 * [spanSize] 占列数（2 列网格；2 = 通栏）
 * [dynamic] 动态强调色档位（BRIGHT=亮 / DARK=暗）；由 DeviceFragment 按状态切换
 */
data class Tile(
    val id: String,
    var title: String,
    val iconRes: Int,
    /** 保留：固定 drawable 资源（0 = 使用动态色） */
    var colorRes: Int = 0,
    var status: String = "",
    val spanSize: Int = 1,
    var dynamic: TileAccent = TileAccent.BRIGHT,
) {
    enum class TileAccent { BRIGHT, DARK }
}

/** Metro 磁贴适配器：颜色全部由 [ThemeManager] 动态生成 */
class TileAdapter(
    private val tiles: MutableList<Tile>,
    private val onClick: (Tile) -> Unit,
) : RecyclerView.Adapter<TileAdapter.VH>() {

    /** 是否启用动态强调色（由页面设置；默认 true） */
    var dynamicColor: Boolean = true

    /** 是否半透明（明显透出壁纸） */
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

        // === 动态着色核心：纯色 Drawable（含按下加深），不使用任何 XML 固定色 ===
        val bg = if (dynamicColor) {
            val base = when {
                t.dynamic == Tile.TileAccent.BRIGHT ->
                    if (useTranslucent) ThemeManager.tileTranslucentDrawable(ctx)
                    else ThemeManager.tileBrightDrawable(ctx)
                else -> ThemeManager.tileDarkDrawable(ctx)
            }
            base
        } else {
            androidx.core.content.ContextCompat.getDrawable(ctx, t.colorRes)
                ?: ThemeManager.tileDarkDrawable(ctx)
        }
        b.root.background = bg

        b.root.setOnClickListener { onClick(t) }
    }

    override fun getItemCount(): Int = tiles.size
}
