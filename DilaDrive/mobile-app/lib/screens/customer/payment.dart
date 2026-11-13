import 'package:flutter/material.dart';

import '../../models/customer_booking.dart';
import '../../models/customer_payment.dart';
import '../../services/customer_payment_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';
import 'upload_receipt.dart';

class PaymentPage extends StatefulWidget {
  const PaymentPage({super.key, required this.booking, this.paymentService});

  final CustomerBooking booking;
  final CustomerPaymentService? paymentService;

  @override
  State<PaymentPage> createState() => _PaymentPageState();
}

class _PaymentPageState extends State<PaymentPage> {
  late final bool _ownsService = widget.paymentService == null;
  late final CustomerPaymentService _service =
      widget.paymentService ?? CustomerPaymentService();
  late Future<List<CustomerPayment>> _payments = _service.getMyPayments();

  @override
  void dispose() {
    if (_ownsService) _service.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    final future = _service.getMyPayments();
    setState(() => _payments = future);
    try {
      await future;
    } catch (_) {
      // The page renders an error state with a retry action.
    }
  }

  Future<void> _upload(double remaining) async {
    final submitted = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => UploadReceiptPage(
          booking: widget.booking,
          paymentService: _service,
          remainingAmount: remaining,
        ),
      ),
    );
    if (submitted == true && mounted) await _refresh();
  }

  void _openLogin() => Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute<void>(builder: (_) => const LoginPage()),
        (_) => false,
      );

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: const Text('Payment information')),
        body: FutureBuilder<List<CustomerPayment>>(
          future: _payments,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              final error = snapshot.error;
              final loginRequired =
                  error is CustomerPaymentException && error.requiresLogin;
              return _PaymentMessage(
                message: error is CustomerPaymentException
                    ? error.message
                    : 'Payment information could not be loaded.',
                action: loginRequired ? 'Sign in' : 'Try again',
                onAction: loginRequired ? _openLogin : _refresh,
              );
            }

            final payments = (snapshot.data ?? const <CustomerPayment>[])
                .where((payment) => payment.bookingId == widget.booking.id)
                .toList(growable: false);
            final fare = widget.booking.fare;
            final verified = payments
                .where((payment) => payment.status.toUpperCase() == 'VERIFIED')
                .fold<double>(0, (total, item) => total + item.amount);
            final remaining = fare == null
                ? null
                : (fare - verified).clamp(0.0, fare).toDouble();
            final pending = payments.any((payment) =>
                payment.status.toUpperCase() == 'PENDING_VERIFICATION');

            return RefreshIndicator(
              onRefresh: _refresh,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(18),
                children: [
                  Card(
                    color: AppTheme.primaryNavy,
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Text(
                        fare == null
                            ? 'Admin has not set the final fare yet.'
                            : pending
                                ? 'Your proof is with Admin for review.'
                                : remaining == 0
                                    ? 'The confirmed fare has been paid.'
                                    : 'Submit proof for a manual payment. Admin will verify it.',
                        style:
                            const TextStyle(color: Colors.white, fontSize: 16),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  _InfoCard(title: 'Booking', value: widget.booking.reference),
                  _InfoCard(
                    title: 'Confirmed fare',
                    value: fare == null ? 'Not set by Admin' : _money(fare),
                  ),
                  if (fare != null) ...[
                    _InfoCard(
                        title: 'Verified payments', value: _money(verified)),
                    _InfoCard(
                        title: 'Remaining balance', value: _money(remaining!)),
                  ],
                  if (payments.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    const Text('Payment history',
                        style: TextStyle(
                            color: AppTheme.primaryNavy,
                            fontWeight: FontWeight.w700,
                            fontSize: 17)),
                    const SizedBox(height: 6),
                    for (final payment in payments) ...[
                      _PaymentRecord(payment: payment),
                      const SizedBox(height: 8),
                    ],
                  ],
                  const SizedBox(height: 10),
                  const Card(
                    child: Padding(
                      padding: EdgeInsets.all(16),
                      child: Text(
                        'Payment is handled outside this app. Do not enter card details here. Card payments include the documented 6% surcharge. A receipt is issued after Admin verifies the proof.',
                        style: TextStyle(
                            color: AppTheme.secondaryText, height: 1.4),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: fare == null ||
                            remaining == 0 ||
                            pending ||
                            !const {
                              'CONFIRMED',
                              'ASSIGNED',
                              'OTW',
                              'WAITING',
                              'PICKUP',
                              'DROP',
                              'COMPLETED',
                            }.contains(widget.booking.status.toUpperCase())
                        ? null
                        : () => _upload(remaining!),
                    icon: const Icon(Icons.upload_file_outlined),
                    label: Text(pending
                        ? 'Waiting for Admin review'
                        : 'Upload payment proof'),
                  ),
                ],
              ),
            );
          },
        ),
      );
}

class _PaymentRecord extends StatelessWidget {
  const _PaymentRecord({required this.payment});
  final CustomerPayment payment;

  @override
  Widget build(BuildContext context) {
    final status = payment.status.toUpperCase();
    final Color color = switch (status) {
      'VERIFIED' => Colors.green.shade700,
      'REJECTED' => Colors.red.shade700,
      _ => Colors.orange.shade800,
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(
              child: Text(
                  '${_money(payment.totalAmount)} • ${_method(payment.paymentMethod)}',
                  style: const TextStyle(fontWeight: FontWeight.w700)),
            ),
            Text(_status(status),
                style: TextStyle(color: color, fontWeight: FontWeight.w700)),
          ]),
          if (payment.surchargeAmount > 0)
            Text('Includes ${_money(payment.surchargeAmount)} card surcharge',
                style: const TextStyle(color: AppTheme.secondaryText)),
          if (payment.rejectReason?.isNotEmpty == true) ...[
            const SizedBox(height: 6),
            Text('Admin note: ${payment.rejectReason}',
                style: TextStyle(color: Colors.red.shade700)),
          ],
        ]),
      ),
    );
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({required this.title, required this.value});
  final String title;
  final String value;

  @override
  Widget build(BuildContext context) => Card(
        child: ListTile(
          title: Text(title,
              style: const TextStyle(color: AppTheme.secondaryText)),
          subtitle: Text(value,
              style: const TextStyle(color: AppTheme.mainText, fontSize: 16)),
        ),
      );
}

class _PaymentMessage extends StatelessWidget {
  const _PaymentMessage(
      {required this.message, required this.action, required this.onAction});
  final String message;
  final String action;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            FilledButton.tonal(onPressed: onAction, child: Text(action)),
          ]),
        ),
      );
}

String _money(double amount) => 'RM ${amount.toStringAsFixed(2)}';
String _method(String value) => switch (value.toUpperCase()) {
      'BANK_TRANSFER' => 'Bank transfer',
      'CARD' => 'Card',
      'CASH' => 'Cash',
      _ => value,
    };
String _status(String value) => switch (value) {
      'VERIFIED' => 'Verified',
      'REJECTED' => 'Rejected',
      _ => 'Pending review',
    };
