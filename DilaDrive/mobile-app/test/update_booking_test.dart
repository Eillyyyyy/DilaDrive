import 'package:diladrive_customer/models/customer_booking.dart';
import 'package:diladrive_customer/screens/customer/update_booking.dart';
import 'package:diladrive_customer/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('pending airport drop-off edit exposes optional flight number',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: UpdateBookingPage(
        booking: CustomerBooking(
          id: 17,
          status: 'PENDING_VERIFICATION',
          type: 'Sedan',
          tripType: 'AIRPORT_DROPOFF',
          tripDate: DateTime(2030, 1, 2),
          pickupTime: '10:30:00',
        ),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.text('Flight number (optional)'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('normal transfer edit hides flight number', (tester) async {
    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: UpdateBookingPage(
        booking: CustomerBooking(
          id: 18,
          status: 'PENDING_VERIFICATION',
          type: 'Sedan',
          tripType: 'NORMAL_TRANSFER',
          tripDate: DateTime(2030, 1, 2),
          pickupTime: '10:30:00',
        ),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.textContaining('Flight number'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
