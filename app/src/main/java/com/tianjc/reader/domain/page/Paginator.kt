package com.tianjc.reader.domain.page

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * 分页引擎：把一章文本按屏幕宽高切成若干页。
 * 每页只记录在章节文本中的字符区间，不复制文本。
 */
object Paginator {

    fun paginate(
        text: String,
        paint: TextPaint,
        contentWidth: Int,
        contentHeight: Int,
        lineSpacingMult: Float = 1.0f,
        lineSpacingAdd: Float = 0f
    ): List<IntRange> {
        if (text.isEmpty()) return listOf(0..0)

        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineSpacingAdd, lineSpacingMult)
            .setIncludePad(false)
            .build()

        val pages = ArrayList<IntRange>()
        var firstLine = 0
        val lineCount = layout.lineCount

        while (firstLine < lineCount) {
            var lastLine = firstLine
            while (lastLine < lineCount) {
                val bottom = layout.getLineBottom(lastLine)
                val top = layout.getLineTop(firstLine)
                if (bottom - top > contentHeight) break
                lastLine++
            }
            // 极端情况：一行就超出高度，也要容纳这一行
            if (lastLine == firstLine) lastLine++

            val start = layout.getLineStart(firstLine)
            val end = layout.getLineVisibleEnd(lastLine - 1)
            pages.add(start until end)
            firstLine = lastLine
        }

        if (pages.isEmpty()) pages.add(0..0)
        return pages
    }

    /**
     * 为某一页文本构建可绘制的 StaticLayout。
     */
    fun buildPageLayout(
        pageText: String,
        paint: TextPaint,
        contentWidth: Int,
        lineSpacingMult: Float,
        lineSpacingAdd: Float
    ): StaticLayout = StaticLayout.Builder
        .obtain(pageText, 0, pageText.length, paint, contentWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(lineSpacingAdd, lineSpacingMult)
        .setIncludePad(false)
        .build()
}
