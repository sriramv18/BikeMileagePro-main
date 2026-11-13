import 'dart:async';
import 'package:geolocator/geolocator.dart';

class GpsTripService {
  StreamSubscription<Position>? _subscription;
  final points=<Map<String,double>>[];
  double distanceKm=0;
  Position? _previous;
  bool get isTracking=>_subscription!=null;

  Future<void> start({required void Function(double distanceKm) onDistance}) async {
    if(!await Geolocator.isLocationServiceEnabled()) {
      throw Exception('Location services are disabled. Please enable GPS.');
    }
    var permission=await Geolocator.checkPermission();
    if(permission==LocationPermission.denied) permission=await Geolocator.requestPermission();
    if(permission==LocationPermission.denied || permission==LocationPermission.deniedForever || permission==LocationPermission.unableToDetermine) {
      throw Exception('Location permission is required to record a trip.');
    }
    points.clear(); distanceKm=0; _previous=null;
    const settings=LocationSettings(accuracy:LocationAccuracy.bestForNavigation,distanceFilter:5);
    _subscription=Geolocator.getPositionStream(locationSettings:settings).listen((pos) {
      final previous=_previous;
      if(previous!=null) {
        final delta=Geolocator.distanceBetween(previous.latitude,previous.longitude,pos.latitude,pos.longitude)/1000;
        if(delta>0 && delta<0.5) distanceKm+=delta;
      }
      _previous=pos;
      points.add({'lat':pos.latitude,'lng':pos.longitude});
      onDistance(distanceKm);
    });
  }
  Future<void> stop() async { await _subscription?.cancel(); _subscription=null; }
}
