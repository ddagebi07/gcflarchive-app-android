package kr.co.gcflarchive.app.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ItemDocumentBinding
import kr.co.gcflarchive.app.web.GcflWebView

/** 문서 자료실 (PDF 아카이브) — native version of documents.html. */
class DocumentsActivity : LibraryListActivity() {
    override val webPath = "/documents"
    override val pageTitleRes = R.string.docs_page_title
    override val pageDescRes = R.string.docs_page_desc
    override val searchHintRes = R.string.docs_search_hint

    private var all: List<LibraryDocument> = emptyList()
    private var category = DocCategory.ALL
    private var sort = DocSort.DATE_DESC
    private val adapter = DocAdapter()

    override fun setUpList() {
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.actionButton.visibility = View.VISIBLE
        binding.actionButton.setIconResource(R.drawable.ic_sort)
        binding.actionButton.setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                DocSort.entries.forEachIndexed { i, s -> menu.add(0, i, i, s.label).isCheckable = true }
                menu.setGroupCheckable(0, true, true)
                menu.findItem(sort.ordinal)?.isChecked = true
                setOnMenuItemClickListener { item ->
                    sort = DocSort.entries[item.itemId]
                    render()
                    true
                }
            }.show()
        }
    }

    override suspend fun fetch() {
        // The 자료실 lists everything except 기출, which has its own page.
        all = LibraryRepository.documents().filter { !it.isPastExam && !it.isPrivate }
    }

    override fun render() {
        val q = query.normalizedForSearch()
        val shown = all
            .filter { category.matches(it.category) }
            .filter { q.isEmpty() || it.searchHaystack().contains(q) }
            .sortedWith(sort.comparator)
        adapter.submit(shown)
        setTotal(shown.size, R.string.docs_total)
        binding.actionButton.text = sort.label
        renderCategoryTabs()
        showEmptyIfNeeded(shown.isEmpty())
    }

    /** .board-category-tabs with counts, e.g. "수업자료 (12)". */
    private fun renderCategoryTabs() {
        val group = binding.filterChips
        group.removeAllViews()
        group.isSingleSelection = true
        group.isSelectionRequired = true
        for (c in DocCategory.entries) {
            val count = all.count { c.matches(it.category) }
            val chip = (layoutInflater.inflate(R.layout.item_krds_filter_chip, group, false) as Chip).apply {
                id = View.generateViewId()
                text = getString(R.string.docs_category_count, c.label, count)
                isCheckable = true
                isChecked = c == category
                setOnClickListener {
                    if (category != c) {
                        category = c
                        render()
                    } else {
                        isChecked = true
                    }
                }
            }
            group.addView(chip)
        }
        binding.filterScroll.visibility = View.VISIBLE
    }

    private fun openDetail(doc: LibraryDocument) {
        val sheet = DetailSheet(this, getString(R.string.docs_detail_heading), doc.title)
            .pair(getString(R.string.docs_detail_category), doc.category.ifBlank { "기타" }, getString(R.string.docs_detail_collection), doc.collectionName)
            .pair(getString(R.string.docs_detail_date), displayDate(doc.modified), getString(R.string.exam_detail_size), formatBytes(doc.size))
            .row(getString(R.string.docs_detail_stats), getString(R.string.docs_stats, doc.viewCount, doc.downloadCount))
            .infoBox(getString(R.string.exam_detail_desc), listOf(doc.notes, doc.tags.joinToString(", ") { "#$it" }).filter { it.isNotBlank() }.joinToString("\n"))
        val download = DetailSheet.Action(getString(R.string.library_download), DetailSheet.Style.PRIMARY, R.drawable.ic_download) {
            GcflWebView.download(this, LibraryRepository.downloadUrl(doc.filename), fileName = doc.displayName.ifBlank { doc.filename })
        }
        if (doc.isPdf) {
            sheet.actions(
                DetailSheet.Action(getString(R.string.library_view), DetailSheet.Style.SECONDARY, R.drawable.ic_visibility) {
                    startActivity(PdfViewerActivity.intent(this, doc.filename, doc.title))
                },
                download,
            )
        } else {
            sheet.actions(download)
        }
        sheet.show()
    }

    private inner class DocAdapter : RecyclerView.Adapter<DocHolder>() {
        private var items: List<LibraryDocument> = emptyList()

        @Suppress("NotifyDataSetChanged") // client-side filter results replace the list
        fun submit(list: List<LibraryDocument>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            DocHolder(ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: DocHolder, position: Int) = holder.bind(items[position])
    }

    private inner class DocHolder(val b: ItemDocumentBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(doc: LibraryDocument) {
            setBadges(b.badges, listOf(doc.category.ifBlank { "기타" } to Badge.NEUTRAL, doc.collectionName to Badge.SUCCESS))
            b.icon.setImageResource(if (doc.isPdf) R.drawable.ic_pdf else R.drawable.ic_file)
            b.title.text = doc.title
            b.sub.text = listOf(displayDate(doc.modified), formatBytes(doc.size), getString(R.string.docs_views, doc.viewCount))
                .joinToString(" · ")
            b.root.setOnClickListener { openDetail(doc) }
        }
    }
}
