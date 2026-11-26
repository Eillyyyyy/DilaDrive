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
  testWidgets('a tile error keeps the route and allows retry on the same map',
      (tester) async {
    final draft = BookingDraft(customerName: 'Customer', phoneNumber: '0123')
      ..pickupCoordinates =
          const BookingCoordinates(latitude: 3, longitude: 101)
      ..destinationCoordinates =
          const BookingCoordinates(latitude: 4, longitude: 102)
      ..roadRoute = {
        'geometry': [
          [101, 3],
          [102, 4]
        ],
      };
    final provider = _LocalTiles();
    Widget screen(EdgeInsets padding) => MaterialApp(
        home: Scaffold(
            body: BookingRouteMap(
                draft: draft,
                onSelect: (_) {},
                tileProvider: provider,
                cameraPadding: padding)));
    await tester.pumpWidget(screen(const EdgeInsets.all(42)));
    await tester.pumpAndSettle();
    final map = tester.element(find.byType(FlutterMap));
    final tiles = tester.widget<TileLayer>(find.byType(TileLayer));
    tiles.errorTileCallback!(_TileFailure(), StateError('offline'), null);
    await tester.pumpAndSettle();
    expect(find.byType(PolylineLayer), findsOneWidget);
    expect(find.byType(MarkerLayer), findsOneWidget);
    final retry = find.byTooltip('Map tiles unavailable. Retry map tiles');
    expect(retry, findsOneWidget);
    await tester.tap(retry);
    await tester.pumpAndSettle();
    expect(retry, findsNothing);
    await tester.pumpWidget(screen(const EdgeInsets.fromLTRB(32, 55, 32, 260)));
    await tester.pumpAndSettle(const Duration(milliseconds: 180));
    expect(tester.element(find.byType(FlutterMap)), same(map));
    expect(tester.widget<TileLayer>(find.byType(TileLayer)).tileProvider,
        same(provider));
    expect(find.byType(PolylineLayer), findsOneWidget);
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

class _TileFailure extends Fake implements TileImage {}
