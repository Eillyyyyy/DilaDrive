import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_location_view.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_location_search_page.dart';
import 'package:diladrive_customer/services/booking_location_service.dart';
import 'package:diladrive_customer/services/booking_service.dart';

void main() {
  BookingDraft draft() =>
      BookingDraft(customerName: 'Customer', phoneNumber: '0123');
  Future<void> mount(
      WidgetTester tester, BookingDraft d, _Locations locations, _Routes routes,
      {VoidCallback? onContinue, bool settle = true}) async {
    tester.view.physicalSize = const Size(1200, 1100);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(routes.dispose);
    await tester.pumpWidget(MaterialApp(
        home: BookingLocationView(
            draft: d,
            locationProvider: locations,
            bookingService: routes,
            tileProvider: _LocalTiles(),
            onContinue: onContinue ?? () {})));
    if (settle) {
      await tester.pumpAndSettle();
    } else {
      await tester.pump();
    }
  }

  Future<void> search(WidgetTester tester, bool pickup, String query) async {
    final tile = find
        .byKey(ValueKey(pickup ? 'pickup-location' : 'destination-location'));
    await tester.ensureVisible(tile);
    await tester.tap(tile);
    await tester.pumpAndSettle();
    await tester.enterText(
        find.byKey(const ValueKey('location-search')), query);
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.byType(ListTile), findsNothing);
    await tester.pump(const Duration(milliseconds: 700));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ListTile, query));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
  }

  Future<void> tapMap(WidgetTester tester,
      {Offset offset = Offset.zero}) async {
    final map = find.byKey(const ValueKey('booking-location-map'));
    await tester.ensureVisible(map);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.tapAt(tester.getCenter(map) + offset);
    await tester.pump(const Duration(milliseconds: 400));
  }

  FilledButton continueButton(WidgetTester tester) =>
      tester.widget(find.byKey(const ValueKey('continue-trip-details')));

  testWidgets(
      'KLIA and KL Sentral suggestions update draft, markers, camera and automatic route',
      (tester) async {
    final d = draft(), locations = _Locations(), routes = _Routes();
    var continued = false;
    await mount(tester, d, locations, routes,
        onContinue: () => continued = true);
    await search(tester, true, 'KLIA');
    expect(d.pickupAddress, 'KLIA');
    expect(d.pickupCoordinates, isNotNull);
    expect(d.destinationCoordinates, isNull);
    await search(tester, false, 'KL Sentral');
    expect(locations.suggestions, ['KLIA', 'KL Sentral']);
    expect(d.destinationAddress, 'KL Sentral');
    expect(find.byKey(const ValueKey('pickup-map-marker')), findsOneWidget);
    expect(
        find.byKey(const ValueKey('destination-map-marker')), findsOneWidget);
    expect(d.routeDistanceKm, 58);
    expect(d.routeDurationSeconds, 7200);
    final camera = MapCamera.of(
        tester.element(find.byKey(const ValueKey('pickup-map-marker'))));
    expect(
        camera.visibleBounds.contains(LatLng(
            d.pickupCoordinates!.latitude, d.pickupCoordinates!.longitude)),
        isTrue);
    expect(
        camera.visibleBounds.contains(LatLng(d.destinationCoordinates!.latitude,
            d.destinationCoordinates!.longitude)),
        isTrue);
    expect(routes.calls, 1);
    expect(find.byType(PolylineLayer), findsOneWidget);
    expect(find.text('58.0 km · 120 minutes'), findsOneWidget);
    expect(continueButton(tester).onPressed, isNotNull);
    await tester
        .ensureVisible(find.byKey(const ValueKey('continue-trip-details')));
    await tester.tap(find.byKey(const ValueKey('continue-trip-details')));
    expect(continued, isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'map taps place pins immediately and preserve the other endpoint when lookup fails',
      (tester) async {
    final d = draft(),
        locations = _Locations()..pendingReverse = true,
        routes = _Routes();
    await mount(tester, d, locations, routes);
    await tapMap(tester);
    expect(d.pickupCoordinates, isNotNull);
    expect(d.pickupAddress, isEmpty);
    expect(find.byKey(const ValueKey('pickup-map-marker')), findsOneWidget);
    locations.pending.last.complete('Tapped pickup');
    await tester.pumpAndSettle();
    final pickup = d.pickupCoordinates;
    await tester.ensureVisible(find.byKey(const ValueKey('dropoff-mode')));
    await tester.tap(find.byKey(const ValueKey('dropoff-mode')));
    await tester.pumpAndSettle();
    await tapMap(tester, offset: const Offset(45, 20));
    expect(d.destinationCoordinates, isNotNull);
    expect(d.pickupCoordinates, same(pickup));
    expect(d.pickupAddress, 'Tapped pickup');
    final destination = d.destinationCoordinates;
    locations.pending.last
        .completeError(const BookingLocationException('Reverse lookup failed'));
    await tester.pumpAndSettle();
    expect(d.destinationCoordinates, same(destination));
    expect(d.destinationAddress, isEmpty);
    expect(continueButton(tester).onPressed, isNull);
    final retry = find.text('Retry drop-off address');
    await tester.ensureVisible(retry);
    await tester.tap(retry);
    await tester.pump();
    locations.pending.last.complete('Tapped drop-off');
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    expect(d.destinationCoordinates, same(destination));
    expect(d.destinationAddress, 'Tapped drop-off');
    expect(d.pickupCoordinates, same(pickup));
    expect(continueButton(tester).onPressed, isNotNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'moving pickup ignores stale reverse responses without clearing drop-off',
      (tester) async {
    final d = draft()
      ..destinationAddress = 'Existing drop-off'
      ..destinationCoordinates =
          const BookingCoordinates(latitude: 3.2, longitude: 101.7);
    final locations = _Locations()..pendingReverse = true;
    await mount(tester, d, locations, _Routes());
    final destination = d.destinationCoordinates;
    await tapMap(tester);
    final old = locations.pending.last;
    await tapMap(tester, offset: const Offset(60, 0));
    final newest = d.pickupCoordinates;
    locations.pending.last.complete('New pickup');
    await tester.pumpAndSettle();
    old.complete('Old pickup');
    await tester.pumpAndSettle();
    expect(d.pickupCoordinates, same(newest));
    expect(d.pickupAddress, 'New pickup');
    expect(d.destinationCoordinates, same(destination));
    expect(d.destinationAddress, 'Existing drop-off');
  });

  testWidgets(
      'route failure has no fake distance and Retry is required before Continue',
      (tester) async {
    final d = draft()
      ..pickupAddress = 'Pickup'
      ..destinationAddress = 'Drop-off'
      ..pickupCoordinates =
          const BookingCoordinates(latitude: 3, longitude: 101)
      ..destinationCoordinates =
          const BookingCoordinates(latitude: 4, longitude: 102);
    final routes = _Routes()..fail = true;
    await mount(tester, d, _Locations(), routes);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    expect(d.roadRoute, isNull);
    expect(d.routeDistanceKm, isNull);
    expect(continueButton(tester).onPressed, isNull);
    routes.fail = false;
    await tester.ensureVisible(find.text('Retry route'));
    await tester.tap(find.text('Retry route'));
    await tester.pumpAndSettle();
    expect(d.routeDistanceKm, 58);
    expect(continueButton(tester).onPressed, isNotNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'GPS retains coordinates and permits explicit address confirmation after reverse failure',
      (tester) async {
    final d = draft(), locations = _Locations()..reverseFailure = true;
    await mount(tester, d, locations, _Routes());
    await tester.tap(find.text('Use Current Location'));
    await tester.pumpAndSettle();
    expect(d.pickupCoordinates, isNotNull);
    final point = d.pickupCoordinates;
    expect(d.pickupAddress, isEmpty);
    expect(continueButton(tester).onPressed, isNull);
    await tester.ensureVisible(find.text('Confirm pickup address'));
    await tester.tap(find.text('Confirm pickup address'));
    await tester.pumpAndSettle();
    await tester.enterText(
        find.byType(TextField), 'User-confirmed address for this pin');
    await tester.tap(find.text('Confirm address'));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 350));
    await tester.pumpAndSettle();
    expect(d.pickupCoordinates, same(point));
    expect(d.pickupAddress, 'User-confirmed address for this pin');
    expect(d.pickupManuallyConfirmed, isTrue);
  });

  testWidgets('changing drop-off preserves pickup and recalculates the route',
      (tester) async {
    final d = draft()
      ..pickupAddress = 'Existing pickup'
      ..destinationAddress = 'Existing drop-off'
      ..pickupCoordinates =
          const BookingCoordinates(latitude: 3, longitude: 101)
      ..destinationCoordinates =
          const BookingCoordinates(latitude: 4, longitude: 102);
    final pickup = d.pickupCoordinates, oldDrop = d.destinationCoordinates;
    await mount(tester, d, _Locations(), _Routes());
    await tester.ensureVisible(find.byKey(const ValueKey('dropoff-mode')));
    await tester.tap(find.byKey(const ValueKey('dropoff-mode')));
    await tester.pump();
    await tapMap(tester, offset: const Offset(50, 20));
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();
    expect(d.pickupCoordinates, same(pickup));
    expect(d.pickupAddress, 'Existing pickup');
    expect(d.destinationCoordinates, isNot(same(oldDrop)));
    expect(d.destinationAddress, 'Fixture address');
    expect(d.roadRoute, isNotNull);
  });

  testWidgets(
      'old routing responses cannot enable Continue for newly moved pins',
      (tester) async {
    final d = draft()
      ..pickupAddress = 'Pickup'
      ..destinationAddress = 'Drop-off'
      ..pickupCoordinates =
          const BookingCoordinates(latitude: 3, longitude: 101)
      ..destinationCoordinates =
          const BookingCoordinates(latitude: 4, longitude: 102);
    final routes = _Routes()..hold = true;
    await mount(tester, d, _Locations(), routes, settle: false);
    await tester.pump(const Duration(milliseconds: 500));
    expect(routes.pending, hasLength(1));
    await tapMap(tester, offset: const Offset(20, 20));
    await tester.pump(const Duration(milliseconds: 500));
    expect(routes.pending, hasLength(2));
    routes.pending.first.complete({
      'distanceKm': 999,
      'durationSeconds': 999,
      'geometry': [
        [101, 3],
        [102, 4]
      ]
    });
    await tester.pump();
    expect(d.roadRoute, isNull);
    expect(continueButton(tester).onPressed, isNull);
    routes.pending.last.complete({
      'distanceKm': 68,
      'durationSeconds': 8100,
      'geometry': [
        [d.pickupCoordinates!.longitude, d.pickupCoordinates!.latitude],
        [102, 4]
      ]
    });
    await tester.pumpAndSettle();
    expect(d.routeDistanceKm, 68);
    expect(continueButton(tester).onPressed, isNotNull);
  });

  testWidgets('unresolved text alone is never accepted as a booking location',
      (tester) async {
    final d = draft();
    await mount(tester, d, _Locations(), _Routes());
    await tester.tap(find.byKey(const ValueKey('pickup-location')));
    await tester.pumpAndSettle();
    await tester.enterText(
        find.byKey(const ValueKey('location-search')), 'Unresolved address');
    await tester.tap(find.text('Enter address manually'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Confirm address'));
    await tester.pumpAndSettle();
    expect(d.pickupCoordinates, isNull);
    expect(d.pickupAddress, isEmpty);
    expect(continueButton(tester).onPressed, isNull);
  });

  testWidgets('autocomplete ignores responses for an older query',
      (tester) async {
    final provider = _DelayedSuggestions();
    await tester.pumpWidget(MaterialApp(
        home: BookingLocationSearchPage(
            title: 'Pickup',
            provider: provider,
            initialQuery: '',
            onQueryChanged: (_) {})));
    await tester.enterText(
        find.byKey(const ValueKey('location-search')), 'KLIA');
    await tester.pump(const Duration(milliseconds: 1300));
    expect(provider.suggestionRequests, hasLength(1));
    await tester.enterText(
        find.byKey(const ValueKey('location-search')), 'KL Sentral');
    await tester.pump(const Duration(milliseconds: 1300));
    expect(provider.suggestionRequests, hasLength(2));
    provider.suggestionRequests.last.complete([
      const BookingLocationResult(
          address: 'Latest station',
          coordinates: BookingCoordinates(latitude: 3, longitude: 101))
    ]);
    await tester.pumpAndSettle();
    provider.suggestionRequests.first.complete([
      const BookingLocationResult(
          address: 'Old airport',
          coordinates: BookingCoordinates(latitude: 2, longitude: 101))
    ]);
    await tester.pumpAndSettle();
    expect(find.widgetWithText(ListTile, 'Latest station'), findsOneWidget);
    expect(find.text('Old airport'), findsNothing);
  });

  testWidgets('swap retains both locations and requests a fresh driving route',
      (tester) async {
    final d = draft()
      ..pickupAddress = 'Pickup'
      ..destinationAddress = 'Drop-off'
      ..pickupCoordinates =
          const BookingCoordinates(latitude: 3, longitude: 101)
      ..destinationCoordinates =
          const BookingCoordinates(latitude: 4, longitude: 102);
    final pickup = d.pickupCoordinates,
        drop = d.destinationCoordinates,
        routes = _Routes();
    await mount(tester, d, _Locations(), routes);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byTooltip('Swap pickup and drop-off'));
    await tester.tap(find.byTooltip('Swap pickup and drop-off'));
    expect(d.roadRoute, isNull);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
    expect(d.pickupCoordinates, same(drop));
    expect(d.destinationCoordinates, same(pickup));
    expect(d.pickupAddress, 'Drop-off');
    expect(d.destinationAddress, 'Pickup');
    expect(routes.calls, 2);
  });
}

class _Locations
    implements
        BookingLocationProvider,
        BookingAddressSearchProvider,
        BookingAddressAutocompleteProvider {
  bool pendingReverse = false, reverseFailure = false;
  final pending = <Completer<String>>[];
  final suggestions = <String>[];
  @override
  bool get supportsAutocomplete => true;
  @override
  Future<List<BookingLocationResult>> suggestAddresses(String query) async {
    suggestions.add(query);
    return searchAddresses(query);
  }

  @override
  Future<List<BookingLocationResult>> searchAddresses(String query) async => [
        BookingLocationResult(
            address: query,
            coordinates: BookingCoordinates(
                latitude: query == 'KLIA' ? 2.7456 : 3.1343,
                longitude: query == 'KLIA' ? 101.7072 : 101.6861))
      ];
  @override
  Future<BookingCoordinates> coordinatesForAddress(String address) async =>
      (await searchAddresses(address)).first.coordinates!;
  @override
  Future<BookingCoordinates> currentCoordinates() async =>
      const BookingCoordinates(latitude: 3.1, longitude: 101.6);
  @override
  Future<String> addressForCoordinates(BookingCoordinates coordinates) async {
    if (reverseFailure) {
      throw const BookingLocationException('Reverse lookup failed');
    }
    if (pendingReverse) {
      final request = Completer<String>();
      pending.add(request);
      return request.future;
    }
    return 'Fixture address';
  }
}

class _Routes extends BookingService {
  int calls = 0;
  bool fail = false;
  bool hold = false;
  final pending = <Completer<Map<String, dynamic>>>[];
  @override
  Future<Map<String, dynamic>> previewRoute(BookingDraft draft) async {
    calls++;
    if (hold) {
      final request = Completer<Map<String, dynamic>>();
      pending.add(request);
      return request.future;
    }
    if (fail) throw const BookingException('Routing unavailable');
    return {
      'distanceKm': 58,
      'durationSeconds': 7200,
      'geometry': [
        [draft.pickupCoordinates!.longitude, draft.pickupCoordinates!.latitude],
        [
          draft.destinationCoordinates!.longitude,
          draft.destinationCoordinates!.latitude
        ]
      ]
    };
  }
}

class _DelayedSuggestions extends _Locations {
  final suggestionRequests = <Completer<List<BookingLocationResult>>>[];
  @override
  Future<List<BookingLocationResult>> suggestAddresses(String query) {
    final request = Completer<List<BookingLocationResult>>();
    suggestionRequests.add(request);
    return request.future;
  }
}

class _LocalTiles extends TileProvider {
  @override
  ImageProvider getImage(TileCoordinates coordinates, TileLayer options) =>
      MemoryImage(base64Decode(
          'R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7'));
}
