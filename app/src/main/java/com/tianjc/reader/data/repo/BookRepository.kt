package com.tianjc.reader.data.repo

import android.content.Context
import android.net.Uri
import com.tianjc.reader.data.BookStore
import com.tianjc.reader.data.db.AppDatabase
import com.tianjc.reader.data.entity.Book
import com.tianjc.reader.data.entity.Chapter
import com.tianjc.reader.domain.importer.BookImporter
import kotlinx.coroutines.flow.Flow
import java.nio.charset.Charset

class BookRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val bookDao = db.bookDao()
    private val chapterDao = db.chapterDao()
    private val bookStore = BookStore(appContext)
    private val importer = BookImporter(appContext, bookStore, bookDao, chapterDao)

    fun observeBooks(): Flow<List<Book>> = bookDao.observeAll()

    suspend fun importBook(uri: Uri) = importer.import(uri)

    suspend fun getBook(bookId: Long): Book? = bookDao.getById(bookId)

    suspend fun getChapters(bookId: Long): List<Chapter> = chapterDao.listByBook(bookId)

    suspend fun saveProgress(
        bookId: Long, chapterIndex: Int, charOffset: Int, percent: Int
    ) {
        bookDao.updateProgress(
            bookId, chapterIndex, charOffset, percent, System.currentTimeMillis()
        )
    }

    suspend fun deleteBook(book: Book) {
        bookDao.deleteById(book.id)
        bookStore.delete(book.filePath)
    }

    /** 读取某一章的文本（含章节标题行）。 */
    suspend fun loadChapterText(book: Book, chapter: Chapter): String {
        return bookStore.readText(
            filePath = book.filePath,
            startOffset = chapter.startByteOffset,
            byteLength = chapter.byteLength,
            charset = Charset.forName(book.charsetName)
        )
    }

    companion object {
        @Volatile
        private var instance: BookRepository? = null

        fun get(context: Context): BookRepository =
            instance ?: synchronized(this) {
                instance ?: BookRepository(context).also { instance = it }
            }
    }
}
