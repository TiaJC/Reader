package com.tianjc.reader.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 管理阅读器私有存储目录中的 TXT 文件：
 *   Android/data/com.tianjc.reader/files/books/
 *
 * 无需任何存储权限；应用卸载时自动清除。
 */
class BookStore(context: Context) {

    private val booksDir: File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "books").apply { mkdirs() }

    /**
     * 将 SAF 选中的文件复制进 books 目录，返回目标文件。
     * 用随机 UUID 命名，规避中文路径与重名问题。
     */
    fun copyFromUri(uri: Uri, resolver: android.content.ContentResolver): File {
        val target = File(booksDir, "book_${java.util.UUID.randomUUID()}.txt")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: throw IllegalStateException("无法读取所选文件")
        return target
    }

    fun delete(filePath: String) {
        runCatching { File(filePath).delete() }
    }

    /**
     * 按字节偏移读取一段文本（供章节懒加载）。
     */
    fun readText(
        filePath: String,
        startOffset: Long,
        byteLength: Long,
        charset: java.nio.charset.Charset
    ): String {
        val len = byteLength.toInt()
        val buf = ByteArray(len)
        File(filePath).inputStream().use { input ->
            var remaining = startOffset
            while (remaining > 0) {
                val s = input.skip(remaining)
                if (s <= 0) break
                remaining -= s
            }
            var read = 0
            while (read < len) {
                val r = input.read(buf, read, len - read)
                if (r < 0) break
                read += r
            }
            if (read < len) return String(buf, 0, read, charset)
        }
        return String(buf, charset)
    }
}
