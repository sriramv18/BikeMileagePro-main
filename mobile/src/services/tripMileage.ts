import { Trip } from '../types';

export interface TripMileageSummary {
  distanceKm: number;
  referenceMileageKmPerL: number;
  estimatedFuelLitres: number;
  fuelPricePerLitre: number;
  estimatedFuelCost: number;
  estimatedMileageKmPerL: number;
}

/**
 * Estimate a completed trip using the selected vehicle's previously recorded
 * mileage. This is an estimate, not measured fuel consumption.
 */
export function calculateTripMileage(
  distanceKm: number,
  referenceMileageKmPerL: number,
  fuelPricePerLitre: number,
): TripMileageSummary | null {
  if (
    !Number.isFinite(distanceKm) || distanceKm <= 0 ||
    !Number.isFinite(referenceMileageKmPerL) || referenceMileageKmPerL <= 0 ||
    !Number.isFinite(fuelPricePerLitre) || fuelPricePerLitre < 0
  ) return null;

  const estimatedFuelLitres = distanceKm / referenceMileageKmPerL;
  return {
    distanceKm,
    referenceMileageKmPerL,
    estimatedFuelLitres,
    fuelPricePerLitre,
    estimatedFuelCost: estimatedFuelLitres * fuelPricePerLitre,
    // The estimate uses the reference mileage, so this is the estimated trip mileage.
    estimatedMileageKmPerL: referenceMileageKmPerL,
  };
}

/** Attach immutable estimate snapshots to a completed trip. */
export function addTripMileageEstimate(
  trip: Trip,
  referenceMileageKmPerL: number,
  fuelPricePerLitre: number,
): Trip {
  const summary = calculateTripMileage(trip.distanceKm, referenceMileageKmPerL, fuelPricePerLitre);
  if (!summary) return trip;
  return {
    ...trip,
    referenceMileageKmPerL: summary.referenceMileageKmPerL,
    estimatedFuelLitres: summary.estimatedFuelLitres,
    fuelPricePerLitre: summary.fuelPricePerLitre,
    estimatedFuelCost: summary.estimatedFuelCost,
  };
}

export interface OverallMileageSummary {
  tripCount: number;
  totalDistanceKm: number;
  totalEstimatedFuelLitres: number;
  overallEstimatedMileageKmPerL: number | null;
  totalEstimatedFuelCost: number;
}

/**
 * Weighted overall mileage = total distance / total estimated fuel.
 * Trips without valid estimate snapshots are excluded from the mileage ratio.
 */
export function calculateOverallMileage(trips: Trip[]): OverallMileageSummary {
  const completed = trips.filter(t => t.status === 'completed' && t.distanceKm > 0);
  const estimated = completed.filter(t =>
    Number.isFinite(t.estimatedFuelLitres) && (t.estimatedFuelLitres ?? 0) > 0
  );
  const totalDistanceKm = completed.reduce((sum, t) => sum + t.distanceKm, 0);
  const totalEstimatedFuelLitres = estimated.reduce((sum, t) => sum + (t.estimatedFuelLitres ?? 0), 0);
  const totalEstimatedFuelCost = estimated.reduce((sum, t) => sum + (t.estimatedFuelCost ?? 0), 0);

  return {
    tripCount: completed.length,
    totalDistanceKm,
    totalEstimatedFuelLitres,
    overallEstimatedMileageKmPerL: totalEstimatedFuelLitres > 0
      ? estimated.reduce((sum, t) => sum + t.distanceKm, 0) / totalEstimatedFuelLitres
      : null,
    totalEstimatedFuelCost,
  };
}
