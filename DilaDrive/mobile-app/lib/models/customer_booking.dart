class CustomerBooking {
  const CustomerBooking({
    required this.id,
    required this.status,
    this.type,
    this.tripType,
    this.notes,
    this.tripDate,
    this.pickupTime,
    this.pickupAddress,
    this.destinationAddress,
    this.flightNumber,
    this.passengerCount,
    this.luggageCount,
    this.luggageSize,
    this.distanceKm,
    this.waitingHours,
    this.fare,
    this.createdAt,
  });

  final int id;
  final String status;
  final String? type;
  final String? tripType;
  final String? notes;
  final DateTime? tripDate;
  final String? pickupTime;
  final String? pickupAddress;
  final String? destinationAddress;
  final String? flightNumber;
  final int? passengerCount;
  final int? luggageCount;
  final String? luggageSize;
  final double? distanceKm;
  final double? waitingHours;
  final double? fare;
  final DateTime? createdAt;

  String get reference => 'BK-${id.toString().padLeft(6, '0')}';

  String get normalizedStatus =>
      status.trim().toUpperCase().replaceAll(RegExp(r'[ -]+'), '_');

  String get normalizedTripType =>
      tripType
          ?.trim()
          .replaceAllMapped(
            RegExp(r'([a-z])([A-Z])'),
            (match) => '${match[1]}_${match[2]}',
          )
          .toUpperCase()
          .replaceAll(RegExp(r'[ -]+'), '_') ??
      '';

  bool get isAirportPickup => normalizedTripType == 'AIRPORT_PICKUP';

  String get tripTypeLabel => switch (normalizedTripType) {
        'AIRPORT_PICKUP' => 'Airport pickup',
        'AIRPORT_DROPOFF' => 'Airport drop-off',
        'NORMAL_TRANSFER' => 'Normal transfer',
        _ => tripType?.trim() ?? '',
      };

  String get statusLabel => switch (normalizedStatus) {
        'PENDING' || 'PENDING_VERIFICATION' => 'Pending verification',
        'CONFIRMED' => 'Confirmed',
        'ASSIGNED' => 'Driver assigned',
        'OTW' => 'On the way',
        'WAITING' => 'Waiting',
        'PICKUP' => 'Passenger picked up',
        'DROP' => 'Drop-off',
        'COMPLETED' => 'Completed',
        'CANCELLED' => 'Cancelled',
        _ => status.trim().isEmpty ? 'Status unavailable' : status,
      };

  bool get isPending =>
      normalizedStatus == 'PENDING' ||
      normalizedStatus == 'PENDING_VERIFICATION';

  bool get isActive => const {
        'CONFIRMED',
        'ASSIGNED',
        'OTW',
        'WAITING',
        'PICKUP',
        'DROP',
      }.contains(normalizedStatus);

  bool get isCompleted => normalizedStatus == 'COMPLETED';

  bool get canCancel => const {
        'PENDING',
        'PENDING_VERIFICATION',
        'CONFIRMED',
      }.contains(normalizedStatus);

  factory CustomerBooking.fromJson(Map<String, dynamic> json) {
    final id = _integer(json['bookingId']);
    final status = json['status'];
    if (id == null || status is! String || status.trim().isEmpty) {
      throw const FormatException(
        'The booking service returned incomplete booking details.',
      );
    }

    return CustomerBooking(
      id: id,
      status: status,
      type: _string(json['bookingType']),
      tripType: _string(json['tripType']),
      notes: _string(json['notes']),
      tripDate: _date(json['tripDate']),
      pickupTime: _string(json['pickupTime']),
      pickupAddress: _string(json['pickupAddress']),
      destinationAddress: _string(json['destinationAddress']),
      flightNumber: _string(json['flightNo']),
      passengerCount: _integer(json['passengerCount']),
      luggageCount: _integer(json['luggageCount']),
      luggageSize: _string(json['luggageSize']),
      distanceKm: _decimal(json['distanceKm']),
      waitingHours: _decimal(json['waitingHour']),
      fare: _decimal(json['tripFare']),
      createdAt: _date(json['createdAt']),
    );
  }
}

String? _string(Object? value) => value is String ? value : null;

int? _integer(Object? value) {
  if (value is int) return value;
  if (value is num && value.isFinite && value == value.roundToDouble()) {
    return value.toInt();
  }
  return null;
}

double? _decimal(Object? value) {
  if (value is num && value.isFinite) return value.toDouble();
  if (value is String) return double.tryParse(value);
  return null;
}

DateTime? _date(Object? value) =>
    value is String ? DateTime.tryParse(value) : null;
