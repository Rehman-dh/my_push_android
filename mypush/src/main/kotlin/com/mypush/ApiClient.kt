package com.mypush

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Thin HTTP client for the push backend. Sends the public App Key on every request. */
internal class ApiClient(private val baseUrl: String, private val appKey: String) {

    private val http = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun builder(path: String) =
        Request.Builder().url("$baseUrl$path").header("X-App-Key", appKey)

    /** GET /api/config — Firebase client options for zero-config init. */
    fun getConfig(): JSONObject? = try {
        http.newCall(builder("/api/config").get().build()).execute().use { r ->
            if (!r.isSuccessful) null else JSONObject(r.body?.string() ?: return null)
        }
    } catch (e: Exception) {
        null
    }

    fun registerDevice(body: JSONObject) = send(builder("/api/devices").post(body.body()).build())

    fun setExternalUserId(deviceId: String, externalId: String?) {
        val b = JSONObject().put("external_user_id", externalId ?: JSONObject.NULL)
        send(builder("/api/devices/$deviceId").patch(b.body()).build())
    }

    fun updateTags(deviceId: String, set: Map<String, String>?, delete: List<String>?) {
        val b = JSONObject()
        if (set != null) b.put("set", JSONObject(set as Map<*, *>))
        if (delete != null) b.put("delete", JSONArray(delete))
        send(builder("/api/devices/$deviceId/tags").patch(b.body()).build())
    }

    fun reportClick(notificationId: String, deviceId: String) {
        val b = JSONObject()
            .put("notification_id", notificationId)
            .put("device_id", deviceId)
            .put("type", "clicked")
        send(builder("/api/events").post(b.body()).build())
    }

    private fun JSONObject.body() = toString().toRequestBody(jsonType)

    private fun send(request: Request) {
        try {
            http.newCall(request).execute().use { /* fire-and-forget */ }
        } catch (e: Exception) {
            // Non-fatal: never crash the host app on a network error.
        }
    }
}
