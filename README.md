# hardware_emergency_trigger

Source: [github.com/savitashukla/hardware_emergency_trigger](https://github.com/savitashukla/hardware_emergency_trigger)

**Android-only** Flutter plugin that listens to **hardware volume keys globally** using an `AccessibilityService`. It detects **single, double, triple, and long-press** patterns, can **bring the app to the foreground** from the background, and can trigger an **emergency call or dialer** on triple press.

On **iOS and other platforms** this plugin does nothing (no-op).

---

## Why this plugin?

- Normal Flutter apps **cannot** read volume keys when the app is in the background or killed.
- Android exposes global key events only through an **`AccessibilityService`** (user must enable it in system settings).
- Configuration (debounce, emergency number, etc.) is stored in **`SharedPreferences`** on the native side so the service can still **dial or launch the app** even when the Flutter engine is not running.

---

## How it works (simple)

| Piece | Role |
|--------|------|
| **`initialize()`** (Dart) | Runs once at startup; sends settings to Android via **MethodChannel** and saves them in **SharedPreferences**. |
| **`VolumeAccessibilityService`** (Android) | Listens for volume keys; detects patterns; reads prefs for number and options. |
| **Triple press** | Native code starts **call** or **dialer** intent — **no Flutter required** for the call itself. |
| **`listen()`** (Dart) | While your app process is alive and the engine is attached, events arrive via **EventChannel** so you can update the UI. |

So: **native Android does the heavy work**; **Flutter is for UI and configuration** when the app is running.

---

## Features

- Global volume key detection (foreground, background, screen on; behaviour varies by OEM)
- Single / double / triple press with configurable **debounce**
- Long press (best-effort; OEM-dependent)
- Optional **volume up / down** filtering
- Optional **launch launcher activity** from background (Android 14+ uses `PendingIntent` with background activity options)
- Optional **emergency call** (`ACTION_CALL`) or **dialer** (`ACTION_DIAL`) on triple press
- `CALL_PHONE`, foreground service, and boot receiver permissions declared for future use / host app configuration

---

## Installation

Add to `pubspec.yaml`:

```yaml
dependencies:
  hardware_emergency_trigger: ^0.0.1
```

```bash
flutter pub get
```

Only **Android** is implemented. No extra setup is required for iOS.

---

## Android setup

### 1. User must enable accessibility

**Settings → Accessibility → [Your app]** → turn the service **On**.

Without this, the plugin receives **no** volume events.

### 2. Initialize early

Call `HardwareEmergencyTrigger.initialize` **before** `runApp` (or at least before you rely on listeners), so native prefs are written:

```dart
import 'package:hardware_emergency_trigger/hardware_emergency_trigger.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await HardwareEmergencyTrigger.initialize(
    debounceMs: 500,
    enableBackgroundLaunch: true,
    emergencyNumber: '112', // optional; triple press uses this from native prefs
    enableForegroundService: false,
    enableBootCompleted: false,
  );

  runApp(const MyApp());
}
```

### 3. Optional: `CALL_PHONE` for direct calls

For triple press, the plugin tries `ACTION_CALL` if `CALL_PHONE` is granted; otherwise it falls back to `ACTION_DIAL`. Request runtime permission in your app when you need direct outgoing calls.

### 4. Manifest merge

The plugin ships its own `AndroidManifest.xml` entries (service, permissions, accessibility XML). If you use manifest placeholders or `tools:node="remove"`, ensure you do **not** strip the accessibility service.

---

## Usage

```dart
StreamSubscription<HardwareEmergencyEvent>? sub;

@override
void initState() {
  super.initState();
  sub = HardwareEmergencyTrigger.listen(
    onEvent: (event) {
      // event.type, event.key, event.timestamp, event.raw
    },
    onSinglePress: () {},
    onDoublePress: () {},
    onTriplePress: () {},
    onEmergencyCall: () {},
    onAppLaunch: () {},
  );
}

@override
void dispose() {
  sub?.cancel();
  super.dispose();
}
```

---

## Behaviour summary

| Pattern | Native (always if service enabled) | Flutter events |
|--------|----------------------------------|----------------|
| Single | May try to open app if `enableBackgroundLaunch` | When engine is running |
| Double | May launch app | When engine is running |
| Triple | Calls `attemptEmergencyCall` (call or dial) | When engine is running |
| Long press | Emits event | When engine is running |

If the process is **killed**, triple press can still dial **if** the accessibility service is still running; **Flutter callbacks** only work after the app starts again.

---

## Troubleshooting

- **No Flutter events** — Enable accessibility; call `initialize` before listening; on some devices hot-restart may require toggling the service off/on.
- **Launch / dial fails** — Android 10+ background restrictions; grant `CALL_PHONE` if you need direct call; check OEM battery/launch settings.
- **Play Store** — Accessibility + global keys are sensitive; declare a clear use case (e.g. safety) and follow current [Google Play policies](https://play.google.com/about/developer-content-policy/).

---

## Contributing

Issues and PRs are welcome. Please test on **real devices**; emulators often behave differently for volume and accessibility.

---

## License

See [LICENSE](LICENSE) in the package repository.
