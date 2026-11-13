import 'dart:math';

import 'package:flutter/material.dart';

import '../../../models/booking_draft.dart';
import '../../../services/booking_service.dart';
import '../../../theme/app_theme.dart';
import '../../auth/login.dart';
import 'booking_page_layout.dart';

class BookingReviewPage extends StatefulWidget {
  const BookingReviewPage({
    super.key,
    required this.draft,
    this.bookingService,
  });

  final BookingDraft draft;
  final BookingService? bookingService;

  @override
  State<BookingReviewPage> createState() => _BookingReviewPageState();
}

class _BookingReviewPageState extends State<BookingReviewPage> {
  late final bool _ownsBookingService = widget.bookingService == null;
  late final BookingService _bookingService =
      widget.bookingService ?? BookingService();
  bool _isSubmitting = false;
  String? _error;
  String? _reference;
  late final String _idempotencyKey = _newIdempotencyKey();

  @override
  void dispose() {
    if (_ownsBookingService) _bookingService.dispose();
    super.dispose();
  }

  Future<void> _confirmBooking() async {
    if (_isSubmitting || _reference != null) return;
    final draft = widget.draft;
    if (draft.recommendedVehicle == null) {
      setState(() {
        _error = 'A live vehicle recommendation is required before submitting.';
      });
      return;
    }

    setState(() {
      _isSubmitting = true;
      _error = null;
    });
    try {
      final reference = await _bookingService.createBooking(
        draft,
        idempotencyKey: _idempotencyKey,
      );
      if (!mounted) return;
      setState(() => _reference = reference);
    } on BookingException catch (exception) {
      if (!mounted) return;
      if (exception.requiresLogin) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute<void>(builder: (_) => const LoginPage()),
          (_) => false,
        );
        return;
      }
      setState(() => _error = exception.message);
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  void _closeFlow() {
    Navigator.of(context).popUntil((route) => route.isFirst);
  }

  String _newIdempotencyKey() {
    final random = Random.secure();
    return '${DateTime.now().microsecondsSinceEpoch}-'
        '${random.nextInt(1 << 32)}';
  }

  @override
  Widget build(BuildContext context) {
    final draft = widget.draft;
    final isSubmitted = _reference != null;
    return BookingPageLayout(
      title: isSubmitted ? 'Booking request received' : 'Review your booking',
      subtitle: isSubmitted
          ? 'Your request is pending Admin verification.'
          : 'Check the details below before sending your request.',
      currentStep: 4,
      actionLabel: isSubmitted
          ? 'Finish'
          : _isSubmitting
              ? 'Submitting booking…'
              : _error == null
                  ? 'Confirm booking'
                  : 'Try again',
      onAction: isSubmitted ? _closeFlow : _confirmBooking,
      isLoading: _isSubmitting,
      actionEnabled:
          isSubmitted || (!_isSubmitting && draft.recommendedVehicle != null),
      child: Column(
        children: [
          if (isSubmitted)
            BookingCard(
              child: Column(
                children: [
                  const Icon(
                    Icons.check_circle_outline,
                    color: Colors.green,
                    size: 52,
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Request submitted',
                    style: TextStyle(
                      color: bookingNavy,
                      fontSize: 20,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text('Booking reference: $_reference'),
                  const SizedBox(height: 8),
                  const Text(
                    'This is not a confirmed trip. An Admin must verify the '
                    'booking before it is confirmed.',
                    textAlign: TextAlign.center,
                    style: TextStyle(height: 1.4),
                  ),
                ],
              ),
            )
          else ...[
            if (_error != null) ...[
              BookingCard(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.error_outline, color: Colors.red),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        _error!,
                        style: TextStyle(
                          color: Colors.red[800],
                          height: 1.4,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),
            ],
            BookingCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _SectionTitle(title: 'Customer'),
                  _SummaryLine(label: 'Name', value: draft.customerName),
                  _SummaryLine(label: 'Phone', value: draft.phoneNumber),
                  const Divider(height: 24),
                  const _SectionTitle(title: 'Trip'),
                  _SummaryLine(label: 'Trip type', value: draft.tripType.label),
                  _SummaryLine(
                    label: 'Pickup',
                    value: draft.pickupAddress,
                    icon: Icons.trip_origin,
                  ),
                  _SummaryLine(
                    label: 'Destination',
                    value: draft.destinationAddress,
                    icon: Icons.location_on_outlined,
                  ),
                  _SummaryLine(
                    label: 'Booking date',
                    value: bookingDateLabel(draft.bookingDate),
                    icon: Icons.calendar_month_outlined,
                  ),
                  if (draft.tripType == BookingTripType.airportPickup)
                    _SummaryLine(
                      label: 'Landed time',
                      value: bookingTimeLabel(context, draft.landedTime),
                      icon: Icons.flight_land_outlined,
                    )
                  else
                    _SummaryLine(
                      label: 'Pickup time',
                      value: bookingTimeLabel(context, draft.pickupTime),
                      icon: Icons.access_time,
                    ),
                  if (draft.flightNumber != null)
                    _SummaryLine(
                      label: 'Flight number',
                      value: draft.flightNumber!,
                      icon: Icons.flight_outlined,
                    ),
                  _SummaryLine(
                    label: 'Passengers',
                    value: '${draft.passengerCount}',
                    icon: Icons.people_outline,
                  ),
                  _SummaryLine(
                    label: 'Luggage',
                    value: draft.luggageCount == 0
                        ? 'None'
                        : '${draft.luggageCount} • ${draft.luggageSize}',
                    icon: Icons.luggage_outlined,
                  ),
                  if (draft.additionalNotes != null)
                    _SummaryLine(
                      label: 'Notes',
                      value: draft.additionalNotes!,
                      icon: Icons.notes_outlined,
                    ),
                  const Divider(height: 24),
                  const _SectionTitle(title: 'Recommendation & fare'),
                  _SummaryLine(
                    label: 'Vehicle',
                    value: draft.recommendedVehicle?.model ?? 'Not available',
                    icon: Icons.airport_shuttle_outlined,
                  ),
                  _SummaryLine(
                    label: 'Fare',
                    value: draft.fareEstimate == null
                        ? 'Admin will set the final fare'
                        : 'Estimate: RM '
                            '${draft.fareEstimate!.total.toStringAsFixed(2)}',
                    icon: Icons.payments_outlined,
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        title,
        style: const TextStyle(
          color: bookingNavy,
          fontSize: 16,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _SummaryLine extends StatelessWidget {
  const _SummaryLine({
    required this.label,
    required this.value,
    this.icon,
  });

  final String label;
  final String value;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 18, color: bookingBlue),
            const SizedBox(width: 9),
          ],
          SizedBox(
            width: icon == null ? 92 : 84,
            child: Text(
              label,
              style:
                  const TextStyle(color: AppTheme.secondaryText, fontSize: 13),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: bookingNavy,
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
