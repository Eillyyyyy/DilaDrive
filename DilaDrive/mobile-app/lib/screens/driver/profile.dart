import 'package:flutter/material.dart';

import '../auth/login.dart';
import 'view_account.dart';
import 'update_account.dart';


class DriverProfilePage extends StatelessWidget {
  const DriverProfilePage({super.key});

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF701A35);

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),



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
              child: const Column(
                children: [
                  CircleAvatar(
                    radius: 45,
                    backgroundColor: Colors.white,
                    child: Icon(
                      Icons.person,
                      size: 50,
                      color: primaryColor,
                    ),
                  ),

                  SizedBox(height: 15),

                  Text(
                    "Ahmad Faiz",
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 22,
                      fontWeight: FontWeight.bold,
                    ),
                  ),

                  SizedBox(height: 5),

                  Text(
                    "Driver",
                    style: TextStyle(
                      color: Colors.white70,
                    ),
                  ),

                  SizedBox(height: 5),

                  Text(
                    "driver@diladrive.com",
                    style: TextStyle(
                      color: Colors.white70,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            Padding(
              padding:
                  const EdgeInsets.symmetric(horizontal: 20),
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
                          builder: (_) =>
                              const DriverViewAccountPage(),
                        ),
                      );
                    },
                  ),

                  _menuCard(
                    context,
                    icon: Icons.edit_outlined,
                    title: "Update Account",
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) =>
                              const DriverUpdateAccountPage(),
                        ),
                      );
                    },
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
                            "Contact DilaDrive support at support@diladrive.com",
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
                      borderRadius:
                          BorderRadius.circular(18),
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
                      onTap: () {
                        Navigator.pushAndRemoveUntil(
                          context,
                          MaterialPageRoute(
                            builder: (_) =>
                                const LoginPage(),
                          ),
                          (route) => false,
                        );
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
          backgroundColor: const Color(0xFFF3E8ED),
          child: Icon(
            icon,
            color: const Color(0xFF701A35),
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