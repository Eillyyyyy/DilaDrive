import 'package:flutter/material.dart';

import '../../models/driver_trip.dart';
import '../../models/mobile_user.dart';
import '../../services/driver_trip_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';
import 'trip_details.dart';

class DriverTripsPage extends StatefulWidget {
  const DriverTripsPage({
    super.key,
    required this.driver,
    this.tripService,
  });

  final MobileUser driver;
  final DriverTripService? tripService;

  @override
  State<DriverTripsPage> createState() => _DriverTripsPageState();
}

class _DriverTripsPageState extends State<DriverTripsPage> {
  late final bool _ownsService = widget.tripService == null;
  late final DriverTripService _service =
      widget.tripService ?? DriverTripService();
  late Future<List<DriverTrip>> _trips;

  @override
  void initState() {
    super.initState();
    _trips = _service.getMyTrips();
  }

  @override
  void dispose() {
    if (_ownsService) _service.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    final future = _service.getMyTrips();
    setState(() {
      _trips = future;
    });
    await future;
  }

  Future<void> _openTrip(DriverTrip trip) async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => DriverTripDetailsPage(trip: trip),
      ),
    );
    if (changed == true && mounted) {
      try {
        await _refresh();
      } catch (error) {
        if (mounted) _showError(error);
      }
    }
  }

  void _showError(Object error) {
    final requiresLogin = error is DriverTripException && error.requiresLogin;
    if (requiresLogin) {
      Navigator.pushAndRemoveUntil(
        context,
        MaterialPageRoute(builder: (_) => const LoginPage()),
        (_) => false,
      );
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(error.toString())),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(
          title: const Text('Assigned Trips'),
          automaticallyImplyLeading: false,
        ),
        body: FutureBuilder<List<DriverTrip>>(
          future: _trips,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              final requiresLogin = snapshot.error is DriverTripException &&
                  (snapshot.error as DriverTripException).requiresLogin;
              return _MessageState(
                icon: Icons.cloud_off_outlined,
                message: snapshot.error.toString(),
                action: TextButton.icon(
                  onPressed: requiresLogin
                      ? _openLogin
                      : () => _refresh().catchError(_showError),
                  icon: const Icon(Icons.refresh),
                  label: Text(requiresLogin ? 'Sign in' : 'Retry'),
                ),
              );
            }

            final trips = (snapshot.data ?? const <DriverTrip>[]).toList()
              ..sort(compareDriverTripsForDashboard);
            if (trips.isEmpty) {
              return RefreshIndicator(
                onRefresh: () => _refresh().catchError(_showError),
                child: ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: const [
                    SizedBox(height: 110),
                    _MessageState(
                      icon: Icons.local_shipping_outlined,
                      message: 'No trips are currently assigned to you.',
                    ),
                  ],
                ),
              );
            }
            return RefreshIndicator(
              onRefresh: () => _refresh().catchError(_showError),
              child: ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: trips.length,
                separatorBuilder: (_, __) => const SizedBox(height: 12),
                itemBuilder: (context, index) => _TripCard(
                  trip: trips[index],
                  onTap: () => _openTrip(trips[index]),
                ),
              ),
            );
          },
        ),
      );

  void _openLogin() {
    Navigator.pushAndRemoveUntil(
      context,
      MaterialPageRoute(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }
}

class _TripCard extends StatelessWidget {
  const _TripCard({required this.trip, required this.onTap});

  final DriverTrip trip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                const Icon(Icons.local_shipping_outlined,
                    color: AppTheme.primaryNavy),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(trip.bookingReference,
                      style: const TextStyle(fontWeight: FontWeight.bold)),
                ),
                _StatusBadge(status: trip.statusLabel),
              ]),
              const SizedBox(height: 12),
              Text(trip.customer ?? 'Customer information unavailable'),
              if (trip.tripTypeLabel.isNotEmpty) ...[
                const SizedBox(height: 5),
                Text(
                  trip.isAirportPickup &&
                          trip.flightNo?.trim().isNotEmpty == true
                      ? '${trip.tripTypeLabel} · Flight ${trip.flightNo!.trim()}'
                      : trip.tripTypeLabel,
                  style: const TextStyle(color: AppTheme.secondaryText),
                ),
              ],
              const SizedBox(height: 6),
              Text(
                '${trip.pickupAddress ?? 'Pickup unavailable'}  →  ${trip.destinationAddress ?? 'Destination unavailable'}',
                style: const TextStyle(color: AppTheme.secondaryText),
              ),
              if (trip.tripDate != null || trip.pickupTime != null) ...[
                const SizedBox(height: 8),
                Text(
                    [
                      if (trip.tripDate != null) _dateLabel(trip.tripDate!),
                      if (trip.pickupTime != null) trip.pickupTime!,
                    ].join(' · '),
                    style: const TextStyle(color: AppTheme.secondaryText)),
              ],
              const SizedBox(height: 12),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: onTap,
                  icon: const Icon(Icons.arrow_forward),
                  label: const Text('View trip'),
                ),
              ),
            ],
          ),
        ),
      );
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.status});
  final String status;

  @override
  Widget build(BuildContext context) {
    final normalized = status.trim().toUpperCase();
    final color = switch (normalized) {
      'COMPLETED' => Colors.green.shade700,
      'CANCELLED' => Colors.red.shade700,
      'ASSIGNED' => Colors.orange.shade800,
      _ => AppTheme.secondarySkyBlue,
    };
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color.withValues(alpha: .12),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        child: Text(
          status,
          style: TextStyle(color: color, fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}

class _MessageState extends StatelessWidget {
  const _MessageState({required this.icon, required this.message, this.action});
  final IconData icon;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 44, color: AppTheme.secondaryText),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            if (action != null) action!,
          ]),
        ),
      );
}

String _dateLabel(DateTime date) =>
    '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
