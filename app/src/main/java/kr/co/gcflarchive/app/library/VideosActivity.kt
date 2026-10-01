package kr.co.gcflarchive.app.library

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ItemVideoBinding
import kr.co.gcflarchive.app.web.GcflWebView

/** 영상 아카이브 — native version of video.html; videos play in the YouTube app. */
class VideosActivity : LibraryListActivity() {
    override val pageTitleRes = R.string.video_page_title
    override val pageDescRes = R.string.video_page_desc
    override val searchHintRes = R.string.video_search_hint

    private var all: List<VideoEntry> = emptyList()
    private val adapter = VideoAdapter()

    override fun setUpList() {
        val columns = if (resources.configuration.screenWidthDp >= 600) 2 else 1
        binding.list.layoutManager = GridLayoutManager(this, columns)
        binding.list.adapter = adapter
    }

    override suspend fun fetch() {
        all = LibraryRepository.videos()
    }

    override fun render() {
        val q = query.normalizedForSearch()
        val shown = all.filter { q.isEmpty() || it.searchHaystack().contains(q) }
        adapter.submit(shown)
        setTotal(shown.size, R.string.video_total)
        showEmptyIfNeeded(shown.isEmpty())
    }

    private fun play(video: VideoEntry) = GcflWebView.openExternal(this, Uri.parse(video.youtubeUrl))

    private fun openDetail(video: VideoEntry) {
        DetailSheet(this, getString(R.string.video_detail_heading), video.title)
            .pair(getString(R.string.docs_detail_category), video.category, getString(R.string.video_detail_date), displayDate(video.timestamp))
            .row(getString(R.string.video_detail_tags), video.tags.joinToString(", ") { "#$it" })
            .infoBox(getString(R.string.exam_detail_desc), video.notes.ifBlank { getString(R.string.video_no_notes) })
            .actions(
                DetailSheet.Action(getString(R.string.copy_link), DetailSheet.Style.SECONDARY, R.drawable.ic_link) {
                    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("link", video.youtubeUrl))
                    Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
                },
                DetailSheet.Action(getString(R.string.video_watch), DetailSheet.Style.PRIMARY, R.drawable.ic_play) { play(video) },
            )
            .show()
    }

    private inner class VideoAdapter : RecyclerView.Adapter<VideoHolder>() {
        private var items: List<VideoEntry> = emptyList()

        @Suppress("NotifyDataSetChanged") // client-side filter results replace the list
        fun submit(list: List<VideoEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VideoHolder(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VideoHolder, position: Int) = holder.bind(items[position])
    }

    private inner class VideoHolder(val b: ItemVideoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(video: VideoEntry) {
            b.title.text = video.title
            setBadges(b.badges, listOf(video.category to Badge.NEUTRAL) + video.tags.take(3).map { "#$it" to Badge.INFO })
            b.notes.text = video.notes
            b.notes.visibility = if (video.notes.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            ImageLoader.into(b.thumb, video.thumbnailUrl, lifecycleScope, targetWidth = 640)
            b.root.setOnClickListener { play(video) }
            b.info.setOnClickListener { openDetail(video) }
        }
    }
}
