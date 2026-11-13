import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import 'package:intl/intl.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import '../../data/app_database.dart';
import '../../models/app_models.dart';
import '../../services/gps_trip_service.dart';
import '../common/ui.dart';

class TripsScreen extends StatefulWidget { const TripsScreen({super.key}); @override State<TripsScreen> createState()=>_TripsScreenState(); }
class _TripsScreenState extends State<TripsScreen> {
  final gps=GpsTripService();
  List<Vehicle> vehicles=[]; List<Trip> trips=[];
  String? vehicleId; double distance=0; bool tracking=false; String? error;
  Future<void> load() async {final db=AppDatabase.instance;final v=await db.vehicles();final t=await db.trips();if(mounted)setState((){vehicles=v;trips=t;if(vehicleId==null&&v.isNotEmpty)vehicleId=v.first.id;});}
  @override void initState(){super.initState();load();}
  @override void dispose(){gps.stop();super.dispose();}
  Future<void> start() async {
    try { await gps.start(onDistance:(d){if(mounted)setState(()=>distance=d);}); if(mounted)setState((){tracking=true;error=null;distance=0;}); }
    catch(e){setState(()=>error=e.toString().replaceFirst('Exception: ',''));}
  }
  Future<void> stop() async {
    await gps.stop();
    final v=vehicles.where((x)=>x.id==vehicleId).firstOrNull;
    if(v!=null && distance>0) {
      final fuel=distance/v.referenceMileage;
      final trip=Trip(id:const Uuid().v4(),vehicleId:v.id,startedAt:DateTime.now().toIso8601String(),
        distanceKm:distance,referenceMileage:v.referenceMileage,fuelPrice:v.fuelPrice,
        fuelLitres:fuel,fuelCost:fuel*v.fuelPrice,route:List.of(gps.points));
      await AppDatabase.instance.saveTrip(trip);
    }
    if(mounted)setState(()=>tracking=false); await load();
  }
  @override Widget build(BuildContext context)=>ListView(padding:const EdgeInsets.fromLTRB(20,18,20,90),children:[
    const PageTitle('Trip tracking'),
    Panel(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
      const Text('Tracking status',style:TextStyle(color:Colors.white60)),
      const SizedBox(height:12),Text(tracking?'RECORDING':'IDLE',style:const TextStyle(fontSize:26,fontWeight:FontWeight.bold)),
      if(vehicles.isNotEmpty) DropdownButton<String>(value:vehicleId, isExpanded:true,items:vehicles.map((v)=>DropdownMenuItem(value:v.id,child:Text(v.name))).toList(),onChanged:tracking?null:(v)=>setState(()=>vehicleId=v)),
      const SizedBox(height:8),Text('Distance: ${distance.toStringAsFixed(2)} km',style:const TextStyle(fontSize:20)),
      if(error!=null) Padding(padding:const EdgeInsets.only(top:8),child:Text(error!,style:const TextStyle(color:Colors.orangeAccent))),
      const SizedBox(height:14),
      ElevatedButton(onPressed:vehicles.isEmpty?null:(tracking?stop:start),child:Text(tracking?'Stop and save trip':'Start trip')),
      const SizedBox(height:8),const Text('Keep this screen open during this initial GPS tracking build. Background tracking and foreground-service integration are a separate Android implementation phase.',style:TextStyle(color:Colors.white54)),
    ])),
    const SizedBox(height:24),const Text('Trip history',style:TextStyle(fontSize:22,fontWeight:FontWeight.bold)),
    const SizedBox(height:12),
    if(trips.isEmpty) const Panel(child:Text('No trips recorded yet. Start a trip to build your history.',style:TextStyle(color:Colors.white60))),
    ...trips.map((t)=>Padding(padding:const EdgeInsets.only(bottom:12),child:Panel(child:ListTile(contentPadding:EdgeInsets.zero,
      title:Text('${t.distanceKm.toStringAsFixed(2)} km · ${(t.distanceKm/t.fuelLitres).toStringAsFixed(1)} km/L'),
      subtitle:Text('${DateFormat('dd MMM yyyy, hh:mm a').format(DateTime.parse(t.startedAt))} · ${t.fuelLitres.toStringAsFixed(2)} L · ₹${t.fuelCost.toStringAsFixed(2)}'),
      trailing:IconButton(icon:const Icon(Icons.map_outlined),onPressed:()=>Navigator.push(context,MaterialPageRoute(builder:(_)=>TripMapScreen(trip:t)))),
    )))),
  ]);
}

class TripMapScreen extends StatelessWidget {
  final Trip trip; const TripMapScreen({super.key,required this.trip});
  @override Widget build(BuildContext context) {
    final points=trip.route.map((p)=>LatLng(p['lat']!,p['lng']!)).toList();
    return Scaffold(appBar:AppBar(title:const Text('Trip route')),body:points.length<2
      ? const Center(child:Text('Not enough GPS points were saved to draw this route.'))
      : GoogleMap(initialCameraPosition:CameraPosition(target:points.first,zoom:14),
        polylines:{Polyline(polylineId:const PolylineId('trip'),points:points,color:const Color(0xFF13D3C2),width:5)},
        markers:{Marker(markerId:const MarkerId('start'),position:points.first,infoWindow:const InfoWindow(title:'Start')),Marker(markerId:const MarkerId('end'),position:points.last,infoWindow:const InfoWindow(title:'End'))}));
  }
}
