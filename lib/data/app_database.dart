import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart' as p;
import '../models/app_models.dart';

class AppDatabase {
  AppDatabase._();
  static final instance=AppDatabase._();
  Database? _db;
  Future<Database> get database async => _db ??= await openDatabase(
    p.join(await getDatabasesPath(),'bike_mileage_pro.db'),version:2,
    onUpgrade:(db, oldVersion, newVersion) async {
      if (oldVersion < 2) {
        await db.execute('CREATE INDEX IF NOT EXISTS idx_fuel_vehicle_date ON fuel_entries(vehicle_id,date)');
        await db.execute('CREATE INDEX IF NOT EXISTS idx_trip_vehicle_date ON trips(vehicle_id,started_at)');
      }
    },
    onCreate:(db,_) async {
      await db.execute('CREATE TABLE vehicles(id TEXT PRIMARY KEY,name TEXT,type TEXT,reference_mileage REAL,fuel_price REAL)');
      await db.execute('CREATE TABLE trips(id TEXT PRIMARY KEY,vehicle_id TEXT,started_at TEXT,distance_km REAL,reference_mileage REAL,fuel_price REAL,fuel_litres REAL,fuel_cost REAL,route TEXT)');
      await db.execute('CREATE TABLE fuel_entries(id TEXT PRIMARY KEY,vehicle_id TEXT,date TEXT,odometer REAL,litres REAL,amount REAL,full_tank INTEGER)');
      await db.insert('vehicles',const Vehicle(id:'default-bike',name:'My Bike').toMap());
      await db.execute('CREATE INDEX IF NOT EXISTS idx_fuel_vehicle_date ON fuel_entries(vehicle_id,date)');
      await db.execute('CREATE INDEX IF NOT EXISTS idx_trip_vehicle_date ON trips(vehicle_id,started_at)');
    });
  Future<List<Vehicle>> vehicles() async => (await (await database).query('vehicles',orderBy:'name')).map(Vehicle.fromMap).toList();
  Future<void> saveVehicle(Vehicle v) async => (await database).insert('vehicles',v.toMap(),conflictAlgorithm:ConflictAlgorithm.replace);
  Future<void> updateVehicle(Vehicle v) async => (await database).update('vehicles',v.toMap(),where:'id=?',whereArgs:[v.id]);
  Future<void> deleteVehicle(String id) async => (await database).delete('vehicles',where:'id=?',whereArgs:[id]);
  Future<List<Trip>> trips() async => (await (await database).query('trips',orderBy:'started_at DESC')).map(Trip.fromMap).toList();
  Future<void> saveTrip(Trip t) async => (await database).insert('trips',t.toMap(),conflictAlgorithm:ConflictAlgorithm.replace);
  Future<void> deleteTrip(String id) async => (await database).delete('trips',where:'id=?',whereArgs:[id]);
  Future<List<FuelEntry>> fuelEntries() async => (await (await database).query('fuel_entries',orderBy:'date DESC')).map(FuelEntry.fromMap).toList();
  Future<void> saveFuel(FuelEntry e) async => (await database).insert('fuel_entries',e.toMap(),conflictAlgorithm:ConflictAlgorithm.replace);
  Future<void> deleteFuel(String id) async => (await database).delete('fuel_entries',where:'id=?',whereArgs:[id]);
}
