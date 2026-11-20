import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/services/booking_location_service.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_location_search_page.dart';
import 'package:diladrive_customer/config/app_config.dart';
import 'package:diladrive_customer/services/mobile_session.dart';

void main() {
  test('reverse address not found preserves the API message', () async {
    final provider = HttpBookingLocationProvider('https://gateway.example.test',
        useMobileSession: true,
        client: MockClient((_) async => http.Response(
            '{"message":"No address was found at this point. Choose a nearby place."}',
            404)));
    addTearDown(provider.dispose);
    await expectLater(
        provider.addressForCoordinates(
            const BookingCoordinates(latitude: 3, longitude: 101)),
        throwsA(isA<BookingLocationException>().having(
            (e) => e.message, 'message', contains('No address was found'))));
  });

  test('gateway preserves UTF-8 formatted addresses', () async {
    final provider = HttpBookingLocationProvider('https://gateway.example.test',
        client: MockClient((_) async => http.Response.bytes(
            utf8.encode(jsonEncode({
              'results': [
                {
                  'address': '吉隆坡, Malaysia',
                  'latitude': 3.139,
                  'longitude': 101.687
                }
              ]
            })),
            200,
            headers: {'content-type': 'application/json'})));
    addTearDown(provider.dispose);
    expect((await provider.searchAddresses('Kuala Lumpur')).single.address,
        '吉隆坡, Malaysia');
  });
  test('default provider uses authenticated backend gateway on all platforms',
      () async {
    MobileSession.storeResponseCookies(
        {'set-cookie': 'JSESSIONID=location-test; Path=/'});
    final provider =
        createBookingLocationProvider(client: MockClient((request) async {
      expect(request.url.origin, Uri.parse(AppConfig.apiBaseUrl).origin);
      expect(request.url.path, '/api/locations/search');
      expect(request.headers['cookie'],
          kIsWeb ? isNull : 'JSESSIONID=location-test');
      return http.Response(
          '{"results":[{"address":"Formatted address","latitude":3,"longitude":101}]}',
          200);
    }));
    addTearDown(() {
      (provider as DisposableBookingLocationProvider).dispose();
      MobileSession.clear();
    });
    expect(provider, isA<HttpBookingLocationProvider>());
    expect(
        (await (provider as BookingAddressSearchProvider)
                .searchAddresses('Search address'))
            .single
            .address,
        'Formatted address');
  });
  test('gateway reverse lookup trims real address and validates input first',
      () async {
    var calls = 0;
    final provider = HttpBookingLocationProvider('https://gateway.example.test',
        client: MockClient((request) async {
      calls++;
      expect(request.url.path, '/reverse');
      expect(request.url.queryParameters['latitude'], '3.0');
      return http.Response(
          '{"address":"  Returned address  ","latitude":3,"longitude":101}',
          200);
    }));
    addTearDown(provider.dispose);
    expect(
        await provider.addressForCoordinates(
            const BookingCoordinates(latitude: 3, longitude: 101)),
        'Returned address');
    await expectLater(
        provider.addressForCoordinates(
            const BookingCoordinates(latitude: 999, longitude: 101)),
        throwsA(isA<BookingLocationException>()));
    expect(calls, 1);
  });
  test('gateway preserves GPS delegation', () async {
    final provider = HttpBookingLocationProvider('https://gateway.example.test',
        device: _GpsLocation());
    addTearDown(provider.dispose);
    expect((await provider.currentCoordinates()).latitude, 3);
  });
  test('rate-limit error is actionable and never becomes a fake result',
      () async {
    final provider = HttpBookingLocationProvider('https://gateway.example.test',
        client: MockClient((_) async => http.Response('{}', 429)));
    addTearDown(provider.dispose);
    await expectLater(
        provider.searchAddresses('Search address'),
        throwsA(isA<BookingLocationException>()
            .having((e) => e.message, 'message', contains('Wait a moment'))));
  });
  test('short queries do not contact upstream or implement autocomplete',
      () async {
    final provider = HttpBookingLocationProvider('https://gateway.example.test',
        client: MockClient((_) async {
      fail('No request expected');
    }));
    addTearDown(provider.dispose);
    await expectLater(provider.searchAddresses('ab'),
        throwsA(isA<BookingLocationException>()));
  });
  test(
      'location gateway uses configured responses and validates real coordinate ranges',
      () async {
    final provider =
        HttpBookingLocationProvider('https://location.example.test/locations',
            client: MockClient((request) async {
      expect(request.url.path, '/locations/search');
      expect(request.url.queryParameters['q'], 'Query address');
      expect(request.headers.containsKey('cookie'), isFalse);
      return http.Response(
          jsonEncode({
            'results': [
              {'address': 'Server address', 'latitude': 1.0, 'longitude': 2.0}
            ]
          }),
          200);
    }));
    addTearDown(provider.dispose);
    final results = await provider.searchAddresses('Query address');
    expect(results.single.address, 'Server address');
    expect(results.single.coordinates!.latitude, 1.0);
    expect(results.single.manuallyConfirmed, isFalse);
  });
  test('invalid provider coordinates are rejected rather than used as a pin',
      () async {
    final provider = HttpBookingLocationProvider(
        'https://location.example.test',
        client: MockClient((_) async => http.Response(
            '{"results":[{"address":"Invalid pin","latitude":999,"longitude":0}]}',
            200)));
    addTearDown(provider.dispose);
    await expectLater(provider.searchAddresses('Query address'),
        throwsA(isA<BookingLocationException>()));
    expect(const BookingCoordinates(latitude: double.nan, longitude: 0).isValid,
        isFalse);
  });
  test('empty results remain empty without fabricated suggestions', () async {
    final provider = HttpBookingLocationProvider(
        'https://location.example.test',
        client: MockClient((_) async => http.Response('{"results":[]}', 200)));
    addTearDown(provider.dispose);
    expect(await provider.searchAddresses('Query address'), isEmpty);
  });
  testWidgets(
      'search result selection returns matching provider address and coordinates',
      (tester) async {
    BookingLocationResult? selected;
    final provider = HttpBookingLocationProvider(
        'https://location.example.test',
        client: MockClient((_) async => http.Response(
            '{"results":[{"address":"Server address","latitude":1,"longitude":2}]}',
            200)));
    addTearDown(provider.dispose);
    await tester.pumpWidget(MaterialApp(
        home: Builder(
            builder: (context) => Scaffold(
                body: TextButton(
                    onPressed: () async {
                      selected = await Navigator.of(context)
                          .push<BookingLocationResult>(MaterialPageRoute(
                              builder: (_) => BookingLocationSearchPage(
                                  title: 'Choose pickup',
                                  provider: provider,
                                  initialQuery: '',
                                  onQueryChanged: (_) {})));
                    },
                    child: const Text('Open search'))))));
    await tester.tap(find.text('Open search'));
    await tester.pumpAndSettle();
    await tester.enterText(
        find.byKey(const ValueKey('location-search')), 'Query address');
    await tester.tap(find.byTooltip('Search addresses'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ListTile, 'Server address'));
    await tester.pumpAndSettle();
    expect(selected!.address, 'Server address');
    expect(selected!.coordinates!.longitude, 2);
  });
  testWidgets(
      'failed lookup permits explicit manual confirmation with no invented pin',
      (tester) async {
    BookingLocationResult? selected;
    await tester.pumpWidget(MaterialApp(
        home: Builder(
            builder: (context) => Scaffold(
                body: TextButton(
                    onPressed: () async {
                      selected = await Navigator.of(context)
                          .push<BookingLocationResult>(MaterialPageRoute(
                              builder: (_) => BookingLocationSearchPage(
                                  title: 'Choose pickup',
                                  provider: UnavailableLocation(),
                                  initialQuery: '',
                                  onQueryChanged: (_) {})));
                    },
                    child: const Text('Open search'))))));
    await tester.tap(find.text('Open search'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('location-search')),
        'User supplied full address');
    await tester.tap(find.byTooltip('Search addresses'));
    await tester.pumpAndSettle();
    expect(find.text('Search unavailable'), findsOneWidget);
    await tester.tap(find.text('Enter address manually'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Confirm address'));
    await tester.pumpAndSettle();
    expect(selected!.address, 'User supplied full address');
    expect(selected!.manuallyConfirmed, isTrue);
    expect(selected!.coordinates, isNull);
  });
  testWidgets('small phone layout tolerates an open keyboard', (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    tester.view.viewInsets = const FakeViewPadding(bottom: 280);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);
    await tester.pumpWidget(MaterialApp(
        home: BookingLocationSearchPage(
            title: 'Choose pickup',
            provider: UnavailableLocation(),
            initialQuery: '',
            onQueryChanged: (_) {})));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('Enter address manually'), findsOneWidget);
  });
}

class _GpsLocation implements BookingLocationProvider {
  @override
  Future<BookingCoordinates> currentCoordinates() async =>
      const BookingCoordinates(latitude: 3, longitude: 101);
  @override
  Future<BookingCoordinates> coordinatesForAddress(String address) async =>
      throw UnimplementedError();
  @override
  Future<String> addressForCoordinates(BookingCoordinates coordinates) async =>
      throw UnimplementedError();
}

class UnavailableLocation implements BookingLocationProvider {
  @override
  Future<BookingCoordinates> currentCoordinates() async =>
      throw const BookingLocationException('GPS denied');
  @override
  Future<BookingCoordinates> coordinatesForAddress(String address) async =>
      throw const BookingLocationException('Search unavailable');
  @override
  Future<String> addressForCoordinates(BookingCoordinates coordinates) async =>
      throw const BookingLocationException('Search unavailable');
}
