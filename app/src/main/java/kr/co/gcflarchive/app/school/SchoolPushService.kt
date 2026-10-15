package kr.co.gcflarchive.app.school

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kr.co.gcflarchive.student.school.SchoolActivity

/**
 * FCM (PUSH_SETUP.md). Works only when the build has app/google-services.json; otherwise
 * Firebase isn't initialised and SchoolNotifier's inbox check covers alerts instead.
 */
class SchoolPushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        register(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // In the foreground FCM doesn't show 'notification' messages on its own: show them here.
        val n = message.notification
        val data = message.data
        SchoolNotifier.post(
            applicationContext,
            id = (message.messageId ?: data.toString()).hashCode() and 0x0FFFFFFF,
            kind = data["kind"] ?: "",
            title = n?.title ?: data["title"] ?: return,
            body = n?.body ?: data["body"].orEmpty(),
            target = data["target"].orEmpty(),
        )
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun isAvailable(context: Context): Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

        /** Fetches the token at start-up (new installs, restored backups). */
        fun refreshToken(context: Context) {
            if (!isAvailable(context)) return
            runCatching {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> register(context.applicationContext, token) }
            }
        }

        private fun register(context: Context, token: String) {
            val services = SchoolActivity.services(context)
            services.prefs.pushToken = token
            scope.launch { runCatching { services.prefs.serverPushes = services.school.registerDevice(token, "android") } }
        }
    }
}
