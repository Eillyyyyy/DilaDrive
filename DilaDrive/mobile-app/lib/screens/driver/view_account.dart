import 'package:flutter/material.dart';
import '../../controllers/driver_profile_controller.dart';
import '../../services/driver_profile_service.dart';
import '../../theme/app_theme.dart';
import '../../widgets/driver_profile_widgets.dart';
import '../auth/login.dart';

class DriverViewAccountPage extends StatefulWidget {
  const DriverViewAccountPage(
      {super.key, this.profileService, this.vehicleOnly = false});
  final DriverProfileService? profileService;
  final bool vehicleOnly;
  @override
  State<DriverViewAccountPage> createState() => _DriverViewAccountPageState();
}

class _DriverViewAccountPageState extends State<DriverViewAccountPage> {
  late final DriverProfileController _controller;
  @override
  void initState() {
    super.initState();
    _controller = DriverProfileController(service: widget.profileService);
    _controller.load();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _signIn() => Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()), (_) => false);
  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(
            title: Text(widget.vehicleOnly
                ? 'Vehicle information'
                : 'Account information')),
        body: AnimatedBuilder(
            animation: _controller,
            builder: (context, _) {
              if (_controller.loading && _controller.profile == null) {
                return const Center(child: CircularProgressIndicator());
              }
              if (_controller.error != null) {
                return Center(
                    child: Padding(
                        padding: const EdgeInsets.all(24),
                        child:
                            Column(mainAxisSize: MainAxisSize.min, children: [
                          Text(_controller.error!.message,
                              textAlign: TextAlign.center),
                          TextButton(
                              onPressed: _controller.error!.requiresLogin
                                  ? _signIn
                                  : _controller.load,
                              child: Text(_controller.error!.requiresLogin
                                  ? 'Sign in'
                                  : 'Retry')),
                        ])));
              }
              final profile = _controller.profile;
              if (profile == null) {
                return Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Text('No account details are available.'),
                      TextButton(
                        onPressed: _controller.load,
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                );
              }
              return RefreshIndicator(
                  onRefresh: _controller.load,
                  child: ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.all(18),
                      children: [
                        widget.vehicleOnly
                            ? DriverAssignedVehicleCard(profile: profile)
                            : DriverProfileContent(profile: profile)
                      ]));
            }),
      );
}
