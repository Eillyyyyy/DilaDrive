import 'package:flutter/material.dart';
import 'dart:convert';
import 'dart:typed_data';

import '../../models/customer_profile.dart';
import '../../models/mobile_user.dart';
import '../../services/auth_service.dart' show AuthService;
import '../../services/customer_profile_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';
import 'bookings.dart';
import 'business_documents.dart';
import 'update_account.dart';
import 'view_account.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({
    super.key,
    required this.customer,
    this.onCustomerUpdated,
  });

  final MobileUser customer;
  final ValueChanged<MobileUser>? onCustomerUpdated;

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final CustomerProfileService _service = CustomerProfileService();
  late Future<CustomerProfile> _profileFuture;

  @override
  void initState() {
    super.initState();
    _profileFuture = _service.getMyProfile();
  }

  @override
  void dispose() {
    _service.dispose();
    super.dispose();
  }

  Future<void> _reload() async {
    final future = _service.getMyProfile();
    setState(() {
      _profileFuture = future;
    });
    try {
      await future;
    } catch (_) {
      // The FutureBuilder provides the retryable state.
    }
  }

  Future<void> _editProfile(CustomerProfile profile) async {
    final updated = await Navigator.push<CustomerProfile>(
      context,
      MaterialPageRoute(
        builder: (_) => UpdateAccountPage(profile: profile),
      ),
    );
    if (updated != null && mounted) {
      widget.onCustomerUpdated?.call(
        MobileUser(
          id: updated.id,
          name: updated.name,
          email: updated.email,
          phone: updated.phone,
          status: updated.status,
          role: widget.customer.role,
        ),
      );
      setState(() {
        _profileFuture = Future.value(updated);
      });
    }
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
            child: const Text('Stay'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Sign out'),
          ),
        ],
      ),
    );
    if (confirm != true || !mounted) return;
    final authService = AuthService();
    await authService.logout();
    authService.dispose();
    if (!mounted) return;
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
        title: const Text('Profile'),
        actions: [
          IconButton(
            tooltip: 'Refresh profile',
            onPressed: _reload,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: FutureBuilder<CustomerProfile>(
        future: _profileFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError || !snapshot.hasData) {
            final error = snapshot.error;
            final needsLogin =
                error is CustomerProfileException && error.requiresLogin;
            return _ProfileMessage(
              message: error is CustomerProfileException
                  ? error.message
                  : 'Your account details could not be loaded.',
              action: needsLogin ? 'Sign in' : 'Retry',
              onPressed: needsLogin
                  ? () => Navigator.of(context).pushAndRemoveUntil(
                        MaterialPageRoute<void>(
                            builder: (_) => const LoginPage()),
                        (_) => false,
                      )
                  : _reload,
            );
          }

          final profile = snapshot.data!;
          return ListView(
            padding: const EdgeInsets.fromLTRB(18, 16, 18, 28),
            children: [
              _ProfileHeader(profile: profile),
              const SizedBox(height: 20),
              _MenuItem(
                icon: Icons.person_outline,
                title: 'View account',
                onTap: () => Navigator.push<void>(
                  context,
                  MaterialPageRoute(
                    builder: (_) => ViewAccountPage(profile: profile),
                  ),
                ),
              ),
              _MenuItem(
                icon: Icons.edit_outlined,
                title: 'Update account',
                onTap: () => _editProfile(profile),
              ),
              _MenuItem(
                icon: Icons.history_rounded,
                title: 'Booking history',
                onTap: () => Navigator.push<void>(
                  context,
                  MaterialPageRoute(
                    builder: (_) => MyBookingsPage(customer: widget.customer),
                  ),
                ),
              ),
              if (profile.type?.toUpperCase() == 'COMPANY')
                _MenuItem(
                  icon: Icons.description_outlined,
                  title: 'Business documents',
                  onTap: () => Navigator.push<void>(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const BusinessDocumentsPage(),
                    ),
                  ),
                ),
              const SizedBox(height: 5),
              const SizedBox(height: 7),
              _MenuItem(
                icon: Icons.logout,
                title: 'Sign out',
                destructive: true,
                onTap: _logout,
              ),
            ],
          );
        },
      ),
    );
  }
}

class _ProfileHeader extends StatelessWidget {
  const _ProfileHeader({required this.profile});

  final CustomerProfile profile;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(22),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [AppTheme.primaryNavy, AppTheme.navyGradientEnd],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(22),
        ),
        child: Row(
          children: [
            CircleAvatar(
              radius: 34,
              backgroundImage: profile.profileImage == null ? null : MemoryImage(_profileImageBytes(profile.profileImage!)),
              backgroundColor: Colors.white,
              child: profile.profileImage == null ? const Icon(Icons.person, size: 38, color: AppTheme.primaryNavy) : null,
            ),
            const SizedBox(width: 15),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    profile.name,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 19,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    _customerTypeLabel(profile.type),
                    style: const TextStyle(color: Colors.white70),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    profile.email,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Colors.white70),
                  ),
                ],
              ),
            ),
          ],
        ),
      );
}

Uint8List _profileImageBytes(String value) => base64Decode(value.substring(value.indexOf(',') + 1));

String _customerTypeLabel(String? type) => switch (type?.toUpperCase()) {
      'NORMAL' => 'Individual customer',
      'COMPANY' => 'Corporate customer',
      _ => type == null || type.trim().isEmpty ? 'Customer' : type,
    };

class _MenuItem extends StatelessWidget {
  const _MenuItem({
    required this.icon,
    required this.title,
    required this.onTap,
    this.destructive = false,
  });

  final IconData icon;
  final String title;
  final VoidCallback onTap;
  final bool destructive;

  @override
  Widget build(BuildContext context) => Card(
        margin: const EdgeInsets.only(bottom: 10),
        child: ListTile(
          leading: CircleAvatar(
            backgroundColor:
                destructive ? Colors.red.shade50 : AppTheme.paleBlue,
            child: Icon(
              icon,
              color: destructive ? Colors.red : AppTheme.primaryNavy,
            ),
          ),
          title: Text(
            title,
            style: TextStyle(
              color: destructive ? Colors.red : AppTheme.mainText,
              fontWeight: FontWeight.w600,
            ),
          ),
          trailing: const Icon(Icons.chevron_right_rounded),
          onTap: onTap,
        ),
      );
}

class _ProfileMessage extends StatelessWidget {
  const _ProfileMessage({
    required this.message,
    required this.action,
    required this.onPressed,
  });

  final String message;
  final String action;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.person_off_outlined,
                  size: 42, color: AppTheme.secondaryText),
              const SizedBox(height: 12),
              Text(message, textAlign: TextAlign.center),
              const SizedBox(height: 14),
              FilledButton.tonal(onPressed: onPressed, child: Text(action)),
            ],
          ),
        ),
      );
}
