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

  final String customerName;
  final String phoneNumber;

  String pickupAddress = '';
  String destinationAddress = '';
  BookingTripType tripType = BookingTripType.normalTransfer;
  DateTime? bookingDate;
  DateTime? pickupTime;
  DateTime? landedTime;
  String? flightNumber;
  int passengerCount = 1;
  int luggageCount = 0;
  String? luggageSize;
  String? additionalNotes;

  RecommendedVehicle? recommendedVehicle;
  FareEstimate? fareEstimate;

  DateTime? get scheduledAt =>
      tripType == BookingTripType.airportPickup ? landedTime : pickupTime;
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
    final id = _asInt(json['vehicleId']);
    final model = json['model'];
    final type = json['vehicleType'];
    final passengerCapacity = _asInt(json['passengerCapacity']);
    final luggageCapacity = _asInt(json['luggageCapacity']);
    if (id == null ||
        model is! String ||
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
  });

  final double distanceKm;
  final String vehicleModel;
  final double baseFare;
  final double distanceCharge;
  final List<FareCharge> additionalCharges;
  final double total;

  factory FareEstimate.fromJson(Map<String, dynamic> json) {
    final distanceKm = _asDouble(json['estimatedDistanceKm']);
    final vehicleModel = json['vehicleModel'];
    final baseFare = _asDouble(json['baseFare']);
    final distanceCharge = _asDouble(json['distanceCharge']);
    final total = _asDouble(json['totalFare']);
    final rawCharges = json['additionalCharges'];

    if (distanceKm == null ||
        vehicleModel is! String ||
        baseFare == null ||
        distanceCharge == null ||
        total == null ||
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
      if (label is! String || amount == null) {
        throw const FormatException(
          'The fare service returned invalid additional charges.',
        );
      }
      return FareCharge(label: label, amount: amount);
    }).toList(growable: false);

    return FareEstimate(
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
