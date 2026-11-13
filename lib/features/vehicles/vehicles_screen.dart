import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import '../../data/app_database.dart';
import '../../models/app_models.dart';
import '../common/ui.dart';

class VehiclesScreen extends StatefulWidget { const VehiclesScreen({super.key}); @override State<VehiclesScreen> createState()=>_VehiclesScreenState(); }
class _VehiclesScreenState extends State<VehiclesScreen> {
  List<Vehicle> items=[];
  Future<void> load() async {final v=await AppDatabase.instance.vehicles();if(mounted)setState(()=>items=v);}
  @override void initState(){super.initState();load();}
  Future<void> add() async {
    final name=TextEditingController(), mileage=TextEditingController(text:'40'), price=TextEditingController(text:'105');
    final ok=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('Add vehicle'),content:Column(mainAxisSize:MainAxisSize.min,children:[
      TextField(controller:name,decoration:const InputDecoration(labelText:'Vehicle name')),
      TextField(controller:mileage,keyboardType:TextInputType.number,decoration:const InputDecoration(labelText:'Reference mileage (km/L)')),
      TextField(controller:price,keyboardType:TextInputType.number,decoration:const InputDecoration(labelText:'Fuel price (₹/L)')),
    ]),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('Cancel')),TextButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('Save'))]));
    if(ok==true && name.text.trim().isNotEmpty) {
      await AppDatabase.instance.saveVehicle(Vehicle(id:const Uuid().v4(),name:name.text.trim(),referenceMileage:double.tryParse(mileage.text)??40,fuelPrice:double.tryParse(price.text)??105)); load();
    }
  }
  @override Widget build(BuildContext context)=>Scaffold(
    floatingActionButton:FloatingActionButton(onPressed:add,child:const Icon(Icons.add)),
    body:ListView(padding:const EdgeInsets.only(bottom:90),children:[
      const PageTitle('My vehicles'),
      ...items.map((v)=>Padding(padding:const EdgeInsets.fromLTRB(20,0,20,14),child:Panel(child:Row(children:[
        const Icon(Icons.two_wheeler,size:34,color:Color(0xFF13D3C2)),const SizedBox(width:14),
        Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(v.name,style:const TextStyle(fontWeight:FontWeight.bold,fontSize:17)),Text('${v.referenceMileage} km/L · ₹${v.fuelPrice}/L',style:const TextStyle(color:Colors.white60))])),
        IconButton(onPressed:()async{await AppDatabase.instance.deleteVehicle(v.id);load();},icon:const Icon(Icons.delete_outline))
      ])))),
    ]));
}
