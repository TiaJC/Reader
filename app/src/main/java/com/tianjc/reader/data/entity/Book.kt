package com.tianjc.reader.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["filePath"], unique = true)]
)
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 显示书名（原始文件名，不含扩展名） */
    val title: String,
    /** 导入前的原始文件名 */
    val originFileName: String,
    /** 复制到应用存储目录后的绝对路径 */
    val filePath: String,
    /** 文本编码，如 UTF-8 / GBK / GB18030 */
    val charsetName: String,
    val fileSize: Long,
    val chapterCount: Int = 0,
    val totalChars: Int = 0,
    /** 阅读进度：当前章节下标 */
    val currentChapterIndex: Int = 0,
    /** 阅读进度：当前章节内的字符偏移（字号变化时也能恢复位置） */
    val currentCharOffset: Int = 0,
    /** 按实际阅读位置计算的全书进度百分比 0..100 */
    val readPercent: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0
)
