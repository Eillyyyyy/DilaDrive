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
    this.requestDistanceReview = false,
  });

  final BookingDraft draft;
  final BookingService? bookingService;
  final bool requestDistanceReview;

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
    if (!widget.requestDistanceReview &&
        (!draft.hasFreshAvailability || draft.fareEstimate == null)) {
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
      final reference = await (widget.requestDistanceReview
          ? _bookingService.requestRoadDistanceReview(draft,
              idempotencyKey: _idempotencyKey)
          : _bookingService.createBooking(draft, idempotencyKey: _idempotencyKey));
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
      if (exception.code == 'FARE_REVIEW_REQUIRED' &&
          exception.refreshedQuote != null) {
        final quote = exception.refreshedQuote!;
        final fare = FareEstimate.fromJson(quote);
        final accepted = await showDialog<bool>(
            context: context,
            builder: (context) => AlertDialog(
                  title: const Text('Review updated fare'),
                  content: Text('The fare or pricing configuration changed.\n'
                      'Previous: MYR ${draft.fareEstimate!.total.toStringAsFixed(2)}\n'
                      'Updated: MYR ${fare.total.toStringAsFixed(2)}\n'
                      'Category: ${quote['recommendedVehicleType']}\n'
                      'Road distance: ${fare.distanceKm.toStringAsFixed(2)} km\n'
                      'Base fare: MYR ${fare.baseFare.toStringAsFixed(2)}\n'
                      'Rate/km: MYR ${(fare.ratePerKm ?? 0).toStringAsFixed(4)}\n'
                      'Distance charge: MYR ${fare.distanceCharge.toStringAsFixed(2)}\n'
                      'Review and accept before submitting.'),
                  actions: [
                    TextButton(
                        onPressed: () => Navigator.pop(context, false),
                        child: const Text('Keep reviewing')),
                    FilledButton(
                        onPressed: () => Navigator.pop(context, true),
                        child: const Text('Accept updated fare'))
                  ],
                ));
        if (!mounted) return;
        if (accepted == true) {
          setState(() {
            draft.fareEstimate = fare;
            draft.recommendedVehicle = RecommendedVehicle.fromJson(
                quote['vehicle'] as Map<String, dynamic>);
            _error =
                'Updated fare accepted. Review the summary and confirm your booking.';
          });
        }
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
              ? 'Submitting bookingâ€¦'
              : _error == null
                  ? 'Confirm booking'
                  : 'Try again',
      onAction: isSubmitted ? _closeFlow : _confirmBooking,
      isLoading: _isSubmitting,
      actionEnabled: isSubmitted ||
          (!_isSubmitting &&
              (widget.requestDistanceReview ||
                  (draft.hasFreshAvailability && draft.fareEstimate != null))),
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
                    'Your booking request has been submitted and is awaiting Admin confirmation. '
                    'Vehicle availability is not reserved until your booking is confirmed.',
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
                  _SummaryLine(
                      label: 'Small / medium / large bags',
                      value:
                          '${draft.smallCount} / ${draft.mediumCount} / ${draft.largeCount}'),
                  _SummaryLine(
                      label: 'Luggage points', value: '${draft.luggageUnits}'),
                  if (!widget.requestDistanceReview) _SummaryLine(
                      label: 'Travel duration',
                      value:
                          '${((draft.fareEstimate?.durationSeconds ?? 0) / 60).ceil()} minutes'),
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
                        : '${draft.luggageCount} â€¢ ${draft.luggageSize}',
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
                  if (draft.fareEstimate != null)
                    _SummaryLine(
                      label: 'Distance',
                      value:
                          '${draft.fareEstimate!.distanceKm.toStringAsFixed(1)} km',
                      icon: Icons.route_outlined,
                    ),
                  _SummaryLine(
                    label: 'Estimated fare',
                    value: draft.fareEstimate == null
                        ? 'Pending Admin confirmation of road distance'
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
