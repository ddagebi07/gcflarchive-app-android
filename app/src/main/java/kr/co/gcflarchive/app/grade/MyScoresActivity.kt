package kr.co.gcflarchive.app.grade

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ActivityMyScoresBinding
import kr.co.gcflarchive.app.databinding.ItemGradeScoreBinding
import kr.co.gcflarchive.app.util.Biometric

/** 내 점수 기록: 등급컷 계산기에 입력한 점수를 생체 인증 후에 보여 줘요. */
class MyScoresActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyScoresBinding
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Scores never show up in screenshots or the recents preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityMyScoresBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.swipe.setOnRefreshListener { if (unlocked) load() else binding.swipe.isRefreshing = false }
        unlocked = savedInstanceState?.getBoolean(KEY_UNLOCKED) == true
        if (unlocked) load() else authenticate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked)
    }

    private fun authenticate() {
        if (!Biometric.isAvailable(this)) {
            return showMessage(getString(R.string.my_scores_no_device_lock), null, null)
        }
        showMessage(getString(R.string.my_scores_locked), getString(R.string.my_scores_unlock)) { authenticate() }
        Biometric.authenticate(this, getString(R.string.my_scores_auth_title), null, onError = {
            showMessage(getString(R.string.my_scores_locked), getString(R.string.my_scores_unlock)) { authenticate() }
        }) {
            unlocked = true
            load()
        }
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        showMessage(null, null, null)
        lifecycleScope.launch {
            val result = runCatching {
                when (val access = GradeRepository.access()) {
                    is GradeAccess.Verified -> getString(R.string.my_scores_who_verified, access.userId) to GradeRepository.profile(null)
                    is GradeAccess.Guest -> getString(R.string.my_scores_who_guest, access.displayName) to GradeRepository.profile(null)
                    else -> null
                }
            }
            binding.swipe.isRefreshing = false
            binding.rows.removeAllViews()
            result.onSuccess { found ->
                if (found == null) {
                    binding.who.setText(R.string.my_scores_desc)
                    return@onSuccess showMessage(getString(R.string.my_scores_no_access), getString(R.string.my_scores_open_calculator)) { openCalculator() }
                }
                val (who, profile) = found
                binding.who.text = who
                if (profile.scores.isEmpty()) {
                    return@onSuccess showMessage(getString(R.string.my_scores_empty), getString(R.string.my_scores_open_calculator)) { openCalculator() }
                }
                for (s in profile.scores) {
                    val row = ItemGradeScoreBinding.inflate(layoutInflater, binding.rows, false)
                    GradeViews.bindScore(row, s)
                    binding.rows.addView(row.root)
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                showMessage(getString(R.string.network_error), getString(R.string.retry)) { load() }
            }
        }
    }

    private fun openCalculator() = startActivity(GradeActivity.intent(this))

    private fun showMessage(text: String?, button: String?, onClick: (() -> Unit)?) {
        binding.messageGroup.isVisible = text != null
        binding.message.text = text
        binding.btnMessage.isVisible = button != null
        binding.btnMessage.text = button
        binding.btnMessage.setOnClickListener { onClick?.invoke() }
    }

    companion object {
        private const val KEY_UNLOCKED = "unlocked"

        fun intent(context: Context) = Intent(context, MyScoresActivity::class.java)
    }
}
