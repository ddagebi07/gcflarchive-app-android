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

/** KRDS tokens (shared/res/values/krds_colors.xml), light and dark. */
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
)

val LightKrds = KrdsColors(
    primary = Color(0xFF0B3492), primaryText = Color(0xFF0B3492), primaryTint = Color(0x140B3492), onPrimary = Color.White,
    bg = Color(0xFFF8FAFC), surface = Color.White, surfaceRaised = Color(0xFFF1F5F9),
    border = Color(0xFFE2E8F0), borderStrong = Color(0xFFCBD5E1),
    textStrong = Color(0xFF131416), text = Color(0xFF1F2123), textSecondary = Color(0xFF64748B), textDisabled = Color(0xFF94A3B8),
    danger = Color(0xFFD62828), dangerTint = Color(0x14D62828), star = Color(0xFFF5A400),
    infoBg = Color(0xFFEFF6FF), infoFg = Color(0xFF1D4ED8),
    successBg = Color(0xFFF0FDF4), successFg = Color(0xFF166534),
    purpleBg = Color(0xFFFAF5FF), purpleFg = Color(0xFF7E22CE),
    neutralBg = Color(0xFFF8FAFC), neutralFg = Color(0xFF475569),
    highlight = Color(0xFFFFF1A8),
)

val DarkKrds = KrdsColors(
    primary = Color(0xFF0B3492), primaryText = Color(0xFF8AB8FF), primaryTint = Color(0x2E388BFD), onPrimary = Color.White,
    bg = Color(0xFF07090E), surface = Color(0xFF121621), surfaceRaised = Color(0xFF1A2035),
    border = Color(0x1AFFFFFF), borderStrong = Color(0x2EFFFFFF),
    textStrong = Color.White, text = Color(0xFFF1F3F9), textSecondary = Color(0xFF9AA4B2), textDisabled = Color(0xFF526077),
    danger = Color(0xFFFF6B6B), dangerTint = Color(0x26D62828), star = Color(0xFFFFC940),
    infoBg = Color(0x29388BFD), infoFg = Color(0xFF8AB8FF),
    successBg = Color(0x292B9348), successFg = Color(0xFF6FD28C),
    purpleBg = Color(0x29A855F7), purpleFg = Color(0xFFD3A6FB),
    neutralBg = Color(0x0FFFFFFF), neutralFg = Color(0xFF9AA4B2),
    highlight = Color(0x66B8860B),
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
    val meta: TextStyle @Composable get() = MaterialTheme.typography.bodySmall.copy(color = Krds.colors.textSecondary)
    val label: TextStyle @Composable get() = MaterialTheme.typography.labelMedium.copy(color = Krds.colors.textSecondary)
    val body: TextStyle @Composable get() = MaterialTheme.typography.bodyLarge.copy(color = Krds.colors.text)
}
