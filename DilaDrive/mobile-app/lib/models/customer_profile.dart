class CustomerProfile {
  const CustomerProfile({
    required this.id,
    required this.name,
    required this.email,
    this.phone,
    this.status,
    this.type,
    this.companyName,
    this.companyRegistrationNumber,
    this.profileImage,
  });

  final int id;
  final String name;
  final String email;
  final String? phone;
  final String? status;
  final String? type;
  final String? companyName;
  final String? companyRegistrationNumber;
  final String? profileImage;

  factory CustomerProfile.fromJson(Map<String, dynamic> json) {
    final id = json['id'];
    final name = json['name'];
    final email = json['email'];
    if (id is! int || name is! String || email is! String) {
      throw const FormatException(
          'The server returned incomplete profile data.');
    }
    return CustomerProfile(
      id: id,
      name: name,
      email: email,
      phone: json['phone'] is String ? json['phone'] as String : null,
      status: json['status'] is String ? json['status'] as String : null,
      type: json['type'] is String ? json['type'] as String : null,
      companyName:
          json['companyName'] is String ? json['companyName'] as String : null,
      companyRegistrationNumber: json['companyRegistrationNumber'] is String
          ? json['companyRegistrationNumber'] as String
          : null,
      profileImage: json['profileImage'] is String ? json['profileImage'] as String : null,
    );
  }
}
