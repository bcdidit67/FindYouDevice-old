package com.mimo.findyoudevice.old

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mimo.findyoudevice.old.databinding.ItemDeviceMetroBinding

/** Metro 设备列表适配器 */
class DeviceAdapter(
    private val onItemClick: (DeviceEntity) -> Unit,
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    private val items = mutableListOf<DeviceEntity>()

    fun submit(list: List<DeviceEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemDeviceMetroBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemDeviceMetroBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.b
        b.tvAlias.text = item.alias
        b.tvIpModel.text = if (item.model.isNullOrBlank()) item.ip else "${item.ip} · ${item.model}"
        b.tvTime.text = stampShort(item.lastFindTime)
        b.dotStatus.setBackgroundResource(
            if (item.isOnline) R.drawable.dot_online else R.drawable.dot_offline
        )
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    private fun stampShort(t: Long): String =
        if (t <= 0L) "从未"
        else java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(t))
}
