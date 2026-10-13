package kr.co.gcflarchive.student.ui

import kotlinx.coroutines.CancellationException
import kr.co.gcflarchive.core.net.ApiException
import kr.co.gcflarchive.core.net.LoginRequiredException
import kr.co.gcflarchive.student.AppState
import kr.co.gcflarchive.student.platform.saveFile

/**
 * Downloads [url] with the app session (cookies) and hands it to the platform:
 * 다운로드 폴더 on Android, the share sheet ("파일에 저장") on iPhone/iPad.
 */
suspend fun AppState.download(url: String, fileName: String) {
    toast("‘$fileName’ 내려받는 중…")
    try {
        val bytes = services.site.bytes(url)
        saveFile(fileName, bytes, mimeTypeOf(fileName))
    } catch (e: CancellationException) {
        throw e
    } catch (e: LoginRequiredException) {
        toast("로그인 후 내려받을 수 있습니다.")
        requireLogin()
    } catch (e: ApiException) {
        toast(e.message ?: "내려받지 못했습니다.")
    } catch (e: Exception) {
        toast("내려받지 못했습니다. 네트워크를 확인해 주세요.")
    }
}

/** User-facing message for a failed request. */
fun Throwable.userMessage(fallback: String = "네트워크 오류가 발생했습니다."): String = when (this) {
    is LoginRequiredException, is ApiException -> message ?: fallback
    else -> fallback
}

fun mimeTypeOf(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    "hwp" -> "application/x-hwp"
    "hwpx" -> "application/hwp+zip"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "ppt" -> "application/vnd.ms-powerpoint"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "xls" -> "application/vnd.ms-excel"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "txt" -> "text/plain"
    "zip" -> "application/zip"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    else -> null
}
