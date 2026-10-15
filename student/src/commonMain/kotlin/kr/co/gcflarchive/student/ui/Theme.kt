package kr.co.gcflarchive.student.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kr.co.gcflarchive.student.res.Res
import kr.co.gcflarchive.student.res.pretendard_gov
import org.jetbrains.compose.resources.Font

/**
 * One TDS hue: [fg] for icons, [bg] (50) for the tinted background behind it. In light mode green, teal,
 * orange and yellow use TDS 700/800 so icons stay visible on their tint; text on a tint uses textStrong.
 */
@Immutable
data class Hue(val fg: Color, val bg: Color)

/**
 * App colors, light and dark. Values are TDS (Toss Design System, @toss/tds-colors); the names are
 * the KRDS ones the screens were written with. Same values as the Android app's res/values(-night)/krds_colors.xml.
 */
@Immutable
data class KrdsColors(
    val primary: Color,
    val primaryText: Color,
    val primaryTint: Color,
    val onPrimary: Color,
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val textStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val danger: Color,
    val dangerTint: Color,
    val star: Color,
    val infoBg: Color, val infoFg: Color,
    val successBg: Color, val successFg: Color,
    val purpleBg: Color, val purpleFg: Color,
    val neutralBg: Color, val neutralFg: Color,
    val highlight: Color,
    val blue: Hue, val green: Hue, val orange: Hue, val purple: Hue, val teal: Hue, val red: Hue, val yellow: Hue, val grey: Hue,
) {
    /** Colorful hues in a fixed order (e.g. one per 과목, picked by [hueFor]). */
    val hues: List<Hue> get() = listOf(blue, green, orange, purple, teal, red, yellow)

    /** The same hue for the same text every time (과목 → 색). */
    fun hueFor(key: String): Hue = if (key.isBlank()) grey else hues[(key.fold(7) { h, c -> h * 31 + c.code } and 0x7fffffff) % hues.size]
}

val LightKrds = KrdsColors(
    primary = Color(0xFF3182F6), primaryText = Color(0xFF2272EB), primaryTint = Color(0xFFE8F3FF), onPrimary = Color.White,
    bg = Color(0xFFF2F4F6), surface = Color.White, surfaceRaised = Color(0xFFF2F4F6),
    border = Color(0xFFE5E8EB), borderStrong = Color(0xFFD1D6DB),
    textStrong = Color(0xFF191F28), text = Color(0xFF333D4B), textSecondary = Color(0xFF6B7684), textDisabled = Color(0xFFB0B8C1),
    danger = Color(0xFFF04452), dangerTint = Color(0xFFFFEEEE), star = Color(0xFFFFC342),
    infoBg = Color(0xFFE8F3FF), infoFg = Color(0xFF1B64DA),
    successBg = Color(0xFFF0FAF6), successFg = Color(0xFF029359),
    purpleBg = Color(0xFFF9F0FC), purpleFg = Color(0xFF8222A2),
    neutralBg = Color(0xFFF2F4F6), neutralFg = Color(0xFF4E5968),
    highlight = Color(0xFFFFF1A8),
    blue = Hue(Color(0xFF3182F6), Color(0xFFE8F3FF)), green = Hue(Color(0xFF029359), Color(0xFFF0FAF6)),
    orange = Hue(Color(0xFFED6700), Color(0xFFFFF3E0)), purple = Hue(Color(0xFFA234C7), Color(0xFFF9F0FC)),
    teal = Hue(Color(0xFF0C8585), Color(0xFFEDF8F8)), red = Hue(Color(0xFFF04452), Color(0xFFFFEEEE)),
    yellow = Hue(Color(0xFFEE8F11), Color(0xFFFFF9E7)), grey = Hue(Color(0xFF6B7684), Color(0xFFF2F4F6)),
)

val DarkKrds = KrdsColors(
    primary = Color(0xFF3485FA), primaryText = Color(0xFF449BFF), primaryTint = Color(0xFF202C4D), onPrimary = Color.White,
    bg = Color(0xFF17171C), surface = Color(0xFF202027), surfaceRaised = Color(0xFF2C2C35),
    border = Color(0xFF2C2C35), borderStrong = Color(0xFF3C3C47),
    textStrong = Color.White, text = Color(0xFFE4E4E5), textSecondary = Color(0xFF9E9EA4), textDisabled = Color(0xFF62626D),
    danger = Color(0xFFF04251), dangerTint = Color(0xFF3C2020), star = Color(0xFFFFB134),
    infoBg = Color(0xFF202C4D), infoFg = Color(0xFF449BFF),
    successBg = Color(0xFF153729), successFg = Color(0xFF16BB76),
    purpleBg = Color(0xFF3F2447), purpleFg = Color(0xFFD18BEA),
    neutralBg = Color(0xFF2C2C35), neutralFg = Color(0xFFC3C3C6),
    highlight = Color(0x66B8860B),
    blue = Hue(Color(0xFF449BFF), Color(0xFF202C4D)), green = Hue(Color(0xFF16BB76), Color(0xFF153729)),
    orange = Hue(Color(0xFFF18600), Color(0xFF3D2500)), purple = Hue(Color(0xFFC770E4), Color(0xFF3F2447)),
    teal = Hue(Color(0xFF2EAAB2), Color(0xFF203537)), red = Hue(Color(0xFFF04251), Color(0xFF3C2020)),
    yellow = Hue(Color(0xFFFFB134), Color(0xFF3D2D1A)), grey = Hue(Color(0xFF9E9EA4), Color(0xFF2C2C35)),
)

val LocalKrds = staticCompositionLocalOf { LightKrds }

/** Shortcut: `Krds.colors.primary`. */
object Krds {
    val colors: KrdsColors @Composable get() = LocalKrds.current
}

@Composable
private fun pretendard(): FontFamily {
    @Composable
    fun weight(w: Int) = Font(Res.font.pretendard_gov, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    return FontFamily(weight(400), weight(500), weight(600), weight(700))
}

@Composable
fun KrdsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val k = if (dark) DarkKrds else LightKrds
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = k.primary,
        onPrimary = k.onPrimary,
        primaryContainer = k.primaryTint,
        onPrimaryContainer = k.primaryText,
        secondaryContainer = k.primaryTint,
        onSecondaryContainer = k.primaryText,
        background = k.bg,
        onBackground = k.text,
        surface = k.surface,
        onSurface = k.text,
        surfaceVariant = k.surfaceRaised,
        onSurfaceVariant = k.textSecondary,
        surfaceContainer = k.surface,
        surfaceContainerLow = k.surface,
        surfaceContainerHigh = k.surfaceRaised,
        outline = k.borderStrong,
        outlineVariant = k.border,
        error = k.danger,
    )
    val family = pretendard()
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    val type = Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(),
        headlineSmall = base.headlineSmall.f().copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.f().copy(fontWeight = FontWeight.Bold), titleMedium = base.titleMedium.f().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.f().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f().copy(fontWeight = FontWeight.SemiBold), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
    CompositionLocalProvider(LocalKrds provides k) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}

/** KRDS text roles used across screens. */
object KrdsText {
    val pageTitle: TextStyle @Composable get() = MaterialTheme.typography.headlineSmall.copy(color = Krds.colors.textStrong)
    val pageDesc: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium.copy(color = Krds.colors.textSecondary)
    val cardTitle: TextStyle @Composable get() = MaterialTheme.typography.titleSmall.copy(color = Krds.colors.textStrong, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val sectionTitle: TextStyle @Composable get() = MaterialTheme.typography.titleMedium.copy(color = Krds.colors.textStrong, fontWeight = FontWeight.Bold)
    // One size above Material's small styles so secondary info stays readable.
    val meta: TextStyle @Composable get() = MaterialTheme.typography.bodySmall.copy(color = Krds.colors.textSecondary, fontSize = 14.sp)
    val label: TextStyle @Composable get() = MaterialTheme.typography.labelMedium.copy(color = Krds.colors.textSecondary, fontSize = 13.sp)
    val body: TextStyle @Composable get() = MaterialTheme.typography.bodyLarge.copy(color = Krds.colors.text)
}
