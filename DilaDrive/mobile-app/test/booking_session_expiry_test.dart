import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/screens/auth/login.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_availability_page.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_review_page.dart';
import 'package:diladrive_customer/services/booking_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('availability redirects to login after session expiry',
      (tester) async {
    final draft =
        BookingDraft(customerName: 'Test Customer', phoneNumber: '0123')
          ..pickupAddress = 'Kuala Lumpur'
          ..destinationAddress = 'Melaka'
          ..bookingDate = DateTime(2030, 1, 1)
          ..pickupTime = DateTime(2030, 1, 1, 10);

    await tester.pumpWidget(MaterialApp(
      home: BookingAvailabilityPage(
        draft: draft,
        bookingService: _ExpiredSessionBookingService(),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.byType(LoginPage), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('booking submission redirects to login after session expiry',
      (tester) async {
    final draft =
        BookingDraft(customerName: 'Test Customer', phoneNumber: '0123')
          ..pickupAddress = 'Kuala Lumpur'
          ..destinationAddress = 'Melaka'
          ..bookingDate = DateTime(2030, 1, 1)
          ..recommendedVehicle = const RecommendedVehicle(
            id: 1,
            model: 'Fleet Sedan',
            type: 'Sedan',
            passengerCapacity: 4,
            luggageCapacity: 2,
          );

    await tester.pumpWidget(MaterialApp(
      home: BookingReviewPage(
        draft: draft,
        bookingService: _ExpiredSessionBookingService(),
      ),
    ));
    await tester.tap(find.text('Confirm booking'));
    await tester.pumpAndSettle();

    expect(find.byType(LoginPage), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

class _ExpiredSessionBookingService extends BookingService {
  @override
  Future<AvailabilityResult> checkAvailability(BookingDraft draft) async =>
      throw const BookingException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );

  @override
  Future<String> createBooking(
    BookingDraft draft, {
    required String idempotencyKey,
  }) async =>
      throw const BookingException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );

  @override
  void dispose() {}
}
