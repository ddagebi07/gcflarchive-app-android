package kr.co.gcflarchive.app.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.archive.ArchiveRepository
import kr.co.gcflarchive.app.archive.PostDetailActivity
import kr.co.gcflarchive.app.archive.SearchAdapter
import kr.co.gcflarchive.app.archive.SearchQuery
import kr.co.gcflarchive.app.archive.SearchSort
import kr.co.gcflarchive.app.databinding.FragmentSearchBinding
import java.text.NumberFormat

/** 통합검색: searches posts and attachments crawled from the school homepage (/api/archive/search). */
class SearchFragment : Fragment(), MainActivity.Reselectable {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val adapter = SearchAdapter { post ->
        startActivity(PostDetailActivity.intent(requireContext(), post.boardCode, post.idx))
    }

    private var query = SearchQuery()
    private var page = 0
    private var totalPages = 1
    private var loadJob: Job? = null
    private var boardsLoaded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val layout = LinearLayoutManager(requireContext())
        binding.results.layoutManager = layout
        binding.results.adapter = adapter
        binding.results.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Infinite scroll: fetch the next page a few rows before the end.
                if (dy > 0 && loadJob?.isActive != true && page < totalPages &&
                    layout.findLastVisibleItemPosition() >= adapter.itemCount - 5
                ) {
                    load(append = true)
                }
            }
        })

        binding.queryInput.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                submit()
                true
            } else {
                false
            }
        }
        binding.queryLayout.setEndIconOnClickListener {
            binding.queryInput.text = null
            submit()
        }

        binding.sortGroup.check(R.id.sortRelevance)
        binding.sortGroup.setOnCheckedStateChangeListener { _, ids ->
            val sort = if (ids.firstOrNull() == R.id.sortDate) SearchSort.DATE else SearchSort.RELEVANCE
            if (sort != query.sort) {
                query = query.copy(sort = sort)
                load(append = false)
            }
        }
        binding.chipAttachments.setOnCheckedChangeListener { _, checked ->
            query = query.copy(attachmentsOnly = checked)
            load(append = false)
        }
        binding.swipe.setOnRefreshListener { load(append = false) }
        binding.btnRetry.setOnClickListener { load(append = false) }

        loadBoards()
        load(append = false)
    }

    override fun onReselected() {
        binding.results.scrollToPosition(0)
        binding.queryInput.requestFocus()
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(binding.queryInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun submit() {
        query = query.copy(q = binding.queryInput.text?.toString().orEmpty().trim())
        binding.queryInput.clearFocus()
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.queryInput.windowToken, 0)
        load(append = false)
    }

    private fun loadBoards() {
        if (boardsLoaded) return
        viewLifecycleOwner.lifecycleScope.launch {
            val boards = runCatching { ArchiveRepository.boards() }.getOrNull() ?: return@launch
            val b = _binding ?: return@launch
            boardsLoaded = true
            b.boardGroup.removeAllViews()
            b.boardGroup.addView(boardChip(getString(R.string.search_board_all), null))
            boards.forEach { b.boardGroup.addView(boardChip(it.name, it.code)) }
            b.boardGroup.check(b.boardGroup.getChildAt(0).id)
            b.boardScroll.isVisible = true
        }
    }

    private fun boardChip(label: String, code: String?): Chip =
        (layoutInflater.inflate(R.layout.item_filter_chip, binding.boardGroup, false) as Chip).apply {
            id = View.generateViewId()
            text = label
            setOnClickListener {
                if (query.board != code) {
                    query = query.copy(board = code)
                    load(append = false)
                }
            }
        }

    private fun load(append: Boolean) {
        loadJob?.cancel()
        val requestedQuery = query
        val requestedPage = if (append) page + 1 else 1
        if (!append) {
            binding.swipe.isRefreshing = true
            binding.errorGroup.isVisible = false
        } else {
            binding.loadingMore.isVisible = true
        }

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { ArchiveRepository.search(requestedQuery, requestedPage) }
            val b = _binding ?: return@launch
            b.swipe.isRefreshing = false
            b.loadingMore.isVisible = false
            result.onSuccess { res ->
                page = res.page
                totalPages = res.totalPages
                adapter.submit(res.items, append)
                if (!append) b.results.scrollToPosition(0)
                b.resultCount.text = getString(
                    if (requestedQuery.q.isBlank()) R.string.search_count_recent else R.string.search_count,
                    NumberFormat.getIntegerInstance().format(res.totalCount),
                )
                b.emptyText.isVisible = res.totalCount == 0
                b.emptyText.text = if (requestedQuery.q.isBlank()) {
                    getString(R.string.search_empty_filtered)
                } else {
                    getString(R.string.search_empty, requestedQuery.q)
                }
            }.onFailure {
                if (!append) {
                    adapter.submit(emptyList(), append = false)
                    b.errorGroup.isVisible = true
                    b.emptyText.isVisible = false
                    b.resultCount.text = ""
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
