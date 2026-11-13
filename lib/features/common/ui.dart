import 'package:flutter/material.dart';
class Panel extends StatelessWidget {
  final Widget child; const Panel({super.key,required this.child});
  @override Widget build(BuildContext context)=>Container(
    width:double.infinity,padding:const EdgeInsets.all(20),
    decoration:BoxDecoration(color:const Color(0xFF12283D),borderRadius:BorderRadius.circular(22)),
    child:child);
}
class PageTitle extends StatelessWidget {
  final String title; const PageTitle(this.title,{super.key});
  @override Widget build(BuildContext context)=>Padding(
    padding:const EdgeInsets.fromLTRB(20,20,20,18),
    child:Text(title,style:const TextStyle(fontSize:28,fontWeight:FontWeight.bold)));
}
