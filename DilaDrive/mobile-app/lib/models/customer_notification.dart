class CustomerNotification {
  const CustomerNotification({
    required this.id,
    required this.title,
    required this.message,
    required this.read,
    this.type,
    this.sentAt,
  });

  final int id;
  final String title;
  final String message;
  final bool read;
  final String? type;
  final DateTime? sentAt;

  factory CustomerNotification.fromJson(Map<String, dynamic> json) {
    final id = json['notificationId'];
    final title = json['title'];
    final message = json['message'];
    final read = json['read'];
    if (id is! int || title is! String || message is! String || read is! bool) {
      throw const FormatException(
          'The server returned an invalid notification.');
    }
    return CustomerNotification(
      id: id,
      title: title,
      message: message,
      read: read,
      type: json['notificationType'] is String
          ? json['notificationType'] as String
          : null,
      sentAt: json['sentAt'] is String
          ? DateTime.tryParse(json['sentAt'] as String)
          : null,
    );
  }
}
