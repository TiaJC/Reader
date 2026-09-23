package com.tianjc.reader.ui.bookshelf

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tianjc.reader.data.entity.Book
import com.tianjc.reader.databinding.FragmentBookshelfBinding
import com.tianjc.reader.ui.reader.ReaderActivity
import com.tianjc.reader.util.applyBottomInset
import com.tianjc.reader.util.applyTopInsetAsHeight
import kotlinx.coroutines.launch

class BookshelfFragment : Fragment() {

    private var _binding: FragmentBookshelfBinding? = null
    private val binding get() = _binding!!

    private val vm: BookshelfViewModel by viewModels()

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                // 长期保留读权限不必要（文件已复制），但保留也无妨
                vm.importBook(it)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookshelfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = BookAdapter(
            onClick = { book -> openReader(book) },
            onLongClick = { book -> confirmDelete(book) }
        )
        binding.bookRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.bookRecycler.adapter = adapter

        // 占位条吃掉状态栏高度，工具栏整体位于其下方，标题与按钮不被系统图标遮挡
        binding.statusBarSpacer.applyTopInsetAsHeight()
        binding.bookRecycler.applyBottomInset()
        binding.toolbar.title = "书架"
        binding.toolbar.inflateMenu(com.tianjc.reader.R.menu.menu_bookshelf)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.tianjc.reader.R.id.action_add -> {
                    pickFile.launch(arrayOf("text/plain", "*/*"))
                    true
                }
                com.tianjc.reader.R.id.action_settings -> {
                    startActivity(android.content.Intent(requireContext(),
                        com.tianjc.reader.ui.settings.SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.books.collect { books ->
                        adapter.submitList(books)
                        binding.emptyView.visibility =
                            if (books.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.importing.collect { importing ->
                        binding.importProgress.visibility =
                            if (importing) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.message.collect { msg ->
                        if (msg != null) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setMessage(msg)
                                .setPositiveButton("知道了", null)
                                .show()
                            vm.consumeMessage()
                        }
                    }
                }
            }
        }
    }

    private fun openReader(book: Book) {
        val intent = Intent(requireContext(), ReaderActivity::class.java)
            .putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
        startActivity(intent)
    }

    private fun confirmDelete(book: Book) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("移除书籍")
            .setMessage("确定将《${book.title}》从书架移除吗？复制的本地文件也会一起删除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("移除") { _, _ -> vm.deleteBook(book) }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
