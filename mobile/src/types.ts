export type VehicleType = 'motorcycle' | 'car' | 'other';
export type TrackingMode = 'bluetooth_obd' | 'gps_only';
export type TripStatus = 'idle' | 'preparing' | 'recording' | 'paused' | 'completed';

export interface Vehicle {
  id: string; name: string; make: string; model: string;
  year?: number; type: VehicleType; fuelType: 'petrol'|'diesel'|'electric'|'hybrid'|'other';
  odometerKm?: number; tankCapacityLitres?: number;
}
export interface FuelEntry {
  id: string; vehicleId: string; dateIso: string; odometerKm: number;
  litres: number; amount: number; isFullTank: boolean;
}
export interface RoutePoint {
  latitude: number; longitude: number; timestampMs: number;
  accuracyM?: number; speedMps?: number;
}
export interface Trip {
  id: string; vehicleId: string; startedAt: string; endedAt?: string;
  mode: TrackingMode; status: TripStatus; distanceKm: number;
  start?: RoutePoint; end?: RoutePoint; route: RoutePoint[];
  maxSpeedKmh?: number; averageSpeedKmh?: number;
}
