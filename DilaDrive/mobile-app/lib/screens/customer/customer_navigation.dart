import 'package:flutter/material.dart';

import 'dashboard.dart';
import 'bookings.dart';
import 'notification.dart';
import 'profile.dart';
import 'create_booking.dart';

class CustomerNavigationPage extends StatefulWidget {
  const CustomerNavigationPage({
    super.key,
    required this.customerId,
  });

  final String customerId;

  @override
  State<CustomerNavigationPage> createState() => _CustomerNavigationPageState();
}

class _CustomerNavigationPageState extends State<CustomerNavigationPage> {
  int currentIndex = 0;

  final pages = [
    const CustomerDashboardPage(),
    const MyBookingsPage(),
    const NotificationPage(),
    const ProfilePage(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: pages[currentIndex],
      floatingActionButton: FloatingActionButton(
        backgroundColor: const Color(0xFF701A35),
        child: const Icon(
          Icons.add,
          color: Colors.white,
        ),
        onPressed: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => CreateBookingPage(customerId: widget.customerId),
            ),
          );
        },
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerDocked,
      bottomNavigationBar: BottomAppBar(
        shape: const CircularNotchedRectangle(),
        notchMargin: 8,
        child: SizedBox(
          height: 65,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              navItem(
                icon: Icons.home_outlined,
                label: "Home",
                index: 0,
              ),
              navItem(
                icon: Icons.inventory_2_outlined,
                label: "Bookings",
                index: 1,
              ),
              const SizedBox(width: 30),
              navItem(
                icon: Icons.notifications_outlined,
                label: "Alerts",
                index: 2,
              ),
              navItem(
                icon: Icons.person_outline,
                label: "Profile",
                index: 3,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget navItem({
    required IconData icon,
    required String label,
    required int index,
  }) {
    final selected = currentIndex == index;

    return GestureDetector(
      onTap: () {
        setState(() {
          currentIndex = index;
        });
      },
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            icon,
            color: selected ? const Color(0xFF701A35) : Colors.grey,
          ),
          Text(
            label,
            style: TextStyle(
              color: selected ? const Color(0xFF701A35) : Colors.grey,
              fontSize: 12,
            ),
          )
        ],
      ),
    );
  }
}
