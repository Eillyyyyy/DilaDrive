import 'package:diladrive_customer/models/driver_trip.dart';
import 'package:diladrive_customer/models/mobile_user.dart';
import 'package:diladrive_customer/screens/driver/trips.dart';
import 'package:diladrive_customer/services/driver_trip_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('empty driver trips can be pulled to refresh', (tester) async {
    final service = _RefreshingTripService();
    await tester.pumpWidget(MaterialApp(
      home: DriverTripsPage(
        driver: const MobileUser(
          id: 9,
          name: 'Test Driver',
          email: 'driver@example.test',
          role: 'DRIVER',
        ),
        tripService: service,
      ),
    ));
    await tester.pumpAndSettle();

    expect(
        find.text('No trips are currently assigned to you.'), findsOneWidget);
    expect(service.requestCount, 1);

    await tester.drag(find.byType(ListView), const Offset(0, 400));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    await tester.pumpAndSettle();

    expect(service.requestCount, 2);
    expect(find.text('BK-000031'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

class _RefreshingTripService extends DriverTripService {
  int requestCount = 0;

  @override
  Future<List<DriverTrip>> getMyTrips() async {
    requestCount++;
    if (requestCount == 1) return const [];
    return const [
      DriverTrip(
        assignmentId: 14,
        bookingId: 31,
        assignmentStatus: 'ASSIGNED',
      ),
    ];
  }

  @override
  void dispose() {}
}
