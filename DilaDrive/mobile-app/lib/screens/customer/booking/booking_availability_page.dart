import 'package:flutter/material.dart';

import '../../../models/booking_draft.dart';
import '../../../services/booking_service.dart';
import '../../../theme/app_theme.dart';
import '../../../widgets/booking_availability_message.dart';
import '../../auth/login.dart';
import 'booking_fare_page.dart';
import 'booking_page_layout.dart';
import 'booking_review_page.dart';

class BookingAvailabilityPage extends StatefulWidget {
  const BookingAvailabilityPage({
    super.key,
    required this.draft,
    this.bookingService,
  });

  final BookingDraft draft;
  final BookingService? bookingService;

  @override
  State<BookingAvailabilityPage> createState() =>
      _BookingAvailabilityPageState();
}

class _BookingAvailabilityPageState extends State<BookingAvailabilityPage> {
  late final bool _ownsBookingService = widget.bookingService == null;
  late final BookingService _bookingService =
      widget.bookingService ?? BookingService();
  bool _isLoading = true;
  bool _distanceReviewAvailable = false;
  AvailabilityResult? _result;

  int _requestVersion = 0;

  @override
  void initState() {
    super.initState();
    _checkAvailability();
  }

  @override
  void dispose() {
    if (_ownsBookingService) _bookingService.dispose();
    super.dispose();
  }

  Future<void> _checkAvailability() async {
    final version = ++_requestVersion;
    final key = widget.draft.availabilityKey;
    widget.draft.clearAvailability();
    setState(() {
      _isLoading = true;
      _result = null;
      _distanceReviewAvailable = false;
    });
    try {
      final result = await _bookingService.checkAvailability(widget.draft);
      if (!mounted || version != _requestVersion) return;
      if (key != widget.draft.availabilityKey) {
        setState(() => _result = AvailabilityResult.unavailable(
            'Trip details changed. Check availability again.',
            status: AvailabilityStatus.serviceError));
        widget.draft.clearAvailability();
        return;
      }
      setState(() {
        _result = result;
        widget.draft.recommendedVehicle =
            result.isAvailable ? result.vehicle : null;
      });
    } on BookingException catch (exception) {
      if (!mounted || version != _requestVersion) return;
      if (exception.requiresLogin) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute<void>(builder: (_) => const LoginPage()),
          (_) => false,
        );
        return;
      }
      setState(() {
        _distanceReviewAvailable = widget.draft.existingBookingId == null &&
            exception.code == 'ROAD_DISTANCE_REVIEW_AVAILABLE';
        _result = AvailabilityResult.unavailable(exception.message,
            status: AvailabilityStatus.serviceError);
      });
    } catch (_) {
      if (mounted && version == _requestVersion) {
        setState(() => _result = AvailabilityResult.unavailable(
            AvailabilityStatus.serviceError.message,
            status: AvailabilityStatus.serviceError));
      }
    } finally {
      if (mounted && version == _requestVersion) {
        setState(() => _isLoading = false);
      }
    }
  }

  void _continue() {
    if (_result?.isAvailable != true || !widget.draft.hasFreshAvailability) {
      _checkAvailability();
      return;
    }
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => BookingFarePage(draft: widget.draft),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isAvailable =
        _result?.isAvailable == true && widget.draft.hasFreshAvailability;
    return BookingPageLayout(
      title: 'Checking your options',
      subtitle:
          'We will recommend a vehicle based on capacity and availability.',
      currentStep: 2,
      actionLabel: 'Continue to Fare',
      onAction: _continue,
      actionEnabled: isAvailable && !_isLoading,
      isLoading: _isLoading,
      child: Column(
        children: [
          BookingCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _RouteLine(
                  icon: Icons.trip_origin,
                  label: 'Pickup',
                  value: widget.draft.pickupAddress,
                ),
                const Padding(
                  padding: EdgeInsets.only(left: 10),
                  child: SizedBox(
                    height: 24,
                    child: VerticalDivider(
                      color: AppTheme.border,
                      thickness: 2,
                    ),
                  ),
                ),
                _RouteLine(
                  icon: Icons.location_on_outlined,
                  label: 'Destination',
                  value: widget.draft.destinationAddress,
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),
          if (_isLoading)
            const BookingCard(
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: 22),
                child: Column(
                  children: [
                    CircularProgressIndicator(color: bookingBlue),
                    SizedBox(height: 14),
                    Text('Requesting live availability from DilaDrive...'),
                  ],
                ),
              ),
            )
          else if (isAvailable) ...[
            const Text('Available when checked',
                style: TextStyle(
                    color: Colors.green, fontWeight: FontWeight.w700)),
            Text(
                'Your trip: ${widget.draft.passengerCount} passengers, ${widget.draft.totalBagCount} bags'),
            const SizedBox(height: 8),
            _RecommendedVehicleCard(vehicle: _result!.vehicle!),
          ] else
            BookingCard(
              child: BookingAvailabilityMessage(
                result: _result ??
                    AvailabilityResult.unavailable(
                        AvailabilityStatus.serviceError.message,
                        status: AvailabilityStatus.serviceError),
                onRetry: _checkAvailability,
                onChange: () => Navigator.of(context).pop(),
              ),
            ),
          const SizedBox(height: 14),
          if (_distanceReviewAvailable && !_isLoading)
            OutlinedButton(
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => BookingReviewPage(
                    draft: widget.draft,
                    bookingService: _bookingService,
                    requestDistanceReview: true,
                  ),
                ),
              ),
              child: const Text('Review booking without an estimate'),
            ),
          const BookingCard(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.verified_user_outlined, color: bookingBlue),
                SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'This recommendation uses recorded fleet capacity and '
                    'current availability. Admin will confirm the vehicle '
                    'and assign a driver after reviewing your request. '
                    'A recommendation does not reserve a vehicle.',
                    style: TextStyle(height: 1.4),
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

class _RecommendedVehicleCard extends StatelessWidget {
  const _RecommendedVehicleCard({required this.vehicle});

  final RecommendedVehicle vehicle;

  @override
  Widget build(BuildContext context) {
    return BookingCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.check_circle, color: Colors.green),
              SizedBox(width: 8),
              Text(
                'Recommended vehicle',
                style: TextStyle(
                  color: bookingNavy,
                  fontSize: 17,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Container(
                width: 54,
                height: 54,
                decoration: BoxDecoration(
                  color: AppTheme.paleBlue,
                  borderRadius: BorderRadius.circular(15),
                ),
                child: const Icon(
                  Icons.airport_shuttle_outlined,
                  color: bookingNavy,
                  size: 30,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Text(
                  vehicle.type,
                  style: const TextStyle(
                    color: bookingNavy,
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const Divider(height: 28),
          _CapacityLine(
            label: 'Passenger capacity',
            value: '${vehicle.passengerCapacity} passengers',
          ),
          const SizedBox(height: 10),
          _CapacityLine(
            label: 'Boot capacity',
            value: vehicle.luggageCapacity == null
                ? 'Fits your luggage'
                : '${vehicle.luggageCapacity} units',
          ),
          const SizedBox(height: 12),
          const Text(
            'Live availability verified by the booking service',
            style: TextStyle(color: Colors.green, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }
}

class _RouteLine extends StatelessWidget {
  const _RouteLine({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: bookingBlue, size: 21),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                    color: AppTheme.secondaryText, fontSize: 12),
              ),
              const SizedBox(height: 3),
              Text(
                value,
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

class _CapacityLine extends StatelessWidget {
  const _CapacityLine({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(label,
              style: const TextStyle(color: AppTheme.secondaryText)),
        ),
        Text(value, style: const TextStyle(fontWeight: FontWeight.w700)),
      ],
    );
  }
}
