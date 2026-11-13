import 'package:flutter/material.dart';

import '../../models/customer_booking.dart';
import '../../models/customer_booking_ordering.dart';
import '../../models/mobile_user.dart';
import '../../services/customer_booking_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';
import 'booking_details.dart';

class CustomerDashboardPage extends StatefulWidget {
  const CustomerDashboardPage({
    super.key,
    required this.customer,
    this.bookingService,
    this.onCreateBooking,
  });

  final MobileUser customer;
  final CustomerBookingService? bookingService;
  final VoidCallback? onCreateBooking;

  @override
  State<CustomerDashboardPage> createState() => _CustomerDashboardPageState();
}

class _CustomerDashboardPageState extends State<CustomerDashboardPage> {
  late final bool _ownsBookingService = widget.bookingService == null;
  late final CustomerBookingService _bookingService =
      widget.bookingService ?? CustomerBookingService();
  late Future<List<CustomerBooking>> _bookingsFuture;

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

  void _reload() {
    setState(() {
      _bookingsFuture = _bookingService.getMyBookings();
    });
  }

  Future<void> _refresh() async {
    final future = _bookingService.getMyBookings();
    setState(() {
      _bookingsFuture = future;
    });
    try {
      await future;
    } catch (_) {
      // FutureBuilder renders the retryable error state.
    }
  }

  void _goToLogin() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text('DilaDrive'),
      ),
      body: FutureBuilder<List<CustomerBooking>>(
        future: _bookingsFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            final requiresLogin = snapshot.error is CustomerBookingException &&
                (snapshot.error as CustomerBookingException).requiresLogin;
            return _DashboardMessage(
              icon: Icons.cloud_off_outlined,
              title: 'Bookings are unavailable',
              message: _errorMessage(snapshot.error),
              actionLabel: requiresLogin ? 'Sign in' : 'Try again',
              onAction: requiresLogin ? _goToLogin : _reload,
            );
          }

          final bookings = snapshot.data ?? const <CustomerBooking>[];
          final pendingCount =
              bookings.where((booking) => booking.isPending).length;
          final activeCount =
              bookings.where((booking) => booking.isActive).length;
          final completedCount =
              bookings.where((booking) => booking.isCompleted).length;
          final now = DateTime.now();
          final activeBookings = bookings
              .where((booking) => booking.isActive)
              .toList()
            ..sort((first, second) => CustomerBookingOrdering.activeBySchedule(
                  first,
                  second,
                  now: now,
                ));
          final recentBookings = bookings.toList()
            ..sort(CustomerBookingOrdering.newestFirst);
          final latestBookings = recentBookings.take(3).toList(growable: false);

          return Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 760),
              child: RefreshIndicator(
                onRefresh: _refresh,
                child: ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
                  children: [
                    _TransportHeroCard(
                      customerName: widget.customer.name,
                      onBook: widget.onCreateBooking,
                    ),
                    const SizedBox(height: 18),
                    Row(
                      children: [
                        Expanded(
                          child: _StatCard(
                            label: 'Pending',
                            value: pendingCount,
                            color: Colors.orange,
                            icon: Icons.hourglass_top_rounded,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _StatCard(
                            label: 'Active',
                            value: activeCount,
                            color: AppTheme.secondarySkyBlue,
                            icon: Icons.local_shipping_outlined,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _StatCard(
                            label: 'Completed',
                            value: completedCount,
                            color: Colors.green,
                            icon: Icons.task_alt_rounded,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 26),
                    const _SectionHeading('Active booking'),
                    const SizedBox(height: 12),
                    if (activeBookings.isEmpty)
                      const _EmptyCard(
                        icon: Icons.route_outlined,
                        title: 'No active bookings',
                        message: 'Your confirmed trips will appear here.',
                      )
                    else
                      _BookingSummaryCard(
                        booking: activeBookings.first,
                        onTap: () => _openBooking(activeBookings.first),
                      ),
                    const SizedBox(height: 26),
                    const _SectionHeading('Recent bookings'),
                    const SizedBox(height: 12),
                    if (latestBookings.isEmpty)
                      const _EmptyCard(
                        icon: Icons.event_note_outlined,
                        title: 'No bookings yet',
                        message: 'Create a booking to see it here.',
                      )
                    else
                      ...latestBookings.map(
                        (booking) => Padding(
                          padding: const EdgeInsets.only(bottom: 10),
                          child: _RecentBookingCard(
                            booking: booking,
                            onTap: () => _openBooking(booking),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _openBooking(CustomerBooking booking) async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => BookingDetailsPage(booking: booking),
      ),
    );
    if (changed == true && mounted) _reload();
  }

  String _errorMessage(Object? error) => error is CustomerBookingException
      ? error.message
      : 'Please check your connection and try again.';
}

class _TransportHeroCard extends StatelessWidget {
  const _TransportHeroCard({required this.customerName, this.onBook});

  final String customerName;
  final VoidCallback? onBook;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(21, 20, 17, 18),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [AppTheme.primaryNavy, AppTheme.navyGradientEnd],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(
                Icons.waving_hand_rounded,
                color: Color(0xFF8DDCFF),
                size: 17,
              ),
              const SizedBox(width: 7),
              Expanded(
                child: Text(
                  'Welcome, $customerName',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white70,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              const Icon(
                Icons.directions_car_filled_rounded,
                color: Color(0xFF8DDCFF),
                size: 27,
              ),
            ],
          ),
          const SizedBox(height: 15),
          const Text(
            'Where are you\ngoing?',
            style: TextStyle(color: Colors.white, fontSize: 25, height: 1.12, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          const Text(
            'Book reliable transport for your next journey.',
            style: TextStyle(color: Colors.white70, height: 1.35),
          ),
          const SizedBox(height: 16),
          Material(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            child: InkWell(
              onTap: onBook,
              borderRadius: BorderRadius.circular(16),
              child: Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                child: Column(
                  children: [
                    const _PlaceHintRow(
                      icon: Icons.trip_origin_rounded,
                      iconColor: AppTheme.secondarySkyBlue,
                      label: 'PICKUP',
                      hint: 'Enter pickup location',
                    ),
                    Padding(
                      padding: const EdgeInsets.only(left: 9),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Container(
                          width: 1,
                          height: 13,
                          color: AppTheme.border,
                        ),
                      ),
                    ),
                    const _PlaceHintRow(
                      icon: Icons.location_on_rounded,
                      iconColor: AppTheme.primaryNavy,
                      label: 'DESTINATION',
                      hint: 'Where are you going?',
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PlaceHintRow extends StatelessWidget {
  const _PlaceHintRow({
    required this.icon,
    required this.iconColor,
    required this.label,
    required this.hint,
  });

  final IconData icon;
  final Color iconColor;
  final String label;
  final String hint;

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Icon(icon, color: iconColor, size: 20),
          const SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    color: AppTheme.secondaryText,
                    fontSize: 10,
                    letterSpacing: .7,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  hint,
                  style: const TextStyle(
                    color: AppTheme.mainText,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
          const Icon(Icons.chevron_right_rounded, color: AppTheme.secondaryText, size: 20),
        ],
      );
}

class _StatCard extends StatelessWidget {
  const _StatCard({
    required this.label,
    required this.value,
    required this.color,
    required this.icon,
  });

  final String label;
  final int value;
  final Color color;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
      decoration: BoxDecoration(
        color: AppTheme.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 19),
          const SizedBox(height: 9),
          Text(
            '$value',
            style: TextStyle(
              color: color,
              fontSize: 23,
              fontWeight: FontWeight.bold,
            ),
          ),
          Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: AppTheme.secondaryText, fontSize: 12),
          ),
        ],
      ),
    );
  }
}

class _SectionHeading extends StatelessWidget {
  const _SectionHeading(this.title);

  final String title;

  @override
  Widget build(BuildContext context) => Text(
        title,
        style: const TextStyle(
          color: AppTheme.mainText,
          fontSize: 17,
          fontWeight: FontWeight.w700,
        ),
      );
}

class _BookingSummaryCard extends StatelessWidget {
  const _BookingSummaryCard({required this.booking, required this.onTap});

  final CustomerBooking booking;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return _BookingCardFrame(
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _BookingHeader(booking: booking),
          const SizedBox(height: 15),
          _RouteLine(booking: booking),
          const SizedBox(height: 13),
          Row(
            children: [
              const Icon(Icons.calendar_today_outlined, size: 16),
              const SizedBox(width: 7),
              Text(_formatDate(context, booking.tripDate)),
              const Spacer(),
              const Text(
                'View details',
                style: TextStyle(
                  color: AppTheme.primaryNavy,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _RecentBookingCard extends StatelessWidget {
  const _RecentBookingCard({required this.booking, required this.onTap});

  final CustomerBooking booking;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return _BookingCardFrame(
      onTap: onTap,
      child: Row(
        children: [
          const Icon(Icons.route_outlined, color: AppTheme.primaryNavy),
          const SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  booking.reference,
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 4),
                _RouteLine(booking: booking),
              ],
            ),
          ),
          const SizedBox(width: 8),
          _StatusBadge(booking: booking),
        ],
      ),
    );
  }
}

class _BookingCardFrame extends StatelessWidget {
  const _BookingCardFrame({
    required this.onTap,
    required this.child,
  });

  final VoidCallback onTap;
  final Widget child;

  @override
  Widget build(BuildContext context) {
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
          child: child,
        ),
      ),
    );
  }
}

class _BookingHeader extends StatelessWidget {
  const _BookingHeader({required this.booking});

  final CustomerBooking booking;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Icon(Icons.local_shipping_outlined, color: AppTheme.primaryNavy),
        const SizedBox(width: 9),
        Expanded(
          child: Text(
            booking.reference,
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
        ),
        _StatusBadge(booking: booking),
      ],
    );
  }
}

class _RouteLine extends StatelessWidget {
  const _RouteLine({required this.booking});

  final CustomerBooking booking;

  @override
  Widget build(BuildContext context) {
    return Text(
      '${_address(booking.pickupAddress)}  →  ${_address(booking.destinationAddress)}',
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
      style: const TextStyle(color: AppTheme.secondaryText, height: 1.35),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.booking});

  final CustomerBooking booking;

  @override
  Widget build(BuildContext context) {
    final color = _statusColor(booking);
    return Container(
      constraints: const BoxConstraints(maxWidth: 150),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .10),
        borderRadius: BorderRadius.circular(30),
      ),
      child: Text(
        booking.statusLabel,
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
}

class _EmptyCard extends StatelessWidget {
  const _EmptyCard({
    required this.icon,
    required this.title,
    required this.message,
  });

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppTheme.border),
      ),
      child: Column(
        children: [
          Icon(icon, color: AppTheme.secondarySkyBlue, size: 29),
          const SizedBox(height: 8),
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

class _DashboardMessage extends StatelessWidget {
  const _DashboardMessage({
    required this.icon,
    required this.title,
    required this.message,
    required this.actionLabel,
    required this.onAction,
  });

  final IconData icon;
  final String title;
  final String message;
  final String actionLabel;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    return Center(
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
            FilledButton.tonal(onPressed: onAction, child: Text(actionLabel)),
          ],
        ),
      ),
    );
  }
}

Color _statusColor(CustomerBooking booking) {
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
