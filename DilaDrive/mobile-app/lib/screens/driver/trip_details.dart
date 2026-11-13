import 'package:flutter/material.dart';

import '../../models/driver_trip.dart';
import '../../services/driver_trip_service.dart';
import '../../services/money_validation.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';
import 'upload_verification_photo.dart';

class DriverTripDetailsPage extends StatefulWidget {
  const DriverTripDetailsPage({super.key, required this.trip});
  final DriverTrip trip;

  @override
  State<DriverTripDetailsPage> createState() => _DriverTripDetailsPageState();
}

class _DriverTripDetailsPageState extends State<DriverTripDetailsPage> {
  final DriverTripService _service = DriverTripService();
  bool _saving = false;

  Future<void> _recordCashReceived() async {
    final amountController = TextEditingController();
    String? amountError;
    final amount = await showDialog<double>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (dialogContext, setDialogState) => AlertDialog(
          title: const Text('Record cash received'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            const Text(
              'Confirm the cash was received after the trip. Admin must verify it before a receipt is issued.',
            ),
            const SizedBox(height: 12),
            TextField(
              controller: amountController,
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: 'Cash received (RM)',
                prefixIcon: const Icon(Icons.payments_outlined),
                errorText: amountError,
              ),
            ),
          ]),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                final valueText = amountController.text.trim();
                final value = double.tryParse(valueText);
                if (!isValidMoneyInput(valueText) || value == null) {
                  setDialogState(() => amountError =
                      'Enter an amount with up to two decimal places.');
                  return;
                }
                Navigator.pop(dialogContext, value);
              },
              child: const Text('Confirm received'),
            ),
          ],
        ),
      ),
    );
    amountController.dispose();
    if (amount == null || !mounted || _saving) return;
    setState(() => _saving = true);
    try {
      await _service.recordCashReceived(widget.trip.bookingId, amount);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Cash recorded for Admin review.')),
      );
      Navigator.pop(context, true);
    } on DriverTripException catch (error) {
      if (!mounted) return;
      if (error.requiresLogin) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute<void>(builder: (_) => const LoginPage()),
          (_) => false,
        );
        return;
      }
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  void dispose() {
    _service.dispose();
    super.dispose();
  }

  Future<void> _advanceStatus() async {
    final next = widget.trip.nextStatus;
    if (next == null || _saving) return;
    setState(() => _saving = true);
    try {
      await _service.updateStatus(widget.trip.assignmentId, next);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Trip updated to ${_label(next)}.')),
      );
      Navigator.pop(context, true);
    } on DriverTripException catch (error) {
      if (mounted) {
        if (error.requiresLogin) {
          Navigator.of(context).pushAndRemoveUntil(
            MaterialPageRoute<void>(builder: (_) => const LoginPage()),
            (_) => false,
          );
          return;
        }
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error.message)));
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final trip = widget.trip;
    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      appBar: AppBar(title: const Text('Trip Details')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _InfoCard(items: {
            'Booking': trip.bookingReference,
            'Customer': trip.customer ?? 'Not provided',
            if (trip.tripTypeLabel.isNotEmpty) 'Trip type': trip.tripTypeLabel,
            'Pickup': trip.pickupAddress ?? 'Not provided',
            'Destination': trip.destinationAddress ?? 'Not provided',
            'Date': trip.tripDate == null
                ? 'Not provided'
                : _dateLabel(trip.tripDate!),
            trip.isAirportPickup ? 'Landed time' : 'Pickup time':
                trip.pickupTime ?? 'Not provided',
            if (trip.flightNo?.trim().isNotEmpty == true)
              'Flight number': trip.flightNo!.trim(),
            'Passengers': trip.passengerCount?.toString() ?? 'Not provided',
            if (trip.luggageCount != null)
              'Luggage': [
                '${trip.luggageCount}',
                if (trip.luggageSize?.trim().isNotEmpty == true)
                  trip.luggageSize!.trim(),
              ].join(' · '),
            'Vehicle plate': trip.vehiclePlate ?? 'Not provided',
          }),
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Trip status',
                        style: TextStyle(color: AppTheme.secondaryText)),
                    const SizedBox(height: 6),
                    Text(trip.statusLabel,
                        style: const TextStyle(
                            fontSize: 18, fontWeight: FontWeight.w600)),
                    if (trip.nextStatus != null) ...[
                      const SizedBox(height: 16),
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton.icon(
                          onPressed: _saving ? null : _advanceStatus,
                          icon: _saving
                              ? const SizedBox.square(
                                  dimension: 18,
                                  child:
                                      CircularProgressIndicator(strokeWidth: 2),
                                )
                              : const Icon(Icons.update),
                          label: Text(_saving
                              ? 'Updating…'
                              : 'Mark ${_label(trip.nextStatus!)}'),
                        ),
                      ),
                    ],
                    if (trip.isCompleted) ...[
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: OutlinedButton.icon(
                          onPressed: _saving ? null : _recordCashReceived,
                          icon: const Icon(Icons.payments_outlined),
                          label: const Text('Record cash received'),
                        ),
                      ),
                    ],
                  ]),
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            icon: const Icon(Icons.camera_alt_outlined),
            label: const Text('Verification photo'),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => UploadVerificationPhotoPage(
                  assignmentId: trip.assignmentId,
                  allowUpload: trip.isActive,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({required this.items});
  final Map<String, String> items;

  @override
  Widget build(BuildContext context) => Card(
        child: Column(
          children: items.entries
              .map((entry) => ListTile(
                    dense: true,
                    title: Text(entry.key,
                        style: const TextStyle(color: AppTheme.secondaryText)),
                    subtitle: Text(entry.value,
                        style: const TextStyle(color: AppTheme.mainText)),
                  ))
              .toList(growable: false),
        ),
      );
}

String _label(String status) => switch (status) {
      'OTW' => 'On the way',
      'WAITING' => 'Waiting for pickup',
      'PICKUP' => 'Passenger picked up',
      'DROP' => 'Drop-off in progress',
      'COMPLETED' => 'Completed',
      _ => status,
    };

String _dateLabel(DateTime date) =>
    '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
