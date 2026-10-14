package kr.co.gcflarchive.app.community

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ActivityCommunityWriteBinding

/** 글쓰기·글 고치기. 게시판은 드롭다운으로 고르고, 쓸 수 없는 표현은 서버가 알려 줘요. */
class CommunityWriteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCommunityWriteBinding
    private var boards: List<CommunityBoard> = emptyList()
    private var boardId: String? = null
    private var editId = 0
    private var submitting = false

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
        }
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
        binding.contentField.error = if (content.isEmpty()) getString(R.string.community_content_required) else null
        if (title.isEmpty() || content.isEmpty()) return
        val board = boardId
        if (editId == 0 && board == null) return snack(getString(R.string.community_pick_board))
        submitting = true
        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                if (editId > 0) {
                    CommunityRepository.edit(editId, title, content)
                    editId
                } else {
                    CommunityRepository.write(board!!, title, content, binding.anonymous.isChecked)
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
        val typed = !binding.titleInput.text.isNullOrBlank() || !binding.contentInput.text.isNullOrBlank()
        if (!typed || editId > 0 && !changed()) return finish()
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.community_leave_confirm)
            .setNegativeButton(R.string.community_keep_writing, null)
            .setPositiveButton(R.string.community_leave) { _, _ -> finish() }
            .show()
    }

    private fun changed() =
        binding.titleInput.text?.toString() != intent.getStringExtra(EXTRA_TITLE) ||
            binding.contentInput.text?.toString() != intent.getStringExtra(EXTRA_CONTENT)

    private fun snack(message: String) = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()

    companion object {
        const val RESULT_POST_ID = "post_id"
        private const val EXTRA_BOARD = "board"
        private const val EXTRA_EDIT_ID = "edit_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CONTENT = "content"

        fun newPost(context: Context, boardId: String) =
            Intent(context, CommunityWriteActivity::class.java).putExtra(EXTRA_BOARD, boardId)

        fun editPost(context: Context, post: CommunityPost) =
            Intent(context, CommunityWriteActivity::class.java)
                .putExtra(EXTRA_BOARD, post.boardId)
                .putExtra(EXTRA_EDIT_ID, post.summary.id)
                .putExtra(EXTRA_TITLE, post.summary.title)
                .putExtra(EXTRA_CONTENT, post.content)
    }
}
