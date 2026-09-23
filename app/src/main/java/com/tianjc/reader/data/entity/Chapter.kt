package com.tianjc.reader.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    indices = [Index(value = ["bookId", "chapterIndex"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Chapter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val title: String,
    /** 章节标题行在 txt 文件中的字节起始偏移 */
    val startByteOffset: Long,
    /** 章节字节长度（到下一章起始，末章到文件尾） */
    val byteLength: Long
)
