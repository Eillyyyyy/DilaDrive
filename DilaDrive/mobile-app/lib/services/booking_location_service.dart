import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'package:flutter/services.dart';
import 'package:geocoding/geocoding.dart';
import 'package:geolocator/geolocator.dart';

import '../models/booking_draft.dart';
import '../config/app_config.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';

abstract interface class BookingLocationProvider {
  Future<BookingCoordinates> currentCoordinates();
  Future<BookingCoordinates> coordinatesForAddress(String address);
  Future<String> addressForCoordinates(BookingCoordinates coordinates);
}

class GeolocatorBookingLocationProvider
    implements BookingLocationProvider, BookingAddressSearchProvider {
  late final Geocoding _geocoding = Geocoding();

  @override
  Future<List<BookingLocationResult>> searchAddresses(String query) async {
    if (kIsWeb) {
      throw const BookingLocationException(
          'Address search is not configured for this browser. Confirm an address manually or configure the location service.');
    }
    try {
      final locations = await _geocoding
          .locationFromAddress(query)
          .timeout(const Duration(seconds: 15));
      final results = <BookingLocationResult>[];
      for (final location in locations.take(3)) {
        final coordinates = BookingCoordinates(
            latitude: location.latitude, longitude: location.longitude);
        if (!coordinates.isValid) continue;
        String address = query;
        try {
          address = await addressForCoordinates(coordinates)
              .timeout(const Duration(seconds: 5));
        } on Exception {
          /* The label remains the user's query, not an invented canonical address. */
        }
        results.add(
            BookingLocationResult(address: address, coordinates: coordinates));
      }
      return results;
    } on MissingPluginException {
      throw const BookingLocationException(
          'Address search is unavailable here. Confirm an address manually.');
    } on TimeoutException {
      throw const BookingLocationException(
          'Search timed out. Retry or confirm the address manually.');
    } on PlatformException {
      throw const BookingLocationException(
          'Address lookup failed. Add a city or postcode and retry, or confirm manually.');
    }
  }

  @override
  Future<BookingCoordinates> currentCoordinates() async {
    try {
      if (!await Geolocator.isLocationServiceEnabled()) {
        throw const BookingLocationException(
          'Turn on location services to use your current location.',
        );
      }

      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied) {
        throw const BookingLocationException(
          'Location permission was denied. Allow location access or enter a pickup address.',
        );
      }
      if (permission == LocationPermission.deniedForever) {
        throw const BookingLocationException(
          'Location permission is disabled for DilaDrive. Enable it in your device settings or enter a pickup address.',
        );
      }

      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 15),
        ),
      );
      return BookingCoordinates(
        latitude: position.latitude,
        longitude: position.longitude,
      );
    } on BookingLocationException {
      rethrow;
    } on TimeoutException {
      throw const BookingLocationException(
        'The current location request timed out. Try again or enter a pickup address.',
      );
    } on LocationServiceDisabledException {
      throw const BookingLocationException(
        'Turn on location services to use your current location.',
      );
    } on PermissionDefinitionsNotFoundException catch (exception) {
      throw BookingLocationException(
        'Location permission is not configured for this app: ${exception.message}',
      );
    } on PermissionRequestInProgressException {
      throw const BookingLocationException(
        'A location permission request is already in progress. Try again shortly.',
      );
    } on PlatformException catch (exception) {
      throw BookingLocationException(
        exception.message ??
            'Your current location could not be retrieved. Enter a pickup address instead.',
      );
    }
  }

  @override
  Future<BookingCoordinates> coordinatesForAddress(String address) async {
    if (kIsWeb) {
      throw const BookingLocationException(
          'Address lookup is not configured for this browser. Enter and confirm an address manually.');
    }
    try {
      final locations = await _geocoding.locationFromAddress(address);
      if (locations.isEmpty) {
        throw const BookingLocationException(
          'That address could not be found. Try adding a city or nearby landmark.',
        );
      }
      final location = locations.first;
      return BookingCoordinates(
        latitude: location.latitude,
        longitude: location.longitude,
      );
    } on BookingLocationException {
      rethrow;
    } on MissingPluginException {
      throw const BookingLocationException(
        'Address lookup is not supported on this device. Try another device or enter a different address.',
      );
    } on PlatformException catch (exception) {
      throw BookingLocationException(
        exception.message ??
            'The address could not be checked. Verify it and try again.',
      );
    }
  }

  @override
  Future<String> addressForCoordinates(
    BookingCoordinates coordinates,
  ) async {
    if (kIsWeb) {
      throw const BookingLocationException(
          'A browser address service is not configured. Enter your pickup manually.');
    }
    try {
      final placemarks = await _geocoding.placemarkFromCoordinates(
        coordinates.latitude,
        coordinates.longitude,
      );
      if (placemarks.isEmpty) {
        throw const BookingLocationException(
          'A pickup address could not be found for your current location.',
        );
      }

      final placemark = placemarks.first;
      final address = [
        placemark.name,
        placemark.street,
        placemark.locality,
        placemark.administrativeArea,
        placemark.postalCode,
        placemark.country,
      ]
          .whereType<String>()
          .map((part) => part.trim())
          .where((part) => part.isNotEmpty)
          .toSet()
          .join(', ');
      if (address.isEmpty) {
        throw const BookingLocationException(
          'A pickup address could not be found for your current location.',
        );
      }
      return address;
    } on BookingLocationException {
      rethrow;
    } on MissingPluginException {
      throw const BookingLocationException(
        'Address lookup is not supported on this device. Try another device or enter a different address.',
      );
    } on PlatformException catch (exception) {
      throw BookingLocationException(
        exception.message ??
            'Your current location could not be converted to an address.',
      );
    }
  }
}

class BookingLocationException implements Exception {
  const BookingLocationException(this.message);

  final String message;

  @override
  String toString() => message;
}

class BookingLocationResult {
  const BookingLocationResult(
      {required this.address,
      this.coordinates,
      this.manuallyConfirmed = false});
  final String address;
  final BookingCoordinates? coordinates;
  final bool manuallyConfirmed;
}

abstract interface class BookingAddressSearchProvider {
  Future<List<BookingLocationResult>> searchAddresses(String query);
}

abstract interface class BookingAddressAutocompleteProvider {
  bool get supportsAutocomplete;
  Future<List<BookingLocationResult>> suggestAddresses(String query);
}

abstract interface class DisposableBookingLocationProvider {
  void dispose();
}

BookingLocationProvider createBookingLocationProvider({http.Client? client}) {
  const configured = String.fromEnvironment('BOOKING_LOCATION_API_URL');
  final endpoint = configured.trim().isEmpty
      ? '${AppConfig.apiBaseUrl}/api/locations'
      : configured.trim();
  final gateway = Uri.tryParse(endpoint);
  final backend = Uri.parse(AppConfig.apiBaseUrl);
  final firstParty = gateway != null &&
      gateway.scheme == backend.scheme &&
      gateway.host == backend.host &&
      gateway.port == backend.port;
  return HttpBookingLocationProvider(endpoint,
      client: client,
      useMobileSession: firstParty,
      autocompleteEnabled: firstParty ||
          const bool.fromEnvironment('BOOKING_LOCATION_AUTOCOMPLETE'));
}

/// Application-controlled gateway, shared by Chrome and Android. GPS remains on device.
/// GET /search?q=... -> {results:[{address,latitude,longitude}]}
/// GET /reverse?latitude=...&longitude=... -> {address:...}
class HttpBookingLocationProvider
    implements
        BookingLocationProvider,
        BookingAddressSearchProvider,
        BookingAddressAutocompleteProvider,
        DisposableBookingLocationProvider {
  HttpBookingLocationProvider(this.baseUrl,
      {http.Client? client,
      BookingLocationProvider? device,
      this.useMobileSession = false,
      this.autocompleteEnabled = false})
      : _client = client ??
            (useMobileSession ? createApiHttpClient() : http.Client()),
        _device = device ?? GeolocatorBookingLocationProvider();
  final String baseUrl;
  final bool useMobileSession;
  final bool autocompleteEnabled;
  @override
  bool get supportsAutocomplete => autocompleteEnabled;
  final http.Client _client;
  final BookingLocationProvider _device;
  @override
  void dispose() => _client.close();
  Future<Map<String, dynamic>> _get(
      String path, Map<String, String> params) async {
    final base = Uri.tryParse(baseUrl);
    if (base == null ||
        !['http', 'https'].contains(base.scheme) ||
        base.host.isEmpty ||
        base.userInfo.isNotEmpty) {
      throw const BookingLocationException(
          'The location service URL is not configured correctly. You can still confirm an address manually.');
    }
    try {
      final uri = base.replace(
          path: '${base.path.replaceFirst(RegExp(r"/$"), "")}$path',
          queryParameters: params,
          fragment: '');
      final response = await _client.get(uri, headers: {
        'Accept': 'application/json',
        if (useMobileSession) ...MobileSession.headers,
      }).timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) {
        if (response.statusCode == 401) {
          throw const BookingLocationException(
              'Your session expired. Sign in again to search addresses.');
        }
        if (useMobileSession) {
          final error = jsonDecode(utf8.decode(response.bodyBytes));
          if (response.statusCode == 404 &&
              error is Map &&
              error['code'] == 'ENDPOINT_NOT_FOUND') {
            throw const BookingLocationException(
                'The location API is missing on this backend. Restart the updated Spring Boot API and sign in again.');
          }
          if (error is Map && error['message'] is String) {
            throw BookingLocationException(error['message'] as String);
          }
        }
        if (response.statusCode == 429) {
          throw const BookingLocationException(
              'Address search is busy. Wait a moment and try again.');
        }
        throw const BookingLocationException(
            'The address service is unavailable. Retry or confirm the address manually.');
      }
      final body = jsonDecode(utf8.decode(response.bodyBytes));
      if (body is! Map<String, dynamic>) throw const FormatException();
      return body;
    } on BookingLocationException {
      rethrow;
    } on Exception {
      throw const BookingLocationException(
          'Could not connect to the address service. Retry or enter the address manually.');
    }
  }

  @override
  Future<BookingCoordinates> currentCoordinates() =>
      _device.currentCoordinates();
  @override
  Future<List<BookingLocationResult>> searchAddresses(String query) =>
      _lookup(query);

  @override
  Future<List<BookingLocationResult>> suggestAddresses(String query) {
    if (!autocompleteEnabled) {
      throw const BookingLocationException(
          'Use the Search button with this address provider.');
    }
    return _lookup(query, autocomplete: true);
  }

  Future<List<BookingLocationResult>> _lookup(String query,
      {bool autocomplete = false}) async {
    final q = query.trim();
    if (q.length < 3 || q.length > 500) {
      throw const BookingLocationException(
          'Enter a specific place or address (3–500 characters).');
    }
    final body = await _get(
        '/search', {'q': q, if (autocomplete) 'autocomplete': 'true'});
    final results = body['results'];
    if (results is! List) {
      throw const BookingLocationException(
          'The address service returned an invalid result. Please retry.');
    }
    return results.take(10).map((item) {
      if (item is! Map ||
          item['address'] is! String ||
          (item['address'] as String).trim().isEmpty ||
          (item['address'] as String).length > 500 ||
          item['latitude'] is! num ||
          item['longitude'] is! num) {
        throw const BookingLocationException(
            'The address service returned an incomplete location. Please retry.');
      }
      final coordinates = BookingCoordinates(
          latitude: (item['latitude'] as num).toDouble(),
          longitude: (item['longitude'] as num).toDouble());
      if (!coordinates.isValid) {
        throw const BookingLocationException(
            'The address service returned invalid coordinates.');
      }
      return BookingLocationResult(
          address: (item['address'] as String).trim(),
          coordinates: coordinates);
    }).toList(growable: false);
  }

  @override
  Future<BookingCoordinates> coordinatesForAddress(String address) async {
    final results = await searchAddresses(address);
    if (results.isEmpty) {
      throw const BookingLocationException(
          'No matching address found. Try a more complete address.');
    }
    return results.first.coordinates!;
  }

  @override
  Future<String> addressForCoordinates(BookingCoordinates coordinates) async {
    if (!coordinates.isValid) {
      throw const BookingLocationException(
          'Choose valid map coordinates before looking up an address.');
    }
    final body = await _get('/reverse', {
      'latitude': coordinates.latitude.toString(),
      'longitude': coordinates.longitude.toString()
    });
    final address = body['address'];
    if (address is! String || address.trim().isEmpty || address.length > 500) {
      throw const BookingLocationException(
          'No valid address was returned for this location. Enter it manually.');
    }
    return address.trim();
  }
}
