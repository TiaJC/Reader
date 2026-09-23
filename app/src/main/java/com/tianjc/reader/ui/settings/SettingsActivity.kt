package com.tianjc.reader.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tianjc.reader.R
import com.tianjc.reader.databinding.ActivitySettingsBinding
import com.tianjc.reader.util.applyBottomInset
import com.tianjc.reader.util.applyTopInset
import com.tianjc.reader.util.enableEdgeToEdge

/**
 * 阅读设置：字号、翻页方式（左右翻页/上下滚动）、色调（白天/护眼/夜晚）。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var s: ReaderSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        s = ReaderSettings(this)
        enableEdgeToEdge()

        binding.toolbar.applyTopInset()
        binding.root.applyBottomInset()
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnFontMinus.setOnClickListener {
            s.textSizeSp = s.textSizeSp - 1f
            refresh()
        }
        binding.btnFontPlus.setOnClickListener {
            s.textSizeSp = s.textSizeSp + 1f
            refresh()
        }

        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                s.readingMode = if (checkedId == R.id.btnModeScroll) 1 else 0
            }
        }

        binding.colorGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                s.colorMode = when (checkedId) {
                    R.id.btnColorEye -> 1
                    R.id.btnColorNight -> 2
                    else -> 0
                }
                applyTheme()
            }
        }

        refresh()
    }

    private fun refresh() {
        binding.tvFontSize.text = s.textSizeSp.toInt().toString()

        val modeId = if (s.readingMode == 1) R.id.btnModeScroll else R.id.btnModePage
        if (binding.modeGroup.checkedButtonId != modeId) binding.modeGroup.check(modeId)

        val colorId = when (s.colorMode) {
            1 -> R.id.btnColorEye
            2 -> R.id.btnColorNight
            else -> R.id.btnColorDay
        }
        if (binding.colorGroup.checkedButtonId != colorId) binding.colorGroup.check(colorId)

        applyTheme()
    }

    /** 随色调即时预览 */
    private fun applyTheme() {
        val c = s.color
        window.decorView.setBackgroundColor(c.bg)
        binding.toolbar.setBackgroundColor(c.bar)
        binding.toolbar.setTitleTextColor(c.barText)
        binding.toolbar.setNavigationIconTint(c.barText)

        listOf(binding.cardFont, binding.cardMode, binding.cardColor).forEach {
            it.setCardBackgroundColor(c.bar)
        }
        listOf(
            binding.tvFontLabel, binding.tvFontSize,
            binding.btnFontMinus, binding.btnFontPlus,
            binding.tvModeLabel, binding.tvColorLabel
        ).forEach { it.setTextColor(c.barText) }
    }
}
