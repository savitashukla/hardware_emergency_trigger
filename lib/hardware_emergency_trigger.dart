
import 'dart:async';

import 'package:flutter/services.dart';

import 'hardware_emergency_trigger_platform_interface.dart';

/// Public Dart API for the hardware_emergency_trigger plugin.
///
/// This class exposes a simple, callback‑based interface while delegating
/// platform work to the platform interface and native Android code.
class HardwareEmergencyTrigger {
  HardwareEmergencyTrigger._internal();

  static final HardwareEmergencyTrigger _instance =
      HardwareEmergencyTrigger._internal();

  /// Singleton instance.
  static HardwareEmergencyTrigger get instance => _instance;

  /// Underlying platform implementation.
  static HardwareEmergencyTriggerPlatform get _platform =>
      HardwareEmergencyTriggerPlatform.instance;

  /// Initialize the native side.
  ///
  /// This should typically be called once early in app startup.
  static Future<void> initialize({
    int debounceMs = 500,
    bool enableBackgroundLaunch = true,
    String? emergencyNumber,
    bool enableForegroundService = false,
    bool enableBootCompleted = false,
    Set<HardwareEmergencyKey> enabledKeys = const {
      HardwareEmergencyKey.volumeUp,
      HardwareEmergencyKey.volumeDown,
    },
  }) {
     return _platform.initialize(
      debounceMs: debounceMs,
      enableBackgroundLaunch: enableBackgroundLaunch,
      emergencyNumber: emergencyNumber,
      enableForegroundService: enableForegroundService,
      enableBootCompleted: enableBootCompleted,
      enabledKeys: enabledKeys,
    );
  }

  /// Listen to hardware trigger events.
  ///
  /// Returns a subscription that can be cancelled when no longer needed.
  static StreamSubscription<HardwareEmergencyEvent> listen({
    void Function(HardwareEmergencyEvent)? onEvent,
    VoidCallback? onSinglePress,
    VoidCallback? onDoublePress,
    VoidCallback? onTriplePress,
    VoidCallback? onEmergencyCall,
    VoidCallback? onAppLaunch,
  }) {
    return _platform.events.listen((event) {
      onEvent?.call(event);

      switch (event.type) {
        case HardwareEmergencyEventType.singlePress:
          onSinglePress?.call();
          break;
        case HardwareEmergencyEventType.doublePress:
          onDoublePress?.call();
          break;
        case HardwareEmergencyEventType.triplePress:
          onTriplePress?.call();
          break;
        case HardwareEmergencyEventType.emergencyCall:
          onEmergencyCall?.call();
          break;
        case HardwareEmergencyEventType.appLaunch:
          onAppLaunch?.call();
          break;
      }
    });
  }

  // ── Audio Recording ────────────────────────────────────────────────────

  /// Start audio recording via the device microphone.
  ///
  /// [outputDirectory] – optional absolute path to the directory where the
  /// audio file should be saved. Defaults to the app's external files /Music
  /// directory on Android.
  ///
  /// [fileName] – optional name for the audio file, without extension.
  /// Defaults to a timestamp-based name like `rec_20260404_120000`.
  ///
  /// Returns the absolute path of the output `.m4a` file, or `null` on error.
  ///
  /// **Note:** Make sure `RECORD_AUDIO` permission is granted before calling
  /// this method (e.g. via the `permission_handler` package).
  static Future<String?> startAudioRecording({
    String? outputDirectory,
    String? fileName,
  }) {
    return _platform.startAudioRecording(
      outputDirectory: outputDirectory,
      fileName: fileName,
    );
  }

  /// Stop the current audio recording.
  ///
  /// Returns the absolute path of the saved `.m4a` file, or `null` if no
  /// recording was in progress.
  static Future<String?> stopAudioRecording() {
    return _platform.stopAudioRecording();
  }
}

/// Keys that can be enabled for detection.
enum HardwareEmergencyKey {
  volumeUp,
  volumeDown,
}

/// Types of high‑level events emitted by the plugin.
enum HardwareEmergencyEventType {
  singlePress,
  doublePress,
  triplePress,
  emergencyCall,
  appLaunch,
}

/// Event data emitted from the native layer.
class HardwareEmergencyEvent {
  HardwareEmergencyEvent({
    required this.type,
    required this.key,
    required this.timestamp,
    required this.raw,
  });

  final HardwareEmergencyEventType type;
  final HardwareEmergencyKey key;
  final DateTime timestamp;
  final Map<String, dynamic> raw;

  factory HardwareEmergencyEvent.fromMap(Map<dynamic, dynamic> map) {
    final type = _eventTypeFromString(map['type'] as String?);
    final key = _keyFromString(map['key'] as String?);

    return HardwareEmergencyEvent(
      type: type,
      key: key,
      timestamp: DateTime.fromMillisecondsSinceEpoch(
        (map['timestamp'] as int?) ?? DateTime.now().millisecondsSinceEpoch,
      ),
      raw: Map<String, dynamic>.from(
        map.map((key, value) => MapEntry(key.toString(), value)),
      ),
    );
  }

  static HardwareEmergencyEventType _eventTypeFromString(String? value) {
    switch (value) {
      case 'single':
        return HardwareEmergencyEventType.singlePress;
      case 'double':
        return HardwareEmergencyEventType.doublePress;
      case 'triple':
        return HardwareEmergencyEventType.triplePress;
      case 'emergency_call':
        return HardwareEmergencyEventType.emergencyCall;
      case 'app_launch':
        return HardwareEmergencyEventType.appLaunch;
      default:
        return HardwareEmergencyEventType.singlePress;
    }
  }

  static HardwareEmergencyKey _keyFromString(String? value) {
    switch (value) {
      case 'volume_down':
        return HardwareEmergencyKey.volumeDown;
      case 'volume_up':
      default:
        return HardwareEmergencyKey.volumeUp;
    }
  }
}

