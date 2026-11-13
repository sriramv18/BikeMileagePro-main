
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:bike_mileage_pro/app.dart';

void main() {
  // Initialize SQLite before running any widget tests.
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  testWidgets(
    'Bike Mileage Pro starts and shows the main navigation',
    (tester) async {
      await tester.pumpWidget(const BikeMileageProApp());
      await tester.pumpAndSettle();

      expect(find.byType(MainNavigation), findsOneWidget);
      expect(find.text('Bike Mileage Pro'), findsOneWidget);
      expect(find.byType(NavigationBar), findsOneWidget);
    },
  );
}
