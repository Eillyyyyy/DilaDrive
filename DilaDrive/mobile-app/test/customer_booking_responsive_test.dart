import 'package:diladrive_customer/models/customer_booking.dart';
import 'package:diladrive_customer/models/mobile_user.dart';
import 'package:diladrive_customer/screens/customer/bookings.dart';
import 'package:diladrive_customer/screens/customer/dashboard.dart';
import 'package:diladrive_customer/services/customer_booking_service.dart';
import 'package:diladrive_customer/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('customer booking screens fit a narrow phone without overflow',
      (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(320, 900);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);

    final service = _BookingsService([
      CustomerBooking(
        id: 120,
        status: 'PENDING_VERIFICATION',
        tripType: 'NORMAL_TRANSFER',
        pickupAddress:
            'Kuala Lumpur International Airport, Main Arrival Terminal',
        destinationAddress:
            'DilaDrive customer destination with a longer address',
        tripDate: DateTime(2026, 12, 1),
        pickupTime: '09:30:00',
        passengerCount: 4,
        luggageCount: 3,
        fare: 120.50,
      ),
    ]);
    const customer = MobileUser(
      id: 1,
      name: 'Test Customer',
      email: 'customer@example.test',
      role: 'CUSTOMER',
    );

    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: CustomerDashboardPage(customer: customer, bookingService: service),
    ));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: MyBookingsPage(customer: customer, bookingService: service),
    ));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}

class _BookingsService extends CustomerBookingService {
  _BookingsService(this.bookings);

  final List<CustomerBooking> bookings;

  @override
  Future<List<CustomerBooking>> getMyBookings() async => bookings;

  @override
  void dispose() {}
}
