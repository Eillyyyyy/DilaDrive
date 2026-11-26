import 'dart:async';
import 'dart:convert';

import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_location_page.dart';
import 'package:diladrive_customer/services/booking_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  testWidgets('map persists and the chosen live quote is submitted once',
      (tester) async {
    final future = DateTime.now().add(const Duration(days: 7));
    final draft = BookingDraft(customerName: 'Customer', phoneNumber: '0123')
      ..pickupAddress = 'Pickup'
      ..destinationAddress = 'Destination'
      ..pickupCoordinates =
          const BookingCoordinates(latitude: 3, longitude: 101)
      ..destinationCoordinates =
          const BookingCoordinates(latitude: 4, longitude: 102)
      ..bookingDate = DateUtils.dateOnly(future)
      ..pickupTime = DateTime(future.year, future.month, future.day, 9, 30)
      ..smallCount = 1
      ..mediumCount = 2
      ..largeCount = 1
      ..showFlightDetails = true
      ..flightNumber = 'MH123'
      ..flightDate = DateTime(future.year, future.month, future.day)
      ..flightTime = '08:17'
      ..roadRoute = {
        'distanceKm': 41.235,
        'durationSeconds': 1800,
        'geometry': [
          [101, 3],
          [102, 4],
        ],
      };
    final response = Completer<http.Response>();
    final requests = <http.Request>[];
    var quoteNumber = 0;
    Map<String, Object> vehicle(String category) => {
          'model': category == 'Compact' ? 'AXIA' : 'ALZA',
          'vehicleType': category,
          'passengerCapacity': category == 'Compact' ? 4 : 6,
          'luggageCapacity': 8,
        };
    http.Response json(Map<String, Object> body, [int status = 200]) =>
        http.Response(jsonEncode(body), status);
    final service = BookingService(client: MockClient((request) async {
      requests.add(request);
      final body = jsonDecode(request.body) as Map;
      switch (request.url.path.split('/').last) {
        case 'availability':
          return json({
            'available': true,
            'availabilityStatus': 'AVAILABLE',
            'vehicle': vehicle('Compact'),
            'suitableVehicleTypes': ['Compact', 'MPV'],
          });
        case 'estimate':
          final category = body['vehicleCategory'] as String;
          return json({
            'available': true,
            'vehicle': vehicle(category),
            'vehicleModel': category,
            'estimatedDistanceKm': 41.235,
            'durationSeconds': 1800,
            'baseFare': 0,
            'ratePerKm': 2,
            'distanceCharge': 82.47,
            'additionalCharges': [],
            'totalFare': 82.47,
            'quoteToken': 'quote-$category-${++quoteNumber}',
          });
        case 'complete':
          return response.future;
        default:
          fail('Unexpected request ${request.url}');
      }
    }));
    addTearDown(service.dispose);
    const mapKey = ValueKey('persistent-test-map');
    await tester.pumpWidget(MaterialApp(
      home: BookingLocationPage(
        draft: draft,
        bookingService: service,
        mapBuilder: (_, __) => const SizedBox(key: mapKey),
      ),
    ));
    final map = tester.element(find.byKey(mapKey));
    Future<void> tap(String label) async {
      final target = find.text(label);
      await tester.ensureVisible(target);
      await tester.pumpAndSettle();
      await tester.tap(target);
      await tester.pumpAndSettle();
    }

    await tap('Continue to trip details');
    expect(tester.element(find.byKey(mapKey)), same(map));
    await tap('Find Available Vehicles');
    expect(draft.luggageUnits, 8);
    expect(find.text('Recommended · Available when checked'), findsOneWidget);
    expect(find.text('Compact'), findsOneWidget);

    await tester.drag(find.byType(ListView), const Offset(0, -220));
    await tester.pumpAndSettle();
    await tap('MPV');
    await tap('Continue with MPV');
    expect(tester.element(find.byKey(mapKey)), same(map));
    expect(draft.recommendedVehicle?.model, 'ALZA');
    expect(draft.fareEstimate?.total, 82.47);
    expect(find.text('MPV'), findsOneWidget);
    expect(find.text('1 / 2 / 1'), findsOneWidget);
    expect(find.text('MH123'), findsOneWidget);
    expect(find.text('08:17 AM'), findsOneWidget);
    expect(find.text('09:30 AM'), findsOneWidget);
    expect(find.text('Trip type'), findsNothing);

    final acceptedToken = draft.fareEstimate!.quoteToken;
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    await tap('Continue with MPV');
    expect(quoteNumber, 4);
    expect(draft.fareEstimate!.quoteToken, acceptedToken);

    final button = find.text('Request Booking');
    await tester.tap(button);
    await tester.tap(button, warnIfMissed: false);
    await tester.pump();
    final submission =
        requests.where((request) => request.url.path.endsWith('/complete'));
    expect(submission, hasLength(1));
    expect(
        submission.single.headers.entries
            .singleWhere(
                (entry) => entry.key.toLowerCase() == 'idempotency-key')
            .value,
        isNotEmpty);
    expect(jsonDecode(submission.single.body),
        containsPair('vehicleCategory', 'MPV'));
    expect(jsonDecode(submission.single.body),
        containsPair('acceptedQuoteToken', acceptedToken));
    expect(find.text('Booking reference: DD-123'), findsNothing);

    response.complete(json({
      'bookingReference': 'DD-123',
      'bookingStatus': 'PENDING',
    }, 201));
    await tester.pumpAndSettle();
    expect(find.text('Booking reference: DD-123'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'compact sheet keeps the action above the keyboard and rejects a past trip',
      (tester) async {
    tester.view
      ..physicalSize = const Size(360, 640)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);

    final past = DateTime.now().subtract(const Duration(days: 1));
    final draft = BookingDraft(customerName: 'Customer', phoneNumber: '0123')
      ..pickupAddress = 'Pickup'
      ..destinationAddress = 'Destination'
      ..pickupCoordinates =
          const BookingCoordinates(latitude: 3, longitude: 101)
      ..destinationCoordinates =
          const BookingCoordinates(latitude: 4, longitude: 102)
      ..bookingDate = DateUtils.dateOnly(past)
      ..pickupTime = past
      ..roadRoute = {
        'distanceKm': 50,
        'durationSeconds': 1800,
        'geometry': [
          [101, 3],
          [102, 4],
        ],
      };
    await tester.pumpWidget(MaterialApp(
      home: BookingLocationPage(
        draft: draft,
        mapBuilder: (_, __) => const SizedBox(),
      ),
    ));
    await tester.tap(find.text('Continue to trip details'));
    await tester.pumpAndSettle();
    tester.view.viewInsets = const FakeViewPadding(bottom: 330);
    await tester.pumpAndSettle();
    final action = find.text('Find Available Vehicles');
    expect(tester.getBottomRight(action).dy, lessThan(310));
    expect(tester.takeException(), isNull);
    tester.view.resetViewInsets();
    await tester.pumpAndSettle();
    await tester.tap(action);
    await tester.pump();
    expect(
        find.text('The booking date cannot be in the past.'), findsOneWidget);
    expect(find.text('Choose your ride'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
