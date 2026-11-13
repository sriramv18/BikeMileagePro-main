import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import 'package:intl/intl.dart';
import '../../data/app_database.dart';
import '../../models/app_models.dart';
import '../common/ui.dart';

class FuelScreen extends StatefulWidget { const FuelScreen({super.key}); @override State<FuelScreen> createState()=>_FuelScreenState(); }
class _FuelScreenState extends State<FuelScreen> {
  List<Vehicle> vehicles=[]; List<FuelEntry> entries=[]; String? vehicleId;
  Future<void> load() async {final db=AppDatabase.instance;final v=await db.vehicles();final e=await db.fuelEntries();if(mounted)setState((){vehicles=v;entries=e;if(vehicleId==null&&v.isNotEmpty)vehicleId=v.first.id;});}
  @override void initState(){super.initState();load();}
  Future<void> add() async {
    if(vehicles.isEmpty)return;
    final odo=TextEditingController(),litres=TextEditingController(),amount=TextEditingController();
    bool full=true;
    final ok=await showDialog<bool>(context:context,builder:(ctx)=>StatefulBuilder(builder:(ctx,setDialog)=>AlertDialog(title:const Text('Add fuel entry'),content:SingleChildScrollView(child:Column(mainAxisSize:MainAxisSize.min,children:[
      DropdownButton<String>(value:vehicleId,items:vehicles.map((v)=>DropdownMenuItem(value:v.id,child:Text(v.name))).toList(),onChanged:(v)=>setDialog(()=>vehicleId=v)),
      TextField(controller:odo,keyboardType:TextInputType.number,decoration:const InputDecoration(labelText:'Odometer (km)')),
      TextField(controller:litres,keyboardType:TextInputType.number,decoration:const InputDecoration(labelText:'Fuel quantity (L)')),
      TextField(controller:amount,keyboardType:TextInputType.number,decoration:const InputDecoration(labelText:'Amount (₹)')),
      SwitchListTile(value:full,onChanged:(v)=>setDialog(()=>full=v),title:const Text('Full tank')),
    ])),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('Cancel')),TextButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('Save'))]));
    if(ok==true) {
      final o=double.tryParse(odo.text),l=double.tryParse(litres.text),a=double.tryParse(amount.text);
      if(o==null||l==null||a==null||l<=0||a<0||vehicleId==null)return;
      await AppDatabase.instance.saveFuel(FuelEntry(id:const Uuid().v4(),vehicleId:vehicleId!,date:DateTime.now().toIso8601String(),odometer:o,litres:l,amount:a,fullTank:full));load();
    }
  }
  @override Widget build(BuildContext context)=>Scaffold(floatingActionButton:FloatingActionButton(onPressed:add,child:const Icon(Icons.add)),
    body:ListView(padding:const EdgeInsets.fromLTRB(20,18,20,90),children:[
      const PageTitle('Fuel & odometer'),
      if(entries.isEmpty)const Panel(child:Text('Add fuel fill-ups with odometer, litres and amount. Full-tank entries can support measured mileage calculations.',style:TextStyle(color:Colors.white60))),
      ...entries.map((e)=>Padding(padding:const EdgeInsets.only(bottom:12),child:Panel(child:ListTile(contentPadding:EdgeInsets.zero,
        title:Text('${e.litres.toStringAsFixed(2)} L · ₹${e.amount.toStringAsFixed(2)}'),
        subtitle:Text('${DateFormat('dd MMM yyyy').format(DateTime.parse(e.date))} · Odometer ${e.odometer.toStringAsFixed(0)} km · ${e.fullTank?'Full tank':'Partial'}'),
      )))),
    ]));
}
