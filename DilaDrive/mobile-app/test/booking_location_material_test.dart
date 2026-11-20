import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_details_page.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_location_page.dart';
import 'package:diladrive_customer/services/booking_location_service.dart';
import 'package:diladrive_customer/services/booking_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('pickup and destination share a clipped white Material panel',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
        home: BookingLocationPage(
            draft: BookingDraft(customerName: 'Customer', phoneNumber: '0123'),
            mapBuilder: (_, __) => const SizedBox())));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    for (final key in ['pickup-location', 'destination-location']) {
      final tile = find.byKey(ValueKey(key));
      expect(tile, findsOneWidget);
      final materials =
          find.ancestor(of: tile, matching: find.byType(Material));
      final material = tester.widget<Material>(materials.first);
      expect(material.color, Colors.white);
      expect(material.borderRadius, BorderRadius.circular(20));
      expect(material.clipBehavior, Clip.antiAlias);
    }
  });

  testWidgets('both searches and suggestions render, tap, and retain locations',
      (tester) async {
    final draft = BookingDraft(customerName: 'Customer', phoneNumber: '0123');
    final service = _RoadService();
    addTearDown(service.dispose);
    await tester.pumpWidget(MaterialApp(
        home: BookingLocationPage(
            draft: draft,
            locationProvider: _Locations(),
            bookingService: service,
            mapBuilder: (_, __) => const SizedBox())));
    for (final pickup in [true, false]) {
      final address = pickup ? 'Pickup hotel' : 'Destination airport';
      final tile = find
          .byKey(ValueKey(pickup ? 'pickup-location' : 'destination-location'));
      await tester.ensureVisible(tile);
      await tester.tap(tile);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.enterText(
          find.byKey(const ValueKey('location-search')), address);
      await tester.tap(find.byTooltip('Search addresses'));
      await tester.pumpAndSettle();
      final suggestion = find.widgetWithText(ListTile, address);
      expect(suggestion, findsOneWidget);
      final card = tester.widget<Card>(
          find.ancestor(of: suggestion, matching: find.byType(Card)).first);
      expect(card.clipBehavior, Clip.antiAlias);
      expect(tester.takeException(), isNull);
      await tester.tap(suggestion);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    }
    expect(draft.pickupAddress, 'Pickup hotel');
    expect(draft.destinationAddress, 'Destination airport');
    expect(draft.pickupCoordinates!.latitude, 3);
    expect(draft.destinationCoordinates!.latitude, 4);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();

    expect(draft.roadRoute!['distanceKm'], 58);
    await tester.ensureVisible(find.text('Continue to trip details'));
    await tester.tap(find.text('Continue to trip details'));
    await tester.pumpAndSettle();
    expect(find.byType(BookingDetailsPage), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('current location still fills pickup without Material errors',
      (tester) async {
    final draft = BookingDraft(customerName: 'Customer', phoneNumber: '0123');
    await tester.pumpWidget(MaterialApp(
        home: BookingLocationPage(
            draft: draft,
            locationProvider: _Locations(),
            mapBuilder: (_, __) => const SizedBox())));
    await tester.tap(find.text('Use Current Location'));
    await tester.pumpAndSettle();
    expect(draft.pickupAddress, 'Current pickup');
    expect(draft.pickupCoordinates!.latitude, 3);
    expect(tester.takeException(), isNull);
  });
}

class _Locations implements BookingLocationProvider {
  @override
  Future<BookingCoordinates> currentCoordinates() async =>
      const BookingCoordinates(latitude: 3, longitude: 101);
  @override
  Future<BookingCoordinates> coordinatesForAddress(String address) async =>
      BookingCoordinates(
          latitude: address.startsWith('Pickup') ? 3 : 4, longitude: 101);
  @override
  Future<String> addressForCoordinates(BookingCoordinates coordinates) async =>
      'Current pickup';
}

class _RoadService extends BookingService {
  @override
  Future<Map<String, dynamic>> previewRoute(BookingDraft draft) async => {
        'distanceKm': 58,
        'durationSeconds': 7200,
        'geometry': [
          [101, 3],
          [101, 4]
        ],
      };
}
