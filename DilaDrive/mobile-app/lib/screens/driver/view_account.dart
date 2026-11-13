import 'package:flutter/material.dart';

import '../../models/driver_profile.dart';
import '../../services/driver_profile_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';

class DriverViewAccountPage extends StatefulWidget {
  const DriverViewAccountPage({super.key});

  @override
  State<DriverViewAccountPage> createState() => _DriverViewAccountPageState();
}

class _DriverViewAccountPageState extends State<DriverViewAccountPage> {
  final DriverProfileService _service = DriverProfileService();
  late Future<DriverProfile> _profile;

  @override
  void initState() {
    super.initState();
    _profile = _service.getProfile();
  }

  @override
  void dispose() {
    _service.dispose();
    super.dispose();
  }

  void _retryOrSignIn(Object? error) {
    final requiresLogin =
        error is DriverProfileException && error.requiresLogin;
    if (requiresLogin) {
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute<void>(builder: (_) => const LoginPage()),
        (_) => false,
      );
    } else {
      setState(() {
        _profile = _service.getProfile();
      });
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: const Text('Driver Account')),
        body: FutureBuilder<DriverProfile>(
          future: _profile,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Text(snapshot.error.toString(),
                        textAlign: TextAlign.center),
                    TextButton.icon(
                      onPressed: () => _retryOrSignIn(snapshot.error),
                      icon: const Icon(Icons.refresh),
                      label: Text(snapshot.error is DriverProfileException &&
                              (snapshot.error as DriverProfileException)
                                  .requiresLogin
                          ? 'Sign in'
                          : 'Retry'),
                    ),
                  ]),
                ),
              );
            }
            final profile = snapshot.data!;
            final items = <String, String>{
              'Driver ID': profile.id.toString(),
              'Full name': profile.name,
              'Email': profile.email,
              'Phone': profile.phone ?? 'Not provided',
              if (profile.licenseNumber != null)
                'Driving license': profile.licenseNumber!,
              if (profile.licenseExpiry != null)
                'License expiry': profile.licenseExpiry!,
              if (profile.driverType != null)
                'Driver type': profile.driverType!,
              if (profile.availability != null)
                'Availability': profile.availability!,
              if (profile.status != null) 'Account status': profile.status!,
            };
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Card(
                  color: AppTheme.primaryNavy,
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(children: [
                      const CircleAvatar(
                        radius: 38,
                        backgroundColor: Colors.white,
                        child: Icon(Icons.person,
                            size: 42, color: AppTheme.primaryNavy),
                      ),
                      const SizedBox(height: 12),
                      Text(profile.name,
                          style: const TextStyle(
                              color: Colors.white,
                              fontSize: 20,
                              fontWeight: FontWeight.bold)),
                      const SizedBox(height: 3),
                      const Text('Driver',
                          style: TextStyle(color: Colors.white70)),
                    ]),
                  ),
                ),
                const SizedBox(height: 12),
                ...items.entries.map((entry) => Card(
                      child: ListTile(
                        title: Text(entry.key,
                            style: const TextStyle(
                                color: AppTheme.secondaryText, fontSize: 13)),
                        subtitle: Text(entry.value,
                            style: const TextStyle(
                                color: AppTheme.mainText, fontSize: 16)),
                      ),
                    )),
              ],
            );
          },
        ),
      );
}
