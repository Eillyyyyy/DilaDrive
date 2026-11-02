import 'package:flutter/material.dart';
import 'booking_details.dart';

class MyBookingsPage extends StatelessWidget {
  const MyBookingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text("My Bookings"),
        backgroundColor: Colors.white,
        elevation: 0,
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: const [
          BookingCard(
            bookingId: "BK-2026-001",
            route: "Melaka → Kuala Lumpur",
            date: "21 Oct 2026",
            status: "On The Way",
            color: Colors.orange,
          ),
          SizedBox(height: 15),
          BookingCard(
            bookingId: "BK-2026-002",
            route: "Melaka → Johor",
            date: "20 Oct 2026",
            status: "Approved",
            color: Colors.blue,
          ),
          SizedBox(height: 15),
          BookingCard(
            bookingId: "BK-2026-003",
            route: "Melaka → Penang",
            date: "18 Oct 2026",
            status: "Completed",
            color: Colors.green,
          ),
        ],
      ),
    );
  }
}

class BookingCard extends StatelessWidget {
  final String bookingId;
  final String route;
  final String date;
  final String status;
  final Color color;

  const BookingCard({
    super.key,
    required this.bookingId,
    required this.route,
    required this.date,
    required this.status,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(22),
      ),
      child: Column(
        children: [
          Row(
            children: [
              const Icon(Icons.local_shipping),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  bookingId,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 16,
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: color.withValues(alpha: .15),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  status,
                  style: TextStyle(
                    color: color,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 15),
          Row(
            children: [
              const Icon(Icons.location_on_outlined),
              const SizedBox(width: 8),
              Expanded(child: Text(route)),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              const Icon(Icons.calendar_today_outlined),
              const SizedBox(width: 8),
              Text(date),
            ],
          ),
          const SizedBox(height: 15),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton(
              onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) =>
                      const BookingDetailsPage(),
                ),
              );
              },
              child: const Text("View Details"),
            ),
          ),
        ],
      ),
    );
  }
}