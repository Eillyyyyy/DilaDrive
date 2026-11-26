import 'package:flutter/material.dart';

import '../../theme/app_theme.dart';
import '../../widgets/responsive_page.dart';

import '../../models/mobile_user.dart';
import '../../services/customer_notification_service.dart';
import '../../services/push_notification_service.dart';
import '../../widgets/unread_notification_badge.dart';
import 'dashboard.dart';
import 'bookings.dart';
import 'notification.dart';
import 'profile.dart';
import 'create_booking.dart';

class CustomerNavigationPage extends StatefulWidget {
  const CustomerNavigationPage({
    super.key,
    required this.customer,
  });

  final MobileUser customer;

  @override
  State<CustomerNavigationPage> createState() => _CustomerNavigationPageState();
}

class _CustomerNavigationPageState extends State<CustomerNavigationPage> {
  int currentIndex = 0;
  final List<int> _tabRefreshVersions = [0, 0, 0, 0];
  late MobileUser _customer;
  final _notificationService = CustomerNotificationService();
  final _inboxChanges = ValueNotifier<int>(0);

  @override
  void initState() {
    super.initState();
    _customer = widget.customer;
  }

  void _updateCustomer(MobileUser customer) {
    setState(() => _customer = customer);
  }

  @override
  void dispose() {
    _notificationService.dispose();
    _inboxChanges.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      CustomerDashboardPage(
        key: ValueKey('customer-dashboard-${_tabRefreshVersions[0]}'),
        customer: _customer,
        onCreateBooking: _openCreateBooking,
      ),
      MyBookingsPage(
        key: ValueKey('customer-bookings-${_tabRefreshVersions[1]}'),
        customer: _customer,
      ),
      NotificationPage(
        key: ValueKey('customer-notifications-${_tabRefreshVersions[2]}'),
        notificationService: _notificationService,
        onInboxChanged: () => _inboxChanges.value++,
      ),
      ProfilePage(
        key: ValueKey('customer-profile-${_tabRefreshVersions[3]}'),
        customer: _customer,
        onCustomerUpdated: _updateCustomer,
      ),
    ];

    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      body: IndexedStack(
        index: currentIndex,
        children: pages,
      ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: AppTheme.primaryNavy,
        foregroundColor: Colors.white,
        shape: const CircleBorder(),
        tooltip: '+ Create Booking',
        onPressed: _openCreateBooking,
        child: const Icon(Icons.add, size: 30),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerDocked,
      bottomNavigationBar: BottomAppBar(
        color: Colors.white,
        shape: const CircularNotchedRectangle(),
        notchMargin: 8,
        height: 72,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        child: DilaDrivePageLayout(
            child: Row(
          children: [
            _navItem(
              Icons.home_outlined,
              Icons.home,
              'Home',
              0,
            ),
            _navItem(
              Icons.inventory_2_outlined,
              Icons.inventory_2,
              'My Bookings',
              1,
            ),
            const SizedBox(width: 60),
            _navItem(
              Icons.notifications_outlined,
              Icons.notifications,
              'Notifications',
              2,
            ),
            _navItem(
              Icons.person_outline,
              Icons.person,
              'Profile',
              3,
            ),
          ],
        )),
      ),
    );
  }

  Future<void> _openCreateBooking() async {
    await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => CreateBookingPage(customer: _customer),
      ),
    );
    if (!mounted) return;
    setState(() {
      _tabRefreshVersions[0]++;
      _tabRefreshVersions[1]++;
    });
  }

  Widget _navItem(
    IconData icon,
    IconData activeIcon,
    String label,
    int index,
  ) {
    final selected = currentIndex == index;
    final navIcon = Icon(
      selected ? activeIcon : icon,
      color: selected ? AppTheme.secondarySkyBlue : AppTheme.secondaryText,
      size: 24,
    );

    return Expanded(
      child: InkWell(
        onTap: () {
          setState(() {
            currentIndex = index;
            _tabRefreshVersions[index]++;
          });
        },
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (index == 2)
              UnreadNotificationBadge(
                icon: navIcon,
                loadNotifications: _notificationService.getMyNotifications,
                inboxChanges: _inboxChanges,
                pushUpdates: PushNotificationService.updates,
              )
            else
              navIcon,
            const SizedBox(height: 4),
            FittedBox(
              fit: BoxFit.scaleDown,
              child: Text(
                label,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
                  color:
                      selected ? AppTheme.primaryNavy : AppTheme.secondaryText,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
