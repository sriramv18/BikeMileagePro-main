import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import 'package:intl/intl.dart';
import '../../data/app_database.dart';
import '../../models/app_models.dart';
import '../common/ui.dart';

class FuelScreen extends StatefulWidget { const FuelScreen({super.key}); @override State<FuelScreen> createState()=>_FuelScreenState(); }
class _FuelScreenState extends State<FuelScreen> {
  List<Vehicle> vehicles=[]; List<FuelEntry> entries=[]; String? vehicleId;
  Future<void> load() async { final db=AppDatabase.instance; final v=await db.vehicles(); final e=await db.fuelEntries(); if(mounted)setState((){vehicles=v;entries=e;if(vehicleId==null&&v.isNotEmpty)vehicleId=v.first.id;}); }
  @override void initState(){super.initState();load();}
  // A measured interval is complete only between two full-tank records.
  List<_MileageInterval> get intervals {
    final result=<_MileageInterval>[];
    for(final v in vehicles){
      final list=entries.where((e)=>e.vehicleId==v.id).toList()..sort((a,b)=>a.date.compareTo(b.date));
      FuelEntry? previousFull; double litresSinceFull=0, amountSinceFull=0;
      for(final e in list){
        litresSinceFull+=e.litres; amountSinceFull+=e.amount;
        if(e.fullTank){
          if(previousFull!=null){
            final km=e.odometer-previousFull.odometer;
            if(km>0&&litresSinceFull>0) result.add(_MileageInterval(v.id,km,litresSinceFull,amountSinceFull));
          }
          previousFull=e; litresSinceFull=0; amountSinceFull=0;
        }
      }
    }
    return result;
  }
  Future<void> add() async {
    if(vehicles.isEmpty){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('Add a vehicle before recording fuel.')));return;}
    final odo=TextEditingController(),litres=TextEditingController(),amount=TextEditingController(); bool full=true; DateTime date=DateTime.now();
    final ok=await showDialog<bool>(context:context,builder:(ctx)=>StatefulBuilder(builder:(ctx,setDialog)=>AlertDialog(title:const Text('Add fuel entry'),content:SingleChildScrollView(child:Column(mainAxisSize:MainAxisSize.min,children:[
      DropdownButton<String>(value:vehicleId,items:vehicles.map((v)=>DropdownMenuItem(value:v.id,child:Text(v.name))).toList(),onChanged:(v)=>setDialog(()=>vehicleId=v)),
      TextField(controller:odo,keyboardType:const TextInputType.numberWithOptions(decimal:true),decoration:const InputDecoration(labelText:'Odometer (km)')),
      TextField(controller:litres,keyboardType:const TextInputType.numberWithOptions(decimal:true),decoration:const InputDecoration(labelText:'Fuel quantity (litres)')),
      TextField(controller:amount,keyboardType:const TextInputType.numberWithOptions(decimal:true),decoration:const InputDecoration(labelText:'Amount (₹)')),
      ListTile(contentPadding:EdgeInsets.zero,title:Text(DateFormat('dd MMM yyyy').format(date)),subtitle:const Text('Fuel date'),trailing:const Icon(Icons.calendar_month),onTap:()async{final d=await showDatePicker(context:ctx,initialDate:date,firstDate:DateTime(2010),lastDate:DateTime.now());if(d!=null)setDialog(()=>date=d);}),
      SwitchListTile(value:full,onChanged:(v)=>setDialog(()=>full=v),title:const Text('Full tank')),
    ])),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('Cancel')),TextButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('Save'))])));
    if(ok==true){final o=double.tryParse(odo.text),l=double.tryParse(litres.text),a=double.tryParse(amount.text);if(o==null||l==null||a==null||o<0||l<=0||a<0||vehicleId==null){if(mounted)ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('Enter valid odometer, litres and amount.')));return;}await AppDatabase.instance.saveFuel(FuelEntry(id:const Uuid().v4(),vehicleId:vehicleId!,date:date.toIso8601String(),odometer:o,litres:l,amount:a,fullTank:full));await load();}
  }
  @override Widget build(BuildContext context){final measured=intervals;final selected=measured.where((x)=>x.vehicleId==vehicleId).toList();final totalKm=selected.fold<double>(0,(s,x)=>s+x.km);final totalL=selected.fold<double>(0,(s,x)=>s+x.litres);final mileage=totalL>0?totalKm/totalL:null;
    return Scaffold(floatingActionButton:FloatingActionButton(onPressed:add,child:const Icon(Icons.add)),body:RefreshIndicator(onRefresh:load,child:ListView(padding:const EdgeInsets.fromLTRB(20,18,20,90),children:[
      const PageTitle('Fuel & odometer'),
      if(vehicles.isNotEmpty) DropdownButton<String>(isExpanded:true,value:vehicleId,items:vehicles.map((v)=>DropdownMenuItem(value:v.id,child:Text(v.name))).toList(),onChanged:(v)=>setState(()=>vehicleId=v)),
      Panel(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[const Text('Measured mileage (full-tank method)',style:TextStyle(color:Colors.white60)),const SizedBox(height:8),Text(mileage==null?'Waiting for two full-tank entries':'${mileage.toStringAsFixed(2)} km/L',style:const TextStyle(fontSize:24,fontWeight:FontWeight.bold)),Text('${totalKm.toStringAsFixed(1)} km ÷ ${totalL.toStringAsFixed(2)} L',style:const TextStyle(color:Colors.white60))])),
      const SizedBox(height:18),const Text('Fuel entries',style:TextStyle(fontSize:20,fontWeight:FontWeight.bold)),
      if(entries.isEmpty)const Panel(child:Text('Tap + to record odometer, litres, amount and fill type.',style:TextStyle(color:Colors.white60))),
      ...entries.where((e)=>vehicleId==null||e.vehicleId==vehicleId).map((e)=>Padding(padding:const EdgeInsets.only(top:10),child:Panel(child:ListTile(contentPadding:EdgeInsets.zero,title:Text('${e.litres.toStringAsFixed(2)} L · ₹${e.amount.toStringAsFixed(2)}'),subtitle:Text('${DateFormat('dd MMM yyyy').format(DateTime.parse(e.date))} · Odometer ${e.odometer.toStringAsFixed(1)} km · ${e.fullTank?'Full tank':'Partial'}'),trailing:IconButton(icon:const Icon(Icons.delete_outline),onPressed:()async{await AppDatabase.instance.deleteFuel(e.id);await load();}))))),
    ])));
  }
}
class _MileageInterval{final String vehicleId;final double km,litres,amount;_MileageInterval(this.vehicleId,this.km,this.litres,this.amount);}
