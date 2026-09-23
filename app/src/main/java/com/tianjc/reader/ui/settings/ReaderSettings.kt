package com.tianjc.reader.ui.settings

import android.content.Context
import android.content.SharedPreferences

/** 色调：背景色 / 正文色 / 菜单条背景 / 菜单条文字色 */
data class ReaderColor(val bg: Int, val text: Int, val bar: Int, val barText: Int)

/**
 * 阅读器设置（SharedPreferences 存储）。
 */
class ReaderSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    /** 正文字号（sp），12..36 */
    var textSizeSp: Float
        get() = prefs.getFloat(KEY_TEXT_SIZE, 18f)
        set(value) = prefs.edit().putFloat(KEY_TEXT_SIZE, value.coerceIn(12f, 36f)).apply()

    /** 行距档位 0..2 */
    var lineSpacingLevel: Int
        get() = prefs.getInt(KEY_LINE_SPACING, 1)
        set(value) = prefs.edit().putInt(KEY_LINE_SPACING, value.coerceIn(0, 2)).apply()

    /** 边距档位 0..2 */
    var marginLevel: Int
        get() = prefs.getInt(KEY_MARGIN, 1)
        set(value) = prefs.edit().putInt(KEY_MARGIN, value.coerceIn(0, 2)).apply()

    /** 阅读方式：0=左右翻页，1=上下滚动 */
    var readingMode: Int
        get() = prefs.getInt(KEY_READING_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_READING_MODE, value.coerceIn(0, 1)).apply()

    /** 色调：0=白天，1=护眼（暖黄/暖棕），2=夜晚 */
    var colorMode: Int
        get() = prefs.getInt(KEY_COLOR_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_COLOR_MODE, value.coerceIn(0, 2)).apply()

    /** 翻页动画 0=覆盖 1=平移 2=无动画（仅左右翻页模式生效） */
    var animation: Int
        get() = prefs.getInt(KEY_ANIMATION, 0)
        set(value) = prefs.edit().putInt(KEY_ANIMATION, value.coerceIn(0, 2)).apply()

    val color: ReaderColor
        get() = colors[colorMode]

    val isNight: Boolean get() = colorMode == 2

    companion object {
        private const val KEY_TEXT_SIZE = "text_size_sp"
        private const val KEY_LINE_SPACING = "line_spacing_level"
        private const val KEY_MARGIN = "margin_level"
        private const val KEY_READING_MODE = "reading_mode"
        private const val KEY_COLOR_MODE = "color_mode"
        private const val KEY_ANIMATION = "animation"

        val colors = listOf(
            ReaderColor(0xFFFFFFFF.toInt(), 0xFF333333.toInt(), 0xFFF7F7F7.toInt(), 0xFF333333.toInt()), // 白天
            ReaderColor(0xFFF5E8D0.toInt(), 0xFF5C4A2E.toInt(), 0xFFEBDCBF.toInt(), 0xFF5C4A2E.toInt()), // 护眼·暖黄暖棕
            ReaderColor(0xFF121212.toInt(), 0xFF9E9E9E.toInt(), 0xFF1E1E1E.toInt(), 0xFFBBBBBB.toInt())  // 夜晚
        )

        val lineSpacingExtraDp = intArrayOf(2, 8, 16)
        val marginDp = intArrayOf(12, 24, 40)
    }
}
