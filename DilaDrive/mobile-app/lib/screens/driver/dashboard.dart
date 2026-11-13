import 'package:flutter/material.dart';

import '../../models/driver_trip.dart';
import '../../models/mobile_user.dart';
import '../../services/driver_trip_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';
import 'trip_details.dart';

class DriverDashboardPage extends StatefulWidget {
  const DriverDashboardPage({super.key, required this.driver});
  final MobileUser driver;

  @override
  State<DriverDashboardPage> createState() => _DriverDashboardPageState();
}

class _DriverDashboardPageState extends State<DriverDashboardPage> {
  final DriverTripService _service = DriverTripService();
  late Future<List<DriverTrip>> _trips;

  @override
  void initState() {
    super.initState();
    _trips = _service.getMyTrips();
  }

  @override
  void dispose() {
    _service.dispose();
    super.dispose();
  }

  Future<void> _reload() async {
    final future = _service.getMyTrips();
    setState(() {
      _trips = future;
    });
    try {
      await future;
    } on DriverTripException catch (error) {
      if (error.requiresLogin && mounted) _openLogin();
    } catch (_) {
      // FutureBuilder shows the retryable error state.
    }
  }

  void _openLogin() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(
          automaticallyImplyLeading: false,
          title: const Text('Driver Dashboard'),
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
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    const Icon(Icons.cloud_off_outlined, size: 42),
                    const SizedBox(height: 12),
                    Text(snapshot.error.toString(),
                        textAlign: TextAlign.center),
                    TextButton.icon(
                      onPressed: requiresLogin ? _openLogin : _reload,
                      icon: const Icon(Icons.refresh),
                      label: Text(requiresLogin ? 'Sign in' : 'Retry'),
                    ),
                  ]),
                ),
              );
            }
            final trips = snapshot.data ?? const <DriverTrip>[];
            final orderedTrips = trips.toList()
              ..sort(compareDriverTripsForDashboard);
            final active = trips.where((trip) => trip.isActive).toList()
              ..sort(compareDriverTripsForDashboard);
            final complete = trips.where((trip) => trip.isCompleted).length;
            final assigned = trips
                .where((trip) => trip.normalizedStatus == 'ASSIGNED')
                .length;
            final current = active.isEmpty ? null : active.first;
            return RefreshIndicator(
              onRefresh: () async => _reload(),
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Card(
                    color: AppTheme.primaryNavy,
                    child: Padding(
                      padding: const EdgeInsets.all(22),
                      child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Welcome, ${widget.driver.name}',
                                style: const TextStyle(color: Colors.white70)),
                            const SizedBox(height: 8),
                            Text(
                                '${active.length} active assignment${active.length == 1 ? '' : 's'}',
                                style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 22,
                                    fontWeight: FontWeight.bold)),
                          ]),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(children: [
                    _StatCard(title: 'Assigned', value: '$assigned'),
                    const SizedBox(width: 8),
                    _StatCard(title: 'Active', value: '${active.length}'),
                    const SizedBox(width: 8),
                    _StatCard(title: 'Completed', value: '$complete'),
                  ]),
                  const SizedBox(height: 20),
                  const Text('Current trip',
                      style:
                          TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  if (current == null)
                    const Card(
                        child: Padding(
                      padding: EdgeInsets.all(18),
                      child: Text('No active trips are assigned right now.'),
                    ))
                  else
                    Card(
                      child: ListTile(
                        leading: const CircleAvatar(
                          backgroundColor: AppTheme.paleBlue,
                          child: Icon(Icons.local_shipping_outlined,
                              color: AppTheme.primaryNavy),
                        ),
                        title: Text(current.bookingReference),
                        subtitle: Text(
                            '${current.pickupAddress ?? 'Pickup unavailable'} → ${current.destinationAddress ?? 'Destination unavailable'}'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () async {
                          final changed = await Navigator.push<bool>(
                              context,
                              MaterialPageRoute(
                                  builder: (_) =>
                                      DriverTripDetailsPage(trip: current)));
                          if (changed == true && mounted) _reload();
                        },
                      ),
                    ),
                  const SizedBox(height: 20),
                  const Text('Assigned trips',
                      style:
                          TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  if (trips.isEmpty)
                    const Card(
                        child: Padding(
                      padding: EdgeInsets.all(18),
                      child: Text('Your assignments will appear here.'),
                    ))
                  else
                    ...orderedTrips.take(5).map((trip) => Card(
                          child: ListTile(
                            title: Text(trip.bookingReference),
                            subtitle: Text(
                                '${trip.customer ?? 'Customer'} · ${trip.statusLabel}'),
                            trailing: const Icon(Icons.chevron_right),
                            onTap: () async {
                              final changed = await Navigator.push<bool>(
                                  context,
                                  MaterialPageRoute(
                                      builder: (_) =>
                                          DriverTripDetailsPage(trip: trip)));
                              if (changed == true && mounted) _reload();
                            },
                          ),
                        )),
                ],
              ),
            );
          },
        ),
      );
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.title, required this.value});
  final String title;
  final String value;

  @override
  Widget build(BuildContext context) => Expanded(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 6),
            child: Column(children: [
              Text(value,
                  style: const TextStyle(
                      color: AppTheme.primaryNavy,
                      fontSize: 21,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              Text(title,
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 12)),
            ]),
          ),
        ),
      );
}
