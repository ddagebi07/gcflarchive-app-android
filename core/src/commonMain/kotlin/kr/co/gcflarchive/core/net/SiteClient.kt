package kr.co.gcflarchive.core.net

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kr.co.gcflarchive.core.Config

/** The site answered 401: the user has to log in first. */
class LoginRequiredException(message: String = "로그인하면 이용할 수 있어요.") : Exception(message)

class ApiException(val code: Int, message: String) : Exception(message)

/** A file to upload (bytes already read by the platform picker / share sheet). */
class UploadFile(val name: String, val bytes: ByteArray, val mimeType: String?)

/**
 * HTTP client for the website's JSON APIs with the persistent session cookie, shared
 * by every screen (and by every platform).
 */
class SiteClient(
    val cookies: SiteCookies = PersistentCookies(Settings()),
    val http: HttpClient = defaultHttpClient(cookies),
) {
    /** Status + body without throwing on HTTP errors (for APIs whose error bodies carry flags). */
    data class Response(val code: Int, val body: String) {
        val isSuccessful: Boolean get() = code in 200..299
        val json: JObject get() = parseObject(body)
        val error: String? get() = json.str("error")
    }

    suspend fun exchange(path: String, method: String = "GET", body: JsonObject? = null): Response {
        val res = http.request(Config.url(path)) {
            this.method = HttpMethod.parse(method)
            if (body != null || method != "GET") {
                contentType(ContentType.Application.Json)
                setBody((body ?: JsonObject(emptyMap())).toString())
            }
        }
        return Response(res.status.value, res.bodyAsText())
    }

    /** GET that throws [LoginRequiredException] on 401 and [ApiException] (server message) otherwise. */
    suspend fun get(path: String): String = checked(exchange(path))

    suspend fun postJson(path: String, body: JsonObject = JsonObject(emptyMap())): String = checked(exchange(path, "POST", body))

    suspend fun getJson(path: String): JObject = parseObject(get(path))
    suspend fun postForJson(path: String, body: JsonObject = JsonObject(emptyMap())): JObject = parseObject(postJson(path, body))

    /** Raw bytes (e.g. protected PDF page images). */
    suspend fun bytes(path: String): ByteArray {
        val res = http.get(Config.url(path))
        if (res.status.value == 401) throw LoginRequiredException()
        if (res.status.value !in 200..299) throw ApiException(res.status.value, "HTTP ${res.status.value}")
        return res.bodyAsBytes()
    }

    /** multipart/form-data POST with upload progress (0..1). */
    suspend fun upload(
        path: String,
        fieldName: String,
        files: List<UploadFile>,
        fields: Map<String, String> = emptyMap(),
        onProgress: (Float) -> Unit = {},
    ): Response {
        val res: HttpResponse = http.request(Config.url(path)) {
            method = HttpMethod.Post
            setBody(MultiPartFormDataContent(formData {
                fields.forEach { (k, v) -> append(k, v) }
                files.forEach { f ->
                    append(fieldName, f.bytes, Headers.build {
                        append(HttpHeaders.ContentType, f.mimeType ?: "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"${f.name.replace("\"", "")}\"")
                    })
                }
            }))
            onUpload { sent, total -> if (total != null && total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f)) }
        }
        return Response(res.status.value, res.bodyAsText())
    }

    private fun checked(res: Response): String {
        if (res.code == 401) throw LoginRequiredException(res.error ?: "로그인하면 이용할 수 있어요.")
        if (!res.isSuccessful) throw ApiException(res.code, res.error ?: "처리하지 못했어요. 잠시 후 다시 시도해주세요.")
        return res.body
    }

    companion object {
        fun defaultHttpClient(cookies: SiteCookies): HttpClient = HttpClient {
            install(HttpCookies) { storage = cookies }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 120_000
            }
            defaultRequest { header(HttpHeaders.UserAgent, Config.USER_AGENT) }
            expectSuccess = false
        }
    }
}
