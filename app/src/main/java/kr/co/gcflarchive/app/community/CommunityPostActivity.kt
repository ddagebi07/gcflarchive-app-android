package kr.co.gcflarchive.app.community

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.databinding.ActivityCommunityPostBinding
import kr.co.gcflarchive.app.databinding.ItemCommunityCommentBinding
import kr.co.gcflarchive.app.library.ImageLoader

/** 글 하나: 본문, 추천, 댓글, 신고. 내 글·댓글은 수정하거나 삭제할 수 있어요. */
class CommunityPostActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCommunityPostBinding
    private var postId = 0
    private var post: CommunityPost? = null
    private var sending = false
    /** The comment being edited in place (survives reloads until saved or cancelled). */
    private var editingCommentId = 0

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) load()
    }

    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityPostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener { finish() }
        postId = intent.getIntExtra(EXTRA_ID, 0)

        binding.swipe.setOnRefreshListener { load() }
        binding.btnRetry.setOnClickListener { load() }
        binding.btnLike.setOnClickListener { toggleLike() }
        binding.btnSend.setOnClickListener { sendComment() }
        load()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        binding.errorGroup.isVisible = false
        lifecycleScope.launch {
            val result = runCatching { CommunityRepository.post(postId) }
            binding.swipe.isRefreshing = false
            result.onSuccess(::render).onFailure { e ->
                if (e is CancellationException) throw e
                binding.body.isVisible = false
                binding.commentBar.isVisible = false
                binding.errorGroup.isVisible = true
                val needLogin = e is CommunityException && e.loginRequired
                binding.errorText.text = if (needLogin) getString(R.string.community_login_desc) else e.message ?: getString(R.string.network_error)
                binding.btnRetry.setText(if (needLogin) R.string.login_action else R.string.retry)
                binding.btnRetry.setOnClickListener { if (needLogin) login.launch(LoginActivity.intent(this@CommunityPostActivity)) else load() }
            }
        }
    }

    private fun render(p: CommunityPost) {
        post = p
        invalidateOptionsMenu()
        val s = p.summary
        binding.body.isVisible = true
        binding.title.text = s.title
        binding.meta.text = listOfNotNull(
            s.author.ifBlank { null },
            CommunityParser.displayTime(s.createdAt),
            if (p.edited) getString(R.string.community_edited) else null,
            getString(R.string.community_views, s.viewCount),
        ).joinToString(" · ")
        binding.content.text = p.content
        binding.content.isVisible = p.content.isNotBlank()
        renderPhotos(p.images)
        renderPoll(p)
        // Moderators still see a blinded post; tell them why others don't.
        binding.blindedNotice.isVisible = s.blinded && p.canEdit
        renderLike(p.liked, s.likeCount)
        binding.btnLike.isEnabled = !s.blinded
        binding.commentBar.isVisible = !s.blinded && p.canComment

        binding.commentHeader.text = getString(R.string.community_comment_header, p.comments.size)
        binding.noComments.isVisible = p.comments.isEmpty()
        binding.comments.removeAllViews()
        for (c in p.comments) {
            val row = ItemCommunityCommentBinding.inflate(layoutInflater, binding.comments, false)
            row.author.text = c.author
            row.content.text = c.content
            row.content.setTextColor(getColor(if (c.blinded) R.color.krds_text_secondary else R.color.krds_text_primary))
            row.meta.text = listOfNotNull(
                CommunityParser.displayTime(c.createdAt),
                if (c.edited) getString(R.string.community_edited) else null,
            ).joinToString(" · ")
            row.btnMore.setOnClickListener { v -> commentActions(v, c) }
            if (c.id == editingCommentId && c.isMine && !c.blinded) showEditor(row, c)
            binding.comments.addView(row.root)
        }
    }

    /** Photos at full width, sized from their known aspect ratio before they load. */
    private fun renderPhotos(images: List<CommunityImage>) {
        binding.photos.removeAllViews()
        binding.photos.isVisible = images.isNotEmpty()
        val width = (binding.photos.width.takeIf { it > 0 } ?: (resources.displayMetrics.widthPixels - dp(40)))
        images.forEachIndexed { i, img ->
            val view = android.widget.ImageView(this).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                contentDescription = getString(R.string.community_photo_desc, i + 1)
                setBackgroundColor(getColor(R.color.krds_surface_raised))
            }
            val height = if (img.width > 0 && img.height > 0) width * img.height / img.width else width * 3 / 4
            binding.photos.addView(view, android.widget.LinearLayout.LayoutParams(width, height).apply { topMargin = dp(12) })
            ImageLoader.into(view, img.url, lifecycleScope, targetWidth = width)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private var voting = false

    private fun renderPoll(p: CommunityPost) {
        val poll = p.poll
        binding.pollCard.isVisible = poll != null
        if (poll != null) PollCard.render(binding.pollCard, poll, voting) { choices -> vote(p, choices) }
    }

    private fun vote(p: CommunityPost, choices: Set<Int>) {
        if (voting) return
        voting = true
        lifecycleScope.launch {
            runCatching { CommunityRepository.vote(p.summary.id, choices) }
                .onSuccess { fresh ->
                    voting = false
                    val updated = p.copy(poll = fresh ?: p.poll)
                    post = updated
                    renderPoll(updated)
                }
                .onFailure { e ->
                    voting = false
                    handle(e)
                }
        }
    }

    /** 댓글 수정: the row turns into a text field with 취소/저장하기 (no popup). */
    private fun showEditor(row: ItemCommunityCommentBinding, c: CommunityComment) {
        row.content.isVisible = false
        row.meta.isVisible = false
        row.btnMore.isVisible = false
        row.editGroup.isVisible = true
        row.editInput.setText(c.content)
        row.editInput.setSelection(row.editInput.text?.length ?: 0)
        row.editInput.requestFocus()
        getSystemService(InputMethodManager::class.java)?.showSoftInput(row.editInput, InputMethodManager.SHOW_IMPLICIT)
        row.btnCancelEdit.setOnClickListener {
            editingCommentId = 0
            hideKeyboard()
            post?.let(::render)
        }
        row.btnSaveEdit.setOnClickListener {
            val text = row.editInput.text?.toString().orEmpty().trim()
            if (text.isEmpty()) {
                row.editField.error = getString(R.string.community_comment_required)
                return@setOnClickListener
            }
            if (text == c.content) {
                editingCommentId = 0
                post?.let(::render)
                return@setOnClickListener
            }
            row.btnSaveEdit.isEnabled = false
            lifecycleScope.launch {
                runCatching { CommunityRepository.editComment(c.id, text) }
                    .onSuccess {
                        editingCommentId = 0
                        hideKeyboard()
                        load()
                    }
                    .onFailure { e ->
                        row.btnSaveEdit.isEnabled = true
                        handle(e)
                    }
            }
        }
    }

    private fun renderLike(liked: Boolean, count: Int) {
        binding.btnLike.text = getString(R.string.community_like_count, count)
        binding.btnLike.setIconResource(if (liked) R.drawable.ic_thumb_up else R.drawable.ic_thumb_up_outline)
        binding.btnLike.contentDescription = getString(if (liked) R.string.community_unlike_desc else R.string.community_like_desc, count)
    }

    private fun toggleLike() {
        binding.btnLike.isEnabled = false
        lifecycleScope.launch {
            runCatching { CommunityRepository.toggleLike(postId) }
                .onSuccess { (liked, count) -> renderLike(liked, count) }
                .onFailure { handle(it) }
            binding.btnLike.isEnabled = true
        }
    }

    private fun sendComment() {
        val text = binding.commentInput.text?.toString().orEmpty().trim()
        if (text.isEmpty() || sending) return
        sending = true
        binding.btnSend.isEnabled = false
        lifecycleScope.launch {
            runCatching { CommunityRepository.comment(postId, text) }
                .onSuccess {
                    binding.commentInput.setText("")
                    hideKeyboard()
                    load()
                }
                .onFailure { e -> handle(e) { sendComment() } }
            sending = false
            binding.btnSend.isEnabled = true
        }
    }

    private fun commentActions(anchor: View, c: CommunityComment) {
        // canEdit on someone else's post means the viewer moderates the community.
        val moderator = post?.let { it.canEdit && !it.summary.isMine } == true
        PopupMenu(this, anchor).apply {
            if (c.isMine && !c.blinded) menu.add(0, 1, 0, R.string.community_edit)
            if (c.isMine || moderator) menu.add(0, 2, 1, R.string.community_delete)
            if (!c.isMine) menu.add(0, 3, 2, R.string.community_report)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> editComment(c)
                    2 -> confirm(R.string.community_delete_comment_confirm) { change { CommunityRepository.deleteComment(c.id) } }
                    3 -> report("comment", c.id)
                }
                true
            }
        }.show()
    }

    private fun editComment(c: CommunityComment) {
        editingCommentId = c.id
        post?.let(::render)
    }

    private fun report(type: String, id: Int) {
        val reasons = resources.getStringArray(R.array.community_report_reasons)
        var picked = 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.community_report_title)
            .setSingleChoiceItems(reasons, 0) { _, which -> picked = which }
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.community_report) { _, _ ->
                lifecycleScope.launch {
                    runCatching { CommunityRepository.report(type, id, reasons[picked]) }
                        .onSuccess { blinded ->
                            snack(getString(if (blinded) R.string.community_reported_blinded else R.string.community_reported))
                            if (blinded) load()
                        }
                        .onFailure { handle(it) }
                }
            }
            .show()
    }

    private fun confirm(messageRes: Int, action: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setMessage(messageRes)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.community_delete) { _, _ -> action() }
            .show()
    }

    /** Runs a change, then reloads; failures show the server's message. */
    private fun change(block: suspend () -> Unit) {
        lifecycleScope.launch {
            runCatching { block() }.onSuccess { load() }.onFailure { handle(it) }
        }
    }

    private fun handle(e: Throwable, retry: (() -> Unit)? = null) {
        if (e is CancellationException) throw e
        when {
            e is CommunityException && e.nicknameRequired -> NicknameDialog.show(this) {
                snack(getString(R.string.community_nickname_set))
                retry?.invoke()
            }
            e is CommunityException && e.loginRequired -> login.launch(LoginActivity.intent(this))
            else -> snack(e.message ?: getString(R.string.network_error))
        }
    }

    private fun snack(message: String) = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(binding.commentInput.windowToken, 0)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.community_post, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val p = post
        val mine = p?.summary?.isMine == true
        menu.findItem(R.id.action_edit)?.isVisible = p?.canEdit == true && !p.summary.blinded
        menu.findItem(R.id.action_delete)?.isVisible = p?.canEdit == true
        menu.findItem(R.id.action_report)?.isVisible = p != null && !mine
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val p = post ?: return super.onOptionsItemSelected(item)
        when (item.itemId) {
            R.id.action_edit -> editor.launch(CommunityWriteActivity.editPost(this, p))
            R.id.action_delete -> confirm(R.string.community_delete_post_confirm) {
                lifecycleScope.launch {
                    runCatching { CommunityRepository.delete(p.summary.id) }
                        .onSuccess { finish() }
                        .onFailure { handle(it) }
                }
            }
            R.id.action_report -> report("post", p.summary.id)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        private const val EXTRA_ID = "post_id"

        fun intent(context: Context, postId: Int) =
            Intent(context, CommunityPostActivity::class.java).putExtra(EXTRA_ID, postId)
    }
}
