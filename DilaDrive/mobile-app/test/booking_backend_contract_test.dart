import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/services/booking_service.dart';

void main() {
  BookingDraft draft() =>
      BookingDraft(customerName: 'Customer', phoneNumber: '0123456789')
        ..pickupAddress = 'Pickup'
        ..destinationAddress = 'Destination'
        ..pickupCoordinates =
            const BookingCoordinates(latitude: 3, longitude: 101)
        ..destinationCoordinates =
            const BookingCoordinates(latitude: 4, longitude: 102)
        ..bookingDate = DateTime(2035, 1, 1)
        ..pickupTime = DateTime(2035, 1, 1, 10)
        ..passengerCount = 4
        ..smallCount = 1
        ..mediumCount = 1;
  final quote = {
    'available': true,
    'availabilityStatus': 'AVAILABLE',
    'vehicle': {
      'vehicleId': 36,
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
  test('mixed luggage units are preserved', () {
    expect(draft().luggageUnits, 3);
  });
  test(
      'availability sends coordinates and bag quantities, uses backend road fare',
      () async {
    final d = draft();
    final paths = <String>[];
    final service = BookingService(client: MockClient((request) async {
      paths.add(request.url.path);
      expect(request.method, 'POST');
      final body = jsonDecode(request.body) as Map;
      expect(body['pickupLatitude'], 3);
      expect(body['smallCount'], 1);
      expect(body['mediumCount'], 1);
      expect(body.containsKey('customerId'), false);
      expect(body.containsKey('fare'), false);
      return http.Response(jsonEncode(quote), 200);
    }));
    final result = await service.checkAvailability(d);
    expect(result.isAvailable, true);
    expect(d.fareEstimate, isNull);
    final fare = await service.estimateFare(d);
    expect(fare.total, 126);
    expect(fare.durationSeconds, 7200);
    expect(paths, ['/api/bookings/availability', '/api/bookings/estimate']);
    service.dispose();
  });
  test('confirm sends retry key and reads persisted pending reference',
      () async {
    final d = draft()
      ..recommendedVehicle =
          RecommendedVehicle.fromJson(quote['vehicle'] as Map<String, dynamic>);
    d.fareEstimate = FareEstimate.fromJson(quote);
    d.availabilityCheckedFor = d.availabilityKey;
    final service = BookingService(client: MockClient((request) async {
      expect(request.url.path, '/api/bookings/customer/complete');
      expect(request.headers['Idempotency-Key'], 'unique-key');
      final body = jsonDecode(request.body) as Map;
      expect(body.containsKey('preferredVehicleId'), isFalse);
      expect(body['vehicleCategory'], 'Sedan');
      expect(body.containsKey('vehicleId'), false);
      expect(body.containsKey('driverId'), false);
      return http.Response(
          '{"bookingReference":"BK-000123","bookingStatus":"PENDING"}', 201);
    }));
    expect(await service.createBooking(d, idempotencyKey: 'unique-key'),
        'BK-000123');
    service.dispose();
  });
  test('routing failure is surfaced without fake fare', () async {
    final service = BookingService(
        client: MockClient((_) async =>
            http.Response('{"message":"Routing unavailable"}', 503)));
    expect((await service.checkAvailability(draft())).status,
        AvailabilityStatus.serviceError);
    service.dispose();
  });
  test(
      'fare refresh keeps the displayed recommendation and backend estimate together',
      () async {
    final d = draft();
    final updated = {
      ...quote,
      'vehicle': {
        'model': 'Van',
        'vehicleType': 'Van',
        'passengerCapacity': 8,
        'luggageCapacity': 10
      },
      'vehicleModel': 'Van',
      'totalFare': 180,
    };
    final service = BookingService(
        client:
            MockClient((_) async => http.Response(jsonEncode(updated), 200)));
    await service.checkAvailability(d);
    final fare = await service.estimateFare(d);
    expect(d.recommendedVehicle!.type, 'Van');
    expect(d.fareEstimate, same(fare));
    expect(fare.total, 180);
    service.dispose();
  });
  test('manual address without coordinates cannot request quote', () async {
    final d = draft()..pickupCoordinates = null;
    final service = BookingService(client: MockClient((_) async {
      fail('Should not send unrouteable addresses');
    }));
    expect((await service.checkAvailability(d)).status,
        AvailabilityStatus.serviceError);
    service.dispose();
  });
  test('no availability clears stale fare', () async {
    final d = draft()..fareEstimate = FareEstimate.fromJson(quote);
    final service = BookingService(
        client: MockClient((_) async => http.Response(
            '{"available":false,"availabilityStatus":"SCHEDULE_UNAVAILABLE","message":"Change pickup time"}',
            200)));
    expect((await service.checkAvailability(d)).message, 'Change pickup time');
    expect(d.fareEstimate, isNull);
    service.dispose();
  });
}
