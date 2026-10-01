package kr.co.gcflarchive.admin.ui.kit

import android.content.Context
import android.text.InputType
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.ApiError
import kr.co.gcflarchive.admin.core.AuthException
import kr.co.gcflarchive.admin.core.OfflineException

object Dialogs {
    fun confirm(context: Context, title: String, message: String, positive: String, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(positive) { _, _ -> onYes() }
            .show()
    }

    /**
     * 민감 작업 이중 확인: a first dialog explains the impact, then the operator must type
     * [keyword] (e.g. the hakbun or "삭제") before the action runs.
     */
    fun confirmSensitive(context: Context, title: String, message: String, keyword: String, onConfirmed: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.next) { _, _ -> typeToConfirm(context, title, keyword, onConfirmed) }
            .show()
    }

    private fun typeToConfirm(context: Context, title: String, keyword: String, onConfirmed: () -> Unit) {
        val layout = TextInputLayout(context).apply {
            hint = context.getString(R.string.confirm_type_hint, keyword)
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val input = TextInputEditText(layout.context).apply { inputType = InputType.TYPE_CLASS_TEXT }
        layout.addView(input)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(context.getString(R.string.confirm_type_message, keyword))
            .setView(FrameLayout(context).apply { addView(layout) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm_do) { _, _ -> onConfirmed() }
            .show()
        val ok = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        ok.isEnabled = false
        input.doAfterTextChanged { ok.isEnabled = it?.toString()?.trim() == keyword }
    }

    fun messageOf(context: Context, e: Throwable): String = when (e) {
        is OfflineException -> context.getString(R.string.error_offline_write)
        is AuthException -> context.getString(R.string.error_auth, e.message.orEmpty())
        is ApiError -> e.message ?: context.getString(R.string.error_generic)
        else -> e.message ?: context.getString(R.string.error_generic)
    }

    fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
