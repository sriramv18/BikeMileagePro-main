class Vehicle {
  final String id, name, type;
  final double referenceMileage, fuelPrice;
  const Vehicle({required this.id, required this.name, this.type='Bike',
    this.referenceMileage=40, this.fuelPrice=105});
  Map<String,Object?> toMap()=>{'id':id,'name':name,'type':type,
    'reference_mileage':referenceMileage,'fuel_price':fuelPrice};
  factory Vehicle.fromMap(Map<String,Object?> m)=>Vehicle(
    id:m['id'] as String, name:m['name'] as String, type:m['type'] as String,
    referenceMileage:(m['reference_mileage'] as num).toDouble(),
    fuelPrice:(m['fuel_price'] as num).toDouble());
}
class Trip {
  final String id, vehicleId, startedAt;
  final double distanceKm, referenceMileage, fuelPrice, fuelLitres, fuelCost;
  final List<Map<String,double>> route;
  const Trip({required this.id,required this.vehicleId,required this.startedAt,
    required this.distanceKm,required this.referenceMileage,required this.fuelPrice,
    required this.fuelLitres,required this.fuelCost,required this.route});
  Map<String,Object?> toMap()=>{'id':id,'vehicle_id':vehicleId,'started_at':startedAt,
    'distance_km':distanceKm,'reference_mileage':referenceMileage,
    'fuel_price':fuelPrice,'fuel_litres':fuelLitres,'fuel_cost':fuelCost,
    'route':route.map((p)=>'${p['lat']},${p['lng']}').join(';')};
  factory Trip.fromMap(Map<String,Object?> m) {
    final raw=(m['route'] as String? ?? '');
    final points=<Map<String,double>>[];
    for(final pair in raw.split(';')) {
      final v=pair.split(',');
      if(v.length==2) { final a=double.tryParse(v[0]), b=double.tryParse(v[1]); if(a!=null&&b!=null) points.add({'lat':a,'lng':b}); }
    }
    return Trip(id:m['id'] as String,vehicleId:m['vehicle_id'] as String,
      startedAt:m['started_at'] as String,distanceKm:(m['distance_km'] as num).toDouble(),
      referenceMileage:(m['reference_mileage'] as num).toDouble(),
      fuelPrice:(m['fuel_price'] as num).toDouble(),
      fuelLitres:(m['fuel_litres'] as num).toDouble(),
      fuelCost:(m['fuel_cost'] as num).toDouble(),route:points);
  }
}
class FuelEntry {
  final String id,vehicleId,date;
  final double odometer,litres,amount;
  final bool fullTank;
  const FuelEntry({required this.id,required this.vehicleId,required this.date,
    required this.odometer,required this.litres,required this.amount,required this.fullTank});
  Map<String,Object?> toMap()=>{'id':id,'vehicle_id':vehicleId,'date':date,
    'odometer':odometer,'litres':litres,'amount':amount,'full_tank':fullTank?1:0};
  factory FuelEntry.fromMap(Map<String,Object?> m)=>FuelEntry(id:m['id'] as String,
    vehicleId:m['vehicle_id'] as String,date:m['date'] as String,
    odometer:(m['odometer'] as num).toDouble(),litres:(m['litres'] as num).toDouble(),
    amount:(m['amount'] as num).toDouble(),fullTank:(m['full_tank'] as int)==1);
}
