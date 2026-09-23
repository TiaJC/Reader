package com.tianjc.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tianjc.reader.data.entity.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: Book): Long

    @Query("SELECT * FROM books ORDER BY lastReadAt DESC, addedAt DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): Book?

    @Query("SELECT filePath FROM books")
    suspend fun getAllFilePaths(): List<String>

    @Query("UPDATE books SET currentChapterIndex = :chapterIndex, currentCharOffset = :charOffset, readPercent = :percent, lastReadAt = :time WHERE id = :bookId")
    suspend fun updateProgress(
        bookId: Long, chapterIndex: Int, charOffset: Int, percent: Int, time: Long
    )

    @Query("UPDATE books SET chapterCount = :count, totalChars = :totalChars WHERE id = :bookId")
    suspend fun updateChapterCount(bookId: Long, count: Int, totalChars: Int)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: Long)
}
