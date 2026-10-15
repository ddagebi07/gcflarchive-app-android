package kr.co.gcflarchive.app.community

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ActivityCommunityWriteBinding
import kr.co.gcflarchive.app.library.ImageLoader

/** 글쓰기·글 고치기. 게시판은 드롭다운으로 고르고, 사진은 10장까지 붙여요. 쓸 수 없는 표현은 서버가 알려 줘요. */
class CommunityWriteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCommunityWriteBinding
    private var boards: List<CommunityBoard> = emptyList()
    private var boardId: String? = null
    private var editId = 0
    private var submitting = false
    private val images = ArrayList<CommunityImage>()
    private var uploading = 0

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(CommunityPhotos.MAX_PER_POST)) { uris ->
        val room = CommunityPhotos.MAX_PER_POST - images.size
        if (uris.isEmpty() || room <= 0) return@registerForActivityResult
        if (uris.size > room) snack(getString(R.string.community_photo_full, CommunityPhotos.MAX_PER_POST))
        uploadPhotos(uris.take(room))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityWriteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        editId = intent.getIntExtra(EXTRA_EDIT_ID, 0)
        boardId = intent.getStringExtra(EXTRA_BOARD)
        supportActionBar?.setTitle(if (editId > 0) R.string.community_edit_title else R.string.community_write_title)
        binding.btnSubmit.setText(if (editId > 0) R.string.community_save else R.string.community_submit)
        if (editId > 0 && savedInstanceState == null) {
            binding.titleInput.setText(intent.getStringExtra(EXTRA_TITLE))
            binding.contentInput.setText(intent.getStringExtra(EXTRA_CONTENT))
            images += imagesExtra(intent.extras)
        }
        savedInstanceState?.let { images += imagesExtra(it) }
        binding.btnAddPhoto.setOnClickListener {
            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        renderPhotos()
        // A post stays on its board when edited.
        binding.boardField.isVisible = editId == 0
        binding.boardPicker.setOnItemClickListener { _, _, position, _ ->
            boardId = boards[position].id
            renderBoard()
        }
        binding.btnSubmit.setOnClickListener { submit() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmLeave()
        })
        loadBoards()
        loadMe()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(EXTRA_IMAGES, ArrayList(images))
    }

    private fun uploadPhotos(uris: List<android.net.Uri>) {
        lifecycleScope.launch {
            uris.forEachIndexed { i, uri ->
                uploading++
                renderPhotos()
                runCatching {
                    val photo = withContext(Dispatchers.IO) { CommunityPhotos.prepare(this@CommunityWriteActivity, uri, images.size + i + 1) }
                        ?: throw IllegalStateException(getString(R.string.community_photo_read_failed))
                    CommunityRepository.uploadImage(photo)
                }.onSuccess { images += it }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        snack(e.message ?: getString(R.string.network_error))
                    }
                uploading--
                renderPhotos()
            }
        }
    }

    /** Thumbnails with an ✕ to take a photo out; the add button shows how many are left. */
    private fun renderPhotos() {
        val max = CommunityPhotos.MAX_PER_POST
        binding.photoCount.text = getString(R.string.community_photo_label, images.size, max)
        binding.photoList.removeAllViews()
        val size = dp(96)
        for (img in images) {
            val frame = FrameLayout(this)
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(getColor(R.color.krds_surface_raised))
                contentDescription = getString(R.string.community_photo_desc, images.indexOf(img) + 1)
            }
            frame.addView(thumb, FrameLayout.LayoutParams(size, size))
            ImageLoader.into(thumb, img.url, lifecycleScope, targetWidth = size)
            val remove = ImageButton(this).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(android.graphics.Color.WHITE)
                setBackgroundColor(0x8C000000.toInt())
                contentDescription = getString(R.string.community_photo_remove)
                setOnClickListener {
                    images.remove(img)
                    renderPhotos()
                }
            }
            frame.addView(remove, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END))
            binding.photoList.addView(frame, android.widget.LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(8) })
        }
        binding.photoScroll.isVisible = images.isNotEmpty()
        binding.btnAddPhoto.isEnabled = images.size < max && uploading == 0
        binding.btnAddPhoto.text = when {
            uploading > 0 -> getString(R.string.community_photo_uploading)
            images.size >= max -> getString(R.string.community_photo_full, max)
            else -> getString(R.string.community_add_photo)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadBoards() {
        if (editId > 0) return
        lifecycleScope.launch {
            runCatching { CommunityRepository.boards() }.onSuccess { list ->
                boards = list.filter { it.canWrite }
                binding.boardPicker.setAdapter(ArrayAdapter(this@CommunityWriteActivity, R.layout.item_dropdown, boards.map { it.name }))
                if (boards.none { it.id == boardId }) boardId = boards.firstOrNull()?.id
                renderBoard()
            }
        }
    }

    private fun loadMe() {
        lifecycleScope.launch {
            val me = runCatching { CommunityRepository.me() }.getOrNull() ?: return@launch
            binding.author.text = if (me.nickname.isBlank()) getString(R.string.community_author_none)
            else getString(R.string.community_author, me.nickname)
        }
    }

    private fun renderBoard() {
        val board = boards.firstOrNull { it.id == boardId } ?: return
        binding.boardPicker.setText(board.name, false)
        // 익명 글쓰기는 운영 설정과 게시판 설정이 모두 켜졌을 때만 보여요.
        binding.anonymous.isVisible = board.allowAnonymous
        if (!board.allowAnonymous) binding.anonymous.isChecked = false
    }

    private fun submit() {
        if (submitting) return
        val title = binding.titleInput.text?.toString().orEmpty().trim()
        val content = binding.contentInput.text?.toString().orEmpty().trim()
        binding.titleField.error = if (title.isEmpty()) getString(R.string.community_title_required) else null
        val noBody = content.isEmpty() && images.isEmpty()
        binding.contentField.error = if (noBody) getString(R.string.community_content_or_photo) else null
        if (title.isEmpty() || noBody) return
        if (uploading > 0) return snack(getString(R.string.community_photo_wait))
        val board = boardId
        if (editId == 0 && board == null) return snack(getString(R.string.community_pick_board))
        submitting = true
        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                if (editId > 0) {
                    CommunityRepository.edit(editId, title, content, images.map { it.id })
                    editId
                } else {
                    CommunityRepository.write(board!!, title, content, binding.anonymous.isChecked, images.map { it.id })
                }
            }.onSuccess { id ->
                setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_POST_ID, if (editId > 0) 0 else id))
                finish()
            }.onFailure { e ->
                if (e is CancellationException) throw e
                if (e is CommunityException && e.nicknameRequired) {
                    NicknameDialog.show(this@CommunityWriteActivity) {
                        loadMe()
                        submit()
                    }
                } else {
                    snack(e.message ?: getString(R.string.network_error))
                }
            }
            submitting = false
            binding.btnSubmit.isEnabled = true
        }
    }

    private fun confirmLeave() {
        val typed = !binding.titleInput.text.isNullOrBlank() || !binding.contentInput.text.isNullOrBlank() || images.isNotEmpty()
        if (!typed || editId > 0 && !changed()) return finish()
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.community_leave_confirm)
            .setNegativeButton(R.string.community_keep_writing, null)
            .setPositiveButton(R.string.community_leave) { _, _ -> finish() }
            .show()
    }

    private fun changed() =
        binding.titleInput.text?.toString() != intent.getStringExtra(EXTRA_TITLE) ||
            binding.contentInput.text?.toString() != intent.getStringExtra(EXTRA_CONTENT) ||
            images.map { it.id } != imagesExtra(intent.extras).map { it.id }

    private fun snack(message: String) = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()

    companion object {
        const val RESULT_POST_ID = "post_id"
        private const val EXTRA_BOARD = "board"
        private const val EXTRA_EDIT_ID = "edit_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CONTENT = "content"
        private const val EXTRA_IMAGES = "images"

        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        private fun imagesExtra(bundle: Bundle?): List<CommunityImage> =
            (bundle?.getSerializable(EXTRA_IMAGES) as? ArrayList<CommunityImage>).orEmpty()

        fun newPost(context: Context, boardId: String) =
            Intent(context, CommunityWriteActivity::class.java).putExtra(EXTRA_BOARD, boardId)

        fun editPost(context: Context, post: CommunityPost) =
            Intent(context, CommunityWriteActivity::class.java)
                .putExtra(EXTRA_BOARD, post.boardId)
                .putExtra(EXTRA_EDIT_ID, post.summary.id)
                .putExtra(EXTRA_TITLE, post.summary.title)
                .putExtra(EXTRA_CONTENT, post.content)
                .putExtra(EXTRA_IMAGES, ArrayList(post.images))
    }
}
