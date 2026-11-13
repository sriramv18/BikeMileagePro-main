import { calculateTripMileage, calculateOverallMileage } from '../src/services/tripMileage';
import { Trip } from '../src/types';

describe('trip mileage estimates', () => {
  it('calculates trip fuel and cost from reference mileage', () => {
    const result = calculateTripMileage(80, 40, 105);
    expect(result?.estimatedFuelLitres).toBe(2);
    expect(result?.estimatedFuelCost).toBe(210);
    expect(result?.estimatedMileageKmPerL).toBe(40);
  });

  it('returns null for invalid inputs', () => {
    expect(calculateTripMileage(80, 0, 105)).toBeNull();
  });

  it('calculates overall mileage as a weighted ratio', () => {
    const trips = [
      { id:'1', vehicleId:'v', startedAt:'', mode:'gps_only', status:'completed', distanceKm:80, route:[], estimatedFuelLitres:2, estimatedFuelCost:210 } as Trip,
      { id:'2', vehicleId:'v', startedAt:'', mode:'gps_only', status:'completed', distanceKm:40, route:[], estimatedFuelLitres:1, estimatedFuelCost:105 } as Trip,
    ];
    const total = calculateOverallMileage(trips);
    expect(total.totalDistanceKm).toBe(120);
    expect(total.totalEstimatedFuelLitres).toBe(3);
    expect(total.overallEstimatedMileageKmPerL).toBe(40);
    expect(total.totalEstimatedFuelCost).toBe(315);
  });
});
