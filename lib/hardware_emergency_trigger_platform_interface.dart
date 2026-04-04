import 'dart:async';

import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'hardware_emergency_trigger.dart';
import 'hardware_emergency_trigger_method_channel.dart';

abstract class HardwareEmergencyTriggerPlatform extends PlatformInterface {
  HardwareEmergencyTriggerPlatform() : super(token: _token);

  static final Object _token = Object();

  static HardwareEmergencyTriggerPlatform _instance =
      MethodChannelHardwareEmergencyTrigger();

  /// Default instance of the platform implementation.
  static HardwareEmergencyTriggerPlatform get instance => _instance;

  static set instance(HardwareEmergencyTriggerPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Initialize native configuration.
  Future<void> initialize({
    required int debounceMs,
    required bool enableBackgroundLaunch,
    required String? emergencyNumber,
    required bool enableForegroundService,
    required bool enableBootCompleted,
    required Set<HardwareEmergencyKey> enabledKeys,
  }) {
    throw UnimplementedError('initialize() has not been implemented.');
  }

  /// Stream of high‑level events from the native layer.
  Stream<HardwareEmergencyEvent> get events {
    throw UnimplementedError('events has not been implemented.');
  }

  /// Start audio recording.
  ///
  /// [outputDirectory] – optional absolute path to save the file.
  /// [fileName] – optional file name without extension.
  ///
  /// Returns the absolute path of the output file.
  Future<String?> startAudioRecording({
    String? outputDirectory,
    String? fileName,
  }) {
    throw UnimplementedError('startAudioRecording() has not been implemented.');
  }

  /// Stop the current audio recording.
  ///
  /// Returns the absolute path of the saved file.
  Future<String?> stopAudioRecording() {
    throw UnimplementedError('stopAudioRecording() has not been implemented.');
  }
}

