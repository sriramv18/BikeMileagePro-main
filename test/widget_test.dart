import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:bike_mileage_pro/app.dart';

void main() {
  testWidgets(
    'Bike Mileage Pro starts and shows the main navigation',
    (tester) async {
      await tester.pumpWidget(const BikeMileageProApp());

      expect(find.byType(MainNavigation), findsOneWidget);
      expect(find.text('Bike Mileage Pro'), findsOneWidget);
      expect(find.byType(NavigationBar), findsOneWidget);
    },
  );
}