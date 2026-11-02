import 'package:flutter/material.dart';

class NotificationPage extends StatelessWidget {
  const NotificationPage({super.key});

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF701A35);

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),
      appBar: AppBar(
        automaticallyImplyLeading: false,
        backgroundColor: Colors.white,
        elevation: 0,
        title: const Text(
          "Notifications",
          style: TextStyle(
            color: Colors.black87,
            fontWeight: FontWeight.bold,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () {},
            child: const Text(
              "Mark All Read",
              style: TextStyle(
                color: primaryColor,
              ),
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: const [
          NotificationCard(
            title: "Booking Approved",
            message:
                "Your booking BK-2026-001 has been approved by admin.",
            time: "10 mins ago",
            icon: Icons.check_circle,
            color: Colors.green,
            unread: true,
          ),

          SizedBox(height: 15),

          NotificationCard(
            title: "Driver Assigned",
            message:
                "Driver Ahmad Faiz has been assigned to your booking.",
            time: "35 mins ago",
            icon: Icons.local_shipping,
            color: Colors.blue,
            unread: true,
          ),

          SizedBox(height: 15),

          NotificationCard(
            title: "On The Way",
            message:
                "Your driver is currently on the way to pickup location.",
            time: "1 hour ago",
            icon: Icons.route,
            color: Colors.orange,
            unread: false,
          ),

          SizedBox(height: 15),

          NotificationCard(
            title: "Payment Verified",
            message:
                "Your payment receipt has been successfully verified.",
            time: "Yesterday",
            icon: Icons.payment,
            color: Colors.purple,
            unread: false,
          ),

          SizedBox(height: 15),

          NotificationCard(
            title: "Booking Completed",
            message:
                "Your transport service has been completed successfully.",
            time: "2 days ago",
            icon: Icons.task_alt,
            color: Colors.green,
            unread: false,
          ),
        ],
      ),
    );
  }
}

class NotificationCard extends StatelessWidget {
  final String title;
  final String message;
  final String time;
  final IconData icon;
  final Color color;
  final bool unread;

  const NotificationCard({
    super.key,
    required this.title,
    required this.message,
    required this.time,
    required this.icon,
    required this.color,
    required this.unread,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(22),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 25,
            backgroundColor: color.withValues(alpha: 0.15),
            child: Icon(
              icon,
              color: color,
            ),
          ),

          const SizedBox(width: 15),

          Expanded(
            child: Column(
              crossAxisAlignment:
                  CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        title,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),

                    if (unread)
                      Container(
                        width: 10,
                        height: 10,
                        decoration: const BoxDecoration(
                          color: Colors.red,
                          shape: BoxShape.circle,
                        ),
                      ),
                  ],
                ),

                const SizedBox(height: 8),

                Text(
                  message,
                  style: const TextStyle(
                    color: Colors.black54,
                    height: 1.4,
                  ),
                ),

                const SizedBox(height: 10),

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