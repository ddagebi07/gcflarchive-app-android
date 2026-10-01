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
import kr.co.gcflarchive.app.databinding.ItemPhotoAlbumBinding
import kr.co.gcflarchive.app.web.GcflWebView

/** 사진 아카이브 — native version of photo.html (albums open in Google Photos etc.). */
class PhotosActivity : LibraryListActivity() {
    override val webPath = "/photo"
    override val pageTitleRes = R.string.photo_page_title
    override val pageDescRes = R.string.photo_page_desc
    override val searchHintRes = R.string.photo_search_hint

    private var all: List<PhotoAlbum> = emptyList()
    private val adapter = AlbumAdapter()

    override fun setUpList() {
        val columns = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        binding.list.layoutManager = GridLayoutManager(this, columns)
        binding.list.adapter = adapter
    }

    override suspend fun fetch() {
        // Requires login (API answers 401 → base class shows the login prompt).
        all = LibraryRepository.photoAlbums()
    }

    override fun render() {
        val q = query.normalizedForSearch()
        val shown = all.filter { q.isEmpty() || it.searchHaystack().contains(q) }
        adapter.submit(shown)
        setTotal(shown.size, R.string.photo_total)
        showEmptyIfNeeded(shown.isEmpty())
    }

    private fun openAlbum(album: PhotoAlbum) {
        if (album.albumUrl.isNotBlank()) GcflWebView.openExternal(this, Uri.parse(album.albumUrl))
    }

    private fun openDetail(album: PhotoAlbum) {
        DetailSheet(this, getString(R.string.photo_detail_heading), album.title)
            .pair(getString(R.string.photo_detail_date), displayDate(album.albumDate), getString(R.string.photo_detail_copyright), album.copyright.ifBlank { "GCFL Archive" })
            .row(getString(R.string.docs_detail_collection), album.collectionName.takeIf { it != album.title })
            .row(getString(R.string.photo_detail_performers), album.performers.joinToString(", "))
            .infoBox(getString(R.string.exam_detail_desc), album.notes)
            .actions(
                DetailSheet.Action(getString(R.string.copy_link), DetailSheet.Style.SECONDARY, R.drawable.ic_link) {
                    copy(album.albumUrl)
                },
                DetailSheet.Action(getString(R.string.photo_open_album), DetailSheet.Style.PRIMARY, R.drawable.ic_open_in_browser) {
                    openAlbum(album)
                },
            )
            .show()
    }

    private fun copy(url: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("link", url))
        Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    private inner class AlbumAdapter : RecyclerView.Adapter<AlbumHolder>() {
        private var items: List<PhotoAlbum> = emptyList()

        @Suppress("NotifyDataSetChanged") // client-side filter results replace the list
        fun submit(list: List<PhotoAlbum>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            AlbumHolder(ItemPhotoAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: AlbumHolder, position: Int) = holder.bind(items[position])
    }

    private inner class AlbumHolder(val b: ItemPhotoAlbumBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(album: PhotoAlbum) {
            b.title.text = album.title
            b.date.text = displayDate(album.albumDate)
            setBadges(b.badges, album.performers.take(3).map { it to Badge.NEUTRAL })
            ImageLoader.into(b.thumb, album.thumbnailUrl, lifecycleScope, targetWidth = 480)
            b.root.setOnClickListener { openDetail(album) }
        }
    }
}
