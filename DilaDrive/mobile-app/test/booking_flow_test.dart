import 'package:diladrive_customer/services/booking_service.dart';
import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_details_page.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_fare_page.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_location_page.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_review_page.dart';
import 'package:diladrive_customer/services/booking_location_service.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_trip_details_sheet.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  BookingDraft createDraft() => BookingDraft(
        customerName: 'Test Customer',
        phoneNumber: '0123456789',
      );

  Future<void> chooseLocation(
      WidgetTester tester, bool pickup, String address) async {
    final tile = find
        .byKey(ValueKey(pickup ? 'pickup-location' : 'destination-location'));
    await tester.ensureVisible(tile);
    await tester.tap(tile);
    await tester.pumpAndSettle();
    await tester.enterText(
        find.byKey(const ValueKey('location-search')), address);
    await tester.tap(find.byTooltip('Search addresses'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ListTile, address));
    await tester.pumpAndSettle();
  }

  testWidgets('unresolved manually entered locations cannot continue',
      (tester) async {
    final draft = createDraft()
      ..pickupAddress = 'Unresolved pickup'
      ..destinationAddress = 'Unresolved drop-off'
      ..pickupManuallyConfirmed = true
      ..destinationManuallyConfirmed = true;
    await tester.pumpWidget(MaterialApp(
        home: BookingLocationPage(
            draft: draft, mapBuilder: (_, __) => const SizedBox())));
    final button = find.byKey(const ValueKey('continue-trip-details'));
    await tester.ensureVisible(button);
    expect(tester.widget<FilledButton>(button).onPressed, isNull);
    expect(draft.roadRoute, isNull);
  });
  testWidgets('search fields match backend address limits', (tester) async {
    await tester.pumpWidget(MaterialApp(
        home: BookingLocationPage(
            draft: createDraft(),
            bookingService: _TestBookingService(),
            mapBuilder: (_, __) => const SizedBox())));
    for (final key in ['pickup-location', 'destination-location']) {
      final tile = find.byKey(ValueKey(key));
      await tester.ensureVisible(tile);
      await tester.tap(tile);
      await tester.pumpAndSettle();
      final field = tester
          .widget<TextFormField>(find.byKey(const ValueKey('location-search')));
      expect(field.controller, isNotNull);
      expect(tester.widget<TextField>(find.byType(TextField)).maxLength, 500);
      await tester.tap(find.descendant(
          of: find.byType(AppBar), matching: find.byType(BackButton)));

      await tester.pumpAndSettle();
    }
  });

  testWidgets('selected location data survives returning from trip details',
      (tester) async {
    final draft = createDraft();
    await tester.pumpWidget(MaterialApp(
        home: BookingLocationPage(
            draft: draft,
            bookingService: _TestBookingService(),
            mapBuilder: (_, __) => const SizedBox(),
            locationProvider: _FakeBookingLocationProvider())));
    await chooseLocation(tester, true, 'Melaka Sentral');
    await chooseLocation(tester, false, 'Kuala Lumpur Airport');
    final pickup = draft.pickupCoordinates,
        destination = draft.destinationCoordinates;
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Continue to trip details'));

    await tester.tap(find.text('Continue to trip details'));
    await tester.pumpAndSettle();
    expect(find.byType(BookingTripDetailsSheet), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(draft.pickupAddress, 'Melaka Sentral');
    expect(draft.destinationAddress, 'Kuala Lumpur Airport');
    expect(draft.pickupCoordinates, same(pickup));
    expect(draft.destinationCoordinates, same(destination));
  });

  testWidgets('airport pickup supports an optional flight number',
      (tester) async {
    final draft = createDraft()..tripType = BookingTripType.airportPickup;
    await tester.pumpWidget(
      MaterialApp(home: BookingDetailsPage(draft: draft)),
    );

    expect(find.text('Landed time'), findsOneWidget);
    await tester.tap(find.text('Check vehicle availability'));
    await tester.pumpAndSettle();

    expect(find.text('Flight number (optional)'), findsOneWidget);
  });

  testWidgets('flight and notes fields match backend length limits',
      (tester) async {
    final draft = createDraft()..tripType = BookingTripType.airportPickup;
    await tester.pumpWidget(
      MaterialApp(home: BookingDetailsPage(draft: draft)),
    );

    final fields = tester.widgetList<TextField>(find.byType(TextField));
    final flightField = fields.singleWhere(
        (field) => field.decoration?.labelText == 'Flight number (optional)');
    final notesField = tester.widget<TextField>(find.byType(TextField).last);

    expect(flightField.maxLength, 20);
    expect(notesField.maxLength, 1000);
  });

  testWidgets('fare step displays its estimate and continues to review',
      (tester) async {
    final draft = createDraft()
      ..pickupAddress = 'Melaka Sentral'
      ..destinationAddress = 'Kuala Lumpur Airport'
      ..pickupCoordinates = const BookingCoordinates(
        latitude: 2.1896,
        longitude: 102.2501,
      )
      ..destinationCoordinates = const BookingCoordinates(
        latitude: 3.1390,
        longitude: 101.6869,
      )
      ..bookingDate = DateTime(2030, 1, 1)
      ..pickupTime = DateTime(2030, 1, 1, 10)
      ..recommendedVehicle = const RecommendedVehicle(
        id: 8,
        model: 'Fleet Sedan',
        type: 'SEDAN',
        passengerCapacity: 4,
        luggageCapacity: 2,
      );

    draft.availabilityCheckedFor = draft.availabilityKey;
    await tester.pumpWidget(
      MaterialApp(
          home: BookingFarePage(
              draft: draft, bookingService: _TestBookingService())),
    );

    await tester.pumpAndSettle();
    expect(find.text('Pickup Location'), findsOneWidget);
    expect(find.text('Destination'), findsOneWidget);
    expect(find.text('Distance (KM)'), findsOneWidget);
    expect(find.text('Recommended Vehicle: SEDAN \u00b7 Fleet Sedan'),
        findsOneWidget);
    expect(find.text('Estimated Fare'), findsOneWidget);
    expect(draft.fareEstimate, isNotNull);
    expect(find.text('Calculate fare estimate'), findsNothing);
    expect(find.text('Review booking'), findsOneWidget);

    await tester.tap(find.text('Review booking'));
    await tester.pumpAndSettle();

    expect(find.byType(BookingReviewPage), findsOneWidget);
    expect(draft.fareEstimate, isNotNull);
  });

  testWidgets('current location fills pickup while destination can be typed',
      (tester) async {
    final draft = createDraft();
    await tester.pumpWidget(
      MaterialApp(
        home: BookingLocationPage(
          draft: draft,
          bookingService: _TestBookingService(),
          mapBuilder: (_, __) => const SizedBox(),
          locationProvider: _FakeBookingLocationProvider(),
        ),
      ),
    );

    await tester.tap(find.byTooltip('Use Current Location'));
    await tester.pumpAndSettle();
    expect(draft.pickupAddress, 'Current pickup');
    await chooseLocation(tester, false, 'Manual destination');
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('Continue to trip details'));
    await tester.tap(find.text('Continue to trip details'));
    await tester.pumpAndSettle();

    expect(draft.pickupCoordinates, isNotNull);
    expect(draft.destinationCoordinates, isNotNull);
    expect(draft.pickupAddress, 'Current pickup');
    expect(draft.destinationAddress, 'Manual destination');
  });
}

class _FakeBookingLocationProvider implements BookingLocationProvider {
  static const _currentCoordinates = BookingCoordinates(
    latitude: 2.1896,
    longitude: 102.2501,
  );

  @override
  Future<String> addressForCoordinates(BookingCoordinates coordinates) async =>
      'Current pickup';

  @override
  Future<BookingCoordinates> coordinatesForAddress(String address) async =>
      address == 'Current pickup' || address == 'Melaka Sentral'
          ? _currentCoordinates
          : const BookingCoordinates(latitude: 3.1390, longitude: 101.6869);

  @override
  Future<BookingCoordinates> currentCoordinates() async => _currentCoordinates;
}

class _TestBookingService extends BookingService {
  @override
  Future<Map<String, dynamic>> previewRoute(BookingDraft draft) async => {
        'distanceKm': 58,
        'durationSeconds': 7200,
        'geometry': [
          [102.2501, 2.1896],
          [101.6869, 3.1390]
        ],
      };
  @override
  Future<FareEstimate> estimateFare(BookingDraft draft) async =>
      const FareEstimate(
          distanceKm: 58,
          baseFare: 10,
          distanceCharge: 116,
          additionalCharges: [],
          total: 126,
          durationSeconds: 7200);
}
