import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:sqflite/sqflite.dart' show databaseFactory;
import 'package:bike_mileage_pro/app.dart';

void main() {
  setUpAll(() {
    // Flutter widget tests run on the host machine, not an Android device.
    // Use SQLite's FFI implementation instead of the native sqflite channel.
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  testWidgets('Bike Mileage Pro starts and shows the main navigation',
      (tester) async {
    await tester.pumpWidget(const BikeMileageProApp());
    // Let asynchronous SQLite reads finish.
    await tester.pumpAndSettle();

    expect(find.byType(MainNavigation), findsOneWidget);
    expect(find.text('Bike Mileage Pro'), findsOneWidget);
    expect(find.byType(NavigationBar), findsOneWidget);
  });
}
