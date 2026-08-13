package com.mypush

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * Handles notification taps and action-button taps: reports the click, dismisses
 * the notification, invokes the app's callback (if the process is alive), and
 * opens the app — passing the push `data` as `mypush_*` intent extras for
 * cold-start routing.
 */
class MyPushActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext

        val data = HashMap<String, String>()
        intent.extras?.keySet()?.forEach { key ->
            if (key.startsWith("d_")) intent.getStringExtra(key)?.let { data[key.substring(2)] = it }
        }
        intent.getStringExtra("action_id")?.let { data["action_id"] = it }

        val notificationId = intent.getIntExtra("notification_id_int", -1)
        if (notificationId != -1) NotificationManagerCompat.from(ctx).cancel(notificationId)

        data["notification_id"]?.let { nid ->
            val api = MyPush.apiFor(ctx)
            val deviceId = MyPush.deviceIdFor(ctx)
            if (api != null) {
                Thread {
                    try {
                        api.reportClick(nid, deviceId)
                    } catch (_: Exception) {
                    }
                }.start()
            }
        }

        MyPush.clickHandler?.invoke(data)

        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            for ((k, v) in data) launch.putExtra("mypush_$k", v)
            ctx.startActivity(launch)
        }
    }
}
