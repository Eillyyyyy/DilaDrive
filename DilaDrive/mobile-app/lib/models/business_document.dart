class BusinessDocument {
  const BusinessDocument({
    required this.id,
    required this.number,
    required this.type,
    required this.bookingId,
    required this.amount,
    required this.status,
    this.sourceDocumentId,
    this.createdAt,
    this.remarks,
  });

  final int id;
  final String number;
  final String type;
  final int bookingId;
  final int? sourceDocumentId;
  final double amount;
  final String status;
  final DateTime? createdAt;
  final String? remarks;

  String get bookingReference => 'BK-${bookingId.toString().padLeft(6, '0')}';

  factory BusinessDocument.fromJson(Map<String, dynamic> json) {
    final id = _integer(json['documentId']);
    final number = json['documentNumber'];
    final type = json['documentType'];
    final bookingId = _integer(json['bookingId']);
    final amount = _decimal(json['amount']);
    final status = json['status'];
    if (id == null ||
        number is! String ||
        type is! String ||
        bookingId == null ||
        amount == null ||
        status is! String) {
      throw const FormatException(
          'The server returned incomplete document data.');
    }
    return BusinessDocument(
      id: id,
      number: number,
      type: type,
      bookingId: bookingId,
      sourceDocumentId: _integer(json['sourceDocumentId']),
      amount: amount,
      status: status,
      createdAt: json['createdAt'] is String
          ? DateTime.tryParse(json['createdAt'] as String)
          : null,
      remarks: json['remarks'] is String ? json['remarks'] as String : null,
    );
  }
}

int? _integer(Object? value) => value is int
    ? value
    : value is num && value == value.roundToDouble()
        ? value.toInt()
        : null;

double? _decimal(Object? value) => value is num && value.isFinite
    ? value.toDouble()
    : value is String
        ? double.tryParse(value)
        : null;
