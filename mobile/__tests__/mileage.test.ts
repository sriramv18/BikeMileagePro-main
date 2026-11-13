import {calculateFullTankIntervals} from '../src/services/mileage';
const base = {vehicleId:'v1',dateIso:'2026-01-01T00:00:00Z',amount:0};
test('includes partial refills between full tank markers',()=>{
 const entries = [
  {...base,id:'1',odometerKm:1000,litres:10,isFullTank:true},
  {...base,id:'2',odometerKm:1100,litres:2,isFullTank:false},
  {...base,id:'3',odometerKm:1200,litres:3,isFullTank:false},
  {...base,id:'4',odometerKm:1300,litres:10,isFullTank:true},
 ];
 const result = calculateFullTankIntervals(entries);
 expect(result[0].distanceKm).toBe(300);
 expect(result[0].litres).toBe(15);
 expect(result[0].mileageKmPerL).toBe(20);
});
