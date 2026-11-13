import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/booking_draft.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';

class BookingService {
  BookingService({http.Client? client})
      : _client = client ?? createApiHttpClient();

  final http.Client _client;

  void dispose() {
    _client.close();
  }

  Future<AvailabilityResult> checkAvailability(BookingDraft draft) async {
    final scheduledAt = draft.scheduledAt;
    if (scheduledAt == null ||
        draft.pickupAddress.trim().isEmpty ||
        draft.destinationAddress.trim().isEmpty ||
        draft.passengerCount < 1 ||
        draft.luggageCount < 0 ||
        (draft.luggageCount > 0 && draft.luggageSize == null)) {
      throw const BookingException(
        'Complete the trip locations, schedule and passenger/luggage details first.',
      );
    }

    final response = await _request(
      () => _client.get(
        Uri.parse(
                '${AppConfig.apiBaseUrl}/api/bookings/availability/recommendation')
            .replace(queryParameters: {
          'pickupAddress': draft.pickupAddress,
          'destinationAddress': draft.destinationAddress,
          'scheduledAt': scheduledAt.toIso8601String(),
          'tripType': _tripTypeValue(draft),
          'passengerCount': '${draft.passengerCount}',
          'luggageCount': '${draft.luggageCount}',
          if (draft.luggageSize != null) 'luggageSize': draft.luggageSize!,
        }),
        headers: MobileSession.headers,
      ),
    );
    final payload = _decodeObject(response);
    final available = payload['available'];
    if (available is! bool) {
      throw const BookingException(
        'The availability service returned an invalid response.',
      );
    }

    if (!available) {
      final message = payload['message'];
      return AvailabilityResult.unavailable(
        message is String && message.isNotEmpty
            ? message
            : 'No suitable vehicle and driver are currently available.',
      );
    }

    final rawVehicle = payload['vehicle'];
    if (rawVehicle is! Map<String, dynamic>) {
      throw const BookingException(
        'The availability service returned no recommended vehicle.',
      );
    }

    try {
      return AvailabilityResult.available(
        RecommendedVehicle.fromJson(rawVehicle),
      );
    } on FormatException catch (exception) {
      throw BookingException(exception.message.toString());
    }
  }

  Future<FareEstimate> estimateFare(BookingDraft draft) async {
    final vehicle = draft.recommendedVehicle;
    if (vehicle == null) {
      throw const BookingException(
        'A live vehicle recommendation is required before requesting a fare.',
      );
    }

    final response = await _request(
      () => _client.get(
        Uri.parse('${AppConfig.apiBaseUrl}/api/bookings/fare-estimate')
            .replace(queryParameters: {
          'pickupAddress': draft.pickupAddress,
          'destinationAddress': draft.destinationAddress,
          'vehicleId': '${vehicle.id}',
          'tripType': draft.tripType.name,
          'passengerCount': '${draft.passengerCount}',
          'luggageCount': '${draft.luggageCount}',
          if (draft.luggageSize != null) 'luggageSize': draft.luggageSize!,
        }),
        headers: MobileSession.headers,
      ),
    );
    try {
      final payload = _decodeObject(response);
      if (payload['available'] == false) {
        final message = payload['message'];
        throw BookingException(
          message is String
              ? message
              : 'A fare estimate is not available for this route yet.',
        );
      }
      return FareEstimate.fromJson(payload);
    } on FormatException catch (exception) {
      throw BookingException(exception.message.toString());
    }
  }

  Future<String> createBooking(
    BookingDraft draft, {
    required String idempotencyKey,
  }) async {
    if (draft.bookingDate == null || draft.recommendedVehicle == null) {
      throw const BookingException(
        'Live vehicle availability is required before submission.',
      );
    }

    final response = await _request(
      () => _client.post(
        Uri.parse('${AppConfig.apiBaseUrl}/api/bookings/customer'),
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          'Idempotency-Key': idempotencyKey,
          ...MobileSession.headers,
        },
        body: jsonEncode({
          'bookingType': draft.recommendedVehicle!.type,
          'tripType': _tripTypeValue(draft),
          'bookingDate': _date(draft.bookingDate!),
          'pickupTime': _time(draft.pickupTime),
          'landedTime': _time(draft.landedTime),
          'flightNumber': draft.flightNumber,
          'pickupAddress': draft.pickupAddress,
          'destinationAddress': draft.destinationAddress,
          'passengerCount': draft.passengerCount,
          'luggageCount': draft.luggageCount,
          'luggageSize': draft.luggageSize,
          'additionalNotes': draft.additionalNotes,
        }),
      ),
    );
    final payload = _decodeObject(response);
    final reference = payload['bookingReference'];
    final status = payload['bookingStatus'] ?? payload['status'];
    if (reference is! String || reference.isEmpty) {
      throw const BookingException(
        'The booking service returned no booking reference.',
      );
    }
    if (status is! String ||
        !const {
          'PENDING_VERIFICATION',
          'PENDING',
        }.contains(status.toUpperCase())) {
      throw const BookingException(
        'The booking service did not return a pending verification status.',
      );
    }
    return reference;
  }

  Future<void> updatePendingBooking(
    int bookingId,
    BookingDraft draft, {
    required String vehicleType,
  }) async {
    if (bookingId <= 0 || draft.bookingDate == null) {
      throw const BookingException('The booking details are incomplete.');
    }
    await _request(
      () => _client.put(
        Uri.parse('${AppConfig.apiBaseUrl}/api/bookings/$bookingId/customer'),
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          ...MobileSession.headers,
        },
        body: jsonEncode({
          'bookingType': vehicleType,
          'tripType': _tripTypeValue(draft),
          'bookingDate': _date(draft.bookingDate),
          'pickupTime': _time(draft.pickupTime),
          'landedTime': _time(draft.landedTime),
          'flightNumber': draft.flightNumber,
          'pickupAddress': draft.pickupAddress,
          'destinationAddress': draft.destinationAddress,
          'passengerCount': draft.passengerCount,
          'luggageCount': draft.luggageCount,
          'luggageSize': draft.luggageSize,
          'additionalNotes': draft.additionalNotes,
        }),
      ),
    );
  }

  Future<http.Response> _request(
    Future<http.Response> Function() send,
  ) async {
    try {
      final response = await send().timeout(const Duration(seconds: 15));
      if (response.statusCode == 401) {
        MobileSession.clear();
        throw const BookingException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true,
        );
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw BookingException(_serverErrorMessage(response));
      }
      return response;
    } on TimeoutException {
      throw const BookingException(
        'The server took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const BookingException(
        'Cannot connect to DilaDrive. Check that the API and database are running.',
      );
    }
  }

  Map<String, dynamic> _decodeObject(http.Response response) {
    try {
      final payload = jsonDecode(response.body);
      if (payload is Map<String, dynamic>) return payload;
    } on FormatException {
      throw const BookingException(
        'The server returned an invalid booking response.',
      );
    }
    throw const BookingException(
      'The server returned an invalid booking response.',
    );
  }

  String _serverErrorMessage(http.Response response) {
    try {
      final payload = jsonDecode(response.body);
      if (payload is String && payload.isNotEmpty) return payload;
      if (payload is Map<String, dynamic>) {
        final message = payload['message'] ?? payload['error'];
        if (message is String && message.isNotEmpty) return message;
      }
    } on FormatException {
      // Fall through to an explicit endpoint availability message.
    }
    if (response.statusCode == 404) {
      return 'This booking service is not available on the configured backend yet.';
    }
    if (response.statusCode == 401 || response.statusCode == 403) {
      return 'The backend could not verify your customer session. Sign in again or contact support.';
    }
    return 'Booking request failed (${response.statusCode}). Please try again.';
  }

  String _date(DateTime? date) {
    if (date == null) {
      throw const BookingException('Select a booking date before continuing.');
    }
    return '${date.year.toString().padLeft(4, '0')}-'
        '${date.month.toString().padLeft(2, '0')}-'
        '${date.day.toString().padLeft(2, '0')}';
  }

  String _tripTypeValue(BookingDraft draft) => switch (draft.tripType) {
        BookingTripType.airportPickup => 'AIRPORT_PICKUP',
        BookingTripType.airportDropoff => 'AIRPORT_DROPOFF',
        BookingTripType.normalTransfer => 'NORMAL_TRANSFER',
      };

  String? _time(DateTime? time) => time == null
      ? null
      : '${time.hour.toString().padLeft(2, '0')}:'
          '${time.minute.toString().padLeft(2, '0')}:'
          '${time.second.toString().padLeft(2, '0')}';
}

class AvailabilityResult {
  const AvailabilityResult._({
    required this.isAvailable,
    this.vehicle,
    this.message,
  });

  factory AvailabilityResult.available(RecommendedVehicle vehicle) =>
      AvailabilityResult._(isAvailable: true, vehicle: vehicle);

  factory AvailabilityResult.unavailable(String message) =>
      AvailabilityResult._(isAvailable: false, message: message);

  final bool isAvailable;
  final RecommendedVehicle? vehicle;
  final String? message;
}

class BookingException implements Exception {
  const BookingException(this.message, {this.requiresLogin = false});

  final String message;
  final bool requiresLogin;

  @override
  String toString() => message;
}
