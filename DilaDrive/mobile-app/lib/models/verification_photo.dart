class VerificationPhoto {
  const VerificationPhoto({
    required this.photoId,
    required this.photoType,
    required this.uploadedAt,
  });

  final int photoId;
  final String photoType;
  final DateTime? uploadedAt;

  factory VerificationPhoto.fromJson(Map<String, dynamic> json) {
    final id = json['photoId'];
    final type = json['photoType'];
    if (id is! int || type is! String) {
      throw const FormatException('The server returned invalid photo data.');
    }
    return VerificationPhoto(
      photoId: id,
      photoType: type,
      uploadedAt: json['uploadedAt'] is String
          ? DateTime.tryParse(json['uploadedAt'] as String)
          : null,
    );
  }
}
