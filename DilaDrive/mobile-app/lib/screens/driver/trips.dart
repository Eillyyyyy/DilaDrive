import 'package:flutter/material.dart';
import 'trip_details.dart';

class DriverTripsPage extends StatelessWidget {
  const DriverTripsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),

      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const SizedBox(height: 20),

          const Text(
            "Assigned Trips",
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.bold,
            ),
          ),

          const SizedBox(height: 20),

          _tripCard(
            context,
            "BK-2026-001",
            "Nur Ayla Izaty",
            "Melaka → Kuala Lumpur",
            "Assigned",
            Colors.orange,
          ),

          const SizedBox(height: 15),

          _tripCard(
            context,
            "BK-2026-002",
            "Nur Ayla Izaty",
            "Melaka → Johor",
            "On The Way",
            Colors.blue,
          ),

          const SizedBox(height: 15),

          _tripCard(
            context,
            "BK-2026-003",
            "Nur Ayla Izaty",
            "Melaka → Selangor",
            "Completed",
            Colors.green,
          ),
        ],
      ),
    );
  }

  Widget _tripCard(
      BuildContext context,
      String bookingId,
      String customer,
      String route,
      String status,
      Color statusColor,
      ) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(
                Icons.local_shipping,
                color: Color(0xFF701A35),
              ),

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
                  color: statusColor.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  status,
                  style: TextStyle(
                    color: statusColor,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),

          const SizedBox(height: 10),

          Text(
            "Customer: $customer",
            style: const TextStyle(
              color: Colors.black87,
            ),
          ),

          const SizedBox(height: 5),

          Text(
            route,
            style: const TextStyle(
              color: Colors.grey,
            ),
          ),

          const SizedBox(height: 15),

          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF701A35),
              ),
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) =>
                    const DriverTripDetailsPage(),
                  ),
                );
              },
              child: const Text(
                "View Details",
                style: TextStyle(
                  color: Colors.white,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}