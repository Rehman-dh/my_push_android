package com.mypush

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import java.net.URL

/** Builds a rich [NotificationCompat] from a push `data` map (image, large icon,
 *  accent color, action buttons) and shows it. */
internal object NotificationFactory {

    private const val CHANNEL_ID = "my_push_default"
    private const val CHANNEL_NAME = "Notifications"

    fun show(ctx: Context, data: Map<String, String>) {
        ensureChannel(ctx)
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle(data["title"])
            .setContentText(data["body"])
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(clickIntent(ctx, data, null, notificationId))

        data["accent_color"]?.let { hex -> parseColor(hex)?.let { builder.color = it } }

        val largeIcon = data["large_icon"]?.let { downloadBitmap(it) }
        if (largeIcon != null) builder.setLargeIcon(largeIcon)

        val bigPicture = data["image"]?.let { downloadBitmap(it) }
        if (bigPicture != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bigPicture))
        } else {
            data["body"]?.let { builder.setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
        }

        data["buttons"]?.let { raw ->
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    val id = b.optString("id")
                    val text = b.optString("text")
                    if (id.isEmpty() || text.isEmpty()) continue
                    builder.addAction(0, text, clickIntent(ctx, data, id, notificationId))
                }
            } catch (_: Exception) {
            }
        }

        try {
            NotificationManagerCompat.from(ctx).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — ignore.
        }
    }

    private fun clickIntent(
        ctx: Context,
        data: Map<String, String>,
        actionId: String?,
        notificationId: Int,
    ): PendingIntent {
        val intent = Intent(ctx, MyPushActionReceiver::class.java).apply {
            action = "com.mypush.CLICK.$notificationId.${actionId ?: "body"}"
            for ((k, v) in data) putExtra("d_$k", v)
            actionId?.let { putExtra("action_id", it) }
            putExtra("notification_id_int", notificationId)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val requestCode = "$notificationId.${actionId ?: "body"}".hashCode()
        return PendingIntent.getBroadcast(ctx, requestCode, intent, flags)
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }

    private fun parseColor(hex: String): Int? = try {
        Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
    } catch (_: Exception) {
        null
    }

    private fun downloadBitmap(url: String): Bitmap? = try {
        URL(url).openStream().use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }
}
