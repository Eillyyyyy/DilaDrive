import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:diladrive_customer/models/customer_booking.dart';
import 'package:diladrive_customer/services/customer_booking_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';

void main() {
  tearDown(MobileSession.clear);

  test('loads the authenticated customer booking list', () async {
    MobileSession.storeResponseCookies({
      'set-cookie':
          'JSESSIONID=test-session; Path=/; HttpOnly; SameSite=Strict',
    });
    late http.Request capturedRequest;
    final client = MockClient((request) async {
      capturedRequest = request;
      return http.Response(
        jsonEncode([
          {
            'bookingId': 12,
            'bookingType': 'NORMAL',
            'tripDate': '2026-12-01',
            'pickupTime': '09:30:00',
            'pickupAddress': 'Kuala Lumpur',
            'destinationAddress': 'Melaka',
            'passengerCount': 2,
            'luggageCount': 1,
            'tripFare': 120.5,
            'status': 'PENDING_VERIFICATION',
          },
        ]),
        200,
        headers: {'content-type': 'application/json'},
      );
    });
    final service = CustomerBookingService(client: client);

    final bookings = await service.getMyBookings();

    expect(capturedRequest.url.path, '/api/bookings/mine');
    expect(capturedRequest.headers['cookie'], 'JSESSIONID=test-session');
    expect(bookings, hasLength(1));
    expect(bookings.single, isA<CustomerBooking>());
    expect(bookings.single.reference, 'BK-000012');
    expect(bookings.single.isPending, isTrue);
    expect(bookings.single.fare, 120.5);
    service.dispose();
  });

  test('clears the session after the server rejects it', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=expired; Path=/; HttpOnly',
    });
    final service = CustomerBookingService(
      client: MockClient((_) async => http.Response('', 401)),
    );

    await expectLater(
      service.getMyBookings(),
      throwsA(isA<CustomerBookingException>()),
    );
    expect(MobileSession.isAuthenticated, isFalse);
    service.dispose();
  });

  test('cancels a booking through the customer session route', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    late http.Request capturedRequest;
    final service = CustomerBookingService(
      client: MockClient((request) async {
        capturedRequest = request;
        return http.Response('{}', 200);
      }),
    );

    await service.cancelBooking(17);

    expect(capturedRequest.method, 'POST');
    expect(capturedRequest.url.path, '/api/bookings/17/cancel');
    expect(capturedRequest.headers['cookie'], 'JSESSIONID=customer-session');
    service.dispose();
  });

  test('surfaces backend cancellation rules for unavailable bookings',
      () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    final service = CustomerBookingService(
      client: MockClient((_) async => http.Response(
            jsonEncode({
              'message':
                  'A booking with an active assignment cannot be cancelled.'
            }),
            409,
          )),
    );

    await expectLater(
      service.cancelBooking(17),
      throwsA(isA<CustomerBookingException>().having(
        (error) => error.message,
        'message',
        contains('active assignment'),
      )),
    );
    service.dispose();
  });

  test('normalizes booking statuses used by the booking workflow', () {
    final booking = CustomerBooking.fromJson({
      'bookingId': 3,
      'status': 'OTW',
    });

    expect(booking.isActive, isTrue);
    expect(booking.statusLabel, 'On the way');
  });
}
