package com.tianjc.reader.domain.parser

/**
 * 解析出的章节标题位置。
 * [startByteOffset] 为标题行起始字节位置。
 */
data class ParsedHeading(
    val title: String,
    val startByteOffset: Long
)

/**
 * 章节提取器：按行扫描字节数组，正则匹配章节标题。
 *
 * 之所以直接扫描字节而不是按 String 切行，是为了精确记录每一章在原文件中的
 * 字节偏移，阅读时可凭偏移直接 RandomAccess 读取，无需拆分文件。
 */
object ChapterParser {

    /** 章节标题行最大长度，超长的行不视为标题（避免正文误判） */
    private const val MAX_TITLE_LINE_LEN = 40

    /** 相邻章节之间至少相隔的字节数，过滤"第1条/第2条"之类的假标题 */
    private const val MIN_CHAPTER_BYTES = 500L

    private val TITLE_REGEXES = listOf(
        // 第一章 / 第123回 / 第十章 风云再起
        Regex("""^第\s*[0-9零〇一二三四五六七八九十百千万两]+\s*[章节回卷部集].{0,30}$"""),
        // 序章、楔子、引子、前言、后记、尾声、番外
        Regex("""^(序章|序言|序|楔子|引子|前言|后记|尾声|番外篇?|终章)\b.{0,30}$"""),
        // Chapter 1 / CHAPTER XII
        Regex("""^Chapter\s+[\dIVXLCDMivxlcdm]+\b.{0,30}$""", RegexOption.IGNORE_CASE),
        // 卷一 / 卷 1
        Regex("""^卷\s*[0-9零〇一二三四五六七八九十百千万两]+\s*.{0,30}$"""),
        // 单独的"正文"
        Regex("""^正文\s*$""")
    )

    /**
     * 扫描全书字节，返回过滤后的章节标题列表（不含末尾兜底章节）。
     */
    fun parseHeadings(bytes: ByteArray, charset: java.nio.charset.Charset): List<ParsedHeading> {
        val candidates = ArrayList<ParsedHeading>()
        var pos = 0
        val n = bytes.size

        while (pos < n) {
            // 找下一行的行首与行尾（按 \n 切，兼容 \r\n 与 \r）
            val lineStart = pos
            var lineEnd = lineStart
            while (lineEnd < n && bytes[lineEnd] != '\n'.code.toByte()) lineEnd++
            var trimEnd = lineEnd
            if (trimEnd > lineStart && bytes[trimEnd - 1] == '\r'.code.toByte()) trimEnd--

            val lineBytes = bytes.copyOfRange(lineStart, trimEnd)
            val line = String(lineBytes, charset).trim()
            pos = lineEnd + 1

            if (line.isEmpty() || line.length > MAX_TITLE_LINE_LEN) continue
            if (TITLE_REGEXES.none { it.matches(line) }) continue
            candidates.add(ParsedHeading(line, lineStart.toLong()))
        }

        return filterFalseHeadings(candidates, n.toLong())
    }

    /**
     * 合并间距过近的假标题：只保留与上一个已保留标题间距 >= MIN_CHAPTER_BYTES 的。
     */
    private fun filterFalseHeadings(
        candidates: List<ParsedHeading>,
        fileSize: Long
    ): List<ParsedHeading> {
        if (candidates.isEmpty()) return candidates
        val result = ArrayList<ParsedHeading>()
        for (heading in candidates) {
            if (result.isEmpty()) {
                // 开头标题若前面正文很长也保留（通常是楔子/序章）
                result.add(heading)
                continue
            }
            val prev = result.last()
            val gapToPrev = heading.startByteOffset - prev.startByteOffset
            if (gapToPrev >= MIN_CHAPTER_BYTES) {
                result.add(heading)
            }
        }
        // 最后一章过短（孤悬文末的标题）则丢弃
        if (result.size >= 2 && fileSize - result.last().startByteOffset < MIN_CHAPTER_BYTES / 2) {
            result.removeAt(result.size - 1)
        }
        // 只有 0~1 个标题时由调用方按整本一章兜底
        return if (result.size >= 2) result else emptyList()
    }
}
