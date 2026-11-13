import React, {useState} from 'react';
import {ScrollView, Text, View, Pressable} from 'react-native';
import {theme} from '../theme';

export default function DashboardScreen() {
  const [tab,setTab] = useState<'Summary'|'Charts'>('Summary');
  return <ScrollView style={{flex:1,backgroundColor:theme.colors.background}} contentContainerStyle={{padding:20,gap:16}}>
    <Text style={{color:theme.colors.primary,fontSize:13,fontWeight:'800'}}>SR MOBILITY LABS</Text>
    <Text style={{color:theme.colors.text,fontSize:26,fontWeight:'800'}}>Bike Mileage Pro</Text>
    <View style={{flexDirection:'row',gap:8}}>
      {(['Summary','Charts'] as const).map(t=><Pressable key={t} onPress={()=>setTab(t)} style={{padding:12,borderRadius:12,backgroundColor:tab===t?theme.colors.primary:theme.colors.surface}}>
        <Text style={{color:tab===t?theme.colors.background:theme.colors.text,fontWeight:'700'}}>{t}</Text>
      </Pressable>)}
    </View>
    {tab==='Summary' ? <View style={{gap:12}}>
      {['Overall mileage','Total distance','Fuel expenses','Vehicles'].map((label,i)=><View key={label} style={{padding:18,backgroundColor:theme.colors.surface,borderRadius:theme.radius}}>
        <Text style={{color:theme.colors.muted}}>{label}</Text><Text style={{color:theme.colors.text,fontSize:24,fontWeight:'800'}}>—</Text>
      </View>)}
    </View> : <View style={{padding:18,backgroundColor:theme.colors.surface,borderRadius:theme.radius}}>
      <Text style={{color:theme.colors.text,fontWeight:'700'}}>Mileage and fuel charts</Text>
      <Text style={{color:theme.colors.muted,marginTop:8}}>Charts populate from saved fuel and trip records.</Text>
    </View>}
    <Text style={{color:theme.colors.muted,fontSize:11}}>© 2026 SR Mobility Labs. All rights reserved.</Text>
  </ScrollView>;
}
