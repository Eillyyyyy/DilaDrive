import 'package:flutter/material.dart';

import '../../widgets/responsive_page.dart';
import '../../widgets/trip_timeline.dart';

import '../../models/customer_booking.dart';
import '../../models/booking_schedule.dart';
import '../../services/customer_booking_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';
import 'update_booking.dart';
import '../../widgets/booking_fare_proposals.dart';

class BookingDetailsPage extends StatefulWidget {
  const BookingDetailsPage({super.key, required this.booking});

  final CustomerBooking booking;

  @override
  State<BookingDetailsPage> createState() => _BookingDetailsPageState();
}

class _BookingDetailsPageState extends State<BookingDetailsPage> {
  final CustomerBookingService _bookingService = CustomerBookingService();
  bool _cancelling = false;
  bool _rescheduling = false;

  Future<void> _editPendingBooking() async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => UpdateBookingPage(booking: widget.booking),
      ),
    );
    if (changed == true && mounted) Navigator.pop(context, true);
  }

  Future<void> _reschedule() async {
    final now = DateTime.now().toUtc().add(const Duration(hours: 8));
    final today = DateTime(now.year, now.month, now.day);
    final tripDate = widget.booking.tripDate;
    final lastDate = DateTime(today.year + 2, today.month, today.day);
    final date = await showDatePicker(
      context: context,
      initialDate: tripDate != null && !tripDate.isBefore(today) ? tripDate : today,
      firstDate: today,
      lastDate: tripDate != null && tripDate.isAfter(lastDate) ? tripDate : lastDate,
      helpText: 'Select pickup date (Malaysia time)',
    );
    if (date == null || !mounted) return;
    final existing = widget.booking.pickupTime?.split(':');
    final hour = existing == null ? null : int.tryParse(existing.first);
    final minute = existing == null || existing.length < 2
        ? null
        : int.tryParse(existing[1]);
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: hour ?? now.hour, minute: minute ?? now.minute),
      helpText: 'Select pickup time (Malaysia time)',
    );
    if (time == null || !mounted) return;

    setState(() => _rescheduling = true);
    try {
      await _bookingService.rescheduleBooking(
        widget.booking.id,
        DateTime(date.year, date.month, date.day, time.hour, time.minute),
      );
      if (mounted) Navigator.pop(context, true);
    } on CustomerBookingException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message)),
      );
      if (error.requiresLogin) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute<void>(builder: (_) => const LoginPage()),
          (_) => false,
        );
      }
    } finally {
      if (mounted) setState(() => _rescheduling = false);
    }
  }

  @override
  void dispose() {
    _bookingService.dispose();
    super.dispose();
  }

  Future<void> _cancelBooking() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Cancel this booking?'),
        content: const Text(
          'Confirmed bookings require advance notice before pickup. Contact Admin if it is too late to cancel.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Keep booking'),
          ),
          FilledButton.tonal(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Cancel booking'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _cancelling = true);
    try {
      await _bookingService.cancelBooking(widget.booking.id);
      if (mounted) Navigator.pop(context, true);
    } on CustomerBookingException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message)),
      );
      if (error.requiresLogin) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute<void>(builder: (_) => const LoginPage()),
          (_) => false,
        );
      }
    } finally {
      if (mounted) setState(() => _cancelling = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final booking = widget.booking;
    final canReschedule = booking.normalizedStatus == 'CONFIRMED' &&
        booking.activeAssignment &&
        (booking.assignedTripStatus == null || booking.assignedTripStatus == 'ASSIGNED');
    final statusColor = _statusColor(booking);
    return DilaDriveScaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      appBar: AppBar(title: const Text('Booking details')),
      body: ListView(
        padding: pagePadding(context),
        children: [
          if (booking.isActive || booking.isCompleted)
            TripTimeline(id: booking.id, customer: true),
          BookingFareProposals(
              bookingId: booking.id,
              onAccepted: () => Navigator.pop(context, true)),
          Container(
            padding: const EdgeInsets.all(19),
            decoration: BoxDecoration(
              color: AppTheme.primaryNavy,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  booking.reference,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 11,
                    vertical: 7,
                  ),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: .15),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Text(
                    booking.statusLabel,
                    style: TextStyle(
                      color: _lightStatusColor(booking),
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          _SectionCard(
            title: 'Trip route',
            children: [
              _InfoLine(
                icon: Icons.trip_origin_rounded,
                label: 'Pickup',
                value: _value(booking.pickupAddress),
              ),
              const Divider(height: 20),
              _InfoLine(
                icon: Icons.location_on_outlined,
                label: 'Destination',
                value: _value(booking.destinationAddress),
              ),
            ],
          ),
          const SizedBox(height: 14),
          _SectionCard(
            title: 'Trip information',
            children: [
              _InfoLine(
                icon: Icons.calendar_today_outlined,
                label: 'Date',
                value: _formatDate(context, booking.tripDate),
              ),
              if (booking.pickupTime != null) ...[
                const Divider(height: 20),
                _InfoLine(
                  icon: Icons.schedule_outlined,
                  label:
                      booking.isAirportPickup ? 'Landed time' : 'Pickup time',
                  value: _formatTime(context, booking.pickupTime),
                ),
              ],
              if (_value(booking.tripType) != 'Not provided') ...[
                const Divider(height: 20),
                _InfoLine(
                  icon: Icons.route_outlined,
                  label: 'Trip type',
                  value: booking.tripTypeLabel,
                ),
              ],
              if (_value(booking.type) != 'Not provided') ...[
                const Divider(height: 20),
                _InfoLine(
                  icon: Icons.directions_car_outlined,
                  label: 'Vehicle category',
                  value:
                      _value(booking.assignedVehicleCategory ?? booking.type),
                ),
              ],
              if (booking.assignedVehiclePlate case final plate?) ...[
                const Divider(height: 20),
                _InfoLine(
                    icon: Icons.directions_car_outlined,
                    label: 'Assigned vehicle',
                    value: [booking.assignedVehicleModel, plate]
                        .whereType<String>()
                        .join(' / ')),
              ],
              if (booking.assignedDriver case final driver?) ...[
                const Divider(height: 20),
                _InfoLine(
                    icon: Icons.person_outline, label: 'Driver', value: driver),
              ],
              if (booking.flightNumber != null &&
                  booking.flightNumber!.trim().isNotEmpty) ...[
                const Divider(height: 20),
                _InfoLine(
                  icon: Icons.flight_outlined,
                  label: 'Flight number',
                  value: booking.flightNumber!,
                ),
              ],
              if (booking.flightDate case final date?) ...[
                const Divider(height: 20),
                _InfoLine(
                    icon: Icons.event_outlined,
                    label: 'Flight date',
                    value: bookingIsoDate(date)),
              ],
              if (parseBookingFlightTime(booking.flightTime)
                  case final time?) ...[
                const Divider(height: 20),
                _InfoLine(
                    icon: Icons.schedule_outlined,
                    label: 'Flight time',
                    value: bookingClockLabel(time.hour, time.minute)),
              ],
              if (booking.passengerCount != null) ...[
                const Divider(height: 20),
                _InfoLine(
                  icon: Icons.people_outline,
                  label: 'Passengers',
                  value: '${booking.passengerCount}',
                ),
              ],
              if (booking.luggageCount != null) ...[
                const Divider(height: 20),
                _InfoLine(
                  icon: Icons.luggage_outlined,
                  label: 'Luggage',
                  value: [
                    '${booking.luggageCount}',
                    if (booking.luggageSize?.trim().isNotEmpty == true)
                      booking.luggageSize!,
                  ].join(' Â· '),
                ),
              ],
              if (booking.smallCount != null) ...[
                const Divider(height: 20),
                _InfoLine(
                    icon: Icons.luggage_outlined,
                    label: 'Small / medium / large',
                    value:
                        '${booking.smallCount} / ${booking.mediumCount} / ${booking.largeCount}'),
                _InfoLine(
                    icon: Icons.inventory_2_outlined,
                    label: 'Total luggage',
                    value:
                        '${(booking.smallCount ?? 0) + (booking.mediumCount ?? 0) + (booking.largeCount ?? 0)} bags'),
                _InfoLine(
                    icon: Icons.access_time,
                    label: 'Estimated duration',
                    value:
                        '${((booking.durationSeconds ?? 0) / 60).ceil()} minutes'),
              ],
              if (booking.isPending &&
                  booking.reservationStatus == 'EXPIRED') ...[
                const Divider(height: 20),
                const _InfoLine(
                    icon: Icons.event_busy_outlined,
                    label: 'Temporary reservation',
                    value:
                        'Expired. DilaDrive will recheck availability during review.'),
              ],
              if (booking.rejectionReason != null) ...[
                const Divider(height: 20),
                _InfoLine(
                    icon: Icons.info_outline,
                    label: 'Rejection reason',
                    value: booking.rejectionReason!),
              ],
              if (booking.distanceKm != null) ...[
                const Divider(height: 20),
                _InfoLine(
                  icon: Icons.straighten_outlined,
                  label: 'Distance',
                  value: '${booking.distanceKm!.toStringAsFixed(1)} km',
                ),
              ],
            ],
          ),
          if (booking.fare != null) ...[
            const SizedBox(height: 14),
            _SectionCard(
              title: 'Fare',
              children: [
                _InfoLine(
                  icon: Icons.payments_outlined,
                  label: 'Base fare',
                  value: 'RM ${booking.fare!.toStringAsFixed(2)}',
                ),
                if (booking.finalFare != null &&
                    (booking.isCompleted || booking.finalFare != booking.fare))
                  _InfoLine(
                    icon: Icons.receipt_long_outlined,
                    label: 'Final fare',
                    value: 'RM ${booking.finalFare!.toStringAsFixed(2)}',
                  ),
                if (booking.payableFare != null &&
                    booking.payableFare != booking.finalFare)
                  _InfoLine(
                    icon: Icons.description_outlined,
                    label: 'Issued invoice amount',
                    value: 'RM ${booking.payableFare!.toStringAsFixed(2)}',
                  ),
              ],
            ),
          ],
          if (_value(booking.notes) != 'Not provided') ...[
            const SizedBox(height: 14),
            _SectionCard(
              title: 'Additional notes',
              children: [
                _InfoLine(
                  icon: Icons.notes_outlined,
                  label: 'Notes',
                  value: _value(booking.notes),
                ),
              ],
            ),
          ],
        ],
      ),
      bottomNavigationBar: booking.canCancel || booking.isPending || canReschedule
          ? SafeArea(
              minimum: const EdgeInsets.fromLTRB(18, 8, 18, 14),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (canReschedule) ...[
                    FilledButton.tonalIcon(
                      onPressed: _rescheduling || _cancelling ? null : _reschedule,
                      icon: const Icon(Icons.event_outlined),
                      label: Text(_rescheduling ? 'Saving…' : 'Change pickup date or time'),
                    ),
                    if (booking.canCancel) const SizedBox(height: 8),
                  ],
                  if (booking.isPending) ...[
                    FilledButton.tonalIcon(
                      onPressed: _cancelling ? null : _editPendingBooking,
                      icon: const Icon(Icons.edit_outlined),
                      label: const Text('Edit pending booking'),
                      style: FilledButton.styleFrom(
                        minimumSize: const Size.fromHeight(48),
                      ),
                    ),
                    if (booking.canCancel) const SizedBox(height: 8),
                  ],
                  if (booking.canCancel)
                    OutlinedButton.icon(
                      onPressed: _cancelling || _rescheduling ? null : _cancelBooking,
                      icon: _cancelling
                          ? const SizedBox(
                              width: 17,
                              height: 17,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.cancel_outlined),
                      label: Text(
                          _cancelling ? 'Cancellingâ€¦' : 'Cancel booking'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.red.shade700,
                        side: BorderSide(color: Colors.red.shade200),
                        minimumSize: const Size.fromHeight(48),
                      ),
                    ),
                ],
              ),
            )
          : null,
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppTheme.white,
          borderRadius: BorderRadius.circular(17),
          border: Border.all(color: AppTheme.border),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 15),
            ...children,
          ],
        ),
      );
}

class _InfoLine extends StatelessWidget {
  const _InfoLine({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: AppTheme.secondarySkyBlue, size: 19),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    color: AppTheme.secondaryText,
                    fontSize: 12,
                  ),
                ),
                const SizedBox(height: 3),
                Text(value, style: const TextStyle(color: AppTheme.mainText)),
              ],
            ),
          ),
        ],
      );
}

Color _statusColor(CustomerBooking booking) {
  if (booking.normalizedStatus == 'CANCELLED') return Colors.red;
  if (booking.isPending) return Colors.orange;
  if (booking.isCompleted) return Colors.green;
  return AppTheme.secondarySkyBlue;
}

Color _lightStatusColor(CustomerBooking booking) {
  if (booking.normalizedStatus == 'CANCELLED') return const Color(0xFFFFA8A8);
  if (booking.isPending) return const Color(0xFFFFCC80);
  if (booking.isCompleted) return const Color(0xFF9BE6B2);
  return const Color(0xFF8EDCFF);
}

String _value(String? value) =>
    value == null || value.trim().isEmpty ? 'Not provided' : value.trim();

String _formatDate(BuildContext context, DateTime? date) => date == null
    ? 'Not provided'
    : MaterialLocalizations.of(context).formatMediumDate(date);

String _formatTime(BuildContext context, String? rawTime) {
  if (rawTime == null) return 'Not provided';
  final parsed = DateTime.tryParse('1970-01-01T$rawTime');
  if (parsed == null) return rawTime;
  return MaterialLocalizations.of(context).formatTimeOfDay(
    TimeOfDay.fromDateTime(parsed),
  );
}
