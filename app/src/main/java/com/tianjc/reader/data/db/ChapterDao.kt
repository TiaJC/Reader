package com.tianjc.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tianjc.reader.data.entity.Chapter

@Dao
interface ChapterDao {

    @Insert
    suspend fun insertAll(chapters: List<Chapter>)

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    suspend fun listByBook(bookId: Long): List<Chapter>

    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId")
    suspend fun countByBook(bookId: Long): Int

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)
}
