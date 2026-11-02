import 'package:flutter/material.dart';

import 'dashboard.dart';
import 'trips.dart';
import 'notification.dart';
import 'profile.dart';

class DriverNavigationPage extends StatefulWidget {
  const DriverNavigationPage({super.key});

  @override
  State<DriverNavigationPage> createState() =>
      _DriverNavigationPageState();
}

class _DriverNavigationPageState
    extends State<DriverNavigationPage> {
  int _selectedIndex = 0;

  final List<Widget> _pages = [
    const DriverDashboardPage(),
    const DriverTripsPage(),
    const DriverNotificationPage(),
    const DriverProfilePage(),
  ];

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _pages[_selectedIndex],

      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _selectedIndex,
        onTap: _onItemTapped,
        type: BottomNavigationBarType.fixed,
        selectedItemColor: const Color(0xFF701A35),
        unselectedItemColor: Colors.grey,
        showUnselectedLabels: true,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.home_rounded),
            label: 'Home',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.local_shipping_rounded),
            label: 'Trips',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.notifications_rounded),
            label: 'Notifications',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.person_rounded),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}