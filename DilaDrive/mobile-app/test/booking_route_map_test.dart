import 'dart:convert';
import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/widgets/booking_route_map.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('destination selected first keeps destination marker identity',
      (tester) async {
    final draft = BookingDraft(customerName: 'Customer', phoneNumber: '0123')
      ..destinationCoordinates =
          const BookingCoordinates(latitude: 4, longitude: 101);
    await tester.pumpWidget(MaterialApp(
        home: Scaffold(
            body: BookingRouteMap(
                draft: draft, onSelect: (_) {}, tileProvider: _LocalTiles()))));
    await tester.pumpAndSettle();
    expect(
        find.byKey(const ValueKey('destination-map-marker')), findsOneWidget);
    expect(find.byKey(const ValueKey('pickup-map-marker')), findsNothing);
    expect(
        tester
            .widget<Icon>(find.byKey(const ValueKey('destination-map-marker')))
            .icon,
        Icons.location_on);
    draft.pickupCoordinates =
        const BookingCoordinates(latitude: 3, longitude: 101);
    await tester.pumpWidget(MaterialApp(
        home: Scaffold(
            body: BookingRouteMap(
                draft: draft, onSelect: (_) {}, tileProvider: _LocalTiles()))));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('pickup-map-marker')), findsOneWidget);
    expect(
        find.byKey(const ValueKey('destination-map-marker')), findsOneWidget);
    final tiles = tester.widget<TileLayer>(find.byType(TileLayer));
    expect(tiles.panBuffer, 0);
    expect(tiles.subdomains, isEmpty);
    expect(tiles.maxNativeZoom, 19);
    expect(find.textContaining('OpenStreetMap contributors'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

// Test-only pixels; no automated traffic goes to OSM public tiles.
class _LocalTiles extends TileProvider {
  @override
  ImageProvider getImage(TileCoordinates coordinates, TileLayer options) =>
      MemoryImage(base64Decode(
          'R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7'));
}
