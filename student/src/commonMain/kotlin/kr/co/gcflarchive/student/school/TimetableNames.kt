package kr.co.gcflarchive.student.school

import androidx.compose.runtime.mutableStateOf
import kr.co.gcflarchive.core.prefs.AppPrefs

/**
 * 설정 > 시간표 과목 이름 (줄임말 심F / 전체 이름 심화영어 Ⅱ), as Compose state so an open
 * timetable redraws as soon as it changes. Both settings screens (Android, shared) write through [set].
 */
object TimetableNames {
    private val state = mutableStateOf<Boolean?>(null)

    fun full(prefs: AppPrefs): Boolean = state.value ?: prefs.timetableFullNames.also { state.value = it }

    fun set(prefs: AppPrefs, full: Boolean) {
        prefs.timetableFullNames = full
        state.value = full
    }
}
