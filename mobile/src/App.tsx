import React from 'react';
import {NavigationContainer} from '@react-navigation/native';
import {createBottomTabNavigator} from '@react-navigation/bottom-tabs';
import DashboardScreen from './screens/DashboardScreen';
import VehiclesScreen from './screens/VehiclesScreen';
import TripsScreen from './screens/TripsScreen';
import FuelScreen from './screens/FuelScreen';
import {theme} from './theme';

const Tab = createBottomTabNavigator();
export default function App() {
 return <NavigationContainer>
  <Tab.Navigator screenOptions={{headerShown:false,tabBarStyle:{backgroundColor:theme.colors.surface,borderTopColor:theme.colors.border},tabBarActiveTintColor:theme.colors.primary,tabBarInactiveTintColor:theme.colors.muted}}>
   <Tab.Screen name="Home" component={DashboardScreen}/>
   <Tab.Screen name="Vehicles" component={VehiclesScreen}/>
   <Tab.Screen name="Trips" component={TripsScreen}/>
   <Tab.Screen name="Fuel" component={FuelScreen}/>
  </Tab.Navigator>
 </NavigationContainer>;
}
