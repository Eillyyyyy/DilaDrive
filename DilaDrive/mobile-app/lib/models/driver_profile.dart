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
    );
  }
}

String? _optionalString(Object? value) => value is String ? value : null;
