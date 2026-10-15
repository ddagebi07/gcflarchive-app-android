package kr.co.gcflarchive.student.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Snackbar
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Top bar used by every pushed screen (← back + title + actions). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrdsTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = KrdsText.sectionTitle) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Krds.colors.bg, scrolledContainerColor = Krds.colors.bg),
    )
}

/** Page title + description (.tit-page / .tit-page-desc). */
@Composable
fun PageHeader(title: String, desc: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = KrdsText.pageTitle)
        if (desc != null) Text(desc, style = KrdsText.pageDesc, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Badge colors: 파랑 INFO · 초록 SUCCESS · 노랑 WARNING · 빨강 DANGER, plus grey NEUTRAL. */
enum class BadgeTone { INFO, SUCCESS, WARNING, NEUTRAL, DANGER }

/** .tag-badge */
@Composable
fun Badge(text: String, tone: BadgeTone = BadgeTone.INFO, modifier: Modifier = Modifier) {
    val k = Krds.colors
    val (bg, fg) = when (tone) {
        BadgeTone.INFO -> k.infoBg to k.infoFg
        BadgeTone.SUCCESS -> k.successBg to k.successFg
        BadgeTone.WARNING -> k.warningBg to k.warningFg
        BadgeTone.NEUTRAL -> k.neutralBg to k.neutralFg
        BadgeTone.DANGER -> k.dangerTint to k.dangerText
    }
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Bordered card (.board-card-item). */
@Composable
fun KrdsCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    // TDS: a white (dark: #202027) rounded card on the grey page, no border.
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Krds.colors.surface, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** Shaded note (.info-box). */
@Composable
fun InfoBox(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.fillMaxWidth().background(Krds.colors.primaryTint, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Krds.colors.primaryText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = KrdsText.body.copy(fontSize = 14.sp, lineHeight = 20.sp))
    }
}

/** Strong rule above a board list. */
@Composable
fun StrongRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).background(Krds.colors.textStrong))
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = Krds.colors.border)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color ?: Krds.colors.primary, contentColor = Krds.colors.onPrimary),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, danger: Boolean = false) {
    // TDS 'weak' button: tinted fill, colored text, no outline.
    val c = if (danger) Krds.colors.danger else Krds.colors.primaryText
    val fill = if (danger) Krds.colors.dangerTint else Krds.colors.primaryTint
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = fill, contentColor = c,
            disabledContainerColor = Krds.colors.surfaceRaised, disabledContentColor = Krds.colors.textDisabled,
        ),
        elevation = null,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Horizontally scrolling single-choice chips (.filter-opt). */
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) -> KrdsChip(label, value == selected) { onSelect(value) } }
    }
}

/**
 * One dropdown (.krds-select) instead of a row of buttons: shows the chosen [options]
 * label under [label] and opens a menu of every option.
 */
@Composable
fun <T> KrdsDropdown(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second.orEmpty()
    Box(modifier) {
        KrdsPickerBox(label, current) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, style = KrdsText.body.copy(fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal)) },
                    onClick = {
                        open = false
                        if (value != selected) onSelect(value)
                    },
                )
            }
        }
    }
}

/** The closed look of [KrdsDropdown]: small label, value, ▾. Also for pickers that open a dialog (날짜). */
@Composable
fun KrdsPickerBox(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .border(1.dp, Krds.colors.borderStrong, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = KrdsText.label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = KrdsText.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null, tint = Krds.colors.textSecondary)
        }
    }
}

@Composable
fun KrdsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Krds.colors.surface,
            labelColor = Krds.colors.text,
            selectedContainerColor = Krds.colors.primary,
            selectedLabelColor = Krds.colors.onPrimary,
        ),
        border = null,
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Empty / error / login-needed state with an optional action. */
@Composable
fun StateMessage(title: String, desc: String? = null, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier, locked: Boolean = false) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (locked) Icon(Icons.Filled.Lock, null, tint = Krds.colors.primaryText, modifier = Modifier.size(40.dp).padding(bottom = 8.dp))
        Text(title, style = KrdsText.cardTitle, textAlign = TextAlign.Center)
        if (desc != null) Text(desc, style = KrdsText.meta, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (action != null && onAction != null) PrimaryButton(action, onAction, Modifier.padding(top = 16.dp))
    }
}

@Composable
fun FullScreen(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().background(Krds.colors.bg)) { content() }

/** Meta tile (.meta-tile): small label/value box. */
@Composable
fun MetaTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Krds.colors.surfaceRaised, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, style = KrdsText.meta)
        Text(value, style = MaterialTheme.typography.labelLarge.copy(color = Krds.colors.textStrong), maxLines = 1)
    }
}

/** The app's message bar: tapping it closes it at once (so it never sits on top of a button). */
@Composable
fun AppSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        Snackbar(
            data,
            modifier = Modifier.clickable(onClickLabel = "닫기") { data.dismiss() },
            shape = RoundedCornerShape(14.dp),
        )
    }
}
