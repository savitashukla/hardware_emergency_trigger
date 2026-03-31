package com.example.hardware_emergency_trigger

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService that globally listens for hardware volume key events
 * and detects single, double, triple and long press patterns.
 *
 * This works even when the host app is in background or killed, as long as
 * the service remains enabled by the user.
 */
class VolumeAccessibilityService : AccessibilityService() {

  private val handler = Handler(Looper.getMainLooper())
  private val keySequence: MutableList<KeyStroke> = mutableListOf()
  private var lastDownEventTime: Long = 0L
  private var longPressPosted = false

  private val prefs: SharedPreferences by lazy {
    getSharedPreferences(HardwareEmergencyTriggerPlugin.sharedPrefsName, Context.MODE_PRIVATE)
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    Log.d("VolumeService", "onServiceConnected")
    val info = serviceInfo ?: AccessibilityServiceInfo()
    info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
    info.flags =
      AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
    info.notificationTimeout = 0
    serviceInfo = info
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // Not used; we rely on onKeyEvent.
  }

  override fun onInterrupt() {
    // No‑op.
  }

  override fun onKeyEvent(event: KeyEvent): Boolean {
    Log.d("VolumeService", "onKeyEvent: action=${event.action} code=${event.keyCode}")

    val key = when (event.keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP -> "volume_up"
      KeyEvent.KEYCODE_VOLUME_DOWN -> "volume_down"
      else -> return false
    }

    val enabledKeys = prefs.getStringSet("enabled_keys", setOf("volume_up", "volume_down"))
    if (enabledKeys != null && !enabledKeys.contains(key)) {
      Log.d("VolumeService", "Key $key not enabled in prefs")
      return false
    }

    val debounceMs = prefs.getInt("debounce_ms", 500).coerceAtLeast(100)

    when (event.action) {
      KeyEvent.ACTION_DOWN -> {
        Log.d("VolumeService", "ACTION_DOWN for $key")
        lastDownEventTime = event.eventTime
        longPressPosted = true
        handler.postDelayed(
          {
            // lastDownEventTime and event.eventTime use uptime (since boot), not wall clock.
            if (longPressPosted && SystemClock.uptimeMillis() - lastDownEventTime >= debounceMs * 2) {
              handlePattern("long", key)
              clearSequence()
            }
          },
          (debounceMs * 2).toLong(),
        )
        keySequence.add(
          KeyStroke(
            key = key,
            timestamp = event.eventTime,
          ),
        )
        scheduleEvaluation(debounceMs)
      }

      KeyEvent.ACTION_UP -> {
        Log.d("VolumeService", "ACTION_UP for $key")
        longPressPosted = false
      }
    }

    // We do not consume the event so normal volume behaviour still works.
    return false
  }

  private fun scheduleEvaluation(debounceMs: Int) {
    handler.removeCallbacksAndMessages(null)
    handler.postDelayed(
      {
        evaluatePattern(debounceMs)
      },
      debounceMs.toLong(),
    )
  }

  private fun evaluatePattern(debounceMs: Int) {
    if (keySequence.isEmpty()) return

    // KeyStroke.timestamp is KeyEvent.eventTime = uptime millis; must not mix with currentTimeMillis().
    val now = SystemClock.uptimeMillis()
    val filtered = keySequence.filter { now - it.timestamp <= debounceMs * 3 }
    keySequence.clear()
    keySequence.addAll(filtered)

    val presses = keySequence.size
    if (presses == 0) return

    val key = keySequence.last().key
    val pattern = when {
      presses >= 3 -> "triple"
      presses == 2 -> "double"
      else -> "single"
    }

    handlePattern(pattern, key)
    clearSequence()
  }

  private fun handlePattern(pattern: String, key: String) {
    val enableBackgroundLaunch = prefs.getBoolean("enable_background_launch", true)
    val emergencyNumber = prefs.getString("emergency_number", null)

    when (pattern) {
      "single" -> {
        if (enableBackgroundLaunch) {
          launchHostApp()
        }
        sendFlutterEvent(
          mapOf(
            "type" to "single",
            "key" to key,
            "timestamp" to System.currentTimeMillis(),
          ),
        )
      }

      "double" -> {
        if (enableBackgroundLaunch) {
          launchHostApp()
        }
        sendFlutterEvent(
          mapOf(
            "type" to "double",
            "key" to key,
            "timestamp" to System.currentTimeMillis(),
            "app_launch" to true,
          ),
        )
      }

      "triple" -> {
        if (!emergencyNumber.isNullOrBlank()) {
          attemptEmergencyCall(emergencyNumber)
        }
        sendFlutterEvent(
          mapOf(
            "type" to "triple",
            "key" to key,
            "timestamp" to System.currentTimeMillis(),
            "emergency_call" to true,
          ),
        )
      }

      "long" -> {
        sendFlutterEvent(
          mapOf(
            "type" to "single",
            "key" to key,
            "timestamp" to System.currentTimeMillis(),
            "long_press" to true,
          ),
        )
      }
    }
  }

  private fun attemptEmergencyCall(number: String) {
    // Prefer ACTION_CALL when permission is granted, otherwise fall back to ACTION_DIAL.
    val callIntent = Intent(Intent.ACTION_CALL).apply {
      data = Uri.parse("tel:$number")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val canCall = checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
      android.content.pm.PackageManager.PERMISSION_GRANTED

    if (canCall) {
      try {
        startActivity(callIntent)
        return
      } catch (_: Exception) {
        // Ignore and fall back to dialer.
      }
    }

    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
      data = Uri.parse("tel:$number")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
      startActivity(dialIntent)
    } catch (_: Exception) {
      // No dialer available; nothing more we can do.
    }
  }

  private fun launchHostApp() {
    val hostPackage = applicationContext.packageName
    val pm = applicationContext.packageManager
    val launchIntent = pm.getLaunchIntentForPackage(hostPackage) ?: run {
      Log.w("VolumeService", "getLaunchIntentForPackage null for $hostPackage")
      return
    }
    launchIntent.addFlags(
      Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
        Intent.FLAG_ACTIVITY_CLEAR_TOP,
    )
    try {
      // Android 14+: BAL mode must be passed to PendingIntent.send(), not getActivity()
      // ("pendingIntentBackgroundActivityStartMode must not be set when creating a PendingIntent").
      if (Build.VERSION.SDK_INT >= 34) {
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(
          applicationContext,
          0,
          launchIntent,
          piFlags,
          null,
        )
        val opts = ActivityOptions.makeBasic().apply {
          setPendingIntentBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
          )
        }
        pi.send(
          applicationContext,
          0,
          null,
          null,
          null,
          null,
          opts.toBundle(),
        )
      } else {
        applicationContext.startActivity(launchIntent)
      }
    } catch (e: Exception) {
      Log.e("VolumeService", "launchHostApp failed", e)
    }
  }

  private fun sendFlutterEvent(event: Map<String, Any>) {
    HardwareEmergencyTriggerPlugin.dispatchEventFromNative(event)
  }

  private fun clearSequence() {
    keySequence.clear()
    longPressPosted = false
  }

  private data class KeyStroke(
    val key: String,
    val timestamp: Long,
  )
}

