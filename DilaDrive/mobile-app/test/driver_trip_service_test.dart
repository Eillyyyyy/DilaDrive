import 'dart:convert';

import 'package:diladrive_customer/services/driver_trip_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  tearDown(MobileSession.clear);

  test('loads assignments through the authenticated driver endpoint', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=driver-session; Path=/; HttpOnly',
    });
    late http.Request captured;
    final service = DriverTripService(
      client: MockClient((request) async {
        captured = request;
        return http.Response(
          jsonEncode([
            {
              'assignmentId': 4,
              'bookingId': 12,
              'assignmentStatus': 'ASSIGNED',
              'pickupAddress': 'Pickup',
              'destinationAddress': 'Destination',
            },
          ]),
          200,
        );
      }),
    );
    addTearDown(service.dispose);

    final trips = await service.getMyTrips();

    expect(captured.url.path, '/api/assignments/mine');
    expect(captured.headers['cookie'], 'JSESSIONID=driver-session');
    expect(trips.single.assignmentId, 4);
    expect(trips.single.nextStatus, 'OTW');
  });

  test('clears an expired driver session and requests sign in', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=expired; Path=/; HttpOnly',
    });
    final service = DriverTripService(
      client: MockClient((_) async => http.Response('Unauthorized', 401)),
    );
    addTearDown(service.dispose);

    await expectLater(
      service.getMyTrips(),
      throwsA(isA<DriverTripException>().having(
        (error) => error.requiresLogin,
        'requiresLogin',
        isTrue,
      )),
    );
    expect(MobileSession.isAuthenticated, isFalse);
  });

  test('records cash received through the authenticated payment endpoint',
      () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=driver-session; Path=/; HttpOnly',
    });
    late http.Request captured;
    final service = DriverTripService(
      client: MockClient((request) async {
        captured = request;
        return http.Response('{}', 201);
      }),
    );
    addTearDown(service.dispose);

    await service.recordCashReceived(12, 1.15);

    expect(captured.method, 'POST');
    expect(captured.url.path, '/api/payments/bookings/12/cash');
    expect(captured.headers['cookie'], 'JSESSIONID=driver-session');
    expect(jsonDecode(captured.body), {'amount': '1.15'});
  });
}
