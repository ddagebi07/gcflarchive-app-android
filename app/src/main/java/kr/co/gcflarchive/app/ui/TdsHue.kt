package kr.co.gcflarchive.app.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import kr.co.gcflarchive.app.R

/**
 * TDS처럼 메뉴마다 색을 다르게: 아이콘은 진한 색(500), 바탕은 옅은 색(50)의 둥근 사각형.
 * Colors are app resources (tds_*) so dark mode gets TDS's dark values.
 */
enum class TdsHue(val fg: Int, val bg: Int) {
    BLUE(R.color.tds_blue, R.color.tds_blue_bg),
    GREEN(R.color.tds_green, R.color.tds_green_bg),
    ORANGE(R.color.tds_orange, R.color.tds_orange_bg),
    PURPLE(R.color.tds_purple, R.color.tds_purple_bg),
    TEAL(R.color.tds_teal, R.color.tds_teal_bg),
    RED(R.color.tds_red, R.color.tds_red_bg),
    YELLOW(R.color.tds_yellow, R.color.tds_yellow_bg),
    GREY(R.color.tds_grey, R.color.tds_grey_bg);

    /** Tints [icon] and its chip background [chip] (a view with @drawable/bg_icon_chip). */
    fun apply(icon: ImageView, chip: View) {
        val ctx = icon.context
        // ImageViewCompat: the icons are tinted with app:tint (AppCompat), which plain imageTintList may not override.
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(ctx.getColor(fg)))
        ViewCompat.setBackgroundTintList(chip, ColorStateList.valueOf(ctx.getColor(bg)))
    }

    companion object {
        /** One hue per menu, by its icon, so a menu looks the same everywhere (홈 · 더보기). */
        fun forIcon(iconRes: Int): TdsHue = when (iconRes) {
            R.drawable.ic_exam, R.drawable.ic_event, R.drawable.ic_drive -> BLUE
            R.drawable.ic_pdf, R.drawable.ic_map -> RED
            R.drawable.ic_photo, R.drawable.ic_score -> GREEN
            R.drawable.ic_video, R.drawable.ic_assignment -> PURPLE
            R.drawable.ic_search, R.drawable.ic_forum -> TEAL
            R.drawable.ic_grade, R.drawable.ic_meal, R.drawable.ic_notice -> ORANGE
            else -> GREY
        }
    }
}
