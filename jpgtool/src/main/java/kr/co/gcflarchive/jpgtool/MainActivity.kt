package kr.co.gcflarchive.jpgtool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            AppTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                MainScreen(state, vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Photos shared from a gallery app. */
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        vm.setImages(uris)
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9DB8FF))
        else -> lightColorScheme(primary = Color(0xFF2E5BBA))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val s = state.settings

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { vm.setImages(it) }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::setLogo) }
    val permissionText = stringResource(R.string.err_storage_permission)
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else vm.showMessage(permissionText)
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    fun start() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.start()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.bodyMedium) }

            item {
                Section(stringResource(R.string.section_photos)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !state.working,
                        ) { Text(stringResource(R.string.pick_photos)) }
                        if (state.images.isNotEmpty()) {
                            OutlinedButton(onClick = vm::clearImages, enabled = !state.working) { Text(stringResource(R.string.clear)) }
                        }
                    }
                    if (state.images.isEmpty()) {
                        Text(stringResource(R.string.no_photos), style = MaterialTheme.typography.bodySmall)
                    } else {
                        val total = state.images.sumOf { it.size.coerceAtLeast(0) }
                        Text(stringResource(R.string.photos_count, state.images.size, formatBytes(total)), fontWeight = FontWeight.Medium)
                        state.images.take(5).forEach {
                            Text("• ${it.name}  (${if (it.size >= 0) formatBytes(it.size) else "?"})", style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.images.size > 5) {
                            Text(stringResource(R.string.and_more, state.images.size - 5), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.section_size)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = s.targetText,
                            onValueChange = { v -> vm.update { it.copy(targetText = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8)) } },
                            label = { Text(stringResource(R.string.target_label)) },
                            singleLine = true,
                            isError = s.targetBytes == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        SizeUnit.entries.forEach { u ->
                            FilterChip(selected = s.unit == u, onClick = { vm.update { it.copy(unit = u) } }, label = { Text(u.name) })
                        }
                    }
                    if (s.targetBytes == null) {
                        Text(stringResource(R.string.target_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { vm.update { it.copy(keepIfSmaller = !it.keepIfSmaller) } },
                    ) {
                        Checkbox(checked = s.keepIfSmaller, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.keep_if_smaller), style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedTextField(
                        value = s.maxSideText,
                        onValueChange = { v -> vm.update { it.copy(maxSideText = v.filter(Char::isDigit).take(5)) } },
                        label = { Text(stringResource(R.string.max_side_label)) },
                        singleLine = true,
                        isError = s.maxSide == null,
                        supportingText = {
                            Text(stringResource(if (s.maxSide == null) R.string.max_side_invalid else R.string.max_side_hint))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Section(stringResource(R.string.section_watermark)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { vm.update { it.copy(watermark = !it.watermark) } },
                    ) {
                        Text(stringResource(R.string.watermark_on), modifier = Modifier.weight(1f))
                        Switch(checked = s.watermark, onCheckedChange = null)
                    }
                    if (s.watermark) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).checkerboard()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                state.logo?.let {
                                    Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                OutlinedButton(onClick = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                    Text(stringResource(if (state.logo == null) R.string.pick_logo else R.string.change_logo))
                                }
                                Text(
                                    stringResource(if (state.logo == null) R.string.no_logo else R.string.logo_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Text(stringResource(R.string.position), fontWeight = FontWeight.Medium)
                        PositionGrid(s.anchor) { a -> vm.update { it.copy(anchor = a) } }

                        Text(stringResource(R.string.logo_size, s.logoPercent.roundToInt()))
                        Slider(value = s.logoPercent, onValueChange = { v -> vm.update { it.copy(logoPercent = v.roundToInt().toFloat()) } }, valueRange = 5f..80f)
                        Text(stringResource(R.string.opacity, (s.opacity * 100).roundToInt()))
                        Slider(value = s.opacity, onValueChange = { v -> vm.update { it.copy(opacity = (v * 100).roundToInt() / 100f) } }, valueRange = 0.05f..1f)
                        Text(stringResource(R.string.margin, s.marginPercent.roundToInt()))
                        Slider(value = s.marginPercent, onValueChange = { v -> vm.update { it.copy(marginPercent = v.roundToInt().toFloat()) } }, valueRange = 0f..20f)
                    }
                }
            }

            state.preview?.let { preview ->
                item {
                    Section(stringResource(R.string.section_preview)) {
                        Image(
                            preview.asImageBitmap(),
                            contentDescription = stringResource(R.string.section_preview),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Text(stringResource(R.string.preview_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                val ready = state.images.isNotEmpty() && s.targetBytes != null && s.maxSide != null && !state.working
                Button(onClick = ::start, enabled = ready, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(
                        if (state.working) stringResource(R.string.working, state.done, state.images.size)
                        else stringResource(R.string.start, state.images.size),
                    )
                }
                if (state.working) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (state.images.isEmpty()) 0f else state.done.toFloat() / state.images.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.results.isNotEmpty()) {
                item {
                    Section(stringResource(R.string.section_results)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.saved_to), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            val shareable = state.results.mapNotNull { it.shareUri }
                            if (shareable.isNotEmpty() && !state.working) {
                                TextButton(onClick = { share(context, shareable) }) { Text(stringResource(R.string.share_all)) }
                            }
                        }
                        state.results.forEach { ResultRow(it) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** 3×3 grid of tappable cells; the selected cell shows a dot. */
@Composable
private fun PositionGrid(selected: Anchor, onSelect: (Anchor) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = Modifier.width(180.dp).aspectRatio(4f / 3f)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Anchor.entries.chunked(3).forEach { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { a ->
                    val isSel = a == selected
                    val label = stringResource(positionLabel(a))
                    Box(
                        modifier = Modifier.weight(1f).fillMaxSize().clip(shape)
                            .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(role = Role.RadioButton) { onSelect(a) }
                            .semantics { contentDescription = label; this.selected = isSel },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSel) Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onPrimary))
                    }
                }
            }
        }
    }
}

private fun positionLabel(a: Anchor): Int = when (a) {
    Anchor.TOP_LEFT -> R.string.pos_TOP_LEFT
    Anchor.TOP_CENTER -> R.string.pos_TOP_CENTER
    Anchor.TOP_RIGHT -> R.string.pos_TOP_RIGHT
    Anchor.CENTER_LEFT -> R.string.pos_CENTER_LEFT
    Anchor.CENTER -> R.string.pos_CENTER
    Anchor.CENTER_RIGHT -> R.string.pos_CENTER_RIGHT
    Anchor.BOTTOM_LEFT -> R.string.pos_BOTTOM_LEFT
    Anchor.BOTTOM_CENTER -> R.string.pos_BOTTOM_CENTER
    Anchor.BOTTOM_RIGHT -> R.string.pos_BOTTOM_RIGHT
}

@Composable
private fun ResultRow(r: ResultItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(r.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        if (r.error != null) {
            Text(stringResource(R.string.result_error, r.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else {
            val orig = if (r.originalSize >= 0) formatBytes(r.originalSize) else "?"
            Text(stringResource(R.string.result_line, orig, formatBytes(r.newSize), r.width, r.height), style = MaterialTheme.typography.bodySmall)
            Text(
                when {
                    !r.fits -> stringResource(R.string.result_not_fit)
                    r.quality == null -> stringResource(R.string.result_kept)
                    else -> stringResource(R.string.result_quality, r.quality)
                },
                color = if (r.fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Light/dark squares behind the logo thumbnail, so transparent logos are visible. */
private fun Modifier.checkerboard(): Modifier = background(Color.White).drawBehind {
    val cell = 8.dp.toPx()
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(Color(0xFFE0E0E0), topLeft = Offset(x, y), size = Size(cell, cell))
            x += cell * 2
        }
        y += cell
        row++
    }
}

private fun share(context: android.content.Context, uris: List<Uri>) {
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.setType("image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}
