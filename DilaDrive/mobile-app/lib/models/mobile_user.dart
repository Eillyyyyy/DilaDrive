class MobileUser {
  const MobileUser({
    required this.id,
    required this.name,
    required this.email,
    required this.role,
    this.phone,
    this.status,
  });

  final int id;
  final String name;
  final String email;
  final String role;
  final String? phone;
  final String? status;

  factory MobileUser.fromJson(Map<String, dynamic> json) {
    final rawId = json['id'];
    final id = rawId is int
        ? rawId
        : rawId is String
            ? int.tryParse(rawId)
            : null;
    final name = json['name'];
    final email = json['email'];
    final role = json['role'];
    final phone = json['phone'];
    final status = json['status'];

    if (id == null ||
        name is! String ||
        email is! String ||
        role is! String ||
        (phone != null && phone is! String) ||
        (status != null && status is! String)) {
      throw const FormatException(
          'The server returned incomplete account data.');
    }

    return MobileUser(
      id: id,
      name: name,
      email: email,
      role: role,
      phone: phone is String ? phone : null,
      status: status is String ? status : null,
    );
  }
}
