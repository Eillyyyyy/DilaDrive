import 'package:flutter/material.dart';
import 'dart:convert';
import 'dart:typed_data';

import '../../models/customer_profile.dart';
import '../../services/customer_profile_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';

class ViewAccountPage extends StatefulWidget {
  const ViewAccountPage({super.key, required this.service});

  final CustomerProfileService service;

  @override
  State<ViewAccountPage> createState() => _ViewAccountPageState();
}

class _ViewAccountPageState extends State<ViewAccountPage> {
  late Future<CustomerProfile> _profile;

  @override
  void initState() {
    super.initState();
    _profile = widget.service.getMyProfile();
  }

  void _retry() {
    final request = widget.service.getMyProfile();
    setState(() {
      _profile = request;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      appBar: AppBar(title: const Text('Account information')),
      body: FutureBuilder<CustomerProfile>(
        future: _profile,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          final profile = snapshot.data;
          if (profile != null) return _accountDetails(profile);
          final error = snapshot.error;
          final needsLogin =
              error is CustomerProfileException && error.requiresLogin;
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    error is CustomerProfileException
                        ? error.message
                        : 'No account details are available.',
                    textAlign: TextAlign.center,
                  ),
                  TextButton(
                    onPressed: needsLogin
                        ? () => Navigator.of(context).pushAndRemoveUntil(
                              MaterialPageRoute<void>(
                                  builder: (_) => const LoginPage()),
                              (_) => false,
                            )
                        : _retry,
                    child: Text(needsLogin ? 'Sign in' : 'Retry'),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _accountDetails(CustomerProfile profile) => ListView(
        padding: const EdgeInsets.all(18),
        children: [
          Center(
              child: CustomerProfileAvatar(
                  image: profile.profileImage, radius: 48)),
          const SizedBox(height: 20),
          _InfoCard(title: 'Full name', value: profile.name),
          _InfoCard(title: 'Email', value: profile.email),
          _InfoCard(title: 'Phone number', value: _value(profile.phone)),
          if (profile.type?.toUpperCase() == 'COMPANY') ...[
            _InfoCard(
                title: 'Company name', value: _value(profile.companyName)),
            _InfoCard(
              title: 'Company registration number',
              value: _value(profile.companyRegistrationNumber),
            ),
          ],
          _InfoCard(
            title: 'Customer type',
            value: _customerTypeLabel(profile.type),
          ),
          _InfoCard(
            title: 'Account status',
            value: _accountStatusLabel(profile.status),
          ),
        ],
      );
}

class CustomerProfileAvatar extends StatelessWidget {
  const CustomerProfileAvatar({
    super.key,
    required this.image,
    required this.radius,
    this.backgroundColor = AppTheme.paleBlue,
  });

  final String? image;
  final double radius;
  final Color backgroundColor;

  Uint8List? _decodeImage() {
    final value = image;
    if (value == null || value.isEmpty) return null;
    try {
      return base64Decode(value.substring(value.indexOf(',') + 1));
    } on FormatException {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final fallback =
        Icon(Icons.person, size: radius, color: AppTheme.primaryNavy);
    final bytes = _decodeImage();
    return CircleAvatar(
      radius: radius,
      backgroundColor: backgroundColor,
      child: bytes == null || bytes.isEmpty
          ? fallback
          : ClipOval(
              child: Image.memory(
                bytes,
                width: radius * 2,
                height: radius * 2,
                fit: BoxFit.cover,
                errorBuilder: (context, error, stackTrace) => fallback,
              ),
            ),
    );
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({required this.title, required this.value});

  final String title;
  final String value;

  @override
  Widget build(BuildContext context) => Card(
        margin: const EdgeInsets.only(bottom: 10),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        elevation: 0,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 17, vertical: 15),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  color: AppTheme.secondaryText,
                  fontSize: 13,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                value,
                style: const TextStyle(
                  color: AppTheme.mainText,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
      );
}

String _value(String? value) =>
    value == null || value.trim().isEmpty ? 'Not provided' : value;

String _customerTypeLabel(String? type) => switch (type?.toUpperCase()) {
      'NORMAL' => 'Individual customer',
      'COMPANY' => 'Corporate customer',
      _ => _value(type),
    };

String _accountStatusLabel(String? status) => switch (status?.toUpperCase()) {
      'ACTIVE' => 'Active',
      'PENDING' => 'Pending verification',
      'INACTIVE' => 'Inactive',
      _ => _value(status),
    };
