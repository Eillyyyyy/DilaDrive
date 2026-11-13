import 'package:flutter/material.dart';

import '../../../models/booking_draft.dart';
import '../../../theme/app_theme.dart';
import 'booking_page_layout.dart';
import 'booking_review_page.dart';

class BookingFarePage extends StatelessWidget {
  const BookingFarePage({super.key, required this.draft});

  final BookingDraft draft;

  @override
  Widget build(BuildContext context) {
    final estimate = draft.fareEstimate;
    return BookingPageLayout(
      title: 'Fare information',
      subtitle: 'DilaDrive has not configured route pricing yet. Admin will '
          'set the final fare before approving your booking.',
      currentStep: 3,
      actionLabel: 'Review booking',
      onAction: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => BookingReviewPage(draft: draft),
        ),
      ),
      child: Column(
        children: [
          BookingCard(
            child: Column(
              children: [
                _FareRouteRow(
                  label: 'Pickup',
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
          if (estimate == null)
            const BookingCard(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.price_change_outlined, color: bookingBlue),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'A fare estimate is unavailable. The Admin will set '
                      'the fare before approving this booking.',
                      style: TextStyle(
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
                        ? 'Recommended vehicle: ${estimate.vehicleModel}'
                        : 'Recommended vehicle: '
                            '${draft.recommendedVehicle?.model ?? 'Pending'}',
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
            'Fare breakdown',
            style: TextStyle(
              color: bookingNavy,
              fontSize: 17,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 16),
          _PriceRow(
            label: 'Estimated distance',
            value: '${estimate.distanceKm.toStringAsFixed(1)} km',
          ),
          const SizedBox(height: 12),
          _PriceRow(
            label: 'Base fare',
            value: _money(estimate.baseFare),
          ),
          const SizedBox(height: 12),
          _PriceRow(
            label: 'Distance charge',
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
            label: 'Estimated total',
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
