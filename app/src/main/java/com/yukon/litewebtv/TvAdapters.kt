package com.yukon.litewebtv

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// =======================
// 频道列表适配器
// =======================
class ChannelAdapter(
    private val onItemClick: (ChannelItem) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    private val items = ArrayList<ChannelItem>()

    fun updateData(newItems: List<ChannelItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_channel_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name

        // 设置选中状态 (当前正在播放的频道)
        holder.itemView.isSelected = item.isActive

        // 如果是当前频道，文字标绿
        if (item.isActive) {
            holder.tvName.setTextColor(Color.parseColor("#00FF00"))
        } else {
            holder.tvName.setTextColor(Color.WHITE)
        }

        // 点击事件
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size
}

// =======================
// 节目单适配器
// =======================
class ProgramAdapter : RecyclerView.Adapter<ProgramAdapter.ViewHolder>() {

    private val items = ArrayList<ProgramItem>()

    fun updateData(newItems: List<ProgramItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tv_program_time)
        val tvTitle: TextView = view.findViewById(R.id.tv_program_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_program, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTime.text = item.displayTime
        holder.tvTitle.text = item.title

        // 高亮当前正在播出的节目
        if (item.isCurrent) {
            holder.tvTitle.setTextColor(Color.parseColor("#00FF00"))
            holder.tvTime.setTextColor(Color.parseColor("#00FF00"))
        } else {
            holder.tvTitle.setTextColor(Color.WHITE)
            holder.tvTime.setTextColor(Color.parseColor("#AAAAAA"))
        }
    }

    override fun getItemCount() = items.size
}