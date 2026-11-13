import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';
import '../../models/mobile_user.dart';

import 'dashboard.dart';
import 'trips.dart';
import 'notification.dart';
import 'profile.dart';

class DriverNavigationPage extends StatefulWidget {
  const DriverNavigationPage({super.key, required this.driver});

  final MobileUser driver;

  @override
  State<DriverNavigationPage> createState() => _DriverNavigationPageState();
}

class _DriverNavigationPageState extends State<DriverNavigationPage> {
  int _selectedIndex = 0;
  late MobileUser _driver;

  late List<Widget> _pages;

  @override
  void initState() {
    super.initState();
    _driver = widget.driver;
    _pages = _buildPages();
  }

  List<Widget> _buildPages() => [
        DriverDashboardPage(driver: _driver),
        DriverTripsPage(driver: _driver),
        DriverNotificationPage(driver: _driver),
        DriverProfilePage(driver: _driver, onDriverUpdated: _updateDriver),
      ];

  void _updateDriver(MobileUser driver) {
    setState(() {
      _driver = driver;
      _pages = _buildPages();
    });
  }

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
        selectedItemColor: AppTheme.primaryNavy,
        unselectedItemColor: AppTheme.secondaryText,
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
