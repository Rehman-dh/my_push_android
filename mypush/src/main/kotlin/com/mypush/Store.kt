package com.mypush

import android.content.Context
import java.util.UUID

/** Local persistence: the generated device id (subscription id) + SDK config. */
internal object Store {
    private const val PREFS = "my_push_prefs"
    private const val KEY_DEVICE = "my_push_device_id"
    private const val KEY_APP = "my_push_app_key"
    private const val KEY_BASE = "my_push_base_url"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun deviceId(ctx: Context): String {
        val p = prefs(ctx)
        var id = p.getString(KEY_DEVICE, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            p.edit().putString(KEY_DEVICE, id).apply()
        }
        return id
    }

    fun saveConfig(ctx: Context, appKey: String, baseUrl: String) {
        prefs(ctx).edit()
            .putString(KEY_APP, appKey)
            .putString(KEY_BASE, baseUrl)
            .apply()
    }

    fun appKey(ctx: Context): String? = prefs(ctx).getString(KEY_APP, null)
    fun baseUrl(ctx: Context): String? = prefs(ctx).getString(KEY_BASE, null)
}
