class DriverProfile {
  const DriverProfile({
    required this.id,
    required this.name,
    required this.email,
    this.phone,
    this.status,
    this.licenseNumber,
    this.licenseExpiry,
    this.driverType,
    this.availability,
    this.profileImage,
    this.vehicleModel,
    this.vehiclePlate,
    this.vehicleType,
    this.vehicleCapacity,
    this.vehicleRoadTaxExpiry,
    this.vehicleStatus,
    this.vehicleAssigned,
    this.totalTrips,
    this.completedTrips,
    this.upcomingTrips,
  });

  final int id;
  final String name;
  final String email;
  final String? phone;
  final String? status;
  final String? licenseNumber;
  final String? licenseExpiry;
  final String? driverType;
  final String? availability;
  final String? profileImage;
  final String? vehicleModel;
  final String? vehiclePlate;
  final String? vehicleType;
  final int? vehicleCapacity;
  final String? vehicleRoadTaxExpiry;
  final String? vehicleStatus;
  final bool? vehicleAssigned;

  final int? totalTrips;
  final int? completedTrips;
  final int? upcomingTrips;

  // Only an explicit backend result can confirm that no vehicle exists.
  bool get vehicleAssignmentKnown =>
      vehicleAssigned != null || hasAssignedVehicle;

  bool get hasAssignedVehicle =>
      vehicleAssigned ??
      ([
            vehicleModel,
            vehiclePlate,
            vehicleType,
            vehicleStatus,
            vehicleRoadTaxExpiry
          ].any((value) => value != null && value.trim().isNotEmpty) ||
          vehicleCapacity != null);

  factory DriverProfile.fromJson(Map<String, dynamic> json) {
    final id = json['id'];
    final name = json['name'];
    final email = json['email'];
    if (id is! int || name is! String || email is! String) {
      throw const FormatException(
          'The server returned incomplete driver profile data.');
    }
    return DriverProfile(
      id: id,
      name: name,
      email: email,
      phone: _optionalString(json['phone']),
      status: _optionalString(json['status']),
      licenseNumber: _optionalString(json['licenseNumber']),
      licenseExpiry: _optionalString(json['licenseExpiry']),
      driverType: _optionalString(json['driverType']),
      availability: _optionalString(json['availability']),
      profileImage: _optionalString(json['profileImage']),
      vehicleModel: _optionalString(json['vehicleModel']),
      vehiclePlate: _optionalString(json['vehiclePlate']),
      vehicleType: _optionalString(json['vehicleType']),
      vehicleCapacity: json['vehicleCapacity'] is int
          ? json['vehicleCapacity'] as int
          : null,
      vehicleRoadTaxExpiry: _optionalString(json['vehicleRoadTaxExpiry']),
      vehicleStatus: _optionalString(json['vehicleStatus']),
      vehicleAssigned: json['vehicleAssigned'] is bool
          ? json['vehicleAssigned'] as bool
          : null,
      totalTrips: json['totalTrips'] is int ? json['totalTrips'] as int : null,
      completedTrips:
          json['completedTrips'] is int ? json['completedTrips'] as int : null,
      upcomingTrips:
          json['upcomingTrips'] is int ? json['upcomingTrips'] as int : null,
    );
  }
}

String? _optionalString(Object? value) => value is String ? value : null;
