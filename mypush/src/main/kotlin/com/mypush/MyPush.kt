package com.mypush

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Self-hosted push SDK — a simple OneSignal-like facade (native Android twin of
 * the `my_push` Flutter package).
 *
 * ```kotlin
 * MyPush.initialize(context, appKey = "pub_...", apiBaseUrl = "https://...")
 * MyPush.requestPermission(activity)          // Android 13+
 * MyPush.onNotificationClick { data ->
 *   // data["action_id"] is present when an action button was tapped
 * }
 * ```
 */
object MyPush {

    const val NOTIFICATION_PERMISSION_REQUEST = 9001

    /**
     * Your self-hosted dashboard's base URL, baked into the SDK so host apps only
     * need to pass the App Key (like OneSignal only needs an App ID). Set this once
     * to your deployment; apps can still override it per-call via `apiBaseUrl`.
     */
    @JvmStatic
    var defaultApiBaseUrl: String = "https://my-push-backend.vercel.app"

    private var appContext: Context? = null
    private var api: ApiClient? = null
    private var deviceId: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    internal var clickHandler: ((Map<String, String>) -> Unit)? = null

    val subscriptionId: String? get() = deviceId

    /**
     * Register the device and wire notifications.
     *
     * @param autoInitializeFirebase when true, fetch Firebase options from the
     *   backend and call [FirebaseApp.initializeApp] at runtime (no
     *   google-services.json needed). For the most reliable delivery when the app
     *   process is killed, prefer adding google-services.json + the
     *   google-services Gradle plugin and leave this false.
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        context: Context,
        appKey: String,
        apiBaseUrl: String = defaultApiBaseUrl,
        autoInitializeFirebase: Boolean = false,
    ) {
        val ctx = context.applicationContext
        appContext = ctx
        require(apiBaseUrl.isNotBlank() && !apiBaseUrl.contains("YOUR-DASHBOARD")) {
            "MyPush: set MyPush.defaultApiBaseUrl to your dashboard URL, or pass apiBaseUrl."
        }
        api = ApiClient(apiBaseUrl, appKey)
        deviceId = Store.deviceId(ctx)
        Store.saveConfig(ctx, appKey, apiBaseUrl)

        scope.launch {
            if (autoInitializeFirebase && FirebaseApp.getApps(ctx).isEmpty()) {
                initFirebaseFromBackend(ctx)
            }
            registerDevice(ctx)
        }
    }

    private fun initFirebaseFromBackend(ctx: Context) {
        val cfg = api?.getConfig() ?: return
        val a = cfg.optJSONObject("android") ?: return
        val options = FirebaseOptions.Builder()
            .setApiKey(a.getString("apiKey"))
            .setApplicationId(a.getString("appId"))
            .setGcmSenderId(a.getString("messagingSenderId"))
            .setProjectId(a.getString("projectId"))
            .apply {
                a.optString("storageBucket").takeIf { it.isNotEmpty() }?.let { setStorageBucket(it) }
            }
            .build()
        FirebaseApp.initializeApp(ctx, options)
    }

    private fun registerDevice(ctx: Context) {
        try {
            val token = Tasks.await(FirebaseMessaging.getInstance().token)
            val body = JSONObject()
                .put("device_id", deviceId)
                .put("push_token", token)
                .put("platform", "android")
            api?.registerDevice(body)
        } catch (e: Exception) {
            // token not available yet — onNewToken() will register later.
        }
    }

    /** Request the POST_NOTIFICATIONS runtime permission (Android 13+). */
    @JvmStatic
    fun requestPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                activity, "android.permission.POST_NOTIFICATIONS"
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf("android.permission.POST_NOTIFICATIONS"),
                    NOTIFICATION_PERMISSION_REQUEST,
                )
            }
        }
    }

    @JvmStatic
    fun login(externalId: String) {
        val id = deviceId ?: return
        scope.launch { api?.setExternalUserId(id, externalId) }
    }

    @JvmStatic
    fun logout() {
        val id = deviceId ?: return
        scope.launch { api?.setExternalUserId(id, null) }
    }

    @JvmStatic
    fun setTag(key: String, value: String) = setTags(mapOf(key to value))

    @JvmStatic
    fun setTags(tags: Map<String, String>) {
        val id = deviceId ?: return
        scope.launch { api?.updateTags(id, tags, null) }
    }

    @JvmStatic
    fun deleteTag(key: String) {
        val id = deviceId ?: return
        scope.launch { api?.updateTags(id, null, listOf(key)) }
    }

    /**
     * Called when a notification (or one of its action buttons) is tapped.
     * `data["action_id"]` is present for button taps. On a cold start the same
     * values are also delivered as `mypush_*` intent extras on the launcher Activity.
     */
    @JvmStatic
    fun onNotificationClick(handler: (Map<String, String>) -> Unit) {
        clickHandler = handler
    }

    // ── internals used by the service / receiver (may run in a fresh process) ──

    internal fun apiFor(ctx: Context): ApiClient? {
        api?.let { return it }
        val key = Store.appKey(ctx) ?: return null
        val base = Store.baseUrl(ctx) ?: return null
        return ApiClient(base, key).also { api = it }
    }

    internal fun deviceIdFor(ctx: Context): String =
        deviceId ?: Store.deviceId(ctx).also { deviceId = it }
}
