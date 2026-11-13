import 'package:flutter/material.dart';

import '../../models/booking_draft.dart';
import '../../models/customer_profile.dart';
import '../../models/mobile_user.dart';
import '../../services/customer_profile_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';
import 'booking/booking_location_page.dart';

class CreateBookingPage extends StatefulWidget {
  const CreateBookingPage({
    super.key,
    required this.customer,
    this.profileService,
  });

  final MobileUser customer;
  final CustomerProfileService? profileService;

  @override
  State<CreateBookingPage> createState() => _CreateBookingPageState();
}

class _CreateBookingPageState extends State<CreateBookingPage> {
  late final bool _ownsProfileService = widget.profileService == null;
  late final CustomerProfileService _profileService =
      widget.profileService ?? CustomerProfileService();
  late Future<CustomerProfile> _profile = _profileService.getMyProfile();

  @override
  void dispose() {
    if (_ownsProfileService) _profileService.dispose();
    super.dispose();
  }

  Future<void> _retry() async {
    final future = _profileService.getMyProfile();
    setState(() {
      _profile = future;
    });
    try {
      await future;
    } catch (_) {
      // FutureBuilder renders the retryable error state.
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
        body: FutureBuilder<CustomerProfile>(
          future: _profile,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError || !snapshot.hasData) {
              final error = snapshot.error;
              final requiresLogin =
                  error is CustomerProfileException && error.requiresLogin;
              return _AccessMessage(
                icon: Icons.cloud_off_outlined,
                title: 'Booking access could not be checked',
                message: error is CustomerProfileException
                    ? error.message
                    : 'Check your connection and try again.',
                actionLabel: requiresLogin ? 'Sign in' : 'Retry',
                onAction: requiresLogin ? _openLogin : _retry,
              );
            }

            final profile = snapshot.data!;
            if (profile.status?.trim().toUpperCase() != 'ACTIVE') {
              return _VerificationRequired(
                status: profile.status,
                onRetry: _retry,
              );
            }

            return BookingLocationPage(
              draft: BookingDraft(
                customerName: profile.name,
                phoneNumber: profile.phone ?? '',
              ),
            );
          },
        ),
      );
}

class _VerificationRequired extends StatelessWidget {
  const _VerificationRequired({required this.status, required this.onRetry});

  final String? status;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final normalized = status?.trim().toUpperCase();
    final pending =
        normalized == 'PENDING' || normalized == 'PENDING_VERIFICATION';
    final unknown = normalized == null || normalized.isEmpty;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  pending
                      ? Icons.pending_actions
                      : unknown
                          ? Icons.help_outline
                          : Icons.lock_outline,
                  size: 48,
                  color: pending || unknown
                      ? Colors.orange.shade800
                      : AppTheme.primaryNavy,
                ),
                const SizedBox(height: 14),
                Text(
                  pending
                      ? 'Admin verification required'
                      : unknown
                          ? 'Verification status unavailable'
                          : 'Booking is unavailable for this account',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: AppTheme.primaryNavy,
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  pending
                      ? 'Your account must be verified by Admin before you '
                          'can create a booking.'
                      : unknown
                          ? 'Your current verification status could not be '
                              'confirmed. Check again or contact DilaDrive '
                              'support.'
                          : 'Your account is not active. Contact DilaDrive '
                              'support if you think this is incorrect.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: AppTheme.secondaryText,
                    height: 1.4,
                  ),
                ),
                if (unknown) ...[
                  const SizedBox(height: 14),
                  FilledButton.tonal(
                    onPressed: onRetry,
                    child: const Text('Check again'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _AccessMessage extends StatelessWidget {
  const _AccessMessage({
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
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 44, color: AppTheme.secondaryText),
              const SizedBox(height: 12),
              Text(title, textAlign: TextAlign.center),
              const SizedBox(height: 8),
              Text(message, textAlign: TextAlign.center),
              const SizedBox(height: 14),
              FilledButton.tonal(
                onPressed: onAction,
                child: Text(actionLabel),
              ),
            ],
          ),
        ),
      );
}
