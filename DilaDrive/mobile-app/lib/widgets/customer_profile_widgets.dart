import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../models/customer_profile.dart';
import '../theme/app_theme.dart';

class CustomerVerificationStatus extends StatelessWidget {
  const CustomerVerificationStatus({
    super.key,
    required this.profile,
    required this.onRefresh,
  });

  final CustomerProfile profile;
  final VoidCallback? onRefresh;

  @override
  Widget build(BuildContext context) {
    final status = profile.status?.toUpperCase();
    final reason = profile.rejectionReason?.trim();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _InfoCard(title: 'Account status', value: _accountStatusLabel(status)),
        if (status == 'REJECTED' && reason != null && reason.isNotEmpty)
          _InfoCard(title: 'Rejection reason', value: reason),
        if (status == 'PENDING' || status == 'REJECTED')
          TextButton.icon(
            onPressed: onRefresh,
            icon: const Icon(Icons.refresh),
            label: const Text('Check status'),
          ),
      ],
    );
  }
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

String _accountStatusLabel(String? status) => switch (status?.toUpperCase()) {
      'ACTIVE' => 'Verified / Active',
      'PENDING' => 'Pending Verification',
      'REJECTED' => 'Verification Rejected',
      'INACTIVE' => 'Inactive',
      _ => _value(status),
    };
