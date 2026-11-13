class CustomerPayment {
  const CustomerPayment({
    required this.id,
    required this.bookingId,
    required this.bookingReference,
    required this.amount,
    required this.surchargeAmount,
    required this.paymentMethod,
    required this.status,
    required this.submittedAt,
    this.bookingFare,
    this.rejectReason,
    this.proofUrl,
    this.verifiedAt,
  });

  final int id;
  final int bookingId;
  final String bookingReference;
  final double amount;
  final double surchargeAmount;
  final String paymentMethod;
  final String status;
  final DateTime? submittedAt;
  final double? bookingFare;
  final String? rejectReason;
  final String? proofUrl;
  final DateTime? verifiedAt;

  factory CustomerPayment.fromJson(Map<String, dynamic> json) {
    int? intValue(Object? value) => value is num ? value.toInt() : null;
    double? doubleValue(Object? value) =>
        value is num ? value.toDouble() : double.tryParse('$value');
    DateTime? dateValue(Object? value) =>
        value is String ? DateTime.tryParse(value) : null;
    final id = intValue(json['paymentId']);
    final bookingId = intValue(json['bookingId']);
    final amount = doubleValue(json['amount']);
    if (id == null || bookingId == null || amount == null) {
      throw const FormatException('Invalid payment record.');
    }
    return CustomerPayment(
      id: id,
      bookingId: bookingId,
      bookingReference: json['bookingReference'] as String? ??
          'BK-${bookingId.toString().padLeft(6, '0')}',
      bookingFare: doubleValue(json['bookingFare']),
      amount: amount,
      surchargeAmount: doubleValue(json['surchargeAmount']) ?? 0,
      paymentMethod: json['paymentMethod'] as String? ?? '',
      status: json['paymentStatus'] as String? ?? 'PENDING_VERIFICATION',
      rejectReason: json['rejectReason'] as String?,
      proofUrl: json['proofUrl'] as String?,
      submittedAt: dateValue(json['submittedAt']),
      verifiedAt: dateValue(json['verifiedAt']),
    );
  }

  double get totalAmount => amount + surchargeAmount;
}
