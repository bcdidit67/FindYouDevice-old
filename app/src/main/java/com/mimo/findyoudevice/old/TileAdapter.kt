package com.mimo.findyoudevice.old

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mimo.findyoudevice.old.databinding.ItemTileBinding

/**
 * Metro 磁贴数据。
 * @param colorRes 磁贴背景 drawable（tile_blue / tile_orange / tile_gray）
 * @param status   右下角状态文字（如"已开启"）
 * @param spanSize 占列数（2 列网格；2 = 通栏）
 */
data class Tile(
    val id: String,
    var title: String,
    val iconRes: Int,
    var colorRes: Int,
    var status: String = "",
    val spanSize: Int = 1,
)

/** Metro 磁贴适配器（RecyclerView + GridLayoutManager，2 列） */
class TileAdapter(
    private val tiles: MutableList<Tile>,
    private val onClick: (Tile) -> Unit,
) : RecyclerView.Adapter<TileAdapter.VH>() {

    inner class VH(val b: ItemTileBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = tiles[position]
        val b = holder.b
        b.ivTileIcon.setImageResource(t.iconRes)
        b.tvTileTitle.text = t.title
        b.tvTileStatus.text = t.status
        b.root.setBackgroundResource(t.colorRes)
        b.root.setOnClickListener { onClick(t) }
    }

    override fun getItemCount(): Int = tiles.size
}
