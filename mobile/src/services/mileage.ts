import { FuelEntry } from '../types';

/**
 * Full-tank interval calculation:
 * For each pair of consecutive full-tank markers, sum all refills AFTER
 * the earlier full tank through and INCLUDING the later full tank.
 * Do not include litres from the earlier marker.
 */
export function calculateFullTankIntervals(entries: FuelEntry[]) {
  const sorted = [...entries].sort((a,b) =>
    a.odometerKm - b.odometerKm || a.dateIso.localeCompare(b.dateIso)
  );
  const fullIndices = sorted.map((e,i)=>e.isFullTank ? i : -1).filter(i=>i>=0);
  const intervals: {fromOdometerKm:number;toOdometerKm:number;distanceKm:number;litres:number;mileageKmPerL:number}|[] = [];

  for (let n=1; n<fullIndices.length; n++) {
    const prev = fullIndices[n-1], curr = fullIndices[n];
    const distanceKm = sorted[curr].odometerKm - sorted[prev].odometerKm;
    const litres = sorted.slice(prev+1, curr+1).reduce((sum,e)=>sum+e.litres,0);
    if (distanceKm > 0 && litres > 0) {
      intervals.push({
        fromOdometerKm: sorted[prev].odometerKm,
        toOdometerKm: sorted[curr].odometerKm,
        distanceKm, litres, mileageKmPerL: distanceKm/litres
      });
    }
  }
  return intervals;
}
