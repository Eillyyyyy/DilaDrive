class DriverTrip {
  const DriverTrip({
    required this.assignmentId,
    required this.bookingId,
    required this.assignmentStatus,
    this.customer,
    this.tripDate,
    this.pickupTime,
    this.tripType,
    this.flightNo,
    this.pickupAddress,
    this.destinationAddress,
    this.passengerCount,
    this.luggageCount,
    this.luggageSize,
    this.vehiclePlate,
    this.assignmentDate,
  });

  final int assignmentId;
  final int bookingId;
  final String assignmentStatus;
  final String? customer;
  final DateTime? tripDate;
  final String? pickupTime;
  final String? tripType;
  final String? flightNo;
  final String? pickupAddress;
  final String? destinationAddress;
  final int? passengerCount;
  final int? luggageCount;
  final String? luggageSize;
  final String? vehiclePlate;
  final DateTime? assignmentDate;

  String get bookingReference => 'BK-${bookingId.toString().padLeft(6, '0')}';

  String get normalizedStatus => assignmentStatus.trim().toUpperCase();

  String get normalizedTripType =>
      tripType?.trim().toUpperCase().replaceAll(RegExp(r'[ -]+'), '_') ?? '';

  bool get isAirportPickup => normalizedTripType == 'AIRPORT_PICKUP';

  String get tripTypeLabel => switch (normalizedTripType) {
        'AIRPORT_PICKUP' => 'Airport pickup',
        'AIRPORT_DROPOFF' => 'Airport drop-off',
        'NORMAL_TRANSFER' => 'Normal transfer',
        _ => tripType?.trim() ?? '',
      };

  bool get isCompleted => normalizedStatus == 'COMPLETED';

  bool get isCancelled => normalizedStatus == 'CANCELLED';

  bool get isActive => const {
        'ASSIGNED',
        'OTW',
        'WAITING',
        'PICKUP',
        'DROP',
      }.contains(normalizedStatus);

  String? get nextStatus => switch (normalizedStatus) {
        'ASSIGNED' => 'OTW',
        'OTW' => 'WAITING',
        'WAITING' => 'PICKUP',
        'PICKUP' => 'DROP',
        'DROP' => 'COMPLETED',
        _ => null,
      };

  String get statusLabel => switch (normalizedStatus) {
        'ASSIGNED' => 'Assigned',
        'OTW' => 'On the way',
        'WAITING' => 'Waiting for pickup',
        'PICKUP' => 'Passenger picked up',
        'DROP' => 'Drop-off in progress',
        'COMPLETED' => 'Completed',
        'CANCELLED' => 'Cancelled',
        _ => assignmentStatus,
      };

  factory DriverTrip.fromJson(Map<String, dynamic> json) {
    final assignmentId = _intValue(json['assignmentId']);
    final bookingId = _intValue(json['bookingId']);
    final assignmentStatus = json['assignmentStatus'];
    if (assignmentId == null ||
        bookingId == null ||
        assignmentStatus is! String ||
        assignmentStatus.trim().isEmpty) {
      throw const FormatException('The server returned incomplete trip data.');
    }
    return DriverTrip(
      assignmentId: assignmentId,
      bookingId: bookingId,
      assignmentStatus: assignmentStatus,
      customer: _stringValue(json['customer']),
      tripDate: _dateValue(json['tripDate']),
      pickupTime: _stringValue(json['pickupTime']),
      tripType: _stringValue(json['tripType']),
      flightNo: _stringValue(json['flightNo']),
      pickupAddress: _stringValue(json['pickupAddress']),
      destinationAddress: _stringValue(json['destinationAddress']),
      passengerCount: _intValue(json['passengerCount']),
      luggageCount: _intValue(json['luggageCount']),
      luggageSize: _stringValue(json['luggageSize']),
      vehiclePlate: _stringValue(json['vehiclePlate']),
      assignmentDate: _dateValue(json['assignmentDate']),
    );
  }
}

int compareDriverTripsForDashboard(DriverTrip left, DriverTrip right) {
  int priority(DriverTrip trip) => switch (trip.normalizedStatus) {
        'OTW' || 'WAITING' || 'PICKUP' || 'DROP' => 0,
        'ASSIGNED' => 1,
        _ => 2,
      };

  final byStatus = priority(left).compareTo(priority(right));
  if (byStatus != 0) return byStatus;

  DateTime? schedule(DriverTrip trip) {
    final date = trip.tripDate;
    if (date == null) return null;
    final timeParts = (trip.pickupTime ?? '').split(':');
    final hour = timeParts.isNotEmpty ? int.tryParse(timeParts[0]) : null;
    final minute = timeParts.length > 1 ? int.tryParse(timeParts[1]) : null;
    final second = timeParts.length > 2 ? int.tryParse(timeParts[2]) : 0;
    if (hour == null || minute == null || second == null) return null;
    return DateTime(date.year, date.month, date.day, hour, minute, second);
  }

  final leftSchedule = schedule(left);
  final rightSchedule = schedule(right);
  if (leftSchedule == null && rightSchedule == null) return 0;
  if (leftSchedule == null) return 1;
  if (rightSchedule == null) return -1;
  return leftSchedule.compareTo(rightSchedule);
}

int? _intValue(Object? value) => value is int
    ? value
    : value is num && value == value.roundToDouble()
        ? value.toInt()
        : null;

String? _stringValue(Object? value) => value is String ? value : null;

DateTime? _dateValue(Object? value) =>
    value is String ? DateTime.tryParse(value) : null;
