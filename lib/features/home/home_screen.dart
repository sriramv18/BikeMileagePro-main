import 'package:flutter/material.dart';
import '../../data/app_database.dart';
import '../../models/app_models.dart';
import '../common/ui.dart';

class HomeScreen extends StatefulWidget { const HomeScreen({super.key}); @override State<HomeScreen> createState()=>_HomeScreenState(); }
class _HomeScreenState extends State<HomeScreen> {
  List<Trip> trips=[]; List<Vehicle> vehicles=[]; List<FuelEntry> fuel=[];
  Future<void> load() async { final db=AppDatabase.instance; final a=await db.trips(); final b=await db.vehicles(); final c=await db.fuelEntries(); if(mounted)setState((){trips=a;vehicles=b;fuel=c;}); }
  @override void initState(){super.initState();load();}
  @override Widget build(BuildContext context) {
    final distance=trips.fold<double>(0,(s,t)=>s+t.distanceKm);
    final litres=trips.fold<double>(0,(s,t)=>s+t.fuelLitres);
    final cost=trips.fold<double>(0,(s,t)=>s+t.fuelCost)+fuel.fold<double>(0,(s,e)=>s+e.amount);
    final mileage=litres>0?distance/litres:null;
    return RefreshIndicator(onRefresh:load,child:ListView(padding:const EdgeInsets.fromLTRB(20,18,20,24),children:[
      const PageTitle('Bike Mileage Pro'),
      _metric('Overall mileage',mileage==null?'—':'${mileage.toStringAsFixed(1)} km/L'),
      _metric('Total distance','${distance.toStringAsFixed(1)} km'),
      _metric('Fuel expenses','₹${cost.toStringAsFixed(2)}'),
      _metric('Vehicles','${vehicles.length}'),
      const SizedBox(height:12),
      const Text('Mileage is estimated from each trip’s saved reference mileage. Fuel fill-ups are recorded separately.',style:TextStyle(color:Colors.white60)),
    ]));
  }
  Widget _metric(String label,String value)=>Padding(padding:const EdgeInsets.only(bottom:16),child:Panel(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
    Text(label,style:const TextStyle(color:Colors.white60,fontSize:16)),
    const SizedBox(height:14),Text(value,style:const TextStyle(fontSize:28,fontWeight:FontWeight.bold))
  ])));
}
