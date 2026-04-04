import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'hardware_emergency_trigger.dart';
import 'hardware_emergency_trigger_platform_interface.dart';

/// Method‑channel based implementation of the plugin.
class MethodChannelHardwareEmergencyTrigger
    extends HardwareEmergencyTriggerPlatform {
  @visibleForTesting
  static const MethodChannel methodChannel =
      MethodChannel('hardware_emergency_trigger');

  @visibleForTesting
  static const EventChannel eventChannel =
      EventChannel('hardware_emergency_trigger/events');

  Stream<HardwareEmergencyEvent>? _cachedEvents;

  @override
  Future<void> initialize({
    required int debounceMs,
    required bool enableBackgroundLaunch,
    required String? emergencyNumber,
    required bool enableForegroundService,
    required bool enableBootCompleted,
    required Set<HardwareEmergencyKey> enabledKeys,
  }) {
    return methodChannel.invokeMethod<void>('initialize', {
      'debounceMs': debounceMs,
      'enableBackgroundLaunch': enableBackgroundLaunch,
      'emergencyNumber': emergencyNumber,
      'enableForegroundService': enableForegroundService,
      'enableBootCompleted': enableBootCompleted,
      'enabledKeys': enabledKeys
          .map((e) => e == HardwareEmergencyKey.volumeUp
              ? 'volume_up'
              : 'volume_down')
          .toList(),
    });
  }

  @override
  Stream<HardwareEmergencyEvent> get events {
    _cachedEvents ??= eventChannel
        .receiveBroadcastStream()
        .map((dynamic event) =>
            HardwareEmergencyEvent.fromMap(event as Map<dynamic, dynamic>))
        .asBroadcastStream();
    return _cachedEvents!;
  }

  @override
  Future<String?> startAudioRecording({
    String? outputDirectory,
    String? fileName,
  }) {
    return methodChannel.invokeMethod<String>(
      'startAudioRecording',
      {
        if (outputDirectory != null) 'outputDirectory': outputDirectory,
        if (fileName != null) 'fileName': fileName,
      },
    );
  }

  @override
  Future<String?> stopAudioRecording() {
    return methodChannel.invokeMethod<String>('stopAudioRecording');
  }
}

