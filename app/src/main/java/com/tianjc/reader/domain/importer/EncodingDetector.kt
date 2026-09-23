package com.tianjc.reader.domain.importer

import org.mozilla.universalchardet.UniversalDetector
import java.nio.charset.Charset

/**
 * 文本编码检测：先识别 BOM，再用 juniversalchardet（Mozilla 编码探测算法）
 * 基于内容猜测。覆盖中文小说常见的 UTF-8 / GBK / GB18030 / UTF-16 / Big5。
 */
object EncodingDetector {

    /** 送入探测器的最大采样长度 */
    private const val SAMPLE_LEN = 64 * 1024

    fun detect(bytes: ByteArray): String {
        detectBom(bytes)?.let { return it }

        val len = minOf(bytes.size, SAMPLE_LEN)
        val detector = UniversalDetector(null)
        detector.handleData(bytes, 0, len)
        detector.dataEnd()
        val name = detector.detectedCharset
        detector.reset()

        if (name == null) {
            // 探测不到：优先尝试严格 UTF-8，失败则按 GB18030（中文小说最常见兜底）
            return if (isStrictUtf8(bytes)) "UTF-8" else "GB18030"
        }

        return when (name.uppercase()) {
            "UTF-8" -> "UTF-8"
            "UTF-16LE", "UTF-16BE", "UTF-16" -> name.uppercase()
            "GBK", "GB2312", "GB18030", "EUC-CN", "WINDOWS-936" -> "GB18030"
            "BIG5", "BIG-5" -> "Big5"
            else -> name
        }
    }

    private fun isStrictUtf8(bytes: ByteArray): Boolean = runCatching {
        val decoder = Charsets.UTF_8.newDecoder()
        decoder.decode(java.nio.ByteBuffer.wrap(bytes))
        true
    }.getOrDefault(false)

    private fun detectBom(bytes: ByteArray): String? {
        if (bytes.size < 2) return null
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return "UTF-8"
        }
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return "UTF-16LE"
        if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return "UTF-16BE"
        return null
    }

    fun isValid(name: String): Boolean = runCatching { Charset.forName(name) }.isSuccess
}
