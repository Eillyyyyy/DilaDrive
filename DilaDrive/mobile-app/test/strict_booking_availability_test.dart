import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/services/booking_service.dart';
import 'package:diladrive_customer/widgets/booking_availability_message.dart';

BookingDraft draft() => BookingDraft(customerName: 'Test', phoneNumber: '0123')
  ..pickupAddress = 'Pickup'
  ..destinationAddress = 'Destination'
  ..pickupCoordinates = const BookingCoordinates(latitude: 3, longitude: 101)
  ..destinationCoordinates =
      const BookingCoordinates(latitude: 4, longitude: 102)
  ..bookingDate = DateTime(2035, 1, 1)
  ..pickupTime = DateTime(2035, 1, 1, 10)
  ..passengerCount = 4
  ..smallCount = 1
  ..mediumCount = 1;
final available = {
  'available': true,
  'availabilityStatus': 'AVAILABLE',
  'vehicle': {
    'model': 'Sedan',
    'vehicleType': 'Sedan',
    'passengerCapacity': 4,
    'luggageCapacity': 4
  },
  'estimatedDistanceKm': 58,
  'durationSeconds': 7200,
  'vehicleModel': 'Sedan',
  'baseFare': 10,
  'ratePerKm': 2,
  'distanceCharge': 116,
  'additionalCharges': <Object>[],
  'totalFare': 126,
};
void main() {
  test(
      'availability needs no pricing and pricing errors preserve valid selections',
      () async {
    final d = draft();
    final service = BookingService(client: MockClient((request) async {
      if (request.url.path.endsWith('/availability')) {
        return http.Response(
            jsonEncode({
              ...available,
              'totalFare': null,
              'baseFare': null,
              'ratePerKm': null,
              'distanceCharge': null
            }),
            200);
      }
      return http.Response(
          '{"code":"PRICING_NOT_CONFIGURED","message":"Configure category pricing."}',
          422);
    }));
    addTearDown(service.dispose);
    expect((await service.checkAvailability(d)).isAvailable, true);
    expect(d.hasFreshAvailability, true);
    expect(d.fareEstimate, isNull);
    await expectLater(
        service.estimateFare(d),
        throwsA(isA<BookingException>()
            .having((e) => e.code, 'code', 'PRICING_NOT_CONFIGURED')));
    expect(d.hasFreshAvailability, true);
    expect(d.recommendedVehicle, isNotNull);
    await expectLater(service.createBooking(d, idempotencyKey: 'no-price'),
        throwsA(isA<BookingException>()));
  });
  test('a late response from another service cannot overwrite the latest check',
      () async {
    final d = draft();
    final first = Completer<http.Response>();
    final oldService = BookingService(client: MockClient((_) => first.future));
    final newService = BookingService(
        client:
            MockClient((_) async => http.Response(jsonEncode(available), 200)));
    addTearDown(oldService.dispose);
    addTearDown(newService.dispose);
    final oldRequest = oldService.checkAvailability(d);
    await newService.checkAvailability(d);
    expect(d.hasFreshAvailability, true);
    first.complete(http.Response(
        '{"available":false,"availabilityStatus":"SCHEDULE_UNAVAILABLE"}',
        200));
    await oldRequest;
    expect(d.hasFreshAvailability, true);
    expect(d.recommendedVehicle!.type, 'Sedan');
  });
  for (final status in AvailabilityStatus.values
      .where((s) => s != AvailabilityStatus.available)) {
    test('${status.apiValue} blocks progression and removes stale data',
        () async {
      final d = draft();
      final service = BookingService(
          client: MockClient((_) async => http.Response(
              jsonEncode({
                'available': false,
                'availabilityStatus': status.apiValue,
                'message': status.message
              }),
              200)));
      addTearDown(service.dispose);
      final result = await service.checkAvailability(d);
      expect(result.status, status);
      expect(result.isAvailable, false);
      expect(d.hasFreshAvailability, false);
      expect(d.recommendedVehicle, isNull);
      expect(d.fareEstimate, isNull);
    });
    testWidgets('${status.apiValue} shows the correct recovery action',
        (tester) async {
      var retry = false, change = false;
      await tester.pumpWidget(MaterialApp(
          home: Scaffold(
              body: BookingAvailabilityMessage(
                  result: AvailabilityResult.unavailable(status.message,
                      status: status),
                  onRetry: () => retry = true,
                  onChange: () => change = true))));
      expect(find.text(status.message), findsOneWidget);
      final serviceFailure = status == AvailabilityStatus.serviceError ||
          status == AvailabilityStatus.fleetNotConfigured;
      if (serviceFailure) {
        await tester.tap(find.text('Retry'));
        expect(retry, true);
      }
      final label = status == AvailabilityStatus.scheduleUnavailable
          ? 'Change Date/Time'
          : 'Change Trip Details';
      await tester.tap(find.text(label));
      expect(change, true);
      expect(find.text('Continue to Fare'), findsNothing);
    });
  }
  test('HTTP 503 is SERVICE_ERROR, never schedule unavailability', () async {
    final service = BookingService(
        client: MockClient((_) async => http.Response(
            '{"message":"Company pricing is not configured"}', 503)));
    addTearDown(service.dispose);
    expect((await service.checkAvailability(draft())).status,
        AvailabilityStatus.serviceError);
  });
  test(
      'old backend response is a service error instead of guessing availability',
      () async {
    final service = BookingService(
        client:
            MockClient((_) async => http.Response('{"available":false}', 200)));
    addTearDown(service.dispose);
    final result = await service.checkAvailability(draft());
    expect(result.status, AvailabilityStatus.serviceError);
    expect(result.message, contains('API_BASE_URL'));
  });
  test('outdated response cannot restore an old recommendation', () async {
    final pending = Completer<http.Response>();
    final d = draft();
    final service = BookingService(client: MockClient((_) => pending.future));
    addTearDown(service.dispose);
    final request = service.checkAvailability(d);
    d.largeCount = 2;
    pending.complete(http.Response(jsonEncode(available), 200));
    await request;
    expect(d.hasFreshAvailability, false);
    expect(d.recommendedVehicle, isNull);
    expect(d.fareEstimate, isNull);
  });
  for (final field in ['passengers', 'bags', 'date', 'route']) {
    test('changing $field invalidates availability and blocks stale submission',
        () async {
      final d = draft();
      var calls = 0;
      final service = BookingService(client: MockClient((_) async {
        calls++;
        return http.Response(jsonEncode(available), 200);
      }));
      addTearDown(service.dispose);
      expect((await service.checkAvailability(d)).isAvailable, true);
      expect(d.hasFreshAvailability, true);
      switch (field) {
        case 'passengers':
          d.passengerCount++;
          break;
        case 'bags':
          d.mediumCount++;
          break;
        case 'date':
          d.pickupTime = d.pickupTime!.add(const Duration(days: 1));
          break;
        case 'route':
          d.destinationCoordinates =
              const BookingCoordinates(latitude: 4.1, longitude: 102.1);
          break;
      }
      expect(d.hasFreshAvailability, false);
      await expectLater(
          service.estimateFare(d), throwsA(isA<BookingException>()));
      await expectLater(service.createBooking(d, idempotencyKey: 'retry'),
          throwsA(isA<BookingException>()));
      expect(calls, 1);
      expect((await service.checkAvailability(d)).isAvailable, true);
      expect(d.hasFreshAvailability, true);
    });
  }
}
