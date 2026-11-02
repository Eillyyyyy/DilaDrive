import 'package:flutter/material.dart';

class DriverNotificationPage extends StatelessWidget {
  const DriverNotificationPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),

      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const SizedBox(height: 20),

          const Text(
            "Notifications",
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.bold,
            ),
          ),

          const SizedBox(height: 20),

          _notificationCard(
            icon: Icons.local_shipping,
            iconColor: Colors.orange,
            title: "New Trip Assigned",
            message:
            "Booking BK-2026-001 has been assigned to you.",
            time: "5 minutes ago",
          ),

          const SizedBox(height: 12),

          _notificationCard(
            icon: Icons.update,
            iconColor: Colors.blue,
            title: "Trip Status Updated",
            message:
            "Booking BK-2026-002 status changed to On The Way.",
            time: "30 minutes ago",
          ),

          const SizedBox(height: 12),

          _notificationCard(
            icon: Icons.check_circle,
            iconColor: Colors.green,
            title: "Trip Completed",
            message:
            "Booking BK-2026-003 completed successfully.",
            time: "1 hour ago",
          ),
        ],
      ),
    );
  }

  Widget _notificationCard({
    required IconData icon,
    required Color iconColor,
    required String title,
    required String message,
    required String time,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            backgroundColor:
            iconColor.withValues(alpha: 0.15),
            child: Icon(
              icon,
              color: iconColor,
            ),
          ),

          const SizedBox(width: 12),

          Expanded(
            child: Column(
              crossAxisAlignment:
              CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                  ),
                ),

                const SizedBox(height: 4),

                Text(
                  message,
                  style: const TextStyle(
                    color: Colors.black87,
                  ),
                ),

                const SizedBox(height: 8),

                Text(
                  time,
                  style: const TextStyle(
                    color: Colors.grey,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}