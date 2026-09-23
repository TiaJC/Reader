package com.tianjc.reader.ui.reader

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tianjc.reader.data.entity.Chapter
import com.tianjc.reader.databinding.ItemChapterBinding

class ChapterListAdapter(
    private val chapters: List<Chapter>,
    private var currentIndex: Int,
    private val currentColor: Int,
    private val normalColor: Int,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ChapterListAdapter.VH>() {

    inner class VH(val binding: ItemChapterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChapterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.binding.tvChapterTitle.text = chapters[position].title
        holder.binding.tvChapterTitle.setTextColor(
            if (position == currentIndex) currentColor else normalColor
        )
        holder.itemView.setOnClickListener { onClick(position) }
    }

    override fun getItemCount(): Int = chapters.size
}
