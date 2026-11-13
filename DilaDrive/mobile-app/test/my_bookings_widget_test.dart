import 'package:diladrive_customer/models/customer_booking.dart';
import 'package:diladrive_customer/models/mobile_user.dart';
import 'package:diladrive_customer/screens/customer/bookings.dart';
import 'package:diladrive_customer/services/customer_booking_service.dart';
import 'package:diladrive_customer/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('bookings with no creation time use newest ID first',
      (tester) async {
    final service = _BookingsService(const [
      CustomerBooking(id: 120, status: 'PENDING_VERIFICATION'),
      CustomerBooking(id: 121, status: 'COMPLETED'),
    ]);

    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: MyBookingsPage(
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

    expect(
      tester.getTopLeft(find.text('BK-000121')).dy,
      lessThan(tester.getTopLeft(find.text('BK-000120')).dy),
    );
  });

  testWidgets('shows real customer bookings and filters by pending status',
      (tester) async {
    final service = _BookingsService([
      CustomerBooking(
        id: 120,
        status: 'PENDING_VERIFICATION',
        tripType: 'NORMAL_TRANSFER',
        pickupAddress: 'Kuala Lumpur',
        destinationAddress: 'Melaka',
        tripDate: DateTime(2026, 12, 1),
        passengerCount: 2,
      ),
      CustomerBooking(
        id: 121,
        status: 'COMPLETED',
        tripType: 'AIRPORT_DROPOFF',
        pickupAddress: 'Petaling Jaya',
        destinationAddress: 'KLIA',
        tripDate: DateTime(2026, 10, 1),
        passengerCount: 1,
      ),
    ]);

    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: MyBookingsPage(
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

    expect(find.text('BK-000120'), findsOneWidget);
    expect(find.text('BK-000121'), findsOneWidget);
    expect(find.text('2 bookings'), findsOneWidget);

    await tester.tap(find.widgetWithText(FilterChip, 'Pending'));
    await tester.pumpAndSettle();

    expect(find.text('BK-000120'), findsOneWidget);
    expect(find.text('BK-000121'), findsNothing);
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(const SizedBox.shrink());
    expect(service.disposeCount, 0);
  });
}

class _BookingsService extends CustomerBookingService {
  _BookingsService(this.bookings);

  final List<CustomerBooking> bookings;
  int disposeCount = 0;

  @override
  Future<List<CustomerBooking>> getMyBookings() async => bookings;

  @override
  void dispose() => disposeCount++;
}
