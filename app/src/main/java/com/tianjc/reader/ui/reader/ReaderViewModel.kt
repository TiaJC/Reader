package com.tianjc.reader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianjc.reader.data.entity.Book
import com.tianjc.reader.data.entity.Chapter
import com.tianjc.reader.data.repo.BookRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ReaderData(
    val book: Book,
    val chapters: List<Chapter>
)

/** 通知 View 展示某一章 */
data class ChapterDisplay(
    val index: Int,
    val text: String,
    val restoreCharOffset: Int?,
    val forward: Boolean?
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository.get(app)

    private val _state = MutableStateFlow<ReaderData?>(null)
    val state: StateFlow<ReaderData?> = _state

    private val _chapterEvent = MutableSharedFlow<ChapterDisplay>(extraBufferCapacity = 1)
    val chapterEvent: SharedFlow<ChapterDisplay> = _chapterEvent

    private val textCache = HashMap<Int, String>()

    private var loadedOnce = false

    fun load(bookId: Long) {
        if (loadedOnce) return
        loadedOnce = true
        viewModelScope.launch {
            val book = repo.getBook(bookId) ?: return@launch
            val chapters = repo.getChapters(bookId)
            _state.value = ReaderData(book, chapters)
            // 恢复到上次阅读位置
            displayChapter(
                index = book.currentChapterIndex.coerceAtMost(chapters.lastIndex),
                restoreCharOffset = book.currentCharOffset,
                forward = null
            )
        }
    }

    /**
     * 加载并展示某一章。
     */
    fun displayChapter(index: Int, restoreCharOffset: Int? = 0, forward: Boolean? = null) {
        val data = _state.value ?: return
        if (index !in data.chapters.indices) return
        viewModelScope.launch {
            val text = textCache.getOrPut(index) {
                repo.loadChapterText(data.book, data.chapters[index])
            }
            preloadNeighbors(index)
            _chapterEvent.emit(
                ChapterDisplay(index, text, restoreCharOffset, forward)
            )
        }
    }

    private suspend fun preloadNeighbors(index: Int) {
        val data = _state.value ?: return
        for (n in listOf(index - 1, index + 1)) {
            if (n in data.chapters.indices && !textCache.containsKey(n)) {
                runCatching {
                    textCache[n] = repo.loadChapterText(data.book, data.chapters[n])
                }
            }
        }
    }

    /**
     * 保存阅读位置，并按"章节位置 + 章内偏移占比"计算全书进度。
     * @param chapterTextLength 当前章实际字符长度
     */
    fun saveProgress(chapterIndex: Int, charOffset: Int, chapterTextLength: Int) {
        val data = _state.value ?: return
        val chapterCount = data.chapters.size
        if (chapterCount <= 0) return

        val frac = if (chapterTextLength <= 0) 0f
        else charOffset.toFloat() / chapterTextLength
        val percent = when {
            chapterIndex >= chapterCount - 1 && frac > 0.99f -> 100
            else -> ((chapterIndex + frac.coerceIn(0f, 1f)) * 100 / chapterCount)
                .toInt().coerceIn(0, 99)
        }

        viewModelScope.launch {
            repo.saveProgress(data.book.id, chapterIndex, charOffset, percent)
        }
    }
}
