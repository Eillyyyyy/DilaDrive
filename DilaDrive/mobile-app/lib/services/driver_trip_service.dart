import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/driver_trip.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';
import 'money_validation.dart';

class DriverTripService {
  DriverTripService({http.Client? client})
      : _client = client ?? createApiHttpClient();

  final http.Client _client;

  void dispose() => _client.close();

  Future<List<DriverTrip>> getMyTrips() async {
    final response = await _request(
      () => _client.get(
        Uri.parse('${AppConfig.apiBaseUrl}/api/assignments/mine'),
        headers: {'Accept': 'application/json', ...MobileSession.headers},
      ),
    );
    final Object? payload;
    try {
      payload = jsonDecode(response.body);
    } on FormatException {
      throw const DriverTripException(
        'The server returned an invalid trips response.',
      );
    }
    if (payload is! List) {
      throw const DriverTripException(
        'The server returned an invalid trips response.',
      );
    }
    try {
      return payload.map((item) {
        if (item is! Map<String, dynamic>) {
          throw const FormatException('The server returned an invalid trip.');
        }
        return DriverTrip.fromJson(item);
      }).toList(growable: false);
    } on FormatException catch (error) {
      throw DriverTripException(error.message);
    }
  }

  Future<void> updateStatus(int assignmentId, String status) async {
    await _request(
      () => _client.patch(
        Uri.parse(
            '${AppConfig.apiBaseUrl}/api/assignments/$assignmentId/status'),
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          ...MobileSession.headers,
        },
        body: jsonEncode({'status': status}),
      ),
    );
  }

  Future<void> recordCashReceived(int bookingId, double amount) async {
    if (!hasAtMostTwoDecimalPlaces(amount)) {
      throw const DriverTripException(
          'Enter a valid cash amount with at most two decimal places.');
    }
    await _request(
      () => _client.post(
        Uri.parse(
            '${AppConfig.apiBaseUrl}/api/payments/bookings/$bookingId/cash'),
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          ...MobileSession.headers,
        },
        body: jsonEncode({'amount': amount.toStringAsFixed(2)}),
      ),
    );
  }

  Future<http.Response> _request(Future<http.Response> Function() send) async {
    if (!MobileSession.isAuthenticated) {
      throw const DriverTripException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }
    late final http.Response response;
    try {
      response = await send().timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const DriverTripException(
        'The server took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const DriverTripException(
        'Cannot connect to DilaDrive. Check the API connection and try again.',
      );
    }
    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const DriverTripException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }
    if (response.statusCode == 403) {
      throw const DriverTripException(
        'These trips are only available to the assigned driver.',
      );
    }
    if (response.statusCode == 404) {
      throw const DriverTripException(
          'This trip is no longer assigned to you.');
    }
    if (response.statusCode == 409) {
      throw DriverTripException(_serverMessage(response.body) ??
          'The trip status can no longer be changed. Refresh and try again.');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw DriverTripException(
        'The trip request failed (${response.statusCode}). Please try again.',
      );
    }
    return response;
  }

  String? _serverMessage(String body) {
    try {
      final payload = jsonDecode(body);
      if (payload is Map<String, dynamic> && payload['message'] is String) {
        return payload['message'] as String;
      }
    } on FormatException {
      return null;
    }
    return null;
  }
}

class DriverTripException implements Exception {
  const DriverTripException(this.message, {this.requiresLogin = false});

  final String message;
  final bool requiresLogin;

  @override
  String toString() => message;
}
