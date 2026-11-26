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
    this.flightDate,
    this.flightTime,
    this.passengerCount,
    this.luggageCount,
    this.luggageSize,
    this.distanceKm,
    this.waitingHours,
    this.fare,
    this.createdAt,
    this.smallCount,
    this.mediumCount,
    this.largeCount,
    this.luggageUnits,
    this.durationSeconds,
    this.pickupLatitude,
    this.pickupLongitude,
    this.destinationLatitude,
    this.destinationLongitude,
    this.contactName,
    this.contactPhone,
    this.rejectionReason,
    this.finalFare,
    this.payableFare,
    this.activeAssignment = false,
    this.assignedDriver,
    this.assignedVehicleModel,
    this.assignedVehiclePlate,
    this.assignedVehicleCategory,
    this.assignedTripStatus,
    this.reservationStatus,
  });

  final int? smallCount;
  final int? mediumCount;
  final int? largeCount;
  final int? luggageUnits;
  final int? durationSeconds;
  final double? pickupLatitude;
  final double? pickupLongitude;
  final double? destinationLatitude;
  final double? destinationLongitude;
  final String? contactName;
  final String? contactPhone;
  final String? rejectionReason;
  final double? finalFare;
  final double? payableFare;
  final bool activeAssignment;
  final String? assignedDriver;
  final String? assignedVehicleModel;
  final String? assignedVehiclePlate;
  final String? assignedVehicleCategory;
  final String? assignedTripStatus;
  final String? reservationStatus;
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
  final DateTime? flightDate;
  final String? flightTime;
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

  String get statusLabel =>
      switch (assignedTripStatus == null || assignedTripStatus == 'ASSIGNED'
          ? normalizedStatus
          : assignedTripStatus) {
        'PENDING' || 'PENDING_VERIFICATION' => 'Pending verification',
        'CONFIRMED' => 'Confirmed',
        'ASSIGNED' => 'Driver assigned',
        'OTW' => 'On the way',
        'WAITING' => 'Waiting',
        'PICKUP' => 'Passenger picked up',
        'DROP' => 'Drop-off',
        'COMPLETED' => 'Completed',
        'CANCELLED' => 'Cancelled',
        'REJECTED' => 'Rejected',
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

  bool get canCancel => isPending
      ? !activeAssignment
      : normalizedStatus == 'CONFIRMED' &&
          (assignedTripStatus == null || assignedTripStatus == 'ASSIGNED');

  factory CustomerBooking.fromJson(Map<String, dynamic> json) {
    final id = _integer(json['bookingId']);
    final status = json['status'];
    if (id == null || status is! String || status.trim().isEmpty) {
      throw const FormatException(
        'The booking service returned incomplete booking details.',
      );
    }

    final assignment = json['assignment'];
    final confirmed = assignment is Map<String, dynamic>
        ? assignment
        : const <String, dynamic>{};
    return CustomerBooking(
      assignedDriver: _string(confirmed['driverName']),
      assignedVehicleModel: _string(confirmed['vehicleModel']),
      assignedVehiclePlate: _string(confirmed['vehiclePlate']),
      assignedVehicleCategory: _string(confirmed['vehicleCategory']),
      assignedTripStatus: _string(confirmed['tripStatus']),
      reservationStatus: _string(json['reservationStatus']),
      smallCount: _integer(json['smallCount']),
      mediumCount: _integer(json['mediumCount']),
      largeCount: _integer(json['largeCount']),
      luggageUnits: _integer(json['luggageUnits']),
      durationSeconds: _integer(json['durationSeconds']),
      pickupLatitude: _decimal(json['pickupLatitude']),
      pickupLongitude: _decimal(json['pickupLongitude']),
      destinationLatitude: _decimal(json['destinationLatitude']),
      destinationLongitude: _decimal(json['destinationLongitude']),
      contactName: _string(json['contactName']),
      contactPhone: _string(json['contactPhone']),
      rejectionReason: _string(json['rejectionReason']),
      finalFare: _decimal(json['finalFare']),
      payableFare: _decimal(json['payableFare']),
      activeAssignment: json['activeAssignment'] == true,
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
      flightDate: _date(json['flightDate']),
      flightTime: _string(json['flightTime']),
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
