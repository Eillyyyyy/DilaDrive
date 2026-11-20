import 'package:flutter/material.dart';
import '../services/booking_service.dart';
import '../theme/app_theme.dart';

class BookingAvailabilityMessage extends StatelessWidget {
  const BookingAvailabilityMessage(
      {super.key,
      required this.result,
      required this.onRetry,
      required this.onChange});
  final AvailabilityResult result;
  final VoidCallback onRetry;
  final VoidCallback onChange;
  @override
  Widget build(BuildContext context) {
    final serviceFailure = result.status == AvailabilityStatus.serviceError ||
        result.status == AvailabilityStatus.fleetNotConfigured;
    return Column(children: [
      Icon(serviceFailure ? Icons.cloud_off : Icons.event_busy,
          color: serviceFailure ? Colors.red[700] : Colors.orange[800],
          size: 38),
      const SizedBox(height: 12),
      Text(result.status.message,
          textAlign: TextAlign.center,
          style: const TextStyle(
              color: AppTheme.primaryNavy,
              fontSize: 16,
              fontWeight: FontWeight.w700)),
      if (serviceFailure &&
          result.message != null &&
          result.message != result.status.message) ...[
        const SizedBox(height: 8),
        Text(result.message!, textAlign: TextAlign.center),
      ],
      const SizedBox(height: 14),
      if (serviceFailure)
        OutlinedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('Retry')),
      OutlinedButton.icon(
          onPressed: onChange,
          icon: const Icon(Icons.edit_outlined),
          label: Text(result.status == AvailabilityStatus.scheduleUnavailable
              ? 'Change Date/Time'
              : 'Change Trip Details')),
    ]);
  }
}
