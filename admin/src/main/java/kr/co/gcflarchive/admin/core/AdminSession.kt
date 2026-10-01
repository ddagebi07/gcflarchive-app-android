package kr.co.gcflarchive.admin.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Who is signed in. Two ways in, like the web admin pages:
 *  - MASTER: the server's upload/master key, sent as X-Master-Auth-Key on every call;
 *  - HAKBUN: a normal student login (/api/verify-login) whose hakbun is in the admin list;
 *    authorization then rides on the Flask session cookie.
 */
class AdminSession private constructor(private val store: SecureStore) {
    enum class Mode { MASTER, HAKBUN }

    @Volatile
    var mode: Mode? = null
        private set

    @Volatile
    var masterKey: String? = null
        private set

    @Volatile
    var hakbun: String? = null
        private set

    @Volatile
    var grants: Grants = Grants.NONE
        private set

    val isSignedIn: Boolean get() = mode != null

    val displayName: String
        get() = when (mode) {
            Mode.MASTER -> "마스터 키"
            Mode.HAKBUN -> "${hakbun.orEmpty()} 관리자"
            null -> ""
        }

    init {
        store.get(KEY_STATE)?.let { runCatching { restore(JSONObject(it)) } }
    }

    fun signInMaster(key: String) {
        mode = Mode.MASTER
        masterKey = key
        hakbun = null
        grants = Grants(master = true, scopes = emptySet())
        persist()
    }

    fun signInHakbun(id: String, scopes: List<String>) {
        mode = Mode.HAKBUN
        masterKey = null
        hakbun = id
        grants = Grants(master = false, scopes = scopes.toSet())
        persist()
    }

    /** Refreshes scopes from /api/admin-status (they can change while signed in). */
    fun updateScopes(scopes: List<String>) {
        if (mode != Mode.HAKBUN) return
        grants = Grants(master = false, scopes = scopes.toSet())
        persist()
    }

    fun signOut() {
        mode = null
        masterKey = null
        hakbun = null
        grants = Grants.NONE
        store.put(KEY_STATE, null)
    }

    private fun persist() {
        val json = JSONObject()
            .put("mode", mode?.name)
            .put("masterKey", masterKey)
            .put("hakbun", hakbun)
            .put("scopes", JSONArray(grants.scopes.toList()))
        store.put(KEY_STATE, json.toString())
    }

    private fun restore(json: JSONObject) {
        val m = json.optString("mode").takeIf { it.isNotBlank() }?.let { Mode.valueOf(it) } ?: return
        mode = m
        masterKey = json.optString("masterKey").takeIf { it.isNotBlank() && m == Mode.MASTER }
        hakbun = json.optString("hakbun").takeIf { it.isNotBlank() }
        val arr = json.optJSONArray("scopes") ?: JSONArray()
        grants = Grants(master = m == Mode.MASTER, scopes = (0 until arr.length()).map { arr.getString(it) }.toSet())
    }

    companion object {
        private const val KEY_STATE = "session"

        @Volatile private var instance: AdminSession? = null

        fun get(context: Context): AdminSession =
            instance ?: synchronized(this) {
                instance ?: AdminSession(SecureStore(context)).also { instance = it }
            }
    }
}
