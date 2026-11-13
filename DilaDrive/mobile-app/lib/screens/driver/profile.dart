import 'package:flutter/material.dart';

import '../../models/driver_profile.dart';
import '../../models/mobile_user.dart';
import '../../services/auth_service.dart' show AuthService;
import '../../theme/app_theme.dart';

import '../auth/login.dart';
import 'view_account.dart';
import 'update_account.dart';

class DriverProfilePage extends StatefulWidget {
  const DriverProfilePage({
    super.key,
    required this.driver,
    this.onDriverUpdated,
  });

  final MobileUser driver;
  final ValueChanged<MobileUser>? onDriverUpdated;

  @override
  State<DriverProfilePage> createState() => _DriverProfilePageState();
}

class _DriverProfilePageState extends State<DriverProfilePage> {
  late String _name = widget.driver.name;
  late String _email = widget.driver.email;

  Future<void> _editProfile() async {
    final updated = await Navigator.push<DriverProfile>(
      context,
      MaterialPageRoute(builder: (_) => const DriverUpdateAccountPage()),
    );
    if (updated == null || !mounted) return;
    widget.onDriverUpdated?.call(
      MobileUser(
        id: updated.id,
        name: updated.name,
        email: updated.email,
        phone: updated.phone,
        status: updated.status,
        role: widget.driver.role,
      ),
    );
    setState(() {
      _name = updated.name;
      _email = updated.email;
    });
  }

  void _openLogin() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    const primaryColor = AppTheme.primaryNavy;

    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      body: SingleChildScrollView(
        child: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.only(
                top: 30,
                bottom: 30,
              ),
              decoration: const BoxDecoration(
                color: primaryColor,
                borderRadius: BorderRadius.only(
                  bottomLeft: Radius.circular(30),
                  bottomRight: Radius.circular(30),
                ),
              ),
              child: Column(
                children: [
                  const CircleAvatar(
                    radius: 45,
                    backgroundColor: Colors.white,
                    child: Icon(
                      Icons.person,
                      size: 50,
                      color: primaryColor,
                    ),
                  ),
                  const SizedBox(height: 15),
                  Text(
                    _name,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 22,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 5),
                  const Text(
                    "Driver",
                    style: TextStyle(
                      color: Colors.white70,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    _email,
                    style: const TextStyle(
                      color: Colors.white70,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Column(
                children: [
                  _menuCard(
                    context,
                    icon: Icons.person_outline,
                    title: "View Account",
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const DriverViewAccountPage(),
                        ),
                      );
                    },
                  ),
                  _menuCard(
                    context,
                    icon: Icons.edit_outlined,
                    title: "Update Account",
                    onTap: _editProfile,
                  ),
                  _menuCard(
                    context,
                    icon: Icons.help_outline,
                    title: "Help & Support",
                    onTap: () {
                      showDialog(
                        context: context,
                        builder: (_) => AlertDialog(
                          title: const Text(
                            "Help & Support",
                          ),
                          content: const Text(
                            'For account or trip assistance, contact your DilaDrive administrator.',
                          ),
                          actions: [
                            TextButton(
                              onPressed: () {
                                Navigator.pop(context);
                              },
                              child: const Text("OK"),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                  _menuCard(
                    context,
                    icon: Icons.info_outline,
                    title: "About DilaDrive",
                    onTap: () {
                      showDialog(
                        context: context,
                        builder: (_) => AlertDialog(
                          title: const Text(
                            "About DilaDrive",
                          ),
                          content: const Text(
                            "DilaDrive Transport Management System v1.0",
                          ),
                          actions: [
                            TextButton(
                              onPressed: () {
                                Navigator.pop(context);
                              },
                              child: const Text("OK"),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 10),
                  Container(
                    decoration: BoxDecoration(
                      color: Colors.red.shade50,
                      borderRadius: BorderRadius.circular(18),
                    ),
                    child: ListTile(
                      leading: const Icon(
                        Icons.logout,
                        color: Colors.red,
                      ),
                      title: const Text(
                        "Logout",
                        style: TextStyle(
                          color: Colors.red,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      trailing: const Icon(
                        Icons.arrow_forward_ios,
                        color: Colors.red,
                        size: 16,
                      ),
                      onTap: () async {
                        final authService = AuthService();
                        await authService.logout();
                        authService.dispose();
                        if (!mounted) return;
                        _openLogin();
                      },
                    ),
                  ),
                  const SizedBox(height: 30),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  static Widget _menuCard(
    BuildContext context, {
    required IconData icon,
    required String title,
    required VoidCallback onTap,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: AppTheme.paleBlue,
          child: Icon(
            icon,
            color: AppTheme.primaryNavy,
          ),
        ),
        title: Text(title),
        trailing: const Icon(
          Icons.arrow_forward_ios,
          size: 16,
        ),
        onTap: onTap,
      ),
    );
  }
}
