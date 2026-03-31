package com.example.hardware_emergency_trigger

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

private const val METHOD_CHANNEL_NAME = "hardware_emergency_trigger"
private const val EVENT_CHANNEL_NAME = "hardware_emergency_trigger/events"
private const val PREFS_NAME = "hardware_emergency_trigger_prefs"

/** HardwareEmergencyTriggerPlugin */
class HardwareEmergencyTriggerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler,
  EventChannel.StreamHandler {

  private lateinit var applicationContext: Context
  private lateinit var methodChannel: MethodChannel
  private lateinit var eventChannel: EventChannel

  private var eventSink: EventChannel.EventSink? = null

  companion object {
    @Volatile
    private var instance: HardwareEmergencyTriggerPlugin? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Called by the AccessibilityService to publish an event to Flutter.
     * Must run on the main thread — [onKeyEvent] runs on a binder thread and
     * [EventChannel.EventSink.success] is not safe off the UI thread.
     */
    fun dispatchEventFromNative(event: Map<String, Any>) {
      val sink = instance?.eventSink ?: return
      if (Looper.myLooper() == Looper.getMainLooper()) {
        sink.success(event)
      } else {
        mainHandler.post {
          instance?.eventSink?.success(event)
        }
      }
    }

    /** Expose the shared preferences name to other classes. */
    const val sharedPrefsName: String = PREFS_NAME
  }

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    applicationContext = binding.applicationContext
    instance = this

    methodChannel =
      MethodChannel(binding.binaryMessenger, METHOD_CHANNEL_NAME).also {
        it.setMethodCallHandler(this)
      }
    eventChannel =
      EventChannel(binding.binaryMessenger, EVENT_CHANNEL_NAME).also {
        it.setStreamHandler(this)
      }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    instance = null
    eventChannel.setStreamHandler(null)
    methodChannel.setMethodCallHandler(null)
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "initialize" -> {
        initialize(call, result)
      }

      else -> result.notImplemented()
    }
  }

  private fun initialize(call: MethodCall, result: MethodChannel.Result) {
    val debounceMs = (call.argument<Int>("debounceMs") ?: 500).coerceAtLeast(100)
    val enableBackgroundLaunch = call.argument<Boolean>("enableBackgroundLaunch") ?: true
    val emergencyNumber = call.argument<String>("emergencyNumber")
    val enableForegroundService = call.argument<Boolean>("enableForegroundService") ?: false
    val enableBootCompleted = call.argument<Boolean>("enableBootCompleted") ?: false
    val enabledKeys = call.argument<List<String>>("enabledKeys") ?: listOf(
      "volume_up",
      "volume_down",
    )

    val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
      .putInt("debounce_ms", debounceMs)
      .putBoolean("enable_background_launch", enableBackgroundLaunch)
      .putString("emergency_number", emergencyNumber)
      .putBoolean("enable_foreground_service", enableForegroundService)
      .putBoolean("enable_boot_completed", enableBootCompleted)
      .putStringSet("enabled_keys", enabledKeys.toSet())
      .apply()

    result.success(null)
  }

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    eventSink = events
  }

  override fun onCancel(arguments: Any?) {
    eventSink = null
  }
}

