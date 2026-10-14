package kr.co.gcflarchive.app.grade

import android.widget.TextView
import androidx.core.view.isVisible
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ItemGradeScoreBinding
import kr.co.gcflarchive.app.meal.MealRepository
import java.time.format.DateTimeFormatter

/** 등급컷 계산기와 내 점수 기록이 같이 쓰는 점수 행. */
object GradeViews {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

    fun bindScore(v: ItemGradeScoreBinding, s: MyScore) {
        val ctx = v.root.context
        v.title.text = s.subject
        val parts = mutableListOf(s.examName)
        if (s.percentile != "-") parts += ctx.getString(R.string.grade_percentile, s.percentile)
        GradeLogic.parseInstant(s.timestamp)?.let { parts += it.atZone(MealRepository.SEOUL).format(TIME) }
        v.meta.text = parts.joinToString(" · ")
        v.score.text = ctx.getString(R.string.grade_score_value, s.score)
        v.grade.isVisible = s.grade != null
        s.grade?.let { styleGradeChip(v.grade, it) }
    }

    fun styleGradeChip(view: TextView, grade: Int) {
        view.text = view.context.getString(R.string.grade_n, grade)
        val (bg, fg) = when {
            grade <= 2 -> R.drawable.bg_krds_badge_info to R.color.krds_badge_info_fg
            grade <= 4 -> R.drawable.bg_krds_badge_success to R.color.krds_badge_success_fg
            grade <= 6 -> R.drawable.bg_krds_badge_neutral to R.color.krds_badge_neutral_fg
            else -> R.drawable.bg_krds_badge_danger to R.color.krds_danger
        }
        view.setBackgroundResource(bg)
        view.setTextColor(view.context.getColor(fg))
    }
}
