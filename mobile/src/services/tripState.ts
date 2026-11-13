import { TrackingMode, TripStatus } from '../types';

export interface TripReadiness {
  gpsReady: boolean;
  gpsAccuracyM?: number;
  bluetoothConnected: boolean;
  obdTelemetryAvailable: boolean;
  engineRunning: boolean | null;
}

export function canStartTrip(readiness: TripReadiness): {
  enabled: boolean; mode?: TrackingMode; reason?: string;
} {
  if (!readiness.gpsReady) return {enabled:false, reason:'Waiting for an accurate GPS fix'};
  if (readiness.bluetoothConnected && readiness.obdTelemetryAvailable &&
      readiness.engineRunning === true) {
    return {enabled:true, mode:'bluetooth_obd'};
  }
  return {enabled:true, mode:'gps_only'};
}

export function nextTripStatus(current: TripStatus, action: 'START'|'PAUSE'|'RESUME'|'STOP'): TripStatus {
  if (action === 'START' && current === 'idle') return 'recording';
  if (action === 'PAUSE' && current === 'recording') return 'paused';
  if (action === 'RESUME' && current === 'paused') return 'recording';
  if (action === 'STOP' && (current === 'recording' || current === 'paused')) return 'completed';
  return current; // idempotent: repeated taps do not create another trip
}
