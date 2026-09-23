package com.tianjc.reader.ui.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.OverScroller
import com.tianjc.reader.domain.page.Paginator
import kotlin.math.abs

/**
 * 自定义文本阅读控件。
 *
 * 两种阅读方式（[readingMode]）：
 *  - 0 左右翻页：StaticLayout 分页，覆盖/平移/无动画；
 *  - 1 上下滚动：整章一个长 StaticLayout，手指/惯性纵向滚动，
 *    在首/末继续滑动请求相邻章节。
 */
class ReaderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Callback {
        fun requestChapter(forward: Boolean)
        fun onProgressChanged(chapterIndex: Int, charOffset: Int)
        fun onCenterTapped()
    }

    var callback: Callback? = null

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f.sp()
    }
    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f.sp()
    }

    private var padHorizontal = 24f.dp()
    private var padVertical = 24f.dp()
    private var lineSpacingExtraPx = 6f.dp()
    private val footerHeight = 22f.dp()

    /** 0 左右翻页 / 1 上下滚动 */
    var readingMode = 0
    var animationType = 0

    private class ChapterState(
        val index: Int,
        val title: String,
        val text: String,
        val pages: List<IntRange>,
        var page: Int,
        var fullLayout: StaticLayout? = null,
        var scrollY: Int = 0
    )

    private var current: ChapterState? = null
    private var incoming: ChapterState? = null
    private var incomingPage = 0
    private var incomingScrollY = 0

    private var curLayout: StaticLayout? = null
    private var inLayout: StaticLayout? = null

    private var animator: ValueAnimator? = null
    private var fraction = 0f
    private var animForward = true

    private val scroller = OverScroller(context)

    init {
        // 消费 ACTION_DOWN，否则后续 MOVE/UP 事件不会送达
        isClickable = true
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (readingMode == 1) {
                callback?.onCenterTapped()
                return true
            }
            when {
                e.x < width / 3f -> turn(false)
                e.x > width * 2f / 3f -> turn(true)
                else -> callback?.onCenterTapped()
            }
            return true
        }

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent,
            distanceX: Float, distanceY: Float
        ): Boolean {
            if (readingMode != 1) return false
            if (animator?.isRunning == true) return false
            scrollChapterBy(distanceY.toInt())
            return true
        }

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent,
            velocityX: Float, velocityY: Float
        ): Boolean {
            if (readingMode == 1) {
                val state = current ?: return false
                val maxS = maxScroll(state)
                when {
                    state.scrollY <= 0 && velocityY > 600 -> {
                        callback?.requestChapter(false); return true
                    }
                    state.scrollY >= maxS && velocityY < -600 -> {
                        callback?.requestChapter(true); return true
                    }
                }
                scroller.fling(0, state.scrollY, 0, -velocityY.toInt(), 0, 0, 0, maxS)
                invalidate()
                return true
            }
            val dx = e2.x - (e1?.x ?: e2.x)
            if (abs(dx) > 60 && abs(velocityX) > 200 && animator?.isRunning != true) {
                turn(dx < 0)
                return true
            }
            return false
        }
    })

    // region 配置

    fun setTextColor(color: Int) {
        textPaint.color = color
        footerPaint.color = color
        clearLayouts()
        invalidate()
    }

    fun setTextSizePx(sizePx: Float) {
        textPaint.textSize = sizePx
        refreshCurrent(rebuild = true)
    }

    fun setMargins(horizontalPx: Float, verticalPx: Float) {
        padHorizontal = horizontalPx
        padVertical = verticalPx
        refreshCurrent(rebuild = true)
    }

    fun setLineSpacingExtra(px: Float) {
        lineSpacingExtraPx = px
        refreshCurrent(rebuild = true)
    }

    val currentChapterIndex: Int get() = current?.index ?: -1

    val currentChapterLength: Int get() = current?.text?.length ?: 0

    /** 当前章节可视位置的章内字符偏移（字号/模式变化时也能恢复） */
    val currentCharOffset: Int
        get() {
            val state = current ?: return 0
            return if (readingMode == 1) offsetAtScroll(state)
            else state.pages[state.page].first.coerceAtMost(state.text.length)
        }

    val pageInfo: Pair<Int, Int>?
        get() = current?.let { it.page to it.pages.size }

    // endregion

    fun showChapter(
        index: Int,
        text: String,
        title: String = "",
        restoreCharOffset: Int? = null,
        forward: Boolean? = null
    ) {
        animator?.cancel()
        scroller.forceFinished(true)

        val state = if (readingMode == 1) {
            val full = buildFullLayout(text)
            val s = when {
                restoreCharOffset != null -> scrollForOffset(full, restoreCharOffset)
                forward == true -> 0
                forward == false -> (full.height - contentHeight()).coerceAtLeast(0)
                else -> 0
            }
            ChapterState(
                index, title, text,
                pages = listOf(0 until text.length),
                page = 0,
                fullLayout = full,
                scrollY = s
            )
        } else {
            val pages = Paginator.paginate(text, textPaint, contentWidth(), contentHeight(),
                lineSpacingAdd = lineSpacingExtraPx)
            val page = when {
                restoreCharOffset != null -> findPageContaining(pages, restoreCharOffset)
                forward == true -> 0
                forward == false -> pages.lastIndex
                else -> 0
            }
            ChapterState(index, title, text, pages, page)
        }

        if (current == null || forward == null ||
            readingMode == 1 || animationType == 2
        ) {
            current = state
            incoming = null
            clearLayouts()
            invalidate()
            notifyProgress()
        } else {
            incoming = state
            incomingPage = state.page
            startAnimation(forward)
        }
    }

    fun turn(forward: Boolean) {
        val state = current ?: return
        if (animator?.isRunning == true) return
        val target = if (forward) state.page + 1 else state.page - 1
        if (target in state.pages.indices) {
            incoming = state
            incomingPage = target
            startAnimation(forward)
        } else {
            callback?.requestChapter(forward)
        }
    }

    fun goToPage(target: Int) {
        val state = current ?: return
        if (readingMode == 1 || animator?.isRunning == true) return
        if (target !in state.pages.indices || target == state.page) return
        incoming = state
        incomingPage = target
        startAnimation(target > state.page)
    }

    private fun startAnimation(forward: Boolean) {
        animForward = forward
        curLayout = pageLayout(current!!, current!!.page)
        inLayout = pageLayout(incoming!!, incomingPage)
        fraction = 0f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = LinearInterpolator()
            addUpdateListener {
                fraction = it.animatedValue as Float
                invalidate()
                if (fraction == 1f) commitIncoming()
            }
            start()
        }
    }

    private fun commitIncoming() {
        val target = incoming ?: return
        if (target !== current) current = target
        current!!.page = incomingPage
        current!!.scrollY = incomingScrollY
        incoming = null
        clearLayouts()
        invalidate()
        notifyProgress()
    }

    private fun notifyProgress() {
        val state = current ?: return
        callback?.onProgressChanged(state.index, currentCharOffset)
    }

    // region 滚动模式

    private fun maxScroll(state: ChapterState): Int {
        val layout = state.fullLayout ?: return 0
        return (layout.height - contentHeight()).coerceAtLeast(0)
    }

    private fun scrollChapterBy(distancePx: Int) {
        val state = current ?: return
        val target = (state.scrollY + distancePx)
        state.scrollY = target.coerceIn(0, maxScroll(state))
        invalidate()
        notifyProgress()
    }

    private fun offsetAtScroll(state: ChapterState): Int {
        val layout = state.fullLayout ?: return 0
        val line = layout.getLineForVertical(state.scrollY + 1)
        return layout.getLineStart(line.coerceAtMost(layout.lineCount - 1))
    }

    private fun scrollForOffset(layout: StaticLayout, offset: Int): Int {
        val line = layout.getLineForOffset(offset.coerceIn(0, layout.text.length))
        return layout.getLineTop(line).coerceIn(0, (layout.height - contentHeight()).coerceAtLeast(0))
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            val state = current ?: return
            state.scrollY = scroller.currY.coerceIn(0, maxScroll(state))
            invalidate()
            notifyProgress()
        }
    }

    // endregion

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = current ?: return

        if (readingMode == 1) {
            val layout = state.fullLayout ?: return
            canvas.save()
            canvas.translate(padHorizontal, padVertical - state.scrollY)
            canvas.clipRect(
                0f, state.scrollY - padVertical,
                width.toFloat(), state.scrollY - padVertical + contentHeight()
            )
            layout.draw(canvas)
            canvas.restore()
            drawFooter(canvas, state)
            return
        }

        if (incoming == null || animator?.isRunning != true) {
            val layout = curLayout ?: pageLayout(state, state.page).also { curLayout = it }
            drawPage(canvas, layout, 0f)
            drawFooter(canvas, state)
            return
        }

        val f = fraction
        val w = width.toFloat()
        val curL = curLayout ?: pageLayout(state, state.page).also { curLayout = it }
        val inL = inLayout ?: pageLayout(incoming!!, incomingPage).also { inLayout = it }

        if (animForward) {
            drawPage(canvas, curL, if (animationType == 1) -f * w else 0f)
            drawPage(canvas, inL, (1 - f) * w)
        } else {
            drawPage(canvas, curL, if (animationType == 1) f * w else 0f)
            drawPage(canvas, inL, (f - 1) * w)
        }
    }

    private fun drawPage(canvas: Canvas, layout: StaticLayout, translationX: Float) {
        canvas.save()
        canvas.translate(padHorizontal + translationX, padVertical)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawFooter(canvas: Canvas, state: ChapterState) {
        val y = height - footerHeight / 2
        val left = state.title.ifEmpty { "" }
        val right = if (readingMode == 1) {
            val percent = if (maxScroll(state) == 0) 100
            else state.scrollY * 100 / maxScroll(state)
            "$percent%"
        } else {
            "${state.page + 1}/${state.pages.size}"
        }
        canvas.drawText(left, padHorizontal, y, footerPaint)
        val w = footerPaint.measureText(right)
        canvas.drawText(right, width - padHorizontal - w, y, footerPaint)
    }

    private fun pageLayout(state: ChapterState, pageIndex: Int): StaticLayout {
        val range = state.pages[pageIndex]
        val end = range.last.coerceAtMost(state.text.length)
        val pageText = state.text.substring(range.first, end)
        return Paginator.buildPageLayout(
            pageText, textPaint, contentWidth(), 1.0f, lineSpacingExtraPx
        )
    }

    private fun buildFullLayout(text: String): StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, textPaint, contentWidth())
        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(lineSpacingExtraPx, 1.0f)
        .setIncludePad(false)
        .build()

    private fun findPageContaining(pages: List<IntRange>, offset: Int): Int {
        val idx = pages.indexOfFirst { offset in it }
        if (idx >= 0) return idx
        return pages.indexOfLast { offset >= it.first }.coerceAtLeast(0)
    }

    private fun contentWidth(): Int = (width - padHorizontal * 2).toInt().coerceAtLeast(1)

    private fun contentHeight(): Int =
        (height - padVertical * 2 - footerHeight).toInt().coerceAtLeast(1)

    private fun clearLayouts() {
        curLayout = null
        inLayout = null
    }

    /** 设置变化后重建：保持当前阅读位置 */
    private fun refreshCurrent(rebuild: Boolean) {
        val state = current ?: return
        val keepOffset = currentCharOffset
        if (readingMode == 1) {
            val full = buildFullLayout(state.text)
            state.fullLayout = full
            state.scrollY = scrollForOffset(full, keepOffset)
        } else {
            val pages = Paginator.paginate(
                state.text, textPaint, contentWidth(), contentHeight(),
                lineSpacingAdd = lineSpacingExtraPx
            )
            val updated = ChapterState(
                state.index, state.title, state.text, pages,
                findPageContaining(pages, keepOffset.coerceAtMost(state.text.length))
            )
            current = updated
        }
        incoming = null
        clearLayouts()
        invalidate()
        notifyProgress()
    }

    /** 切换左右翻页/上下滚动：由外部重新 showChapter 当前章 */
    fun modeChanged() {
        refreshCurrent(rebuild = true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if ((w != oldw || h != oldh) && current != null) refreshCurrent(rebuild = true)
    }

    private var downX = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                scroller.forceFinished(true)
            }
            MotionEvent.ACTION_UP -> {
                if (readingMode == 0) {
                    val dx = event.x - downX
                    if (abs(dx) > 80 && animator?.isRunning != true) turn(dx < 0)
                }
            }
        }
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun Float.dp(): Float = this * resources.displayMetrics.density
    private fun Float.sp(): Float = this * resources.displayMetrics.scaledDensity
}
