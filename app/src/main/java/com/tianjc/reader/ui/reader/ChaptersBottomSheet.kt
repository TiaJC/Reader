package com.tianjc.reader.ui.reader

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tianjc.reader.R
import com.tianjc.reader.databinding.ViewChapterListBinding

/**
 * 章节目录底部弹窗。
 */
class ChaptersBottomSheet : BottomSheetDialogFragment() {

    private var _binding: ViewChapterListBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.Theme_Reader_BottomSheet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        _binding = ViewChapterListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        val vm = ViewModelProvider(requireActivity())[ReaderViewModel::class.java]
        val data = vm.state.value ?: run {
            dismissAllowingStateLoss()
            return
        }

        binding.tvSheetTitle.text = data.book.title
        binding.tvSheetCount.text = "共 ${data.chapters.size} 章"

        val current = vm.let {
            // 当前阅读章下标以阅读控件为准
            (activity as? ReaderActivity)?.currentChapterIndex() ?: 0
        }

        val accent = ContextCompat.getColor(requireContext(), R.color.reader_accent)
        val normal = ContextCompat.getColor(requireContext(), R.color.chapter_title_normal)

        val adapter = ChapterListAdapter(
            chapters = data.chapters,
            currentIndex = current,
            currentColor = accent,
            normalColor = normal
        ) { position ->
            vm.displayChapter(position, 0, null)
            dismiss()
        }
        binding.chapterRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.chapterRecycler.adapter = adapter
        binding.chapterRecycler.scrollToPosition(current)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
