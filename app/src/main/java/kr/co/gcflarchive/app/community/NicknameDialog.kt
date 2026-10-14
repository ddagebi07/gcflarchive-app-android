package kr.co.gcflarchive.app.community

import android.content.DialogInterface
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.DialogInputBinding
import kr.co.gcflarchive.app.map.MapLogic
import kr.co.gcflarchive.app.map.MapRepository

/** 커뮤니티 글쓴이 이름은 지도 후기와 같은 닉네임이에요. 없으면 여기서 정하고 [onDone]을 불러요. */
object NicknameDialog {
    fun show(activity: AppCompatActivity, onDone: () -> Unit) {
        val field = DialogInputBinding.inflate(activity.layoutInflater)
        field.inputLayout.hint = activity.getString(R.string.map_nickname_hint)
        field.input.inputType = InputType.TYPE_CLASS_TEXT
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.community_nickname_title)
            .setMessage(R.string.community_nickname_desc)
            .setView(field.root)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.community_nickname_submit, null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = field.input.text?.toString().orEmpty().trim()
            MapLogic.nicknameError(name)?.let {
                field.inputLayout.error = it
                return@setOnClickListener
            }
            activity.lifecycleScope.launch {
                runCatching { MapRepository.registerNickname(name) }
                    .onSuccess {
                        dialog.dismiss()
                        onDone()
                    }
                    .onFailure { e -> if (e is CancellationException) throw e else field.inputLayout.error = e.message }
            }
        }
    }
}
