import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';

class BookingService {
  BookingService({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;

  void dispose() {
    _client.close();
  }

  Future<String> checkAvailability({
    required String pickupDate,
    required String pickupTime,
    required String vehicleType,
  }) async {
    late final http.Response response;
    try {
      response = await _client
          .get(
            Uri.parse('${AppConfig.apiBaseUrl}/bookings/availability')
                .replace(queryParameters: {
              'vehicleType': vehicleType,
              'tripDate': pickupDate,
              'pickupTime': pickupTime,
            }),
          )
          .timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const BookingException(
        'The server took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const BookingException(
        'Cannot connect to DilaDrive. Check that the API and database are running.',
      );
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw BookingException(_serverErrorMessage(response));
    }

    try {
      final payload = jsonDecode(response.body);
      if (payload is! Map<String, dynamic> ||
          payload['availableDrivers'] is! num ||
          payload['availableVehicles'] is! num ||
          payload['available'] is! bool) {
        throw const BookingException(
          'The server returned an invalid availability response.',
        );
      }

      final drivers = (payload['availableDrivers'] as num).toInt();
      final vehicles = (payload['availableVehicles'] as num).toInt();
      final status = payload['available'] == true ? 'Available' : 'Unavailable';
      return 'Available Drivers : $drivers\n'
          'Available Vehicles : $vehicles\n'
          'Status : $status';
    } on FormatException {
      throw const BookingException(
        'The server returned an invalid availability response.',
      );
    }
  }

  Future<void> createBooking({
    required int customerId,
    required String pickupDate,
    required String pickupTime,
    required String pickupAddress,
    required String destinationAddress,
    required String vehicleType,
    required double fare,
  }) async {
    late final http.Response response;
    try {
      response = await _client
          .post(
            Uri.parse('${AppConfig.apiBaseUrl}/bookings'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({
              'custId': customerId,
              'bookingTripdate': pickupDate,
              'bookingPickuptime': pickupTime,
              'bookingPickupaddress': pickupAddress,
              'bookingDestinationaddress': destinationAddress,
              'bookingType': vehicleType,
              'bookingTripfare': fare,
            }),
          )
          .timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const BookingException(
        'The server took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const BookingException(
        'Cannot connect to DilaDrive. Check that the API and database are running.',
      );
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw BookingException(_serverErrorMessage(response));
    }
  }

  String _serverErrorMessage(http.Response response) {
    try {
      final payload = jsonDecode(response.body);
      if (payload is String && payload.isNotEmpty) {
        return payload;
      }
      if (payload is Map<String, dynamic>) {
        final message = payload['message'] ?? payload['error'];
        if (message is String && message.isNotEmpty) {
          return message;
        }
      }
    } on FormatException {
      // Use a generic message when the server response is not JSON.
    }
    return 'Booking failed (${response.statusCode}). Please try again.';
  }
}

class BookingException implements Exception {
  const BookingException(this.message);

  final String message;

  @override
  String toString() => message;
}
