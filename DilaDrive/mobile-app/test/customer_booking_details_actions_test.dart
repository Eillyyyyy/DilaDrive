import 'package:diladrive_customer/models/customer_booking.dart';
import 'package:diladrive_customer/screens/customer/booking_details.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('pending bookings can be edited and cancelled', (tester) async {
    await _showBooking(tester, 'PENDING_VERIFICATION');

    expect(find.text('Edit pending booking'), findsOneWidget);
    expect(find.text('Cancel booking'), findsOneWidget);
  });

  testWidgets('confirmed bookings can be cancelled but not edited',
      (tester) async {
    await _showBooking(tester, 'CONFIRMED');

    expect(find.text('Edit pending booking'), findsNothing);
    expect(find.text('Cancel booking'), findsOneWidget);
  });

  testWidgets('assigned bookings do not offer customer edit or cancellation',
      (tester) async {
    await _showBooking(tester, 'ASSIGNED');

    expect(find.text('Edit pending booking'), findsNothing);
    expect(find.text('Cancel booking'), findsNothing);
  });
}

Future<void> _showBooking(WidgetTester tester, String status) async {
  await tester.pumpWidget(MaterialApp(
    home: BookingDetailsPage(
      booking: CustomerBooking(id: 17, status: status),
    ),
  ));
  await tester.pumpAndSettle();
}
