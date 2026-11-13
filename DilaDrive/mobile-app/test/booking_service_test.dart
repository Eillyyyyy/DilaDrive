import 'dart:convert';

import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/services/booking_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

void main() {
  test('customer booking request matches the backend DTO and retry contract',
      () async {
    MobileSession.clear();
    MobileSession.storeResponseCookies(
        {'set-cookie': 'JSESSIONID=booking-session; Path=/'});
    final client = _ResponseClient(http.Response(
      jsonEncode({
        'bookingReference': 'BK-000017',
        'bookingStatus': 'PENDING_VERIFICATION',
      }),
      201,
      headers: {'content-type': 'application/json'},
    ));
    final service = BookingService(client: client);
    final draft = BookingDraft(
      customerName: 'Test Customer',
      phoneNumber: '0123456789',
    )
      ..pickupAddress = 'Kuala Lumpur'
      ..destinationAddress = 'KLIA'
      ..tripType = BookingTripType.airportPickup
      ..bookingDate = DateTime(2030, 1, 2)
      ..landedTime = DateTime(2030, 1, 2, 9, 45)
      ..flightNumber = 'MH123'
      ..passengerCount = 2
      ..luggageCount = 1
      ..luggageSize = 'SMALL'
      ..additionalNotes = 'Meet at arrival hall'
      ..recommendedVehicle = const RecommendedVehicle(
        id: 8,
        model: 'Fleet Sedan',
        type: 'Sedan',
        passengerCapacity: 4,
        luggageCapacity: 2,
      );

    final reference = await service.createBooking(
      draft,
      idempotencyKey: 'create-retry-key',
    );

    expect(reference, 'BK-000017');
    expect(client.requestedMethod, 'POST');
    expect(client.requestedUri!.path, '/api/bookings/customer');
    expect(client.requestHeaders?['cookie'], 'JSESSIONID=booking-session');
    expect(client.requestHeaders?['idempotency-key'], 'create-retry-key');
    final body = jsonDecode(client.requestBody!) as Map<String, dynamic>;
    expect(body, {
      'bookingType': 'Sedan',
      'tripType': 'AIRPORT_PICKUP',
      'bookingDate': '2030-01-02',
      'pickupTime': null,
      'landedTime': '09:45:00',
      'flightNumber': 'MH123',
      'pickupAddress': 'Kuala Lumpur',
      'destinationAddress': 'KLIA',
      'passengerCount': 2,
      'luggageCount': 1,
      'luggageSize': 'SMALL',
      'additionalNotes': 'Meet at arrival hall',
    });
    service.dispose();
    MobileSession.clear();
  });

  test('availability request includes the authenticated mobile session',
      () async {
    MobileSession.clear();
    MobileSession.storeResponseCookies(
        {'set-cookie': 'JSESSIONID=test; Path=/'});
    final client = _ResponseClient(http.Response('Unauthorized', 401));
    final service = BookingService(client: client);
    final draft = BookingDraft(
      customerName: 'Test Customer',
      phoneNumber: '0123456789',
    )
      ..pickupAddress = 'Pickup'
      ..destinationAddress = 'Destination'
      ..bookingDate = DateTime(2030, 1, 1)
      ..pickupTime = DateTime(2030, 1, 1, 10)
      ..passengerCount = 2;

    await expectLater(
      service.checkAvailability(draft),
      throwsA(isA<BookingException>()),
    );
    expect(client.requestHeaders?['cookie'], 'JSESSIONID=test');
    expect(MobileSession.isAuthenticated, isFalse);
    service.dispose();
  });

  test('missing availability endpoint is an explicit error, not success',
      () async {
    final client = _ResponseClient(
      http.Response('Not Found', 404),
    );
    final service = BookingService(client: client);
    final draft = BookingDraft(
      customerName: 'Test Customer',
      phoneNumber: '0123456789',
    )
      ..pickupAddress = 'Melaka Sentral'
      ..destinationAddress = 'Kuala Lumpur Airport'
      ..bookingDate = DateTime(2030, 1, 1)
      ..pickupTime = DateTime(2030, 1, 1, 10)
      ..passengerCount = 2;

    await expectLater(
      service.checkAvailability(draft),
      throwsA(
        isA<BookingException>().having(
          (exception) => exception.message,
          'message',
          contains('not available'),
        ),
      ),
    );
    expect(
      client.requestedUri!.path,
      '/api/bookings/availability/recommendation',
    );
    service.dispose();
  });

  test('pending booking edit uses the owned customer endpoint and session',
      () async {
    MobileSession.clear();
    MobileSession.storeResponseCookies(
        {'set-cookie': 'JSESSIONID=edit-session; Path=/'});
    final client = _ResponseClient(http.Response('{}', 200));
    final service = BookingService(client: client);
    final draft = BookingDraft(customerName: '', phoneNumber: '')
      ..pickupAddress = 'Updated pickup'
      ..destinationAddress = 'Updated destination'
      ..tripType = BookingTripType.normalTransfer
      ..bookingDate = DateTime(2030, 1, 1)
      ..pickupTime = DateTime(2030, 1, 1, 11)
      ..passengerCount = 3
      ..luggageCount = 0;

    await service.updatePendingBooking(17, draft, vehicleType: 'MPV');

    expect(client.requestedMethod, 'PUT');
    expect(client.requestedUri!.path, '/api/bookings/17/customer');
    expect(client.requestHeaders?['cookie'], 'JSESSIONID=edit-session');
    final body = jsonDecode(client.requestBody!) as Map<String, dynamic>;
    expect(body['bookingType'], 'MPV');
    expect(body['pickupAddress'], 'Updated pickup');
    expect(body['passengerCount'], 3);
    service.dispose();
    MobileSession.clear();
  });
}

class _ResponseClient extends http.BaseClient {
  _ResponseClient(this.response);

  final http.Response response;
  Uri? requestedUri;
  String? requestedMethod;
  String? requestBody;
  Map<String, String>? requestHeaders;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    requestedUri = request.url;
    requestedMethod = request.method;
    requestHeaders = request.headers;
    if (request is http.Request) requestBody = request.body;
    return http.StreamedResponse(
      Stream<List<int>>.value(utf8.encode(response.body)),
      response.statusCode,
      headers: response.headers,
    );
  }
}
