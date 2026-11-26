import 'package:flutter/material.dart';

import '../../widgets/responsive_page.dart';
import '../../controllers/driver_profile_controller.dart';
import '../../models/driver_profile.dart';
import '../../models/mobile_user.dart';
import '../../services/auth_service.dart' show AuthService;
import '../../services/driver_profile_service.dart';
import '../../theme/app_theme.dart';
import '../../widgets/driver_profile_widgets.dart';
import '../auth/login.dart';
import 'view_account.dart';
import 'update_account.dart';
import 'change_password.dart';

class DriverProfilePage extends StatefulWidget {
  const DriverProfilePage(
      {super.key,
      required this.driver,
      this.onDriverUpdated,
      this.profileService});
  final MobileUser driver;
  final ValueChanged<MobileUser>? onDriverUpdated;
  final DriverProfileService? profileService;
  @override
  State<DriverProfilePage> createState() => _DriverProfilePageState();
}

class _DriverProfilePageState extends State<DriverProfilePage> {
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

  void _openLogin() => Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()), (_) => false);
  Future<void> _editProfile() async {
    final updated = await Navigator.push<DriverProfile>(
        context,
        MaterialPageRoute(
            builder: (_) => DriverUpdateAccountPage(
                profileService: widget.profileService)));
    if (updated == null || !mounted) return;
    _controller.update(updated);
    widget.onDriverUpdated?.call(MobileUser(
        id: updated.id,
        name: updated.name,
        email: updated.email,
        phone: updated.phone,
        status: updated.status,
        role: widget.driver.role));
  }

  Future<void> _logout() async {
    final confirm = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
                title: const Text('Sign out?'),
                content: const Text(
                    'You will need to sign in again to access your account.'),
                actions: [
                  TextButton(
                      onPressed: () => Navigator.pop(context, false),
                      child: const Text('Stay')),
                  FilledButton(
                      onPressed: () => Navigator.pop(context, true),
                      child: const Text('Sign out'))
                ]));
    if (confirm != true || !mounted) return;
    final auth = AuthService();
    try {
      await auth.logout();
    } finally {
      auth.dispose();
    }
    if (mounted) _openLogin();
  }

  void _view({bool vehicleOnly = false}) => Navigator.push<void>(
      context,
      MaterialPageRoute(
          builder: (_) => DriverViewAccountPage(
              profileService: widget.profileService,
              vehicleOnly: vehicleOnly)));
  void _password() => Navigator.push<void>(
      context,
      MaterialPageRoute(
          builder: (_) =>
              DriverChangePasswordPage(profileService: widget.profileService)));

  @override
  Widget build(BuildContext context) => DilaDriveScaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(
            automaticallyImplyLeading: false,
            title: const Text('Profile'),
            actions: [
              IconButton(
                  tooltip: 'Refresh profile',
                  onPressed: _controller.load,
                  icon: const Icon(Icons.refresh_rounded))
            ]),
        body: AnimatedBuilder(
            animation: _controller,
            builder: (context, _) {
              if (_controller.loading && _controller.profile == null) {
                return const Center(child: CircularProgressIndicator());
              }
              if (_controller.error != null) {
                return Center(
                    child: Padding(
                        padding: const EdgeInsets.all(28),
                        child:
                            Column(mainAxisSize: MainAxisSize.min, children: [
                          const Icon(Icons.person_off_outlined,
                              size: 42, color: AppTheme.secondaryText),
                          const SizedBox(height: 12),
                          Text(_controller.error!.message,
                              textAlign: TextAlign.center),
                          const SizedBox(height: 14),
                          FilledButton.tonal(
                              onPressed: _controller.error!.requiresLogin
                                  ? _openLogin
                                  : _controller.load,
                              child: Text(_controller.error!.requiresLogin
                                  ? 'Sign in'
                                  : 'Retry')),
                        ])));
              }
              final profile = _controller.profile;
              if (profile == null) return const SizedBox.shrink();
              return RefreshIndicator(
                  onRefresh: _controller.load,
                  child: ListView(padding: pagePadding(context), children: [
                    DriverProfileHeader(profile: profile),
                    const SizedBox(height: 20),
                    DriverProfileMenu(
                        onView: () => _view(),
                        onUpdate: _editProfile,
                        onVehicle: () => _view(vehicleOnly: true),
                        onPassword: _password,
                        onSignOut: _logout),
                  ]));
            }),
      );
}
