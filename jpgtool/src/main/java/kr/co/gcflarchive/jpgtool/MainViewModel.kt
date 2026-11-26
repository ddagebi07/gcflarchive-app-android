package kr.co.gcflarchive.jpgtool

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ResultItem(
    val name: String,
    val originalSize: Long,
    val newSize: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val quality: Int? = null,
    val fits: Boolean = true,
    val error: String? = null,
    /** Copy in the app cache, for sharing. */
    val shareUri: Uri? = null,
)

data class UiState(
    val images: List<InputImage> = emptyList(),
    val settings: Settings = Settings(),
    val logo: Bitmap? = null,
    val preview: Bitmap? = null,
    val working: Boolean = false,
    val done: Int = 0,
    val results: List<ResultItem> = emptyList(),
    val message: String? = null,
    /** Name of the folder the photos came from, when picked as a folder. */
    val sourceFolder: String? = null,
    val scanning: Boolean = false,
    /** Chosen save folder (SAF tree), or null for the default Pictures/JPG Tool. */
    val saveFolder: Uri? = null,
    val saveFolderName: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val logoFile = File(app.filesDir, "logo.png")
    private val outDir = File(app.cacheDir, "out")

    private val _state = MutableStateFlow(UiState(settings = loadSettings()).withSaveFolder(app))
    val state: StateFlow<UiState> = _state

    /** First picked photo decoded small, the base for the live preview. */
    private var previewBase: Bitmap? = null
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val logo = withContext(Dispatchers.IO) { ImageProcessor.loadLogo(logoFile) }
            _state.update { it.copy(logo = logo) }
            refreshPreview()
        }
    }

    private val ctx: Context get() = getApplication()

    fun setImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val images = withContext(Dispatchers.IO) {
                uris.distinct().mapNotNull { runCatching { ImageProcessor.describe(ctx, it) }.getOrNull() }
            }
            showImages(images, folder = null)
        }
    }

    /** All photos in a folder picked with the system folder picker. */
    fun setFolder(tree: Uri) {
        val recursive = _state.value.settings.includeSubfolders
        _state.update { it.copy(scanning = true) }
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { Folders.displayName(ctx, tree) to Folders.listImages(ctx, tree, recursive) }
            }
            _state.update { it.copy(scanning = false) }
            found.onFailure { e -> showMessage(ctx.getString(R.string.err_folder, e.message ?: e.javaClass.simpleName)) }
            found.onSuccess { (name, images) ->
                if (images.isEmpty()) showMessage(ctx.getString(R.string.folder_empty, name))
                else {
                    if (images.size >= Folders.MAX_FILES) showMessage(ctx.getString(R.string.folder_capped, Folders.MAX_FILES))
                    showImages(images, folder = name)
                }
            }
        }
    }

    private fun showImages(images: List<InputImage>, folder: String?) {
        if (images.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(images = images, results = emptyList(), sourceFolder = folder) }
            previewBase = withContext(Dispatchers.Default) {
                images.firstOrNull()?.let { runCatching { ImageProcessor.decode(ctx, it.uri, 1080) }.getOrNull() }
            }
            refreshPreview(debounce = false)
        }
    }

    fun clearImages() {
        previewBase = null
        _state.update { it.copy(images = emptyList(), results = emptyList(), preview = null, sourceFolder = null) }
    }

    /** Save results into [tree] from now on (remembered across restarts). */
    fun setSaveFolder(tree: Uri) {
        _state.value.saveFolder?.takeIf { it != tree }?.let { Folders.releaseAccess(ctx, it) }
        Folders.keepAccess(ctx, tree, write = true)
        prefs.edit().putString("saveFolder", tree.toString()).apply()
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { Folders.displayName(ctx, tree) }
            _state.update { it.copy(saveFolder = tree, saveFolderName = name) }
        }
    }

    /** Back to Pictures/JPG Tool. */
    fun resetSaveFolder() {
        _state.value.saveFolder?.let { Folders.releaseAccess(ctx, it) }
        prefs.edit().remove("saveFolder").apply()
        _state.update { it.copy(saveFolder = null, saveFolderName = null) }
    }

    private fun UiState.withSaveFolder(app: Application): UiState {
        val tree = prefs.getString("saveFolder", null)?.let(Uri::parse) ?: return this
        if (!Folders.canWrite(app, tree)) { // access was revoked or the folder is gone
            prefs.edit().remove("saveFolder").apply()
            return this
        }
        return copy(saveFolder = tree, saveFolderName = runCatching { Folders.displayName(app, tree) }.getOrNull())
    }

    fun setLogo(uri: Uri) {
        viewModelScope.launch {
            val logo = withContext(Dispatchers.IO) { runCatching { ImageProcessor.saveLogo(ctx, uri, logoFile) }.getOrNull() }
            if (logo == null) {
                _state.update { it.copy(message = ctx.getString(R.string.err_logo)) }
            } else {
                _state.update { it.copy(logo = logo) }
                update { it.copy(watermark = true) }
                refreshPreview(debounce = false)
            }
        }
    }

    fun update(change: (Settings) -> Settings) {
        val before = _state.value.settings
        val s = change(before)
        _state.update { it.copy(settings = s) }
        saveSettings(s)
        val previewChanged = s.watermark != before.watermark || s.anchor != before.anchor ||
            s.logoPercent != before.logoPercent || s.opacity != before.opacity || s.marginPercent != before.marginPercent
        if (previewChanged) refreshPreview()
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun refreshPreview(debounce: Boolean = true) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (debounce) delay(40)
            val base = previewBase
            val st = _state.value
            val preview = withContext(Dispatchers.Default) {
                base?.let {
                    val copy = it.copy(Bitmap.Config.ARGB_8888, true)
                    if (st.settings.watermark && st.logo != null) ImageProcessor.applyWatermark(copy, st.logo, st.settings)
                    copy
                }
            }
            _state.update { it.copy(preview = preview) }
        }
    }

    fun start() {
        val st = _state.value
        if (st.working || st.images.isEmpty() || st.settings.targetBytes == null || st.settings.maxSide == null) return
        val settings = st.settings
        val logo = st.logo
        val saveFolder = st.saveFolder
        _state.update { it.copy(working = true, done = 0, results = emptyList()) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { outDir.deleteRecursively(); outDir.mkdirs() }
            for (img in st.images) {
                val result = withContext(Dispatchers.Default) {
                    try {
                        val out = ImageProcessor.process(ctx, img, settings, logo)
                        val name = ImageProcessor.outputName(img.name)
                        val saved = if (saveFolder != null) Folders.save(ctx, saveFolder, name, out.bytes)
                        else ImageProcessor.save(ctx, name, out.bytes)
                        // Android 8–9 default folder gives a file:// Uri, which other apps may not open: share a cache copy.
                        val shareUri = if (saved.scheme == "file") {
                            FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(outDir, name).apply { writeBytes(out.bytes) })
                        } else saved
                        ResultItem(
                            name = name, originalSize = img.size, newSize = out.bytes.size.toLong(),
                            width = out.width, height = out.height, quality = out.quality, fits = out.fits,
                            shareUri = shareUri,
                        )
                    } catch (e: OutOfMemoryError) {
                        ResultItem(img.name, img.size, error = ctx.getString(R.string.err_memory))
                    } catch (e: Exception) {
                        ResultItem(img.name, img.size, error = e.message ?: e.javaClass.simpleName)
                    }
                }
                _state.update { it.copy(done = it.done + 1, results = it.results + result) }
            }
            _state.update { it.copy(working = false) }
        }
    }

    private fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            targetText = prefs.getString("target", d.targetText)!!,
            unit = runCatching { SizeUnit.valueOf(prefs.getString("unit", d.unit.name)!!) }.getOrDefault(d.unit),
            keepIfSmaller = prefs.getBoolean("keep", d.keepIfSmaller),
            includeSubfolders = prefs.getBoolean("subfolders", d.includeSubfolders),
            maxSideText = prefs.getString("maxSide", d.maxSideText)!!,
            watermark = prefs.getBoolean("wm", d.watermark),
            anchor = runCatching { Anchor.valueOf(prefs.getString("anchor", d.anchor.name)!!) }.getOrDefault(d.anchor),
            logoPercent = prefs.getFloat("logoPct", d.logoPercent),
            opacity = prefs.getFloat("opacity", d.opacity),
            marginPercent = prefs.getFloat("margin", d.marginPercent),
        )
    }

    private fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("target", s.targetText)
            .putString("unit", s.unit.name)
            .putBoolean("keep", s.keepIfSmaller)
            .putBoolean("subfolders", s.includeSubfolders)
            .putString("maxSide", s.maxSideText)
            .putBoolean("wm", s.watermark)
            .putString("anchor", s.anchor.name)
            .putFloat("logoPct", s.logoPercent)
            .putFloat("opacity", s.opacity)
            .putFloat("margin", s.marginPercent)
            .apply()
    }
}
