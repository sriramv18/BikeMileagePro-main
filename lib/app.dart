import 'package:flutter/material.dart';
import 'features/home/home_screen.dart';
import 'features/vehicles/vehicles_screen.dart';
import 'features/trips/trips_screen.dart';
import 'features/fuel/fuel_screen.dart';

class BikeMileageProApp extends StatelessWidget {
  const BikeMileageProApp({super.key});

  @override
  Widget build(BuildContext context) {
    const navy = Color(0xFF071321);
    const panel = Color(0xFF12283D);
    const accent = Color(0xFF13D3C2);
    return MaterialApp(
      title: 'Bike Mileage Pro',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: navy,
        colorScheme: const ColorScheme.dark(
          primary: accent, secondary: accent, surface: panel,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: navy, foregroundColor: Colors.white,
          elevation: 0, titleTextStyle: TextStyle(
            fontSize: 26, fontWeight: FontWeight.bold, color: Colors.white),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true, fillColor: panel,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(14),
            borderSide: BorderSide.none),
          labelStyle: const TextStyle(color: Colors.white70),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(style: ElevatedButton.styleFrom(
          backgroundColor: accent, foregroundColor: navy,
          minimumSize: const Size.fromHeight(50),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        )),
      ),
      home: const MainNavigation(),
    );
  }
}

class MainNavigation extends StatefulWidget {
  const MainNavigation({super.key});
  @override
  State<MainNavigation> createState() => _MainNavigationState();
}
class _MainNavigationState extends State<MainNavigation> {
  int index = 0;
  final pages = const [HomeScreen(), VehiclesScreen(), TripsScreen(), FuelScreen()];
  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(child: IndexedStack(index: index, children: pages)),
    bottomNavigationBar: NavigationBar(
      selectedIndex: index,
      onDestinationSelected: (v) => setState(() => index = v),
      backgroundColor: const Color(0xFF12283D),
      indicatorColor: const Color(0xFF13D3C2),
      destinations: const [
        NavigationDestination(icon: Icon(Icons.dashboard_outlined), selectedIcon: Icon(Icons.dashboard), label: 'Home'),
        NavigationDestination(icon: Icon(Icons.two_wheeler_outlined), selectedIcon: Icon(Icons.two_wheeler), label: 'Vehicles'),
        NavigationDestination(icon: Icon(Icons.route_outlined), selectedIcon: Icon(Icons.route), label: 'Trips'),
        NavigationDestination(icon: Icon(Icons.local_gas_station_outlined), selectedIcon: Icon(Icons.local_gas_station), label: 'Fuel'),
      ],
    ),
  );
}
