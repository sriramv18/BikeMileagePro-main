import 'package:flutter/material.dart';
import 'app.dart';
import 'data/app_database.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppDatabase.instance.database;
  runApp(const BikeMileageProApp());
}
