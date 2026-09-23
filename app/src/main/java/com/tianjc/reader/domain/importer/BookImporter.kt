package com.tianjc.reader.domain.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tianjc.reader.data.BookStore
import com.tianjc.reader.data.db.BookDao
import com.tianjc.reader.data.db.ChapterDao
import com.tianjc.reader.data.entity.Book
import com.tianjc.reader.data.entity.Chapter
import com.tianjc.reader.domain.parser.ChapterParser
import java.io.File
import java.nio.charset.Charset

/**
 * 导入流程：复制文件 → 检测编码 → 提取章节 → 写入数据库。
 */
class BookImporter(
    private val context: Context,
    private val bookStore: BookStore,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao
) {

    suspend fun import(uri: Uri) {
        val originName = queryDisplayName(uri)
        val title = originName.substringBeforeLast('.', originName)

        // 1. 复制到私有目录
        val copied: File = bookStore.copyFromUri(uri, context.contentResolver)

        try {
            val bytes = copied.readBytes()

            // 2. 检测编码
            val charsetName = EncodingDetector.detect(bytes)
            val charset = Charset.forName(charsetName)

            // 3. 章节提取（无章节时整本兜底为一章）
            val headings = ChapterParser.parseHeadings(bytes, charset)
            val totalChars = String(bytes, charset).length

            val bookId = bookDao.insert(
                Book(
                    title = title,
                    originFileName = originName,
                    filePath = copied.absolutePath,
                    charsetName = charsetName,
                    fileSize = copied.length(),
                    totalChars = totalChars
                )
            )

            val chapters = if (headings.isEmpty()) {
                listOf(
                    Chapter(
                        bookId = bookId,
                        chapterIndex = 0,
                        title = title,
                        startByteOffset = 0,
                        byteLength = copied.length()
                    )
                )
            } else {
                headings.mapIndexed { index, heading ->
                    val end = if (index < headings.size - 1) {
                        headings[index + 1].startByteOffset
                    } else {
                        copied.length()
                    }
                    Chapter(
                        bookId = bookId,
                        chapterIndex = index,
                        title = heading.title,
                        startByteOffset = heading.startByteOffset,
                        byteLength = end - heading.startByteOffset
                    )
                }
            }
            chapterDao.insertAll(chapters)
            bookDao.updateChapterCount(bookId, chapters.size, totalChars)
        } catch (e: Exception) {
            bookStore.delete(copied.absolutePath)
            throw e
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment ?: "unknown.txt"
    }
}
