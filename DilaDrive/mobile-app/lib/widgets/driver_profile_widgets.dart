import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import '../models/driver_profile.dart';
import '../theme/app_theme.dart';

Uint8List? driverPhotoBytes(String? image) {
  if (image == null || image.isEmpty) return null;
  try {
    return base64Decode(image.substring(image.indexOf(',') + 1));
  } on FormatException {
    return null;
  }
}

class DriverProfileAvatar extends StatelessWidget {
  const DriverProfileAvatar(
      {super.key,
      this.image,
      this.size = 96,
      this.backgroundColor = AppTheme.paleBlue,
      this.iconSize});
  final String? image;
  final double size;
  final Color backgroundColor;
  final double? iconSize;
  @override
  Widget build(BuildContext context) {
    final bytes = driverPhotoBytes(image);
    final fallback = ColoredBox(
        color: backgroundColor,
        child: Icon(Icons.person,
            size: iconSize ?? size * .48, color: AppTheme.primaryNavy));
    return Semantics(
        label: 'Profile picture',
        image: true,
        child: ClipOval(
            child: SizedBox.square(
          dimension: size,
          child: bytes == null
              ? fallback
              : Image.memory(bytes,
                  fit: BoxFit.cover, errorBuilder: (_, __, ___) => fallback),
        )));
  }
}

String driverValue(String? value) =>
    value == null || value.trim().isEmpty ? 'Not provided' : value;
String driverStatusLabel(String? value) =>
    driverValue(value).replaceAll('_', ' ');

// Layout tokens match Customer Profile, View Account and Update Account.
class DriverProfileHeader extends StatelessWidget {
  const DriverProfileHeader({super.key, required this.profile});
  final DriverProfile profile;
  @override
  Widget build(BuildContext context) => Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
          gradient: const LinearGradient(
              colors: [AppTheme.primaryNavy, AppTheme.navyGradientEnd],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight),
          borderRadius: BorderRadius.circular(22)),
      child: Row(children: [
        DriverProfileAvatar(
            image: profile.profileImage,
            size: 68,
            backgroundColor: Colors.white,
            iconSize: 38),
        const SizedBox(width: 15),
        Expanded(
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(profile.name,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                  color: Colors.white,
                  fontSize: 19,
                  fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(driverValue(profile.driverType),
              style: const TextStyle(color: Colors.white70)),
          const SizedBox(height: 3),
          Text(profile.email,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Colors.white70)),
        ]))
      ]));
}

class DriverProfileMenu extends StatelessWidget {
  const DriverProfileMenu(
      {super.key,
      required this.onView,
      required this.onUpdate,
      required this.onVehicle,
      required this.onPassword,
      required this.onSignOut});
  final VoidCallback onView, onUpdate, onVehicle, onPassword, onSignOut;
  @override
  Widget build(BuildContext context) => Column(children: [
        _item(Icons.person_outline, 'View account', onView),
        _item(Icons.edit_outlined, 'Update account', onUpdate),
        _item(Icons.directions_car_outlined, 'Vehicle information', onVehicle),
        _item(Icons.lock_outline, 'Change password', onPassword),
        const SizedBox(height: 12),
        _item(Icons.logout, 'Sign out', onSignOut, destructive: true),
      ]);
  Widget _item(IconData icon, String title, VoidCallback onTap,
          {bool destructive = false}) =>
      Card(
          margin: const EdgeInsets.only(bottom: 10),
          child: ListTile(
              leading: CircleAvatar(
                  backgroundColor:
                      destructive ? Colors.red.shade50 : AppTheme.paleBlue,
                  child: Icon(icon,
                      color: destructive ? Colors.red : AppTheme.primaryNavy)),
              title: Text(title,
                  style: TextStyle(
                      color: destructive ? Colors.red : AppTheme.mainText,
                      fontWeight: FontWeight.w600)),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: onTap));
}

class DriverProfileContent extends StatelessWidget {
  const DriverProfileContent({super.key, required this.profile});
  final DriverProfile profile;
  @override
  Widget build(BuildContext context) =>
      Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Center(
            child: DriverProfileAvatar(image: profile.profileImage, size: 96)),
        const SizedBox(height: 20),
        DriverInfoCard(title: 'Driver Information', items: {
          'Full name': profile.name,
          'Email': profile.email,
          'Phone number': driverValue(profile.phone),
          'Driving license number': driverValue(profile.licenseNumber),
          'License expiry date': driverValue(profile.licenseExpiry),
          'Driver type': driverValue(profile.driverType),
          'Availability status': driverStatusLabel(profile.availability),
          'Account status': driverStatusLabel(profile.status),
        }),
        const SizedBox(height: 10),
        DriverAssignedVehicleCard(profile: profile),
      ]);
}

class DriverInfoCard extends StatelessWidget {
  const DriverInfoCard({super.key, required this.title, required this.items});
  final String title;
  final Map<String, String> items;
  @override
  Widget build(BuildContext context) =>
      Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Text(title,
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(fontWeight: FontWeight.w600))),
        ...items.entries.map((entry) => Card(
            margin: const EdgeInsets.only(bottom: 10),
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            elevation: 0,
            child: Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 17, vertical: 15),
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(entry.key,
                          style: const TextStyle(
                              color: AppTheme.secondaryText, fontSize: 13)),
                      const SizedBox(height: 6),
                      SelectableText(entry.value,
                          style: const TextStyle(
                              color: AppTheme.mainText,
                              fontWeight: FontWeight.w700)),
                    ])))),
      ]);
}

InputDecoration driverProfileInput(String label, IconData icon) =>
    InputDecoration(
      labelText: label,
      prefixIcon: Icon(icon),
      filled: true,
      fillColor: AppTheme.white,
      border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
      enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppTheme.border)),
    );

class DriverAssignedVehicleCard extends StatelessWidget {
  const DriverAssignedVehicleCard({super.key, required this.profile});
  final DriverProfile profile;

  @override
  Widget build(BuildContext context) => DriverInfoCard(
        title: 'Assigned Vehicle',
        items: !profile.vehicleAssignmentKnown
            ? const {
                'Vehicle information':
                    'Vehicle information is unavailable. Refresh your account to try again.'
              }
            : profile.hasAssignedVehicle
                ? {
                    'Vehicle Model': driverValue(profile.vehicleModel),
                    'Plate Number': driverValue(profile.vehiclePlate),
                    'Vehicle Type': driverValue(profile.vehicleType),
                    'Passenger Capacity':
                        profile.vehicleCapacity?.toString() ?? 'Not provided',
                    'Road Tax Expiry Date':
                        driverValue(profile.vehicleRoadTaxExpiry),
                    'Vehicle Status': driverStatusLabel(profile.vehicleStatus),
                  }
                : const {'Assignment': 'No assigned vehicle'},
      );
}
