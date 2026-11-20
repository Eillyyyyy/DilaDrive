import 'dart:convert';

enum BookingTripType {
  airportPickup('Airport Pickup'),
  airportDropoff('Airport Drop-off'),
  normalTransfer('Normal Transfer');

  const BookingTripType(this.label);

  final String label;
}

class BookingDraft {
  BookingDraft({
    required this.customerName,
    required this.phoneNumber,
  });

  int? existingBookingId;
  final String customerName;
  final String phoneNumber;

  String pickupAddress = '';
  String destinationAddress = '';
  String pickupSearchQuery = '';
  String destinationSearchQuery = '';
  bool pickupManuallyConfirmed = false;
  bool destinationManuallyConfirmed = false;
  BookingCoordinates? pickupCoordinates;
  BookingCoordinates? destinationCoordinates;
  BookingTripType tripType = BookingTripType.normalTransfer;
  DateTime? bookingDate;
  DateTime? pickupTime;
  DateTime? landedTime;
  String? flightNumber;
  int passengerCount = 1;
  int smallCount = 0;
  int mediumCount = 0;
  int largeCount = 0;
  int get luggageUnits => smallCount + 2 * mediumCount + 3 * largeCount;
  Map<String, dynamic>? roadRoute;
  double? get routeDistanceKm => (roadRoute?['distanceKm'] as num?)?.toDouble();
  int? get routeDurationSeconds =>
      (roadRoute?['durationSeconds'] as num?)?.round();
  int luggageCount = 0;
  String? luggageSize;
  String? additionalNotes;

  RecommendedVehicle? recommendedVehicle;
  FareEstimate? fareEstimate;
  String? availabilityCheckedFor;
  int availabilityRequestVersion = 0;
  String get availabilityKey => jsonEncode([
        pickupAddress,
        destinationAddress,
        pickupCoordinates?.latitude,
        pickupCoordinates?.longitude,
        destinationCoordinates?.latitude,
        destinationCoordinates?.longitude,
        scheduledAt?.toIso8601String(),
        bookingDate?.toIso8601String(),
        tripType.name,
        passengerCount,
        smallCount,
        mediumCount,
        largeCount,
        flightNumber,
        additionalNotes,
        routeDistanceKm,
        routeDurationSeconds,
      ]);
  bool get hasFreshAvailability =>
      recommendedVehicle != null && availabilityCheckedFor == availabilityKey;
  void clearAvailability() {
    availabilityRequestVersion++;
    availabilityCheckedFor = null;
    recommendedVehicle = null;
    fareEstimate = null;
  }

  DateTime? get scheduledAt =>
      tripType == BookingTripType.airportPickup ? landedTime : pickupTime;
}

class BookingCoordinates {
  const BookingCoordinates({
    required this.latitude,
    required this.longitude,
  });

  final double latitude;
  final double longitude;

  bool get isValid =>
      latitude.isFinite &&
      longitude.isFinite &&
      latitude >= -90 &&
      latitude <= 90 &&
      longitude >= -180 &&
      longitude <= 180;
}

class RecommendedVehicle {
  const RecommendedVehicle({
    required this.id,
    required this.model,
    required this.type,
    required this.passengerCapacity,
    required this.luggageCapacity,
  });

  final int id;
  final String model;
  final String type;
  final int passengerCapacity;
  final int? luggageCapacity;

  factory RecommendedVehicle.fromJson(Map<String, dynamic> json) {
    final id = _asInt(json['vehicleId']) ?? 0;
    final model = json['model'];
    final type = json['vehicleType'];
    final passengerCapacity = _asInt(json['passengerCapacity']);
    final luggageCapacity = _asInt(json['luggageCapacity']);
    if (model is! String ||
        model.trim().isEmpty ||
        type is! String ||
        type.trim().isEmpty ||
        passengerCapacity == null) {
      throw const FormatException(
        'The availability service returned incomplete vehicle details.',
      );
    }

    return RecommendedVehicle(
      id: id,
      model: model,
      type: type,
      passengerCapacity: passengerCapacity,
      luggageCapacity: luggageCapacity,
    );
  }
}

class FareEstimate {
  const FareEstimate({
    required this.distanceKm,
    required this.vehicleModel,
    required this.baseFare,
    required this.distanceCharge,
    required this.additionalCharges,
    required this.total,
    this.ratePerKm,
    this.durationSeconds = 0,
    this.quoteToken,
  });

  final double distanceKm;
  final String vehicleModel;
  final double baseFare;
  final double distanceCharge;
  final List<FareCharge> additionalCharges;
  final double total;
  final int durationSeconds;
  final double? ratePerKm;
  final String? quoteToken;

  factory FareEstimate.fromJson(Map<String, dynamic> json) {
    final distanceKm = _asDouble(json['estimatedDistanceKm']);
    final vehicleModel = json['vehicleModel'];
    final baseFare = _asDouble(json['baseFare']);
    final distanceCharge = _asDouble(json['distanceCharge']);
    final total = _asDouble(json['totalFare']);
    final rawCharges = json['additionalCharges'];

    if (distanceKm == null ||
        distanceKm <= 0 ||
        vehicleModel is! String ||
        baseFare == null ||
        baseFare < 0 ||
        distanceCharge == null ||
        distanceCharge < 0 ||
        total == null ||
        total < 0 ||
        (json['currency'] != null && json['currency'] != 'MYR') ||
        rawCharges is! List) {
      throw const FormatException(
        'The fare service returned incomplete pricing details.',
      );
    }

    final charges = rawCharges.map((rawCharge) {
      if (rawCharge is! Map<String, dynamic>) {
        throw const FormatException(
          'The fare service returned invalid additional charges.',
        );
      }
      final label = rawCharge['label'];
      final amount = _asDouble(rawCharge['amount']);
      if (label is! String || amount == null || amount < 0) {
        throw const FormatException(
          'The fare service returned invalid additional charges.',
        );
      }
      return FareCharge(label: label, amount: amount);
    }).toList(growable: false);

    return FareEstimate(
      quoteToken: json['quoteToken'] as String?,
      durationSeconds: _asInt(json['durationSeconds']) ?? 0,
      ratePerKm: _asDouble(json['ratePerKm']),
      distanceKm: distanceKm,
      vehicleModel: vehicleModel,
      baseFare: baseFare,
      distanceCharge: distanceCharge,
      additionalCharges: charges,
      total: total,
    );
  }
}

class FareCharge {
  const FareCharge({required this.label, required this.amount});

  final String label;
  final double amount;
}

int? _asInt(Object? value) {
  if (value is int) return value;
  if (value is num && value.isFinite && value == value.roundToDouble()) {
    return value.toInt();
  }
  return null;
}

double? _asDouble(Object? value) {
  if (value is num && value.isFinite) return value.toDouble();
  return null;
}
