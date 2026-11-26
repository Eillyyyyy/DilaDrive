import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../../models/booking_draft.dart';
import '../../../models/booking_schedule.dart';
import '../../../services/booking_service.dart';
import '../../../widgets/booking_availability_message.dart';
import '../../auth/login.dart';
import 'booking_page_layout.dart';

class BookingVehiclePage extends StatefulWidget {
  const BookingVehiclePage({
    super.key,
    required this.draft,
    required this.onChangeTrip,
    this.onChangeTime,
    required this.onReview,
    this.service,
  });

  final BookingDraft draft;
  final BookingService? service;
  final VoidCallback onChangeTrip;
  final VoidCallback? onChangeTime;
  final VoidCallback onReview;

  @override
  State<BookingVehiclePage> createState() => _BookingVehiclePageState();
}

class _BookingVehiclePageState extends State<BookingVehiclePage> {
  late final _service = widget.service ?? BookingService();
  List<BookingVehicleQuote> _options = [];
  String? _recommendedCategory;
  AvailabilityResult? _error;
  bool _loading = true;
  bool _someQuotesUnavailable = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    if (_loading) widget.draft.clearAvailability();
    if (widget.service == null) _service.dispose();
    super.dispose();
  }

  Future<BookingVehicleQuote?> _quote(String category) async {
    try {
      return await _service.quoteVehicle(widget.draft, category);
    } on BookingException catch (error) {
      if (error.requiresLogin) rethrow;
      return null;
    }
  }

  Future<void> _load() async {
    final draft = widget.draft;
    final key = draft.availabilityKey;
    final previousCategory = draft.recommendedVehicle?.type;
    final previousFare = draft.hasFreshAvailability ? draft.fareEstimate : null;
    setState(() {
      _loading = true;
      _error = null;
      _options = [];
      _someQuotesUnavailable = false;
    });
    try {
      final result = await _service.checkAvailability(draft);
      if (!mounted) return;
      if (!result.isAvailable) {
        setState(() => _error = result);
        return;
      }

      final recommended = result.vehicle!.type;
      final categories = {recommended, ...result.categories};
      final quotes = await Future.wait(categories.map(_quote));
      if (!mounted) return;
      final options = quotes.whereType<BookingVehicleQuote>().map((quote) {
        // Preserve the accepted payload when revisiting Review for the same fare.
        // The backend can return the persisted result even after the quote expires.
        if (previousFare != null &&
            quote.vehicle.type == previousCategory &&
            _sameFare(previousFare, quote.fare)) {
          return BookingVehicleQuote(quote.vehicle, previousFare);
        }
        return quote;
      }).toList();
      if (options.isEmpty || key != draft.availabilityKey) {
        draft.clearAvailability();
        _showError('Vehicle fares are unavailable. Check availability again.');
        return;
      }
      setState(() {
        _options = options;
        _recommendedCategory = options.first.vehicle.type;
        _someQuotesUnavailable = options.length < categories.length;
        final selection = options
                .where((option) =>
                    option.vehicle.type.toLowerCase() ==
                    previousCategory?.toLowerCase())
                .firstOrNull ??
            options.first;
        _applyQuote(selection);
      });
    } on BookingException catch (error) {
      if (!mounted) return;
      if (error.requiresLogin) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute<void>(builder: (_) => const LoginPage()),
          (_) => false,
        );
        return;
      }
      _showError(error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  bool _sameFare(FareEstimate a, FareEstimate b) =>
      a.distanceKm == b.distanceKm &&
      a.durationSeconds == b.durationSeconds &&
      a.ratePerKm == b.ratePerKm &&
      a.baseFare == b.baseFare &&
      a.distanceCharge == b.distanceCharge &&
      a.total == b.total &&
      listEquals(
        a.additionalCharges
            .map((charge) => (charge.label, charge.amount))
            .toList(),
        b.additionalCharges
            .map((charge) => (charge.label, charge.amount))
            .toList(),
      );
  void _showError(String message) {
    setState(() => _error = AvailabilityResult.unavailable(message,
        status: AvailabilityStatus.serviceError));
  }

  void _applyQuote(BookingVehicleQuote quote) {
    widget.draft
      ..recommendedVehicle = quote.vehicle
      ..fareEstimate = quote.fare;
  }

  void _review() {
    if (!widget.draft.hasFreshAvailability ||
        widget.draft.fareEstimate == null) {
      return;
    }
    widget.onReview();
  }

  @override
  Widget build(BuildContext context) => BookingPageLayout(
        title: 'Choose your ride',
        subtitle:
            'Vehicles are confirmed by Admin after you request a booking.',
        currentStep: 2,
        actionLabel: widget.draft.recommendedVehicle == null
            ? 'Continue'
            : 'Continue with ${_categoryLabel(widget.draft.recommendedVehicle!.type)}',
        actionEnabled: !_loading &&
            _error == null &&
            widget.draft.hasFreshAvailability &&
            widget.draft.fareEstimate != null,
        isLoading: _loading,
        onAction: _review,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('${widget.draft.passengerCount} passengers / '
                '${widget.draft.totalBagCount} bags'),
            if (widget.draft.scheduledAt case final pickup?)
              Text(
                  '${bookingIsoDate(pickup)} ${TimeOfDay.fromDateTime(pickup).format(context)} (Malaysia)'),
            const SizedBox(height: 12),
            if (_loading) const Text('Checking the fleet and estimated fares…'),
            if (_error case final error?)
              BookingAvailabilityMessage(
                result: error,
                onRetry: _load,
                onChange: widget.onChangeTrip,
                onChangeTime: widget.onChangeTime,
              ),
            if (_error case final error?
                when error.status == AvailabilityStatus.scheduleUnavailable ||
                    error.status == AvailabilityStatus.capacityUnavailable)
              TextButton.icon(
                onPressed: _load,
                icon: const Icon(Icons.refresh),
                label: const Text('Retry'),
              ),
            if (_someQuotesUnavailable)
              TextButton(
                onPressed: _load,
                child: const Text('Some fares are unavailable. Check again'),
              ),
            if (_options.isNotEmpty)
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                spacing: 12,
                children: [
                  for (final quote in _options)
                    _VehicleCard(
                      quote: quote,
                      selected: widget.draft.fareEstimate == quote.fare,
                      recommended: quote.vehicle.type == _recommendedCategory,
                      onSelected: () => setState(() => _applyQuote(quote)),
                    ),
                ],
              ),
          ],
        ),
      );
}

String _categoryLabel(String type) => switch (type.trim().toUpperCase()) {
      'SEDAN' => 'Sedan',
      'MPV' => 'MPV',
      _ => type,
    };

class _VehicleCard extends StatelessWidget {
  const _VehicleCard({
    required this.quote,
    required this.selected,
    required this.recommended,
    required this.onSelected,
  });

  final BookingVehicleQuote quote;
  final bool selected;
  final bool recommended;
  final VoidCallback onSelected;

  @override
  Widget build(BuildContext context) {
    final vehicle = quote.vehicle;
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      selected: selected,
      button: true,
      child: Card(
        margin: EdgeInsets.zero,
        clipBehavior: Clip.antiAlias,
        color: selected ? colors.primaryContainer : colors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(18),
          side: BorderSide(
              color: selected ? colors.primary : colors.outlineVariant),
        ),
        child: InkWell(
          onTap: onSelected,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                        vehicle.type.toLowerCase().contains('van')
                            ? Icons.airport_shuttle_outlined
                            : Icons.directions_car_outlined,
                        size: 36,
                        color: colors.primary),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(_categoryLabel(vehicle.type),
                          style: Theme.of(context).textTheme.titleMedium),
                    ),
                    Icon(selected ? Icons.check_circle : Icons.circle_outlined,
                        color: colors.primary),
                  ],
                ),
                Text('Up to ${vehicle.passengerCapacity} passengers'),
                Text(vehicle.luggageCapacity == null
                    ? 'Fits your luggage'
                    : 'Boot capacity: ${vehicle.luggageCapacity} units'),
                const SizedBox(height: 5),
                Text('Estimated RM ${quote.fare.total.toStringAsFixed(2)}'),
                Text(recommended
                    ? 'Recommended · Available when checked'
                    : 'Available when checked'),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
