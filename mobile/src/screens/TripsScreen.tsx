import React, {useState} from 'react';
import {ScrollView, Text, View, Pressable} from 'react-native';
import {theme} from '../theme';
import {canStartTrip, nextTripStatus} from '../services/tripState';
import {TripStatus} from '../types';

export default function TripsScreen() {
  const [status,setStatus] = useState<TripStatus>('idle');
  const readiness = {gpsReady:false, bluetoothConnected:false, obdTelemetryAvailable:false, engineRunning:null};
  const ready = canStartTrip(readiness);
  const action = status==='idle' || status==='completed' ? 'START' : status==='recording' ? 'PAUSE' : 'RESUME';
  return <ScrollView style={{flex:1,backgroundColor:theme.colors.background}} contentContainerStyle={{padding:20,gap:14}}>
    <Text style={{color:theme.colors.text,fontSize:25,fontWeight:'800'}}>Trip tracking</Text>
    <View style={{backgroundColor:theme.colors.surface,padding:18,borderRadius:18,gap:8}}>
      <Text style={{color:theme.colors.muted}}>Tracking status</Text>
      <Text style={{color:theme.colors.text,fontSize:22,fontWeight:'700'}}>{status.toUpperCase()}</Text>
      <Text style={{color:theme.colors.warning}}>GPS and Bluetooth readiness will be populated by native Android services.</Text>
      <Text style={{color:theme.colors.muted}}>GPS-only mode is available when a GPS fix is ready but compatible OBD telemetry is unavailable.</Text>
      <Pressable disabled={action==='START' && !ready.enabled} onPress={()=>setStatus(nextTripStatus(status,action as any))} style={{padding:15,backgroundColor:action==='START'&&!ready.enabled?theme.colors.surface2:theme.colors.primary,borderRadius:12}}>
        <Text style={{textAlign:'center',fontWeight:'800',color:theme.colors.background}}>{action==='START'?'Start trip':action==='PAUSE'?'Pause trip':'Resume trip'}</Text>
      </Pressable>
      {(status==='recording'||status==='paused') && <Pressable onPress={()=>setStatus(nextTripStatus(status,'STOP'))} style={{padding:14,borderColor:theme.colors.danger,borderWidth:1,borderRadius:12}}>
        <Text style={{textAlign:'center',color:theme.colors.danger,fontWeight:'700'}}>End trip</Text>
      </Pressable>}
    </View>
  </ScrollView>;
}
