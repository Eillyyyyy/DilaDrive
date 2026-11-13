import 'package:flutter/material.dart';

import '../../models/customer_booking.dart';
import '../../models/customer_booking_ordering.dart';
import '../../models/mobile_user.dart';
import '../../services/customer_booking_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';
import 'booking_details.dart';

enum _BookingFilter { all, pending, active, completed, cancelled }

class MyBookingsPage extends StatefulWidget {
  const MyBookingsPage({
    super.key,
    required this.customer,
    this.bookingService,
  });

  final MobileUser customer;
  final CustomerBookingService? bookingService;

  @override
  State<MyBookingsPage> createState() => _MyBookingsPageState();
}

class _MyBookingsPageState extends State<MyBookingsPage> {
  late final bool _ownsBookingService = widget.bookingService == null;
  late final CustomerBookingService _bookingService =
      widget.bookingService ?? CustomerBookingService();
  late Future<List<CustomerBooking>> _bookingsFuture;
  _BookingFilter _filter = _BookingFilter.all;

  @override
  void initState() {
    super.initState();
    _bookingsFuture = _bookingService.getMyBookings();
  }

  @override
  void dispose() {
    if (_ownsBookingService) _bookingService.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    final future = _bookingService.getMyBookings();
    setState(() {
      _bookingsFuture = future;
    });
    try {
      await future;
    } catch (_) {
      // FutureBuilder displays the request error after refresh completes.
    }
  }

  void _goToLogin() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text('My Bookings'),
      ),
      body: FutureBuilder<List<CustomerBooking>>(
        future: _bookingsFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            final error = snapshot.error;
            final message = error is CustomerBookingException
                ? error.message
                : 'Please check your connection and try again.';
            return _BookingsMessage(
              icon: Icons.cloud_off_outlined,
              title: 'Bookings are unavailable',
              message: message,
              actionLabel:
                  error is CustomerBookingException && error.requiresLogin
                      ? 'Sign in'
                      : 'Retry',
              onRetry: error is CustomerBookingException && error.requiresLogin
                  ? _goToLogin
                  : _refresh,
            );
          }

          final bookings = (snapshot.data ?? const <CustomerBooking>[]).toList()
            ..sort(CustomerBookingOrdering.newestFirst);
          final visibleBookings =
              bookings.where(_matchesFilter).toList(growable: false);

          return RefreshIndicator(
            onRefresh: _refresh,
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(18, 16, 18, 28),
              children: [
                _BookingsIntro(totalBookings: bookings.length),
                const SizedBox(height: 16),
                SizedBox(
                  height: 42,
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    children: [
                      for (final filter in _BookingFilter.values)
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: FilterChip(
                            label: Text(_filterLabel(filter)),
                            selected: _filter == filter,
                            onSelected: (_) => setState(() => _filter = filter),
                            selectedColor: AppTheme.paleBlue,
                            checkmarkColor: AppTheme.primaryNavy,
                            side: BorderSide(
                              color: _filter == filter
                                  ? AppTheme.secondarySkyBlue
                                  : AppTheme.border,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                if (visibleBookings.isEmpty)
                  _EmptyBookings(
                    hasAnyBookings: bookings.isNotEmpty,
                  )
                else
                  ...visibleBookings.map(
                    (booking) => Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: _CustomerBookingCard(
                        booking: booking,
                        onTap: () => _openBooking(booking),
                      ),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  bool _matchesFilter(CustomerBooking booking) => switch (_filter) {
        _BookingFilter.all => true,
        _BookingFilter.pending => booking.isPending,
        _BookingFilter.active => booking.isActive,
        _BookingFilter.completed => booking.isCompleted,
        _BookingFilter.cancelled => booking.normalizedStatus == 'CANCELLED',
      };

  Future<void> _openBooking(CustomerBooking booking) async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => BookingDetailsPage(booking: booking),
      ),
    );
    if (changed == true && mounted) _refresh();
  }
}

class _BookingsIntro extends StatelessWidget {
  const _BookingsIntro({required this.totalBookings});

  final int totalBookings;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(17),
      decoration: BoxDecoration(
        color: AppTheme.white,
        borderRadius: BorderRadius.circular(17),
        border: Border.all(color: AppTheme.border),
      ),
      child: Row(
        children: [
          Container(
            width: 46,
            height: 46,
            decoration: const BoxDecoration(
              color: AppTheme.paleBlue,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.receipt_long_outlined,
              color: AppTheme.primaryNavy,
            ),
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Your trips',
                  style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                ),
                const SizedBox(height: 3),
                Text(
                  '$totalBookings ${totalBookings == 1 ? 'booking' : 'bookings'}',
                  style: const TextStyle(color: AppTheme.secondaryText),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CustomerBookingCard extends StatelessWidget {
  const _CustomerBookingCard({required this.booking, required this.onTap});

  final CustomerBooking booking;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final statusColor = _bookingStatusColor(booking);
    return Material(
      color: AppTheme.white,
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(17),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: AppTheme.border),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(
                    Icons.local_shipping_outlined,
                    color: AppTheme.primaryNavy,
                    size: 21,
                  ),
                  const SizedBox(width: 9),
                  Expanded(
                    child: Text(
                      booking.reference,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                  ),
                  _StatusBadge(
                    label: booking.statusLabel,
                    color: statusColor,
                  ),
                ],
              ),
              const SizedBox(height: 15),
              _DetailLine(
                icon: Icons.trip_origin_rounded,
                text: _address(booking.pickupAddress),
              ),
              const SizedBox(height: 8),
              _DetailLine(
                icon: Icons.location_on_outlined,
                text: _address(booking.destinationAddress),
              ),
              const SizedBox(height: 13),
              Wrap(
                spacing: 16,
                runSpacing: 7,
                children: [
                  _SmallDetail(
                    icon: Icons.calendar_today_outlined,
                    text: _formatDate(context, booking.tripDate),
                  ),
                  if (booking.passengerCount != null)
                    _SmallDetail(
                      icon: Icons.people_outline,
                      text: '${booking.passengerCount} passengers',
                    ),
                  if (booking.fare != null)
                    _SmallDetail(
                      icon: Icons.payments_outlined,
                      text: 'RM ${booking.fare!.toStringAsFixed(2)}',
                    ),
                ],
              ),
              const SizedBox(height: 14),
              const Align(
                alignment: Alignment.centerRight,
                child: Text(
                  'View details  →',
                  style: TextStyle(
                    color: AppTheme.primaryNavy,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DetailLine extends StatelessWidget {
  const _DetailLine({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: AppTheme.secondaryText, size: 17),
          const SizedBox(width: 8),
          Expanded(
              child: Text(text, maxLines: 2, overflow: TextOverflow.ellipsis)),
        ],
      );
}

class _SmallDetail extends StatelessWidget {
  const _SmallDetail({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: AppTheme.secondaryText, size: 15),
          const SizedBox(width: 5),
          Text(
            text,
            style: const TextStyle(color: AppTheme.secondaryText, fontSize: 12),
          ),
        ],
      );
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
        constraints: const BoxConstraints(maxWidth: 148),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: color.withValues(alpha: .10),
          borderRadius: BorderRadius.circular(28),
        ),
        child: Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            color: color,
            fontSize: 11,
            fontWeight: FontWeight.w700,
          ),
        ),
      );
}

class _EmptyBookings extends StatelessWidget {
  const _EmptyBookings({required this.hasAnyBookings});

  final bool hasAnyBookings;

  @override
  Widget build(BuildContext context) {
    final title = hasAnyBookings ? 'No matching bookings' : 'No bookings yet';
    final message = hasAnyBookings
        ? 'Try another status filter to see your trips.'
        : 'Bookings you make will appear here.';
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 30, 20, 30),
      decoration: BoxDecoration(
        color: AppTheme.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppTheme.border),
      ),
      child: Column(
        children: [
          const Icon(
            Icons.event_note_outlined,
            color: AppTheme.secondarySkyBlue,
            size: 34,
          ),
          const SizedBox(height: 9),
          Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppTheme.secondaryText),
          ),
        ],
      ),
    );
  }
}

class _BookingsMessage extends StatelessWidget {
  const _BookingsMessage({
    required this.icon,
    required this.title,
    required this.message,
    required this.actionLabel,
    required this.onRetry,
  });

  final IconData icon;
  final String title;
  final String message;
  final String actionLabel;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: AppTheme.secondaryText, size: 42),
              const SizedBox(height: 12),
              Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
              const SizedBox(height: 6),
              Text(message, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton.tonal(onPressed: onRetry, child: Text(actionLabel)),
            ],
          ),
        ),
      );
}

String _filterLabel(_BookingFilter filter) => switch (filter) {
      _BookingFilter.all => 'All',
      _BookingFilter.pending => 'Pending',
      _BookingFilter.active => 'Active',
      _BookingFilter.completed => 'Completed',
      _BookingFilter.cancelled => 'Cancelled',
    };

Color _bookingStatusColor(CustomerBooking booking) {
  if (booking.normalizedStatus == 'CANCELLED') return Colors.red;
  if (booking.isPending) return Colors.orange;
  if (booking.isCompleted) return Colors.green;
  return AppTheme.secondarySkyBlue;
}

String _address(String? value) =>
    value == null || value.trim().isEmpty ? 'Address unavailable' : value;

String _formatDate(BuildContext context, DateTime? date) => date == null
    ? 'Date unavailable'
    : MaterialLocalizations.of(context).formatMediumDate(date);
