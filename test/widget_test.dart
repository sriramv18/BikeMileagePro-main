import 'package:flutter_test/flutter_test.dart';
import 'package:bike_mileage_pro/app.dart';

void main() {
  testWidgets('renders Bike Mileage Pro navigation', (tester) async {
    await tester.pumpWidget(const BikeMileageProApp());
    expect(find.text('Bike Mileage Pro'), findsOneWidget);
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Vehicles'), findsOneWidget);
    expect(find.text('Trips'), findsOneWidget);
    expect(find.text('Fuel'), findsOneWidget);
  });
}
