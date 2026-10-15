package kr.co.gcflarchive.student.school

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import kr.co.gcflarchive.student.ui.KrdsTheme

/**
 * The shared '내 시간표' card ([TodayTimetableCard]) as a plain View, for the Android user app's
 * home (a View layout). Takes no space when there is nothing to show. Call [refresh] on resume.
 */
class TodayTimetableView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    /** Tapping the card. */
    var onOpen: () -> Unit = {}
    private var version by mutableIntStateOf(0)

    /** The host theme's filled-card color (Material colorSurfaceContainerHighest), if it has one. */
    private val cardColor: Color? = run {
        val attr = context.resources.getIdentifier("colorSurfaceContainerHighest", "attr", context.packageName)
        val tv = TypedValue()
        when {
            attr == 0 || !context.theme.resolveAttribute(attr, tv, true) -> null
            tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> Color(tv.data)
            tv.resourceId != 0 -> runCatching { Color(context.getColor(tv.resourceId)) }.getOrNull()
            else -> null
        }
    }

    init {
        addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KrdsTheme {
                    TodayTimetableCard(SchoolActivity.services(context), version, Modifier.padding(top = 12.dp), containerColor = cardColor) { onOpen() }
                }
            }
        })
    }

    fun refresh() {
        version++
    }
}
