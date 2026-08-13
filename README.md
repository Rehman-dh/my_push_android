# MyPush.Android — native Android Push SDK (Kotlin)

Self-hosted push notifications via FCM — the native Android twin of the `my_push`
Flutter package. Same backend, same App Key. Use it across multiple apps.

- Min SDK **23**, Kotlin, distributed via **JitPack**.
- Rich notifications (big picture, large icon, accent color) + **action buttons**
  (Android needs no NSE — everything renders natively).

## Install

**1) Add JitPack** in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**2) Add the dependency** in your app module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Rehman-dh:MyPush.Android:0.1.0") // or a commit hash
}
```

**3) Firebase.** Two options:

- **Recommended (most reliable):** add `google-services.json` to your app and the
  `com.google.gms.google-services` Gradle plugin — standard Firebase setup. Then
  leave `autoInitializeFirebase = false` (default).
- **Zero-config (like OneSignal):** skip `google-services.json` and pass
  `autoInitializeFirebase = true`; the SDK fetches Firebase options from the
  backend (`GET /api/config`, set via the dashboard) and initializes Firebase at
  runtime. Add the iOS/Android config in the dashboard first.

## Usage

Set your dashboard URL once (e.g. in `App.onCreate`, before `initialize`):

```kotlin
MyPush.defaultApiBaseUrl = "https://my-push-backend.vercel.app"
```

…or edit the default in `MyPush.kt`. After that, apps pass only the **App Key**
(like OneSignal only needs an App ID):

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MyPush.initialize(
            context = this,
            appKey = "pub_xxxxxxxx",                 // App Key from the dashboard
            // autoInitializeFirebase = true,        // only for zero-config
        )
        MyPush.onNotificationClick { data ->
            // data e.g. {"screen":"order","order_id":"A-100","notification_id":"..."}
            // data["action_id"] is set when an action button was tapped.
        }
    }
}
```

Request the notifications permission (Android 13+) from an Activity:

```kotlin
MyPush.requestPermission(this)   // no-op below Android 13
```

User identity & tags:

```kotlin
MyPush.login("4821")             // external_user_id
MyPush.logout()
MyPush.setTag("city", "lahore")
MyPush.setTags(mapOf("plan" to "premium", "city" to "lahore"))
MyPush.deleteTag("city")
```

**Cold-start routing:** when the app is launched by a notification tap, the push
`data` is delivered as `mypush_*` intent extras on your launcher Activity:

```kotlin
val screen = intent.getStringExtra("mypush_screen")
val actionId = intent.getStringExtra("mypush_action_id")
```

## Behaviour

- **Device id**: a locally generated UUID (`SharedPreferences`) — your subscription id.
- **Registration**: the FCM token is sent to the backend on init and re-sent on refresh (`onNewToken`).
- **Rendering** (`NotificationFactory`): big-picture image, large icon, accent color, and action buttons are built with `NotificationCompat`. Plain notifications in the background use the system tray; button-carrying (data-only) pushes are built by `MyPushMessagingService` in any state.
- **Clicks**: taps + action-button taps report to `POST /api/events` and invoke `onNotificationClick`.

## Building / publishing this library

This repo ships without a committed Gradle wrapper. Open it once in **Android
Studio** (it generates `gradlew` + the wrapper), or run `gradle wrapper`, then
commit. Push to GitHub as `MyPush.Android` and JitPack builds each tagged release
as `com.github.Rehman-dh:MyPush.Android:<tag>`.
