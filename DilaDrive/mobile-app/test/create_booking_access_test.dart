import 'dart:convert';

import 'package:diladrive_customer/models/mobile_user.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_location_page.dart';
import 'package:diladrive_customer/screens/customer/create_booking.dart';
import 'package:diladrive_customer/services/customer_profile_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

void main() {
  tearDown(MobileSession.clear);

  testWidgets('pending customer is stopped before booking details',
      (tester) async {
    MobileSession.storeResponseCookies({'set-cookie': 'JSESSIONID=pending'});
    final client = _ProfileClient(status: 'PENDING');

    await tester.pumpWidget(
      MaterialApp(
        home: CreateBookingPage(
          customer: _customer(status: 'PENDING'),
          profileService: CustomerProfileService(client: client),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Admin verification required'), findsOneWidget);
    expect(find.byType(BookingLocationPage), findsNothing);
    expect(client.requestedUri!.path, '/api/customers/me');
    expect(client.requestHeaders?['cookie'], 'JSESSIONID=pending');
  });

  testWidgets('current active profile allows a customer to create a booking',
      (tester) async {
    MobileSession.storeResponseCookies({'set-cookie': 'JSESSIONID=active'});
    final client = _ProfileClient(status: 'ACTIVE');

    await tester.pumpWidget(
      MaterialApp(
        home: CreateBookingPage(
          // The session's original status may be stale after Admin verifies it.
          customer: _customer(status: 'PENDING'),
          profileService: CustomerProfileService(client: client),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(BookingLocationPage), findsOneWidget);
    expect(find.text('Where are you going?'), findsOneWidget);
  });

  testWidgets('missing verification status can be checked again',
      (tester) async {
    MobileSession.storeResponseCookies({'set-cookie': 'JSESSIONID=unknown'});
    final client = _ProfileClient(status: null);

    await tester.pumpWidget(
      MaterialApp(
        home: CreateBookingPage(
          customer: _customer(status: 'PENDING'),
          profileService: CustomerProfileService(client: client),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Verification status unavailable'), findsOneWidget);
    expect(find.text('Where are you going?'), findsNothing);
    await tester.tap(find.text('Check again'));
    await tester.pumpAndSettle();

    expect(client.requestCount, 2);
  });
}

MobileUser _customer({required String status}) => MobileUser(
      id: 42,
      name: 'Test Customer',
      email: 'customer@example.test',
      phone: '0123456789',
      status: status,
      role: 'CUSTOMER',
    );

class _ProfileClient extends http.BaseClient {
  _ProfileClient({required this.status});

  final String? status;
  Uri? requestedUri;
  Map<String, String>? requestHeaders;
  int requestCount = 0;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    requestCount++;
    requestedUri = request.url;
    requestHeaders = request.headers;
    final body = jsonEncode({
      'id': 42,
      'name': 'Test Customer',
      'email': 'customer@example.test',
      'phone': '0123456789',
      'status': status,
      'type': 'NORMAL',
    });
    return http.StreamedResponse(
      Stream<List<int>>.value(utf8.encode(body)),
      200,
      headers: const {'content-type': 'application/json'},
    );
  }
}
