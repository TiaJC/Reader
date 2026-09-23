package com.tianjc.reader.ui.reader

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tianjc.reader.R
import com.tianjc.reader.databinding.ActivityReaderBinding
import com.tianjc.reader.ui.settings.ReaderSettings
import com.tianjc.reader.ui.settings.SettingsActivity
import com.tianjc.reader.util.applyBottomInset
import com.tianjc.reader.util.applyTopInset
import com.tianjc.reader.util.enableEdgeToEdge
import kotlinx.coroutines.launch

class ReaderActivity : AppCompatActivity(), ReaderView.Callback {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var settings: ReaderSettings
    private val vm: ReaderViewModel by viewModels()

    private var barsVisible = false
    private var sliderDragging = false
    private var firstShown = false
    private val autoHideRunnable = Runnable {
        if (barsVisible && binding.settingsPanel.visibility != View.VISIBLE) {
            hideOverlays()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = ReaderSettings(this)
        enableEdgeToEdge()
        binding.readerView.callback = this

        binding.topBar.applyTopInset(extraTopPx = 0)
        binding.bottomBar.applyBottomInset()
        binding.settingsPanel.applyBottomInset()

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId <= 0) {
            finish()
            return
        }

        observeViewModel()
        wireMenus()
        applyAllSettings()
        vm.load(bookId)
        hideOverlays()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.state.collect { data ->
                        data?.let { binding.tvTopTitle.text = it.book.title }
                    }
                }
                launch {
                    vm.chapterEvent.collect { display ->
                        val chapterTitle = vm.state.value
                            ?.chapters?.getOrNull(display.index)?.title.orEmpty()
                        binding.readerView.showChapter(
                            index = display.index,
                            text = display.text,
                            title = chapterTitle,
                            restoreCharOffset = display.restoreCharOffset,
                            forward = display.forward
                        )
                        updateProgressUi()
                        maybeShowFirstTimeHint()
                    }
                }
            }
        }
    }

    private fun wireMenus() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnChapters.setOnClickListener {
            ChaptersBottomSheet().show(supportFragmentManager, "chapters")
        }
        binding.btnPrevChapter.setOnClickListener { switchChapter(false) }
        binding.btnNextChapter.setOnClickListener { switchChapter(true) }

        binding.btnSettings.setOnClickListener {
            binding.bottomBar.visibility = View.GONE
            binding.settingsPanel.visibility = View.VISIBLE
            refreshSettingsPanel()
        }

        binding.progressSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                sliderDragging = true
                binding.readerView.goToPage(value.toInt())
            }
        }
        binding.progressSlider.addOnSliderTouchListener(object :
            com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                sliderDragging = true
            }

            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                sliderDragging = false
            }
        })

        binding.quickColorGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                settings.colorMode = when (checkedId) {
                    R.id.btnQuickEye -> 1
                    R.id.btnQuickNight -> 2
                    else -> 0
                }
                applyAllSettings()
            }
        }

        binding.btnFontMinus.setOnClickListener {
            settings.textSizeSp = settings.textSizeSp - 1f
            applyAllSettings()
        }
        binding.btnFontPlus.setOnClickListener {
            settings.textSizeSp = settings.textSizeSp + 1f
            applyAllSettings()
        }

        binding.btnMoreSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // region ReaderView.Callback

    override fun requestChapter(forward: Boolean) = switchChapter(forward)

    override fun onProgressChanged(chapterIndex: Int, charOffset: Int) {
        vm.saveProgress(chapterIndex, charOffset, binding.readerView.currentChapterLength)
        updateProgressUi()
    }

    override fun onCenterTapped() {
        // 有任何浮层（菜单/设置面板）→ 全部关闭；否则呼出菜单
        if (barsVisible) hideOverlays() else showBars()
    }

    // endregion

    private fun switchChapter(forward: Boolean) {
        val data = vm.state.value ?: return
        val target = binding.readerView.currentChapterIndex + if (forward) 1 else -1
        if (target !in data.chapters.indices) return
        vm.displayChapter(
            index = target,
            restoreCharOffset = if (forward) 0 else null,
            forward = forward
        )
    }

    fun currentChapterIndex(): Int = binding.readerView.currentChapterIndex

    private fun maybeShowFirstTimeHint() {
        if (firstShown) return
        firstShown = true
        showBars()
        binding.root.postDelayed(autoHideRunnable, 4000)
    }

    private fun showBars() {
        barsVisible = true
        binding.topBar.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE
        binding.settingsPanel.visibility = View.GONE

        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.show(WindowInsetsCompat.Type.systemBars())
        updateProgressUi()
    }

    private fun hideOverlays() {
        binding.root.removeCallbacks(autoHideRunnable)
        barsVisible = false
        binding.topBar.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        binding.settingsPanel.visibility = View.GONE

        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun applyAllSettings() {
        val c = settings.color
        val oldMode = binding.readerView.readingMode
        val modeChanged = oldMode != settings.readingMode
        val keepIndex = binding.readerView.currentChapterIndex
        val keepOffset = binding.readerView.currentCharOffset

        binding.readerRoot.setBackgroundColor(c.bg)
        binding.readerView.setBackgroundColor(c.bg)
        binding.readerView.setTextColor(c.text)
        binding.readerView.animationType = settings.animation
        binding.readerView.readingMode = settings.readingMode

        val sizePx = settings.textSizeSp * resources.displayMetrics.scaledDensity
        binding.readerView.setTextSizePx(sizePx)

        val marginPx = ReaderSettings.marginDp[settings.marginLevel] * resources.displayMetrics.density
        binding.readerView.setMargins(marginPx, marginPx)

        val spacingPx =
            ReaderSettings.lineSpacingExtraDp[settings.lineSpacingLevel] * resources.displayMetrics.density
        binding.readerView.setLineSpacingExtra(spacingPx)

        val barTint = android.content.res.ColorStateList.valueOf(c.bar)
        listOf(binding.topBar, binding.bottomBar, binding.settingsPanel).forEach {
            it.backgroundTintList = barTint
        }
        listOf(
            binding.btnBack, binding.tvTopTitle, binding.btnChapters,
            binding.btnPrevChapter, binding.btnSettings, binding.btnNextChapter,
            binding.btnFontMinus, binding.btnFontPlus, binding.tvFontSize,
            binding.tvProgress
        ).forEach { it.setTextColor(c.barText) }

        WindowInsetsControllerCompat(window, binding.root)
            .isAppearanceLightStatusBars = !settings.isNight

        if (modeChanged && keepIndex >= 0) {
            vm.displayChapter(keepIndex, keepOffset, null)
        }
        refreshSettingsPanel()
    }

    private fun refreshSettingsPanel() {
        if (!::settings.isInitialized) return
        binding.tvFontSize.text = settings.textSizeSp.toInt().toString()

        val colorId = when (settings.colorMode) {
            1 -> R.id.btnQuickEye
            2 -> R.id.btnQuickNight
            else -> R.id.btnQuickDay
        }
        if (binding.quickColorGroup.checkedButtonId != colorId) {
            binding.quickColorGroup.check(colorId)
        }
    }

    private fun updateProgressUi() {
        val info = binding.readerView.pageInfo ?: return
        val (page, total) = info
        binding.progressSlider.valueTo = (total - 1).coerceAtLeast(1).toFloat()
        if (!sliderDragging) binding.progressSlider.value = page.toFloat()
        binding.tvProgress.text = "${page + 1}/$total"
    }

    override fun onResume() {
        super.onResume()
        if (::settings.isInitialized) applyAllSettings()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                binding.readerView.turn(true); true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                binding.readerView.turn(false); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
    }
}
