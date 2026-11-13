import React from 'react';
import {ScrollView, Text, View} from 'react-native';
import {theme} from '../theme';
export default function FuelScreen() {
 return <ScrollView style={{flex:1,backgroundColor:theme.colors.background}} contentContainerStyle={{padding:20,gap:12}}>
  <Text style={{color:theme.colors.text,fontSize:25,fontWeight:'800'}}>Fuel & odometer</Text>
  <View style={{backgroundColor:theme.colors.surface,padding:18,borderRadius:18}}>
   <Text style={{color:theme.colors.text,fontWeight:'700'}}>Fuel entries</Text>
   <Text style={{color:theme.colors.muted,marginTop:8}}>Date picker, odometer, litres, amount and full/partial fill-up forms are next implementation tasks.</Text>
  </View>
 </ScrollView>;
}
