package kr.co.gcflarchive.core.i18n

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kr.co.gcflarchive.core.net.ApiException
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.jsonOf
import kr.co.gcflarchive.core.net.objects
import kr.co.gcflarchive.core.net.parseObject

/** A language the server has a file for (i18n/<code>.json); [name] is its own name ("English"). */
data class Language(val code: String, val name: String)

/**
 * 다국어 (지금은 교사 화면): the same files and rules as the website (archive_dev I18N.md).
 *
 * - The Korean text in the code is the key: `tr("보강 부탁")`. No translation → the Korean text.
 * - `{name}` placeholders: `tr("{name} 선생님께 전달했어요.", "name" to n)`.
 * - Same Korean, different meaning: "취소|상태" (only the part before '|' shows in Korean).
 * - The language is the teacher's own setting on the server (GET/PUT /api/i18n/me); students stay in 한국어.
 *   The catalog is cached on the device and re-downloaded when its version changes, so a new language or
 *   fixed translation reaches the app without an update.
 */
object I18n {
    const val DEFAULT = "ko"

    private val settings: Settings by lazy { Settings() }
    private var strings: Map<String, String> = emptyMap()
    private var loaded = false

    /** The language in use now. */
    var lang: String = DEFAULT
        private set

    /** Languages to choose from (한국어 first); filled after [sync]. */
    var languages: List<Language> = emptyList()
        private set

    /** Only teachers may pick another language. */
    var canChoose: Boolean = false
        private set

    /** Called after the language or its strings change (the Compose UI redraws from here). */
    var onChange: (() -> Unit)? = null

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            lang = settings.getString(KEY_LANG, DEFAULT)
            strings = if (lang == DEFAULT) emptyMap() else decode(settings.getStringOrNull(KEY_STRINGS + lang))
        }
    }

    fun tr(text: String, vararg params: Pair<String, Any?>): String {
        ensureLoaded()
        var out = strings[text] ?: text.substringBefore('|')
        for ((k, v) in params) out = out.replace("{$k}", v?.toString() ?: "")
        return out
    }

    /** After login / on app start: this user's language from the server, and its strings if they changed. */
    suspend fun sync(site: SiteClient) {
        ensureLoaded()
        val res = runCatching { site.exchange("/api/i18n/me") }.getOrNull() ?: return  // offline: keep the cache
        if (res.code == 401) { use(DEFAULT, emptyMap()); canChoose = false; return }
        if (!res.isSuccessful) return
        val o = res.json
        languages = o.optJSONArray("languages").objects().map { Language(it.optString("code"), it.optString("name")) }
        canChoose = o.optBoolean("canChoose")
        load(site, o.optString("lang", DEFAULT), o.optLong("version"))
    }

    /** 설정 > 화면 언어. Throws the server's message when it refuses. */
    suspend fun choose(site: SiteClient, code: String) {
        val res = site.exchange("/api/i18n/me", "PUT", jsonOf("lang" to code))
        if (!res.isSuccessful) throw ApiException(res.code, res.error ?: "처리하지 못했어요. 잠시 후 다시 시도해주세요.")
        val o = res.json
        load(site, o.optString("lang", DEFAULT), o.optLong("version"))
    }

    /** 로그아웃: back to 한국어. */
    fun reset() {
        ensureLoaded()
        canChoose = false
        use(DEFAULT, emptyMap())
    }

    private suspend fun load(site: SiteClient, code: String, version: Long) {
        if (code == DEFAULT) return use(DEFAULT, emptyMap())
        val cached = settings.getLongOrNull(KEY_VERSION + code)
        val map = if (cached == version && settings.hasKey(KEY_STRINGS + code)) decode(settings.getStringOrNull(KEY_STRINGS + code))
        else {
            val body = runCatching { site.exchange("/api/i18n/$code") }.getOrNull()?.takeIf { it.isSuccessful }?.body ?: return
            val raw = parseObject(body).optJSONObject("strings")?.raw ?: return
            settings.putString(KEY_STRINGS + code, raw.toString())
            settings.putLong(KEY_VERSION + code, version)
            decode(raw.toString())
        }
        use(code, map)
    }

    /** Tests: switch to [code] with [map] as its strings, no server. */
    internal fun useForTest(code: String, map: Map<String, String>) = use(code, map)

    private fun use(code: String, map: Map<String, String>) {
        val changed = code != lang || map != strings
        lang = code
        strings = map
        settings.putString(KEY_LANG, code)
        if (changed) onChange?.invoke()
    }

    private fun decode(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        val raw: JsonObject = runCatching { parseObject(text).raw }.getOrNull() ?: return emptyMap()
        return raw.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()
    }

    private const val KEY_LANG = "i18n_lang"
    private const val KEY_STRINGS = "i18n_strings_"
    private const val KEY_VERSION = "i18n_version_"
}
