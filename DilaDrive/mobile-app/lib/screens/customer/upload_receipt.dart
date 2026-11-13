import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'dart:typed_data';

import '../../models/customer_booking.dart';
import '../../services/customer_payment_service.dart';
import '../../services/money_validation.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';

class UploadReceiptPage extends StatefulWidget {
  const UploadReceiptPage({
    super.key,
    required this.booking,
    required this.remainingAmount,
    this.paymentService,
  });

  final CustomerBooking booking;
  final double remainingAmount;
  final CustomerPaymentService? paymentService;

  @override
  State<UploadReceiptPage> createState() => _UploadReceiptPageState();
}

class _UploadReceiptPageState extends State<UploadReceiptPage> {
  late final bool _ownsService = widget.paymentService == null;
  late final CustomerPaymentService _service =
      widget.paymentService ?? CustomerPaymentService();
  late final TextEditingController _amountController =
      TextEditingController(text: widget.remainingAmount.toStringAsFixed(2));
  String _paymentMethod = 'BANK_TRANSFER';
  Uint8List? _proofBytes;
  String? _proofName;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _amountController.dispose();
    if (_ownsService) _service.dispose();
    super.dispose();
  }

  Future<void> _pickProof() async {
    final result = await FilePicker.pickFile(
      type: FileType.custom,
      allowedExtensions: const ['jpg', 'jpeg', 'png', 'pdf'],
    );
    if (result == null || !mounted) return;
    final bytes = await result.readAsBytes();
    if (!mounted) return;
    setState(() {
      _proofBytes = bytes;
      _proofName = result.name;
      _error = null;
    });
  }

  Future<void> _submit() async {
    final amountText = _amountController.text.trim();
    final amount = double.tryParse(amountText);
    if (!isValidMoneyInput(amountText) ||
        amount == null ||
        amount > widget.remainingAmount) {
      setState(() => _error =
          'Enter an amount up to RM ${widget.remainingAmount.toStringAsFixed(2)} with no more than two decimal places.');
      return;
    }
    final proofBytes = _proofBytes;
    final proofName = _proofName;
    if (proofBytes == null || proofName == null) {
      setState(() => _error = 'Choose a bank or payment receipt file.');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await _service.submit(
        bookingId: widget.booking.id,
        amount: amount,
        paymentMethod: _paymentMethod,
        proofBytes: proofBytes,
        proofFileName: proofName,
      );
      if (mounted) Navigator.pop(context, true);
    } on CustomerPaymentException catch (error) {
      if (!mounted) return;
      if (error.requiresLogin) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute<void>(builder: (_) => const LoginPage()),
          (_) => false,
        );
      } else {
        setState(() => _error = error.message);
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: const Text('Submit payment proof')),
        body: ListView(
          padding: const EdgeInsets.all(18),
          children: [
            Card(
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                ListTile(
                  title: const Text('Booking'),
                  subtitle: Text(widget.booking.reference),
                ),
                ListTile(
                  title: const Text('Remaining confirmed fare'),
                  subtitle:
                      Text('RM ${widget.remainingAmount.toStringAsFixed(2)}'),
                ),
              ]),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _amountController,
              enabled: !_submitting,
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(
                labelText: 'Payment amount (RM)',
                prefixIcon: Icon(Icons.payments_outlined),
              ),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _paymentMethod,
              decoration: const InputDecoration(
                labelText: 'Payment method',
                prefixIcon: Icon(Icons.account_balance_outlined),
              ),
              items: const [
                DropdownMenuItem(
                    value: 'BANK_TRANSFER', child: Text('Bank transfer')),
                DropdownMenuItem(value: 'CARD', child: Text('Card')),
              ],
              onChanged: _submitting
                  ? null
                  : (value) => setState(() => _paymentMethod = value!),
            ),
            if (_paymentMethod == 'CARD') ...[
              const SizedBox(height: 8),
              const Card(
                child: ListTile(
                  leading: Icon(Icons.info_outline,
                      color: AppTheme.secondarySkyBlue),
                  title: Text('Card surcharge: 6%'),
                  subtitle: Text(
                      'Card payment happens outside this app. Do not enter card details here.'),
                ),
              ),
            ],
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _submitting ? null : _pickProof,
              icon: const Icon(Icons.attach_file),
              label: Text(_proofName ?? 'Choose JPEG, PNG or PDF proof'),
            ),
            const SizedBox(height: 8),
            const Text('Maximum file size: 10 MB.',
                style: TextStyle(color: AppTheme.secondaryText)),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, style: const TextStyle(color: Colors.red)),
            ],
            const SizedBox(height: 18),
            FilledButton.icon(
              onPressed: _submitting ? null : _submit,
              icon: _submitting
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.upload_file_outlined),
              label:
                  Text(_submitting ? 'Submitting…' : 'Submit for Admin review'),
            ),
          ],
        ),
      );
}
