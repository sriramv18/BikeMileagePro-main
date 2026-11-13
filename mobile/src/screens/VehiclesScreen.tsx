import React from 'react';
import {ScrollView, Text, View} from 'react-native';
import {theme} from '../theme';
export default function VehiclesScreen() {
 return <ScrollView style={{flex:1,backgroundColor:theme.colors.background}} contentContainerStyle={{padding:20,gap:12}}>
  <Text style={{color:theme.colors.text,fontSize:25,fontWeight:'800'}}>My vehicles</Text>
  {['Honda H’ness CB350 DLX Pro','Kia vehicle'].map(v=><View key={v} style={{backgroundColor:theme.colors.surface,padding:18,borderRadius:18}}><Text style={{color:theme.colors.text,fontWeight:'700'}}>{v}</Text><Text style={{color:theme.colors.muted}}>Demo profile — edit and save vehicle details in the database integration.</Text></View>)}
 </ScrollView>;
}
