package kr.co.gcflarchive.app.archive

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.text.inSpans
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ItemSearchResultBinding

class SearchAdapter(private val onClick: (PostSummary) -> Unit) : RecyclerView.Adapter<SearchAdapter.Holder>() {
    private val items = mutableListOf<PostSummary>()

    @Suppress("NotifyDataSetChanged") // a new query replaces the whole result set
    fun submit(newItems: List<PostSummary>, append: Boolean) {
        if (append) {
            val start = items.size
            items += newItems
            notifyItemRangeInserted(start, newItems.size)
        } else {
            items.clear()
            items += newItems
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val b: ItemSearchResultBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(post: PostSummary) {
            val ctx = b.root.context
            b.board.text = post.boardName.ifBlank { post.boardCode }
            b.date.text = ArchiveRepository.displayDate(post.postedAt)
            b.title.text = post.title
            b.author.text = post.author
            b.attachments.isVisible = post.attachmentCount > 0
            b.attachments.text = ctx.getString(R.string.search_attachment_count, post.attachmentCount)

            val runs = ArchiveRepository.snippetRuns(post.snippet)
            b.snippet.isVisible = runs.isNotEmpty()
            val highlight = ContextCompat.getColor(ctx, R.color.search_highlight)
            b.snippet.text = SpannableStringBuilder().apply {
                for ((text, marked) in runs) {
                    if (marked) inSpans(BackgroundColorSpan(highlight), StyleSpan(Typeface.BOLD)) { append(text) }
                    else append(text)
                }
            }
            b.root.setOnClickListener { onClick(post) }
        }
    }
}
