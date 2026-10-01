package kr.co.gcflarchive.admin.ui.kit

import android.graphics.Rect
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.databinding.SheetFormBinding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Values collected from a [FormSheet]. */
class FormValues internal constructor(private val map: Map<String, Any?>) {
    fun text(key: String): String = (map[key] as? String).orEmpty().trim()
    fun int(key: String): Int = map[key] as? Int ?: 0
    fun bool(key: String): Boolean = map[key] as? Boolean ?: false
    @Suppress("UNCHECKED_CAST")
    fun list(key: String): List<String> = map[key] as? List<String> ?: emptyList()
    /** ISO-8601 instant ("…Z") or "" for date-time fields. */
    fun instant(key: String): String = (map[key] as? Instant)?.toString() ?: ""
}

/**
 * Bottom-sheet form builder used by every create/edit screen. [onSubmit] runs in a
 * coroutine; returning normally closes the sheet, throwing shows the error inline.
 *
 * The fields scroll in a height-capped area above a pinned 취소/저장 bar, and the cap
 * follows the keyboard, so the buttons can never be pushed off screen or covered.
 */
class FormSheet(private val activity: FragmentActivity, title: String, description: String? = null) {
    private val dialog = BottomSheetDialog(activity)
    private val b = SheetFormBinding.inflate(LayoutInflater.from(activity))
    private val readers = mutableMapOf<String, () -> Any?>()
    /** Field view + check; the view is scrolled to when its check fails. */
    private val validators = mutableListOf<Pair<View, () -> String?>>()
    private var onClose: (() -> Unit)? = null
    private val frame = Rect()
    private val capper = ViewTreeObserver.OnGlobalLayoutListener { capScroll() }

    init {
        b.formTitle.text = title
        b.formDesc.isVisible = description != null
        b.formDesc.text = description
        dialog.setContentView(b.root)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        b.formCancel.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            b.root.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(capper)
            onClose?.invoke()
        }
    }

    /**
     * Limits the field area to what is left of the visible window (minus the keyboard,
     * title and button bar), keeping a strip of the page visible above the sheet.
     */
    private fun capScroll() {
        if (!b.root.isAttachedToWindow) return
        b.root.getWindowVisibleDisplayFrame(frame)
        val chrome = b.root.height - b.formScroll.height
        b.formScroll.maxScrollHeight = (frame.height() - chrome - dp(40)).coerceAtLeast(dp(96))
    }

    private fun label(text: String) {
        b.formFields.addView(TextView(activity).apply {
            this.text = text
            setTextAppearance(R.style.TextAppearance_Krds_DetailLabel)
        }, lp(top = 14))
    }

    fun text(
        key: String,
        label: String,
        initial: String = "",
        hint: String? = null,
        multiline: Boolean = false,
        number: Boolean = false,
        password: Boolean = false,
        required: Boolean = false,
        enabled: Boolean = true,
        validate: ((String) -> String?)? = null,
    ): FormSheet {
        val layout = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            this.hint = label
            helperText = hint
            if (password) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val edit = TextInputEditText(layout.context).apply {
            setText(initial)
            isEnabled = enabled
            inputType = when {
                number -> InputType.TYPE_CLASS_NUMBER
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (multiline) { minLines = 3; maxLines = 10 }
        }
        layout.addView(edit)
        b.formFields.addView(layout, lp(top = 12))
        readers[key] = { edit.text?.toString().orEmpty() }
        validators += layout to {
            val v = edit.text?.toString().orEmpty().trim()
            val err = when {
                required && v.isEmpty() -> activity.getString(R.string.form_required, label)
                else -> validate?.invoke(v)
            }
            layout.error = err
            err
        }
        return this
    }

    /** −/+ stepper (기준 횟수, 기준 시간 등). */
    fun stepper(key: String, label: String, initial: Int, min: Int, max: Int, step: Int = 1): FormSheet {
        label(label)
        var value = initial.coerceIn(min, max)
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val valueView = TextView(activity).apply {
            setTextAppearance(R.style.TextAppearance_Admin_StatValue)
            gravity = android.view.Gravity.CENTER
            minWidth = dp(72)
        }
        fun render() { valueView.text = value.toString() }
        val minus = iconButton(R.drawable.ic_remove) { value = (value - step).coerceAtLeast(min); render() }
        val plus = iconButton(R.drawable.ic_add) { value = (value + step).coerceAtMost(max); render() }
        row.addView(minus); row.addView(valueView); row.addView(plus)
        render()
        b.formFields.addView(row, lp(top = 4))
        readers[key] = { value }
        return this
    }

    fun switch(key: String, label: String, initial: Boolean, description: String? = null): FormSheet {
        val sw = MaterialSwitch(activity).apply {
            text = label
            isChecked = initial
            setTextAppearance(R.style.TextAppearance_Krds_DetailValue)
        }
        b.formFields.addView(sw, lp(top = 8))
        description?.let {
            b.formFields.addView(TextView(activity).apply { text = it; setTextAppearance(R.style.TextAppearance_Krds_Meta) }, lp(top = 0))
        }
        readers[key] = { sw.isChecked }
        return this
    }

    /** Multiple choice (e.g. 권한 체크리스트). */
    fun checklist(key: String, label: String, options: List<Pair<String, String>>, selected: Set<String>): FormSheet {
        label(label)
        val boxes = options.map { (value, text) ->
            MaterialCheckBox(activity).apply {
                this.text = text
                isChecked = value in selected
                tag = value
            }.also { b.formFields.addView(it, lp(top = 0)) }
        }
        readers[key] = { boxes.filter { it.isChecked }.map { it.tag as String } }
        return this
    }

    /** Editable chip list (금지어, 공개 역할, 출연자 등). */
    fun chips(key: String, label: String, initial: List<String>, addHint: String): FormSheet {
        label(label)
        val group = ChipGroup(activity)
        val values = initial.toMutableList()
        fun addChip(v: String) {
            group.addView(Chip(activity).apply {
                text = v
                isCloseIconVisible = true
                setOnCloseIconClickListener { values.remove(v); group.removeView(this) }
            })
        }
        initial.forEach(::addChip)
        b.formFields.addView(group, lp(top = 4))
        val inputLayout = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = addHint
            endIconMode = TextInputLayout.END_ICON_CUSTOM
            setEndIconDrawable(R.drawable.ic_add)
        }
        val input = TextInputEditText(inputLayout.context).apply { inputType = InputType.TYPE_CLASS_TEXT; maxLines = 1 }
        inputLayout.addView(input)
        fun commit() {
            input.text?.toString().orEmpty().split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() && it !in values }.forEach {
                values += it
                addChip(it)
            }
            input.setText("")
        }
        inputLayout.setEndIconOnClickListener { commit() }
        input.setOnEditorActionListener { _, _, _ -> commit(); true }
        b.formFields.addView(inputLayout, lp(top = 4))
        readers[key] = { commit(); values.toList() }
        return this
    }

    /** Dropdown of fixed options (value to label). */
    fun select(key: String, label: String, options: List<Pair<String, String>>, initial: String): FormSheet {
        val layout = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply { hint = label }
        val view = AutoCompleteTextView(layout.context).apply {
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(activity, android.R.layout.simple_list_item_1, options.map { it.second }))
            setText(options.firstOrNull { it.first == initial }?.second ?: options.firstOrNull()?.second.orEmpty(), false)
        }
        layout.addView(view)
        b.formFields.addView(layout, lp(top = 12))
        readers[key] = { options.firstOrNull { it.second == view.text.toString() }?.first ?: initial }
        return this
    }

    /** Date + time picker (local time); [initialIso] is an ISO instant or blank. */
    fun dateTime(key: String, label: String, initialIso: String): FormSheet {
        val zone = ZoneId.systemDefault()
        var value: LocalDateTime? = runCatching { Instant.parse(initialIso).atZone(zone).toLocalDateTime() }.getOrNull()
        val fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
        label(label)
        val button = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun render() { button.text = value?.format(fmt) ?: activity.getString(R.string.form_pick_datetime) }
        render()
        button.setOnClickListener {
            val start = value ?: LocalDateTime.now()
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .build()
            datePicker.addOnPositiveButtonClickListener { millis ->
                val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                val timePicker = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(start.hour).setMinute(start.minute).build()
                timePicker.addOnPositiveButtonClickListener {
                    value = date.atTime(timePicker.hour, timePicker.minute)
                    render()
                }
                timePicker.show(activity.supportFragmentManager, "time")
            }
            datePicker.show(activity.supportFragmentManager, "date")
        }
        b.formFields.addView(button, lp(top = 4))
        readers[key] = { value?.atZone(zone)?.toInstant() }
        return this
    }

    fun note(text: String): FormSheet {
        b.formFields.addView(TextView(activity).apply {
            this.text = text
            setTextAppearance(R.style.TextAppearance_Krds_Meta)
            setBackgroundResource(R.drawable.bg_krds_info_box)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }, lp(top = 12))
        return this
    }

    fun view(v: View): FormSheet {
        b.formFields.addView(v, lp(top = 12))
        return this
    }

    fun onDismiss(block: () -> Unit): FormSheet {
        onClose = block
        return this
    }

    fun submitText(text: String, danger: Boolean = false): FormSheet {
        b.formSubmit.text = text
        if (danger) b.formSubmit.backgroundTintList = android.content.res.ColorStateList.valueOf(activity.getColor(R.color.krds_danger))
        return this
    }

    fun show(onSubmit: suspend (FormValues) -> Unit): FormSheet {
        b.formSubmit.setOnClickListener {
            val failed = validators.map { (view, check) -> view to check() }.filter { it.second != null }
            if (failed.isNotEmpty()) {
                // Bring the first problem into view instead of failing silently off screen.
                b.formScroll.smoothScrollTo(0, (failed.first().first.top - dp(12)).coerceAtLeast(0))
                return@setOnClickListener
            }
            val values = FormValues(readers.mapValues { it.value() })
            b.formSubmit.isEnabled = false
            b.formError.isVisible = false
            activity.lifecycleScope.launch {
                runCatching { onSubmit(values) }
                    .onSuccess { dialog.dismiss() }
                    .onFailure { e ->
                        b.formError.isVisible = true
                        b.formError.text = Dialogs.messageOf(activity, e)
                        b.formSubmit.isEnabled = true
                    }
            }
        }
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        b.root.viewTreeObserver.addOnGlobalLayoutListener(capper)
        dialog.show()
        return this
    }

    private fun iconButton(icon: Int, onClick: () -> Unit) =
        MaterialButton(activity, null, com.google.android.material.R.attr.materialIconButtonOutlinedStyle).apply {
            setIconResource(icon)
            setOnClickListener { onClick() }
        }

    private fun lp(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(top)
    }

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()
}
