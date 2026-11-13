import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_review_page.dart';
import 'package:diladrive_customer/services/booking_service.dart';
import 'package:diladrive_customer/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('a submission retry reuses the original idempotency key',
      (tester) async {
    final service = _RetryingBookingService();
    final draft =
        BookingDraft(customerName: 'Test Customer', phoneNumber: '0123')
          ..pickupAddress = 'Kuala Lumpur'
          ..destinationAddress = 'Melaka'
          ..bookingDate = DateTime(2026, 12, 1)
          ..recommendedVehicle = const RecommendedVehicle(
            id: 8,
            model: 'Fleet Sedan',
            type: 'Sedan',
            passengerCapacity: 4,
            luggageCapacity: 2,
          );

    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: BookingReviewPage(draft: draft, bookingService: service),
    ));

    await tester.tap(find.text('Confirm booking'));
    await tester.pumpAndSettle();
    expect(find.text('The server took too long to respond.'), findsOneWidget);

    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();

    expect(service.idempotencyKeys, hasLength(2));
    expect(service.idempotencyKeys[0], service.idempotencyKeys[1]);
    expect(find.text('Booking reference: BK-000123'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

class _RetryingBookingService extends BookingService {
  int requestCount = 0;
  final List<String> idempotencyKeys = [];

  @override
  Future<String> createBooking(
    BookingDraft draft, {
    required String idempotencyKey,
  }) async {
    idempotencyKeys.add(idempotencyKey);
    requestCount++;
    if (requestCount == 1) {
      throw const BookingException('The server took too long to respond.');
    }
    return 'BK-000123';
  }

  @override
  void dispose() {}
}
