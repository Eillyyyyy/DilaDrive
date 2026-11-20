import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';
import '../../models/mobile_user.dart';
import '../../services/driver_notification_service.dart';
import '../../services/push_notification_service.dart';
import '../../widgets/unread_notification_badge.dart';

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
  final _notificationService = DriverNotificationService();
  final _inboxChanges = ValueNotifier<int>(0);

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
        DriverNotificationPage(
          driver: _driver,
          notificationService: _notificationService,
          onInboxChanged: () => _inboxChanges.value++,
        ),
        DriverProfilePage(driver: _driver, onDriverUpdated: _updateDriver),
      ];

  @override
  void dispose() {
    _notificationService.dispose();
    _inboxChanges.dispose();
    super.dispose();
  }

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
        items: [
          const BottomNavigationBarItem(
            icon: Icon(Icons.home_rounded),
            label: 'Home',
          ),
          const BottomNavigationBarItem(
            icon: Icon(Icons.local_shipping_rounded),
            label: 'Trips',
          ),
          BottomNavigationBarItem(
            icon: UnreadNotificationBadge(
              icon: const Icon(Icons.notifications_rounded),
              loadNotifications: () =>
                  _notificationService.getNotifications(_driver.id),
              inboxChanges: _inboxChanges,
              pushUpdates: PushNotificationService.updates,
            ),
            label: 'Notifications',
          ),
          const BottomNavigationBarItem(
            icon: Icon(Icons.person_rounded),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}
