package com.example.hardware_emergency_trigger

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

  // ── Audio recording ──────────────────────────────────────────────────────
  private var mediaRecorder: MediaRecorder? = null
  private var currentRecordingPath: String? = null

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
    // Release any active recording so we don't leak the MediaRecorder.
    try {
      mediaRecorder?.release()
    } catch (_: Exception) {}
    mediaRecorder = null
    currentRecordingPath = null
    eventChannel.setStreamHandler(null)
    methodChannel.setMethodCallHandler(null)
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "initialize" -> {
        initialize(call, result)
      }

      "startAudioRecording" -> {
        startAudioRecording(call, result)
      }

      "stopAudioRecording" -> {
        stopAudioRecording(result)
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

  // ── Audio Recording ──────────────────────────────────────────────────────

  /**
   * Starts audio recording and saves the file to external/cache storage.
   *
   * Arguments (optional):
   *   - outputDirectory: String – absolute path to save the file; defaults to app's external files dir.
   *   - fileName: String       – custom file name (without extension); defaults to timestamp.
   */
  @Suppress("DEPRECATION")
  private fun startAudioRecording(call: MethodCall, result: MethodChannel.Result) {
    if (mediaRecorder != null) {
      result.error("ALREADY_RECORDING", "Audio recording is already in progress.", null)
      return
    }

    try {
      val outputDir = call.argument<String>("outputDirectory")
        ?.let { File(it) }
        ?: applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        ?: applicationContext.cacheDir

      if (!outputDir.exists()) outputDir.mkdirs()

      val fileName = call.argument<String>("fileName")
        ?: "rec_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"

      val outputFile = File(outputDir, "$fileName.m4a")
      currentRecordingPath = outputFile.absolutePath

      val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(applicationContext)
      } else {
        MediaRecorder()
      }

      recorder.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(128_000)
        setAudioSamplingRate(44_100)
        setOutputFile(outputFile.absolutePath)
        prepare()
        start()
      }

      mediaRecorder = recorder
      Log.d("AudioRecorder", "Recording started: ${outputFile.absolutePath}")
      result.success(outputFile.absolutePath)
    } catch (e: Exception) {
      Log.e("AudioRecorder", "startAudioRecording failed", e)
      mediaRecorder = null
      currentRecordingPath = null
      result.error("START_FAILED", e.message, null)
    }
  }

  /**
   * Stops the current audio recording.
   * Returns the path of the saved file.
   */
  private fun stopAudioRecording(result: MethodChannel.Result) {
    val recorder = mediaRecorder
    if (recorder == null) {
      result.error("NOT_RECORDING", "No recording is in progress.", null)
      return
    }

    try {
      recorder.stop()
      recorder.release()
      mediaRecorder = null
      val path = currentRecordingPath
      currentRecordingPath = null
      Log.d("AudioRecorder", "Recording stopped: $path")
      result.success(path)
    } catch (e: Exception) {
      Log.e("AudioRecorder", "stopAudioRecording failed", e)
      mediaRecorder = null
      currentRecordingPath = null
      result.error("STOP_FAILED", e.message, null)
    }
  }

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    eventSink = events
  }

  override fun onCancel(arguments: Any?) {
    eventSink = null
  }
}

