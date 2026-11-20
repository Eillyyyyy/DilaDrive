import 'package:flutter/material.dart';

import '../../../models/booking_draft.dart';
import '../../../services/booking_service.dart';
import '../../../theme/app_theme.dart';
import 'booking_page_layout.dart';
import 'booking_review_page.dart';

class BookingFarePage extends StatefulWidget {
  const BookingFarePage({super.key, required this.draft, this.bookingService});

  final BookingDraft draft;
  final BookingService? bookingService;

  @override
  State<BookingFarePage> createState() => _BookingFarePageState();
}

class _BookingFarePageState extends State<BookingFarePage> {
  FareEstimate? _estimate;
  String? _estimateError;
  bool _distanceReviewAvailable = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _estimate = null;
      _estimateError = null;
      _distanceReviewAvailable = false;
      widget.draft.fareEstimate = null;
    });
    if (!widget.draft.hasFreshAvailability) {
      setState(() => _estimateError =
          'Check vehicle availability again before calculating fare.');
      return;
    }
    final service = widget.bookingService ?? BookingService();
    try {
      final estimate = await service.estimateFare(widget.draft);
      if (!mounted) return;
      setState(() {
        _estimate = estimate;
        widget.draft.fareEstimate = estimate;
      });
    } on BookingException catch (e) {
      if (mounted) {
        setState(() {
          _estimateError = e.message;
          _distanceReviewAvailable = widget.draft.existingBookingId == null &&
              e.code == 'ROAD_DISTANCE_REVIEW_AVAILABLE';
        });
      }
    } finally {
      if (widget.bookingService == null) service.dispose();
    }
  }

  @override
  Widget build(BuildContext context) {
    final draft = widget.draft;
    final estimate = _estimate;
    return BookingPageLayout(
      title: 'Fare information',
      subtitle:
          'Road distance and fare calculated by DilaDrive. Admin will review your request.',
      actionEnabled: (_estimate != null && draft.hasFreshAvailability) ||
          _distanceReviewAvailable,
      currentStep: 3,
      actionLabel: 'Review booking',
      onAction: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => BookingReviewPage(
              draft: draft, bookingService: widget.bookingService,
              requestDistanceReview: _distanceReviewAvailable),
        ),
      ),
      child: Column(
        children: [
          BookingCard(
            child: Column(
              children: [
                _FareRouteRow(
                  label: 'Pickup Location',
                  address: draft.pickupAddress,
                  icon: Icons.trip_origin,
                ),
                const Divider(height: 24),
                _FareRouteRow(
                  label: 'Destination',
                  address: draft.destinationAddress,
                  icon: Icons.location_on_outlined,
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),
          if (estimate == null && _estimateError == null)
            const CircularProgressIndicator(),
          if (_estimateError != null)
            TextButton(
                onPressed: () {
                  setState(() => _estimateError = null);
                  _load();
                },
                child: const Text('Retry fare estimate')),
          if (estimate == null)
            BookingCard(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(Icons.price_change_outlined, color: bookingBlue),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      _estimateError ??
                          'An estimated distance and fare are unavailable.',
                      style: const TextStyle(
                        color: AppTheme.secondaryText,
                        height: 1.4,
                      ),
                    ),
                  ),
                ],
              ),
            )
          else
            _FareBreakdown(estimate: estimate),
            const Text(
              'The estimate covers the road distance. Actual tolls, parking '
              'and approved extra hours may be added to the final fare.',
            ),
          const SizedBox(height: 14),
          BookingCard(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.directions_car_outlined, color: bookingBlue),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    estimate != null
                        ? 'Recommended Vehicle: '
                            '${draft.recommendedVehicle!.type} Ã‚Â· '
                            '${estimate.vehicleModel}'
                        : 'Recommended vehicle: '
                            '${draft.recommendedVehicle?.type ?? 'Pending'}'
                            '${draft.recommendedVehicle == null ? '' : ' Ã‚Â· ${draft.recommendedVehicle!.model}'}',
                    style: const TextStyle(
                      color: bookingNavy,
                      fontWeight: FontWeight.w600,
                      height: 1.4,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _FareBreakdown extends StatelessWidget {
  const _FareBreakdown({required this.estimate});

  final FareEstimate estimate;

  @override
  Widget build(BuildContext context) {
    return BookingCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Fare breakdown (MYR)',
            style: TextStyle(
              color: bookingNavy,
              fontSize: 17,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 16),
          _PriceRow(
            label: 'Distance (KM)',
            value: '${estimate.distanceKm.toStringAsFixed(1)} km',
          ),
          if (estimate.ratePerKm != null) ...[
            const SizedBox(height: 12),
            _PriceRow(
              label: 'Rate per kilometer',
              value: _money(estimate.ratePerKm!),
            ),
          ],
          if (estimate.baseFare > 0) ...[
            const SizedBox(height: 12),
            _PriceRow(
              label: 'Fixed base fare',
              value: _money(estimate.baseFare),
            ),
          ],
          const SizedBox(height: 12),
          _PriceRow(
            label: estimate.baseFare > 0 ? 'Distance charge' : 'Base fare',
            value: _money(estimate.distanceCharge),
          ),
          for (final charge in estimate.additionalCharges) ...[
            const SizedBox(height: 12),
            _PriceRow(
              label: charge.label,
              value: _money(charge.amount),
            ),
          ],
          const Divider(height: 28),
          _PriceRow(
            label: 'Estimated Fare',
            value: _money(estimate.total),
            emphasize: true,
          ),
        ],
      ),
    );
  }

  static String _money(double amount) => 'RM ${amount.toStringAsFixed(2)}';
}

class _PriceRow extends StatelessWidget {
  const _PriceRow({
    required this.label,
    required this.value,
    this.emphasize = false,
  });

  final String label;
  final String value;
  final bool emphasize;

  @override
  Widget build(BuildContext context) {
    final style = TextStyle(
      color: emphasize ? bookingNavy : AppTheme.secondaryText,
      fontWeight: emphasize ? FontWeight.w800 : FontWeight.w500,
      fontSize: emphasize ? 17 : 14,
    );
    return Row(
      children: [
        Expanded(child: Text(label, style: style)),
        Text(value, style: style),
      ],
    );
  }
}

class _FareRouteRow extends StatelessWidget {
  const _FareRouteRow({
    required this.label,
    required this.address,
    required this.icon,
  });

  final String label;
  final String address;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: bookingBlue),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label,
                  style: const TextStyle(color: AppTheme.secondaryText)),
              const SizedBox(height: 4),
              Text(
                address,
                style: const TextStyle(
                  color: bookingNavy,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
