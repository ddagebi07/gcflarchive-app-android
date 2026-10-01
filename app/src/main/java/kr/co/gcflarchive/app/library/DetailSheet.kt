package kr.co.gcflarchive.app.library

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.SheetDetailBinding

/** Builds the KRDS "상세 정보" modal as a bottom sheet: label/value rows + action buttons. */
class DetailSheet(private val context: Context, heading: String, title: String) {
    val dialog = BottomSheetDialog(context, R.style.ThemeOverlay_Krds_BottomSheet)
    private val b = SheetDetailBinding.inflate(LayoutInflater.from(context))

    init {
        b.sheetHeading.text = heading
        b.sheetTitle.text = title
        dialog.setContentView(b.root)
    }

    /** .detail-label + value. Blank values are skipped. */
    fun row(label: String, value: String?, valueColor: Int? = null): DetailSheet {
        if (value.isNullOrBlank()) return this
        b.sheetBody.addView(labeled(label, value, valueColor), fullWidth(bottom = 12))
        return this
    }

    /** .detail-pair: two label/value columns side by side. */
    fun pair(label1: String, value1: String?, label2: String, value2: String?): DetailSheet {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(labeled(label1, value1.orDash(), null), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(labeled(label2, value2.orDash(), null), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        b.sheetBody.addView(row, fullWidth(bottom = 12))
        return this
    }

    /** .info-box: a shaded block of text (설명 / 태그). */
    fun infoBox(label: String, text: String?): DetailSheet {
        if (text.isNullOrBlank()) return this
        b.sheetBody.addView(label(label), fullWidth(bottom = 4))
        val box = TextView(context).apply {
            this.text = text
            setTextAppearance(R.style.TextAppearance_Krds_DetailValue)
            setBackgroundResource(R.drawable.bg_krds_info_box)
            val p = dp(12)
            setPadding(p, p, p, p)
            setTextIsSelectable(true)
        }
        b.sheetBody.addView(box, fullWidth(bottom = 12))
        return this
    }

    fun view(view: View): DetailSheet {
        b.sheetBody.addView(view, fullWidth(bottom = 12))
        return this
    }

    data class Action(
        val text: String,
        val style: Style,
        val icon: Int? = null,
        val onClick: (MaterialButton) -> Unit,
    )

    enum class Style(val layout: Int) {
        PRIMARY(R.layout.view_krds_button_primary),
        SECONDARY(R.layout.view_krds_button_secondary),
        TERTIARY(R.layout.view_krds_button_tertiary),
    }

    /** A row of equally wide buttons (.modal-btn). */
    fun actions(vararg actions: Action): DetailSheet {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actions.forEachIndexed { i, a ->
            val btn = (LayoutInflater.from(context).inflate(a.style.layout, row, false) as MaterialButton).apply {
                text = a.text
                a.icon?.let { setIconResource(it) }
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                setOnClickListener { a.onClick(this) }
            }
            row.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = dp(8)
            })
        }
        b.sheetActions.addView(row, fullWidth(bottom = 4))
        return this
    }

    fun show(): DetailSheet {
        dialog.show()
        return this
    }

    fun dismiss() = dialog.dismiss()

    private fun labeled(label: String, value: String, valueColor: Int?): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(label))
            addView(TextView(context).apply {
                text = value
                setTextAppearance(R.style.TextAppearance_Krds_DetailValue)
                valueColor?.let { setTextColor(ContextCompat.getColor(context, it)) }
                setTextIsSelectable(true)
            })
        }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        setTextAppearance(R.style.TextAppearance_Krds_DetailLabel)
    }

    private fun fullWidth(bottom: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(bottom)
        }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun String?.orDash() = if (isNullOrBlank()) "-" else this
}

/** .tag-badge variants. */
enum class Badge(val background: Int, val textColor: Int) {
    INFO(R.drawable.bg_krds_badge_info, R.color.krds_badge_info_fg),            // 답지
    SUCCESS(R.drawable.bg_krds_badge_success, R.color.krds_badge_success_fg),   // 학과
    PURPLE(R.drawable.bg_krds_badge_purple, R.color.krds_badge_purple_fg),      // 등급컷
    NEUTRAL(R.drawable.bg_krds_badge_neutral, R.color.krds_badge_neutral_fg),   // 시험 구분 / 분류
    DANGER(R.drawable.bg_krds_badge_danger, R.color.krds_danger),               // 비공개
}

/** Replaces [group]'s children with badges for the non-blank labels. */
fun setBadges(group: ViewGroup, badges: List<Pair<String, Badge>>) {
    group.removeAllViews()
    val inflater = LayoutInflater.from(group.context)
    for ((label, badge) in badges) {
        if (label.isBlank()) continue
        val tv = inflater.inflate(R.layout.view_badge, group, false) as TextView
        tv.text = label
        tv.setBackgroundResource(badge.background)
        tv.setTextColor(ContextCompat.getColor(group.context, badge.textColor))
        group.addView(tv)
    }
    group.visibility = if (group.childCount > 0) View.VISIBLE else View.GONE
}
