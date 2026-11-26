import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/booking_draft.dart';
import '../models/booking_schedule.dart';
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
    final key = draft.availabilityKey;
    draft.clearAvailability();
    final version = draft.availabilityRequestVersion;
    try {
      final payload = await _quote(draft, availabilityOnly: true);
      final status = AvailabilityStatus.values
          .where((s) => s.apiValue == payload['availabilityStatus'])
          .firstOrNull;
      if (status == null ||
          (payload['available'] == true) !=
              (status == AvailabilityStatus.available)) {
        throw const BookingException(
            'The availability API response is incompatible. Restart the updated backend and verify API_BASE_URL.');
      }
      if (version != draft.availabilityRequestVersion ||
          key != draft.availabilityKey) {
        return AvailabilityResult.unavailable(
            'Trip details changed. Check availability again.',
            status: AvailabilityStatus.serviceError);
      }
      final available = payload['available'];
      if (available is! bool) {
        throw const BookingException(
          'The availability service returned an invalid response.',
        );
      }

      if (!available) {
        final message = payload['message'];
        return AvailabilityResult.unavailable(
          message is String && message.isNotEmpty ? message : status.message,
          status: status,
        );
      }

      final rawVehicle = payload['vehicle'];
      if (rawVehicle is! Map<String, dynamic>) {
        throw const BookingException(
          'The availability service returned no recommended vehicle.',
        );
      }

      try {
        final vehicle = RecommendedVehicle.fromJson(rawVehicle);
        draft.recommendedVehicle = vehicle;
        draft.availabilityCheckedFor = key;
        final categories = payload['suitableVehicleTypes'];
        return AvailabilityResult.available(vehicle,
            categories: categories is List
                ? categories.whereType<String>().toList(growable: false)
                : const []);
      } on FormatException catch (exception) {
        throw BookingException(exception.message.toString());
      }
    } on BookingException catch (error) {
      if (version == draft.availabilityRequestVersion) {
        draft.clearAvailability();
      }
      if (error.requiresLogin ||
          error.code == 'ROAD_DISTANCE_REVIEW_AVAILABLE') {
        rethrow;
      }
      return AvailabilityResult.unavailable(error.message,
          status: AvailabilityStatus.serviceError);
    } on FormatException catch (error) {
      if (version == draft.availabilityRequestVersion) {
        draft.clearAvailability();
      }
      return AvailabilityResult.unavailable(error.message.toString(),
          status: AvailabilityStatus.serviceError);
    }
  }

  Future<FareEstimate> estimateFare(BookingDraft draft) async {
    if (!draft.hasFreshAvailability) {
      throw const BookingException(
          'Check vehicle availability again before calculating fare.');
    }
    final key = draft.availabilityKey;
    final version = draft.availabilityRequestVersion;
    try {
      final payload = await _quote(draft);
      if (payload['available'] != true) {
        throw BookingException(
            payload['message'] as String? ?? 'No vehicles available.');
      }
      final estimate = FareEstimate.fromJson(payload);
      if (draft.availabilityKey != key ||
          version != draft.availabilityRequestVersion) {
        throw const BookingException(
            'Trip details changed. Check availability again.');
      }
      draft.recommendedVehicle = RecommendedVehicle.fromJson(
          payload['vehicle'] as Map<String, dynamic>);
      draft.fareEstimate = estimate;
      return estimate;
    } on BookingException catch (error) {
      if (version == draft.availabilityRequestVersion) {
        draft.fareEstimate = null;
        if (!const {'PRICING_NOT_CONFIGURED', 'INVALID_PRICING_CONFIGURATION'}
            .contains(error.code)) {
          draft.clearAvailability();
        }
      }
      rethrow;
    } on FormatException catch (error) {
      if (version == draft.availabilityRequestVersion) {
        draft.clearAvailability();
      }
      throw BookingException(error.message.toString());
    }
  }

  Future<BookingVehicleQuote> quoteVehicle(
      BookingDraft draft, String category) async {
    final payload = await _quote(draft, category: category);
    if (payload['available'] != true) {
      throw BookingException(
          payload['message'] as String? ?? 'No vehicles available.');
    }
    final vehicle = payload['vehicle'];
    if (vehicle is! Map<String, dynamic>) {
      throw const BookingException('Vehicle details are unavailable.');
    }
    try {
      return BookingVehicleQuote(
          RecommendedVehicle.fromJson(vehicle), FareEstimate.fromJson(payload));
    } on FormatException catch (error) {
      throw BookingException(error.message.toString());
    }
  }

  Map<String, dynamic> tripPayload(BookingDraft draft,
      {bool routeOnly = false}) {
    final pickup = draft.pickupCoordinates;
    final destination = draft.destinationCoordinates;
    if (pickup?.isValid != true || destination?.isValid != true) {
      throw const BookingException(
          'Choose geocoded pickup and destination locations before continuing.');
    }
    return {
      'pickupAddress': draft.pickupAddress,
      'destinationAddress': draft.destinationAddress,
      'pickupLatitude': pickup!.latitude,
      'pickupLongitude': pickup.longitude,
      'destinationLatitude': destination!.latitude,
      'destinationLongitude': destination.longitude,
      'scheduledAt': (routeOnly
              ? malaysiaNow().add(const Duration(days: 1))
              : draft.scheduledAt)
          ?.toIso8601String()
          .replaceFirst(RegExp(r'Z$'), ''),
      'passengerCount': draft.passengerCount,
      'smallCount': draft.smallCount,
      'mediumCount': draft.mediumCount,
      'largeCount': draft.largeCount,
      'tripType': _tripTypeValue(draft),
      'flightNumber': draft.flightNumber,
      if (draft.flightDate case final date?) 'flightDate': bookingIsoDate(date),
      if (draft.flightTime case final time?) 'flightTime': time,
      'additionalNotes': draft.additionalNotes,
      if (!routeOnly && draft.recommendedVehicle != null)
        'vehicleCategory': draft.recommendedVehicle!.type,
      if (!routeOnly && draft.fareEstimate?.quoteToken != null)
        'acceptedQuoteToken': draft.fareEstimate!.quoteToken,
    };
  }

  Future<Map<String, dynamic>> previewRoute(BookingDraft draft) async {
    return _decodeObject(await _request(() => _client.post(
          Uri.parse('${AppConfig.apiBaseUrl}/api/bookings/route'),
          headers: {
            'Content-Type': 'application/json',
            ...MobileSession.headers
          },
          body: jsonEncode(tripPayload(draft, routeOnly: true)),
        )));
  }

  Future<Map<String, dynamic>> _quote(BookingDraft draft,
      {bool availabilityOnly = false, String? category}) async {
    final endpoint = availabilityOnly ? 'availability' : 'estimate';
    final uri = Uri.parse('${AppConfig.apiBaseUrl}/api/bookings/$endpoint')
        .replace(
            queryParameters: draft.existingBookingId == null
                ? null
                : {'bookingId': '${draft.existingBookingId}'});
    final payload = tripPayload(draft);
    if (category != null) {
      payload['vehicleCategory'] = category;
      payload.remove('acceptedQuoteToken');
    }
    return _decodeObject(await _request(() => _client.post(
          uri,
          headers: {
            'Content-Type': 'application/json',
            ...MobileSession.headers
          },
          body: jsonEncode(payload),
        )));
  }

  Future<String> createBooking(
    BookingDraft draft, {
    required String idempotencyKey,
  }) async {
    if (draft.bookingDate == null ||
        !draft.hasFreshAvailability ||
        draft.fareEstimate == null) {
      throw const BookingException(
        'Live vehicle availability is required before submission.',
      );
    }

    return _submitBooking(draft, idempotencyKey: idempotencyKey);
  }

  Future<String> requestRoadDistanceReview(
    BookingDraft draft, {
    required String idempotencyKey,
  }) {
    if (draft.existingBookingId != null || draft.bookingDate == null) {
      throw const BookingException(
          'Only a new booking can request distance review.');
    }
    return _submitBooking(draft,
        idempotencyKey: idempotencyKey, distanceReview: true);
  }

  Future<String> _submitBooking(
    BookingDraft draft, {
    required String idempotencyKey,
    bool distanceReview = false,
  }) async {
    final uri = Uri.parse(distanceReview
        ? '${AppConfig.apiBaseUrl}/api/bookings/customer/road-distance-review'
        : draft.existingBookingId == null
            ? '${AppConfig.apiBaseUrl}/api/bookings/customer/complete'
            : '${AppConfig.apiBaseUrl}/api/bookings/${draft.existingBookingId}/customer/complete');
    final headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Idempotency-Key': idempotencyKey,
      ...MobileSession.headers
    };
    final body = jsonEncode(tripPayload(draft));
    final response = await _request(() => draft.existingBookingId == null
        ? _client.post(uri, headers: headers, body: body)
        : _client.put(uri, headers: headers, body: body));
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
        Map<String, dynamic>? error;
        try {
          final body = jsonDecode(response.body);
          if (body is Map<String, dynamic>) error = body;
        } on FormatException {/* Use the standard endpoint error below. */}
        throw BookingException(_serverErrorMessage(response),
            code: error?['code'] as String?,
            refreshedQuote: error?['refreshedQuote'] is Map<String, dynamic>
                ? error!['refreshedQuote'] as Map<String, dynamic>
                : null);
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

enum AvailabilityStatus {
  available('AVAILABLE', 'Available when checked'),
  capacityUnavailable(
      'CAPACITY_UNAVAILABLE', 'No ride fits your trip details.'),
  passengerCapacityUnavailable('PASSENGER_CAPACITY_UNAVAILABLE',
      'No ride can accommodate this many passengers.'),
  luggageCapacityUnavailable('LUGGAGE_CAPACITY_UNAVAILABLE',
      'No ride can accommodate the selected passengers and luggage.'),
  scheduleUnavailable(
      'SCHEDULE_UNAVAILABLE', 'No rides available for this time.'),
  driverUnavailable(
      'DRIVER_UNAVAILABLE', 'No drivers are available for this ride.'),
  noVehicles('NO_VEHICLES', 'No vehicles are currently listed.'),
  fleetNotConfigured('FLEET_NOT_CONFIGURED',
      'Vehicle availability is not configured. Please retry later or contact DilaDrive.'),
  serviceError('SERVICE_ERROR',
      'Unable to check vehicle availability. Please try again.');

  const AvailabilityStatus(this.apiValue, this.message);
  final String apiValue;
  final String message;
}

class AvailabilityResult {
  const AvailabilityResult._({
    required this.isAvailable,
    this.vehicle,
    this.categories = const [],
    this.message,
    required this.status,
  });

  factory AvailabilityResult.available(RecommendedVehicle vehicle,
          {List<String> categories = const []}) =>
      AvailabilityResult._(
          isAvailable: true,
          vehicle: vehicle,
          categories: categories,
          status: AvailabilityStatus.available);

  factory AvailabilityResult.unavailable(String message,
          {AvailabilityStatus status =
              AvailabilityStatus.scheduleUnavailable}) =>
      AvailabilityResult._(
          isAvailable: false, message: message, status: status);

  final bool isAvailable;
  final AvailabilityStatus status;
  final RecommendedVehicle? vehicle;
  final List<String> categories;
  final String? message;
}

class BookingVehicleQuote {
  const BookingVehicleQuote(this.vehicle, this.fare);

  final RecommendedVehicle vehicle;
  final FareEstimate fare;
}

class BookingException implements Exception {
  const BookingException(this.message,
      {this.requiresLogin = false, this.code, this.refreshedQuote});

  final String message;
  final bool requiresLogin;
  final String? code;
  final Map<String, dynamic>? refreshedQuote;

  @override
  String toString() => message;
}
