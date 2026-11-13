import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:diladrive_customer/models/customer_booking.dart';
import 'package:diladrive_customer/models/mobile_user.dart';
import 'package:diladrive_customer/screens/customer/dashboard.dart';
import 'package:diladrive_customer/services/customer_booking_service.dart';
import 'package:diladrive_customer/theme/app_theme.dart';

void main() {
  testWidgets('dashboard displays fetched bookings and derives status totals',
      (tester) async {
    final service = _DashboardBookingService(const [
      CustomerBooking(
        id: 7,
        status: 'PENDING_VERIFICATION',
        pickupAddress: 'Kuala Lumpur',
        destinationAddress: 'Shah Alam',
      ),
      CustomerBooking(
        id: 8,
        status: 'ASSIGNED',
        pickupAddress: 'Putrajaya',
        destinationAddress: 'Cyberjaya',
      ),
      CustomerBooking(
        id: 9,
        status: 'COMPLETED',
        pickupAddress: 'Klang',
        destinationAddress: 'Subang Jaya',
      ),
    ]);

    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: CustomerDashboardPage(
        customer: const MobileUser(
          id: 1,
          name: 'Test Customer',
          email: 'customer@example.test',
          role: 'CUSTOMER',
        ),
        bookingService: service,
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.text('No bookings yet'), findsNothing);
    expect(find.text('BK-000007'), findsOneWidget);
    expect(find.text('BK-000008'), findsNWidgets(2));
    expect(find.text('BK-000009'), findsOneWidget);
    expect(find.text('Pending'), findsOneWidget);
    expect(find.text('Active'), findsOneWidget);
    expect(find.text('Completed'), findsNWidgets(2));
    expect(find.text('1'), findsNWidgets(3));
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(const SizedBox.shrink());
    expect(service.disposeCount, 0);
  });

  testWidgets('dashboard refresh reloads bookings without a setState error',
      (tester) async {
    final service = _RefreshingBookingService();
    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: CustomerDashboardPage(
        customer: const MobileUser(
          id: 1,
          name: 'Test Customer',
          email: 'customer@example.test',
          role: 'CUSTOMER',
        ),
        bookingService: service,
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.text('No bookings yet'), findsOneWidget);
    expect(service.requestCount, 1);

    await tester.drag(find.byType(ListView).first, const Offset(0, 400));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    await tester.pumpAndSettle();

    expect(service.requestCount, 2);
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(const SizedBox.shrink());
    expect(service.disposeCount, 0);
  });
}

class _DashboardBookingService extends CustomerBookingService {
  _DashboardBookingService(this.bookings);

  final List<CustomerBooking> bookings;
  int disposeCount = 0;

  @override
  Future<List<CustomerBooking>> getMyBookings() async => bookings;

  @override
  void dispose() => disposeCount++;
}

class _RefreshingBookingService extends CustomerBookingService {
  int requestCount = 0;
  int disposeCount = 0;

  @override
  Future<List<CustomerBooking>> getMyBookings() async {
    requestCount++;
    return const [];
  }

  @override
  void dispose() => disposeCount++;
}
