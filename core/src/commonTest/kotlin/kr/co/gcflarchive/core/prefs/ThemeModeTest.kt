package kr.co.gcflarchive.core.prefs

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeModeTest {
    @Test
    fun defaultsToSystemAndRejectsUnknownValues() {
        val prefs = AppPrefs(MapSettings())
        assertEquals(AppPrefs.THEME_SYSTEM, prefs.themeMode)
        prefs.themeMode = AppPrefs.THEME_DARK
        assertEquals("dark", prefs.themeMode)
        prefs.themeMode = "sepia"
        assertEquals(AppPrefs.THEME_SYSTEM, prefs.themeMode)
        assertEquals("시스템 설정 따르기", AppPrefs.themeLabel(AppPrefs.THEME_SYSTEM))
        assertEquals("라이트 모드", AppPrefs.themeLabel(AppPrefs.THEME_LIGHT))
    }
}
