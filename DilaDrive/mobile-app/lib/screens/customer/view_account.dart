import 'package:flutter/material.dart';
import 'dart:convert';
import 'dart:typed_data';

import '../../models/customer_profile.dart';
import '../../theme/app_theme.dart';

class ViewAccountPage extends StatelessWidget {
  const ViewAccountPage({super.key, required this.profile});

  final CustomerProfile profile;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      appBar: AppBar(title: const Text('Account information')),
      body: ListView(
        padding: const EdgeInsets.all(18),
        children: [
          Center(child: CircleAvatar(radius: 48, backgroundColor: AppTheme.paleBlue,
            backgroundImage: profile.profileImage == null ? null : MemoryImage(_imageBytes(profile.profileImage!)),
            child: profile.profileImage == null ? const Icon(Icons.person, size: 46, color: AppTheme.primaryNavy) : null)),
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

Uint8List _imageBytes(String value) => base64Decode(value.substring(value.indexOf(',') + 1));
