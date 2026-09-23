package com.tianjc.reader.util

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity

/**
 * 开启 edge-to-edge：内容绘制到状态栏/导航栏与刘海区后面，
 * 由各页面自行按需消费 insets。
 */
fun AppCompatActivity.enableEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

/**
 * 把状态栏/刘海高度直接设为 View 的高度（用于顶部占位条）。
 */
fun View.applyTopInsetAsHeight() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val lp = v.layoutParams
        if (lp.height != bars.top) {
            lp.height = bars.top
            v.layoutParams = lp
        }
        insets
    }
}

/**
 * 给顶部浮层（Toolbar/菜单条）追加状态栏高度的 padding-top。
 */
fun View.applyTopInset(extraTopPx: Int = 0) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(v.paddingLeft, bars.top + extraTopPx, v.paddingRight, v.paddingBottom)
        insets
    }
}

/**
 * 给底部浮层追加导航栏高度的 padding-bottom。
 */
fun View.applyBottomInset(extraBottomPx: Int = 0) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bars.bottom + extraBottomPx)
        insets
    }
}

/** 一次性读取系统栏 insets（按需使用） */
fun View.systemBarInsets(): Insets? =
    ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())
