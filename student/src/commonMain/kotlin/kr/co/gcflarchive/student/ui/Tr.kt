package kr.co.gcflarchive.student.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kr.co.gcflarchive.core.i18n.I18n

/** Bumped when the language (or its strings) changes, so every screen that called [tr] redraws. */
internal object LangState {
    var revision by mutableIntStateOf(0)

    init {
        I18n.onChange = { revision++ }
    }
}

/**
 * The Korean [text] in the teacher's language (I18n): `tr("보강 부탁")`, `tr("{n}지망", "n" to 1)`.
 * Reads [LangState] so Compose redraws when the language changes. Students always get the Korean text.
 */
fun tr(text: String, vararg params: Pair<String, Any?>): String {
    LangState.revision
    return I18n.tr(text, *params)
}
