package com.mypush

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

/**
 * Receives FCM messages. Registered automatically via the library manifest.
 *
 * - `onNewToken` re-registers the device.
 * - `onMessageReceived` builds a rich notification. FCM only invokes this for
 *   data messages (any app state) or for notification messages in the foreground.
 *   The backend sends button-carrying pushes as **data-only**, so buttons render
 *   in the background here; plain notifications use the system tray as usual.
 */
class MyPushMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val ctx = applicationContext
        val api = MyPush.apiFor(ctx) ?: return
        val body = JSONObject()
            .put("device_id", MyPush.deviceIdFor(ctx))
            .put("push_token", token)
            .put("platform", "android")
        Thread {
            try {
                api.registerDevice(body)
            } catch (_: Exception) {
            }
        }.start()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = HashMap<String, String>(message.data)
        message.notification?.let { n ->
            n.title?.let { if (!data.containsKey("title")) data["title"] = it }
            n.body?.let { if (!data.containsKey("body")) data["body"] = it }
        }
        if (data["title"] == null && data["body"] == null) return
        NotificationFactory.show(applicationContext, data)
    }
}
