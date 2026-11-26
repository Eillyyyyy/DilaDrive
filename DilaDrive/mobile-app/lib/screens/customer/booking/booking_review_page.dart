import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';

import '../../../models/booking_draft.dart';
import '../../../models/booking_schedule.dart';
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
    this.onSubmitting,
    this.onSubmitted,
    this.onChooseRide,
    this.idempotencyKey,
  });

  final BookingDraft draft;
  final BookingService? bookingService;
  final bool requestDistanceReview;
  final ValueChanged<bool>? onSubmitting;
  final VoidCallback? onSubmitted;
  final VoidCallback? onChooseRide;
  final String Function(String, String Function())? idempotencyKey;

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
  bool _rideUnavailable = false;
  final _submissionKeys = <String, String>{};

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
    widget.onSubmitting?.call(true);
    final scope = '${draft.availabilityKey}|${draft.recommendedVehicle?.type}|'
        '${draft.recommendedVehicle?.id}|${draft.fareEstimate?.quoteToken}';
    try {
      final requestKey = widget.idempotencyKey?.call(scope, const Uuid().v4) ??
          _submissionKeys.putIfAbsent(scope, const Uuid().v4);
      final reference = await (widget.requestDistanceReview
          ? _bookingService.requestRoadDistanceReview(draft,
              idempotencyKey: requestKey)
          : _bookingService.createBooking(draft, idempotencyKey: requestKey));
      if (!mounted) return;
      setState(() => _reference = reference);
      widget.onSubmitted?.call();
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
                      'Road distance: ${bookingDistanceLabel(fare.distanceKm)}\n'
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
      setState(() {
        _error = exception.message;
        _rideUnavailable = const {
          'CATEGORY_UNAVAILABLE',
          'PREFERRED_VEHICLE_UNAVAILABLE'
        }.contains(exception.code);
        if (_rideUnavailable) draft.clearAvailability();
      });
    } catch (_) {
      if (mounted) {
        setState(
            () => _error = 'Could not complete the request. Please try again.');
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
        widget.onSubmitting?.call(false);
      }
    }
  }

  void _closeFlow() {
    Navigator.of(context).popUntil((route) => route.isFirst);
  }

  @override
  Widget build(BuildContext context) {
    final draft = widget.draft;
    final isSubmitted = _reference != null;
    final mapBooking = widget.onSubmitted != null;
    final flightTime = parseBookingFlightTime(draft.flightTime);
    return BookingPageLayout(
      title: isSubmitted ? 'Booking request received' : 'Review your booking',
      subtitle: isSubmitted
          ? 'Your request is pending Admin verification.'
          : 'Your booking request will be reviewed by DilaDrive Admin. '
              'Vehicle and driver assignments are confirmed after approval.',
      currentStep: 4,
      actionLabel: isSubmitted
          ? 'Finish'
          : _isSubmitting
              ? 'Submitting booking'
              : _rideUnavailable
                  ? 'Check available rides'
                  : _error == null
                      ? (widget.onSubmitted == null
                          ? 'Confirm booking'
                          : 'Request Booking')
                      : 'Try again',
      onAction: isSubmitted
          ? _closeFlow
          : _rideUnavailable
              ? (widget.onChooseRide ?? () => Navigator.of(context).pop())
              : _confirmBooking,
      isLoading: _isSubmitting,
      actionEnabled: isSubmitted ||
          _rideUnavailable ||
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
                  Text(
                    widget.requestDistanceReview
                        ? 'DilaDrive will review the route before reserving a ride.'
                        : 'A driver and vehicle are temporarily held while Admin reviews your request.',
                    textAlign: TextAlign.center,
                    style: const TextStyle(height: 1.4),
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
            Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                spacing: 12,
                children: [
                  _ReviewSection(
                    title: 'Customer',
                    lines: [
                      _SummaryLine(label: 'Name', value: draft.customerName),
                      _SummaryLine(label: 'Phone', value: draft.phoneNumber),
                    ],
                  ),
                  _ReviewSection(
                    title: 'Pickup and destination',
                    lines: [
                      if (!mapBooking)
                        _SummaryLine(
                            label: 'Trip type', value: draft.tripType.label),
                      _SummaryLine(
                          label: 'Pickup',
                          value: draft.pickupAddress,
                          icon: Icons.trip_origin),
                      _SummaryLine(
                          label: 'Destination',
                          value: draft.destinationAddress,
                          icon: Icons.location_on_outlined),
                      if (draft.fareEstimate case final fare?) ...[
                        _SummaryLine(
                            label: 'Road distance',
                            value: bookingDistanceLabel(fare.distanceKm)),
                        if (fare.durationSeconds > 0)
                          _SummaryLine(
                              label: 'Travel duration',
                              value:
                                  '${(fare.durationSeconds / 60).ceil()} minutes'),
                      ],
                    ],
                  ),
                  _ReviewSection(
                    title: 'Pickup date & time',
                    lines: [
                      _SummaryLine(
                          label: 'Date',
                          value: bookingDateLabel(draft.bookingDate),
                          icon: Icons.calendar_month_outlined),
                      if (draft.tripType == BookingTripType.airportPickup)
                        _SummaryLine(
                            label: 'Landed time',
                            value: bookingTimeLabel(context, draft.landedTime))
                      else
                        _SummaryLine(
                            label: 'Time',
                            value: mapBooking && draft.pickupTime != null
                                ? bookingClockLabel(draft.pickupTime!.hour,
                                    draft.pickupTime!.minute)
                                : bookingTimeLabel(context, draft.pickupTime),
                            icon: Icons.access_time),
                    ],
                  ),
                  if (draft.flightNumber?.trim().isNotEmpty == true ||
                      draft.flightDate != null ||
                      flightTime != null)
                    _ReviewSection(
                      title: 'Flight details',
                      lines: [
                        if (draft.flightNumber?.trim().isNotEmpty == true)
                          _SummaryLine(
                              label: 'Number',
                              value: draft.flightNumber!.trim(),
                              icon: Icons.flight_outlined),
                        if (draft.flightDate case final date?)
                          _SummaryLine(
                              label: 'Date', value: bookingDateLabel(date)),
                        if (flightTime != null)
                          _SummaryLine(
                              label: 'Time',
                              value: bookingClockLabel(
                                  flightTime.hour, flightTime.minute)),
                      ],
                    ),
                  _ReviewSection(
                    title: 'Passengers and luggage',
                    lines: [
                      _SummaryLine(
                          label: 'Passengers',
                          value: '${draft.passengerCount}'),
                      _SummaryLine(
                          label: 'Small / medium / large bags',
                          value:
                              '${draft.smallCount} / ${draft.mediumCount} / ${draft.largeCount}'),
                      _SummaryLine(
                          label: 'Total luggage',
                          value: '${draft.totalBagCount} bags'),
                      _SummaryLine(
                          label: 'Total boot usage',
                          value: '${draft.luggageUnits} units'),
                      if (draft.additionalNotes != null)
                        _SummaryLine(
                            label: 'Notes', value: draft.additionalNotes!),
                    ],
                  ),
                  _ReviewSection(
                    title: 'Selected ride',
                    lines: [
                      _SummaryLine(
                          label: 'Vehicle',
                          value:
                              draft.recommendedVehicle?.type ?? 'Not available',
                          icon: Icons.airport_shuttle_outlined),
                      if (draft.recommendedVehicle case final vehicle?) ...[
                        _SummaryLine(
                            label: 'Passenger capacity',
                            value: '${vehicle.passengerCapacity}'),
                      ],
                    ],
                  ),
                  _ReviewSection(
                    title: 'Estimated fare',
                    lines: [
                      if (draft.fareEstimate case final fare?) ...[
                        if (fare.ratePerKm case final rate?)
                          _SummaryLine(
                              label: 'Distance rate',
                              value: 'RM ${rate.toStringAsFixed(2)} / km'),
                        _SummaryLine(
                            label: 'Road-distance fare',
                            value:
                                'RM ${fare.distanceCharge.toStringAsFixed(2)}'),
                        for (final charge in fare.additionalCharges)
                          _SummaryLine(
                              label: charge.label,
                              value: 'RM ${charge.amount.toStringAsFixed(2)}'),
                        const _SummaryLine(
                            label: 'Extra hours',
                            value: 'Approved hours are charged separately.'),
                        const _SummaryLine(
                            label: 'Toll and parking',
                            value: 'Actual charges are added if applicable.'),
                      ],
                      _SummaryLine(
                          label: 'Estimated total',
                          value: draft.fareEstimate == null
                              ? 'Pending Admin confirmation of road distance'
                              : 'RM ${draft.fareEstimate!.total.toStringAsFixed(2)}',
                          icon: Icons.payments_outlined),
                    ],
                  ),
                ]),
          ],
        ],
      ),
    );
  }
}

class _ReviewSection extends StatelessWidget {
  const _ReviewSection({required this.title, required this.lines});

  final String title;
  final List<Widget> lines;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: BookingCard(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 4),
              ...lines,
            ],
          ),
        ),
      );
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
