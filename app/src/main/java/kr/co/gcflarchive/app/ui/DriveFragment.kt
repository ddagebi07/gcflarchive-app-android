package kr.co.gcflarchive.app.ui

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.data.LoginRequiredException
import kr.co.gcflarchive.app.data.LoginState
import kr.co.gcflarchive.app.data.SiteSession
import kr.co.gcflarchive.app.databinding.DialogInputBinding
import kr.co.gcflarchive.app.databinding.FragmentDriveBinding
import kr.co.gcflarchive.app.databinding.ItemDriveFileBinding
import kr.co.gcflarchive.app.meal.MealRepository
import kr.co.gcflarchive.app.share.DriveFile
import kr.co.gcflarchive.app.share.DriveFileStatus
import kr.co.gcflarchive.app.share.DriveListing
import kr.co.gcflarchive.app.share.DriveLogic
import kr.co.gcflarchive.app.share.DriveRepository
import kr.co.gcflarchive.app.share.ShareReceiverActivity
import kr.co.gcflarchive.app.util.Links
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 극플드라이브 tab: storage, PIN / drive address, upload and the stored files (share.html). */
class DriveFragment : Fragment(), MainActivity.Reselectable {
    private var _binding: FragmentDriveBinding? = null
    private val binding get() = _binding!!

    private var userId: String? = null
    private var listing: DriveListing? = null
    private var loadJob: Job? = null

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) startActivity(ShareReceiverActivity.intent(requireContext(), uris))
    }

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { load() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDriveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.swipe.setOnRefreshListener { load() }
        binding.btnLogin.setOnClickListener { login.launch(LoginActivity.intent(requireContext())) }
        binding.dropzone.setOnClickListener { pickFiles.launch(arrayOf("*/*")) }
        binding.btnCopyUrl.setOnClickListener {
            val id = userId ?: return@setOnClickListener
            val pin = listing?.pin ?: return@setOnClickListener
            Links.copy(requireContext(), getString(R.string.drive_title), DriveLogic.driveUrl(id, pin))
        }
        binding.btnChangePin.setOnClickListener { changePin() }
    }

    override fun onResume() {
        super.onResume()
        // Also covers coming back from an upload (share sheet or picker).
        if (!isHidden) load()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden && _binding != null) load()
    }

    override fun onReselected() {
        binding.scroll.smoothScrollTo(0, 0)
        load()
    }

    private fun load() {
        loadJob?.cancel()
        binding.swipe.isRefreshing = true
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val state = SiteSession.state()
            val result = if (state is LoginState.LoggedOut) Result.failure<DriveListing>(LoginRequiredException()) else runCatching { DriveRepository.list() }
            val b = _binding ?: return@launch
            b.swipe.isRefreshing = false
            userId = (state as? LoginState.LoggedIn)?.userId ?: userId
            result.onSuccess(::render).onFailure { e ->
                if (e is CancellationException) throw e
                val needLogin = e is LoginRequiredException
                b.loginGroup.isVisible = needLogin
                b.contentGroup.isVisible = false
                b.errorText.isVisible = !needLogin
                b.errorText.text = e.message ?: getString(R.string.drive_load_failed)
            }
        }
    }

    private fun render(data: DriveListing) {
        listing = data
        val b = binding
        b.loginGroup.isVisible = false
        b.errorText.isVisible = false
        b.contentGroup.isVisible = true

        val usage = DriveLogic.usage(data.files)
        b.countBadge.text = getString(R.string.drive_count, usage.count)
        b.countBadge.setBackgroundResource(if (usage.isFull) R.drawable.bg_krds_badge_danger else R.drawable.bg_krds_badge_info)
        b.countBadge.setTextColor(requireContext().getColor(if (usage.isFull) R.color.krds_danger else R.color.krds_badge_info_fg))
        b.usageText.text = getString(R.string.drive_usage, String.format(Locale.ROOT, "%.2f", usage.bytes / 1048576.0), usage.percent)
        b.usageBar.setProgressCompat(usage.percent, true)
        b.usageBar.setIndicatorColor(
            requireContext().getColor(
                when {
                    usage.percent >= 90 -> R.color.krds_danger
                    usage.percent >= 70 -> R.color.krds_star
                    else -> R.color.krds_primary
                },
            ),
        )

        b.pinText.text = getString(R.string.drive_pin, data.pin)
        b.driveUrl.text = userId?.let { DriveLogic.driveUrl(it, data.pin) } ?: ""

        b.fileList.removeAllViews()
        b.emptyBox.isVisible = data.files.isEmpty()
        for (file in data.files) b.fileList.addView(fileRow(file))
    }

    private fun fileRow(file: DriveFile): View {
        val row = ItemDriveFileBinding.inflate(layoutInflater, binding.fileList, false)
        val ctx = requireContext()
        row.name.text = file.filename
        val uploaded = DriveLogic.parse(file.uploadedAt)?.atZone(MealRepository.SEOUL)?.format(TIME_FORMAT) ?: "-"
        row.meta.text = getString(R.string.drive_meta, Formatter.formatShortFileSize(ctx, file.fileSize), uploaded)

        when (val status = DriveLogic.status(file)) {
            is DriveFileStatus.Downloaded -> {
                row.status.setText(R.string.drive_status_downloaded)
                row.status.setBackgroundResource(R.drawable.bg_krds_badge_danger)
                row.status.setTextColor(ctx.getColor(R.color.krds_danger))
                row.help.text = getString(R.string.drive_minutes_left, status.minutesLeft)
                row.help.setTextColor(ctx.getColor(R.color.krds_danger))
            }
            is DriveFileStatus.Stored -> {
                row.status.setText(if (file.quotaExempt) R.string.drive_status_exam else R.string.drive_status_stored)
                row.status.setBackgroundResource(if (file.quotaExempt) R.drawable.bg_krds_badge_purple else R.drawable.bg_krds_badge_success)
                row.status.setTextColor(ctx.getColor(if (file.quotaExempt) R.color.krds_badge_purple_fg else R.color.krds_badge_success_fg))
                row.help.text = getString(R.string.drive_days_left, status.daysLeft)
            }
        }

        row.btnDownload.setOnClickListener { confirmDownload(file) }
        row.btnShare.setOnClickListener { Links.share(ctx, "${file.filename}\n${file.shortUrl}") }
        row.btnDelete.setOnClickListener { confirmDelete(file) }
        return row.root
    }

    /** Downloading starts the 5-minute deletion timer, so make that explicit. */
    private fun confirmDownload(file: DriveFile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.drive_download_confirm_title)
            .setMessage(R.string.drive_download_confirm_desc)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.drive_download) { _, _ ->
                Links.download(requireContext(), file.shortUrl, fileName = file.filename)
            }
            .show()
    }

    private fun confirmDelete(file: DriveFile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.drive_delete_title)
            .setMessage(R.string.drive_delete_desc)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.drive_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = runCatching { DriveRepository.delete(file.fileId) }
                    val ctx = context ?: return@launch
                    result.onSuccess {
                        Toast.makeText(ctx, R.string.drive_deleted, Toast.LENGTH_SHORT).show()
                        load()
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        Toast.makeText(ctx, e.message ?: getString(R.string.drive_load_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun changePin() {
        val current = listing?.pin ?: return
        val field = DialogInputBinding.inflate(layoutInflater)
        field.inputLayout.hint = getString(R.string.drive_pin_hint)
        field.input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        field.input.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(4))
        field.input.setText(current)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.drive_pin_dialog_title)
            .setMessage(R.string.drive_pin_dialog_desc)
            .setView(field.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.drive_change, null)
            .show()
        // Validate without closing the dialog on bad input.
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val pin = field.input.text?.toString().orEmpty()
            if (!DriveLogic.isValidPin(pin)) {
                field.inputLayout.error = getString(R.string.drive_pin_invalid)
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val result = runCatching { DriveRepository.setPin(pin) }
                result.onSuccess {
                    dialog.dismiss()
                    context?.let { Toast.makeText(it, R.string.drive_pin_changed, Toast.LENGTH_SHORT).show() }
                    load()
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    field.inputLayout.error = e.message ?: getString(R.string.drive_pin_invalid)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")
    }
}
