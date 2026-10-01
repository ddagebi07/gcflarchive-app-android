package kr.co.gcflarchive.admin.ui.kit

import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminApi
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.core.Fetched
import kr.co.gcflarchive.admin.core.Grants
import kr.co.gcflarchive.admin.databinding.FragmentListBinding
import java.text.DateFormat
import java.util.Date

/**
 * Base for admin list screens: pull-to-refresh, search, filter chips, offline banner
 * (last cached data shown read-only), empty/error state, FAB, swipe action and
 * multi-select with a contextual action bar.
 */
abstract class ListFragment : Fragment(), RowAdapter.Listener {
    private var _binding: FragmentListBinding? = null
    protected val binding get() = _binding!!
    protected val api: AdminApi get() = AdminApi.get(requireContext())
    protected val grants: Grants get() = AdminSession.get(requireContext()).grants
    protected val adapter = RowAdapter(this)

    private var allRows: List<Row> = emptyList()
    private var loadJob: Job? = null
    private var actionMode: ActionMode? = null

    /** True while the shown data came from the offline cache. */
    protected var offline = false
        private set

    // ── Configuration hooks ──────────────────────────────────────────────
    protected open val searchHint: String? = null
    protected open val emptyText: String get() = getString(R.string.list_empty)
    /** Label + icon for the FAB, or null for none. */
    protected open val fab: Pair<String, Int>? = null
    protected open fun onFab() {}
    /** Swipe-left label (e.g. "해제"), or null to disable swiping. */
    protected open val swipeLabel: String? = null
    protected open fun onSwipe(row: Row) {}
    /** Action for selected rows (e.g. "삭제"), or null to disable multi-select. */
    protected open val selectionAction: String? = null
    protected open fun onSelectionAction(rows: List<Row>) {}

    /** Loads rows; use [fetch] for GETs so offline fallback is tracked. */
    protected abstract suspend fun load(): List<Row>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.swipe.setOnRefreshListener { reload() }
        searchHint?.let { hint ->
            binding.controls.isVisible = true
            binding.searchLayout.isVisible = true
            binding.searchInput.hint = hint
            binding.searchInput.doAfterTextChanged { render() }
        }
        fab?.let { (label, icon) ->
            binding.fab.isVisible = true
            binding.fab.text = label
            binding.fab.setIconResource(icon)
            binding.fab.setOnClickListener { if (guardWrite()) onFab() }
            // Collapse to an icon while scrolling down so it covers less of the rows.
            binding.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 8 && binding.fab.isExtended) binding.fab.shrink()
                    else if (dy < -8 && !binding.fab.isExtended) binding.fab.extend()
                }
            })
        }
        // Room below the last row: clears the FAB when there is one, a small margin otherwise.
        binding.list.updatePadding(bottom = view.dp(if (fab != null) 104 else 24))
        if (swipeLabel != null) attachSwipe()
        reload()
    }

    /** Filter chips under the search box; [onSelect] gets the chosen key. */
    protected fun setChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
        binding.controls.isVisible = true
        binding.chipScroll.isVisible = true
        binding.chips.removeAllViews()
        options.forEach { (key, label) ->
            val chip = (layoutInflater.inflate(R.layout.item_filter_chip, binding.chips, false) as Chip).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isChecked = key == selected
                setOnClickListener { if (isChecked) onSelect(key) }
            }
            binding.chips.addView(chip)
        }
    }

    /** Snackbar that sits above the FAB instead of under it. */
    protected fun snackbar(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        val b = _binding ?: return
        val bar = Snackbar.make(b.root, message, Snackbar.LENGTH_LONG)
        if (b.fab.isVisible) bar.anchorView = b.fab
        if (action != null && onAction != null) bar.setAction(action) { onAction() }
        bar.show()
    }

    protected fun setSummary(text: String?) {
        binding.summary.isVisible = !text.isNullOrBlank()
        binding.summary.text = text
    }

    /** GET with offline fallback; marks the screen read-only when cached data is used. */
    protected suspend fun fetch(path: String): Fetched {
        val f = api.get(path)
        if (f.fromCache) offline = true
        return f
    }

    fun reload() {
        val b = _binding ?: return
        loadJob?.cancel()
        offline = false
        b.swipe.isRefreshing = true
        b.stateGroup.isVisible = false
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { load() }
            val vb = _binding ?: return@launch
            vb.swipe.isRefreshing = false
            result.onSuccess { rows ->
                allRows = rows
                vb.offlineBanner.isVisible = offline
                vb.offlineBanner.text = getString(R.string.offline_banner)
                render()
            }.onFailure { e ->
                if (e is CancellationException) throw e
                allRows = emptyList()
                adapter.submit(emptyList())
                showState(getString(R.string.list_error), Dialogs.messageOf(requireContext(), e), getString(R.string.retry)) { reload() }
            }
        }
    }

    private fun render() {
        val q = _binding?.searchInput?.text?.toString().orEmpty().trim()
        val rows = allRows.filter { it.matches(q) }
        adapter.submit(rows)
        if (rows.none { !it.header }) showState(emptyText, null, null, null) else showState(null, null, null, null)
    }

    protected fun showState(title: String?, desc: String?, button: String?, onClick: (() -> Unit)?) {
        val b = _binding ?: return
        b.stateGroup.isVisible = title != null
        b.stateTitle.text = title
        b.stateDesc.isVisible = desc != null
        b.stateDesc.text = desc
        b.stateButton.isVisible = button != null
        b.stateButton.text = button
        b.stateButton.setOnClickListener { onClick?.invoke() }
    }

    /** Writes are blocked while showing cached data or without a network. */
    protected fun guardWrite(): Boolean {
        if (offline || !api.isOnline()) {
            Dialogs.toast(requireContext(), getString(R.string.error_offline_write))
            return false
        }
        return true
    }

    /** Runs a write, toasts the outcome and reloads. */
    protected fun act(success: String? = null, block: suspend () -> Unit) {
        if (!guardWrite()) {
            reload()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { block() }
                .onSuccess {
                    success?.let { Dialogs.toast(requireContext(), it) }
                    reload()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Dialogs.toast(requireContext(), Dialogs.messageOf(requireContext(), e))
                    reload()
                }
        }
    }

    // ── Swipe ────────────────────────────────────────────────────────────

    private fun attachSwipe() {
        val ctx = requireContext()
        val bg = ContextCompat.getColor(ctx, R.color.krds_danger)
        val label = swipeLabel.orEmpty()
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 15 * resources.displayMetrics.scaledDensity
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val row = adapter.items.getOrNull(vh.bindingAdapterPosition) ?: return 0
                return if (row.swipeable && !adapter.selecting) ItemTouchHelper.LEFT else 0
            }

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val row = adapter.items.getOrNull(vh.bindingAdapterPosition) ?: return
                adapter.notifyItemChanged(vh.bindingAdapterPosition) // snap back; the action confirms first
                if (guardWrite()) onSwipe(row)
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, state: Int, active: Boolean) {
                val v = vh.itemView
                if (dX < 0) {
                    c.drawRoundRect(v.right + dX, v.top.toFloat(), v.right.toFloat(), (v.bottom - v.dp(8)).toFloat(), v.dp(12).toFloat(), v.dp(12).toFloat(), android.graphics.Paint().apply { color = bg })
                    val w = paint.measureText(label)
                    c.drawText(label, v.right - w - v.dp(20), v.top + (v.height - v.dp(8)) / 2f + paint.textSize / 3, paint)
                }
                super.onChildDraw(c, rv, vh, dX, dY, state, active)
            }
        }).attachToRecyclerView(binding.list)
    }

    // ── Multi-select ─────────────────────────────────────────────────────

    override fun onSelectionChanged(selected: Set<String>) {
        val action = selectionAction ?: return
        val activity = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        if (selected.isEmpty()) {
            actionMode?.finish()
            return
        }
        if (actionMode == null) {
            actionMode = activity.startSupportActionMode(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
                    menu.add(0, 1, 0, action).setIcon(R.drawable.ic_delete).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu) = false
                override fun onActionItemClicked(mode: ActionMode, item: android.view.MenuItem): Boolean {
                    val rows = adapter.items.filter { it.id in adapter.selected }
                    if (guardWrite()) onSelectionAction(rows)
                    return true
                }
                override fun onDestroyActionMode(mode: ActionMode) {
                    actionMode = null
                    if (adapter.selecting) adapter.clearSelection()
                }
            })
        }
        actionMode?.title = getString(R.string.selected_count, selected.size)
    }

    protected fun endSelection() {
        actionMode?.finish()
    }

    protected fun formatTime(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

    override fun onDestroyView() {
        actionMode?.finish()
        super.onDestroyView()
        _binding = null
    }
}
