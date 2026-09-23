package com.tianjc.reader.ui.bookshelf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianjc.reader.data.entity.Book
import com.tianjc.reader.data.repo.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookshelfViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository.get(app)

    val books: StateFlow<List<Book>> = repo.observeBooks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun importBook(uri: Uri) {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            try {
                repo.importBook(uri)
            } catch (e: Exception) {
                _message.value = "导入失败：${e.message ?: "未知错误"}"
            } finally {
                _importing.value = false
            }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            runCatching { repo.deleteBook(book) }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
