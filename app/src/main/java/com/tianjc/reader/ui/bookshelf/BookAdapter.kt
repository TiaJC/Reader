package com.tianjc.reader.ui.bookshelf

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tianjc.reader.data.entity.Book
import com.tianjc.reader.databinding.ItemBookBinding

class BookAdapter(
    private val onClick: (Book) -> Unit,
    private val onLongClick: (Book) -> Unit
) : ListAdapter<Book, BookAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemBookBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBookBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val book = getItem(position)
        with(holder.binding) {
            tvTitle.text = book.title
            tvMeta.text = buildMeta(book)
            tvBookProgress.text = progressText(book)
            tvCoverChar.text = book.title.take(1)
            coverBg.setBackgroundResource(coverDrawable(book))
            root.setOnClickListener { onClick(book) }
            root.setOnLongClickListener {
                onLongClick(book)
                true
            }
        }
    }

    /** 按书名稳定选取封面配色 */
    private fun coverDrawable(book: Book): Int =
        COVER_DRAWABLES[(book.title.hashCode().toUInt() % COVER_DRAWABLES.size.toUInt()).toInt()]

    private fun buildMeta(book: Book): String {
        val sizeKb = book.fileSize / 1024
        val sizeText = if (sizeKb > 1024) "%.1fMB".format(sizeKb / 1024.0) else "${sizeKb}KB"
        return "$sizeText · ${book.chapterCount}章 · ${book.charsetName}"
    }

    private fun progressText(book: Book): String {
        if (book.lastReadAt == 0L) return "未读"
        return if (book.readPercent >= 100) "已读完" else "已读 ${book.readPercent}%"
    }

    companion object {
        private val COVER_DRAWABLES = listOf(
            com.tianjc.reader.R.drawable.bg_cover_blue,
            com.tianjc.reader.R.drawable.bg_cover_purple,
            com.tianjc.reader.R.drawable.bg_cover_orange,
            com.tianjc.reader.R.drawable.bg_cover_teal,
            com.tianjc.reader.R.drawable.bg_cover_red
        )

        private val DIFF = object : DiffUtil.ItemCallback<Book>() {
            override fun areItemsTheSame(oldItem: Book, newItemItem: Book) =
                oldItem.id == newItemItem.id

            override fun areContentsTheSame(oldItem: Book, newItemItem: Book) =
                oldItem == newItemItem
        }
    }
}
