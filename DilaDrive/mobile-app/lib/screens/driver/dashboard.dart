import 'package:flutter/material.dart';

class DriverDashboardPage extends StatelessWidget {
  const DriverDashboardPage({super.key});

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
          "Driver Dashboard",
          style: TextStyle(
            color: Colors.black87,
            fontWeight: FontWeight.bold,
          ),
        ),
      ),

      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [

            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: primaryColor,
                borderRadius: BorderRadius.circular(25),
              ),
              child: const Column(
                crossAxisAlignment:
                CrossAxisAlignment.start,
                children: [
                  Text(
                    "TODAY'S ASSIGNMENTS",
                    style: TextStyle(
                      color: Colors.white70,
                    ),
                  ),
                  SizedBox(height: 10),
                  Text(
                    "5 Assigned Trips",
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            Row(
              children: [
                Expanded(
                  child: _statCard(
                    title: "Assigned",
                    value: "5",
                    color: Colors.orange,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _statCard(
                    title: "Active",
                    value: "2",
                    color: Colors.blue,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _statCard(
                    title: "Done",
                    value: "10",
                    color: Colors.green,
                  ),
                ),
              ],
            ),

            const SizedBox(height: 25),

            const Text(
              "Current Active Trip",
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),

            const SizedBox(height: 10),

            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(20),
              ),
              child: Column(
                children: [
                  Row(
                    children: [
                      Container(
                        padding:
                        const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: Colors.blue.withValues(
                            alpha: 0.1,
                          ),
                          borderRadius:
                          BorderRadius.circular(
                            12,
                          ),
                        ),
                        child: const Icon(
                          Icons.local_shipping,
                          color: Colors.blue,
                        ),
                      ),

                      const SizedBox(width: 12),

                      const Expanded(
                        child: Column(
                          crossAxisAlignment:
                          CrossAxisAlignment
                              .start,
                          children: [
                            Text(
                              "BK-2026-001",
                              style: TextStyle(
                                fontWeight:
                                FontWeight.bold,
                              ),
                            ),
                            Text(
                              "Nur Ayla Izaty",
                            ),
                          ],
                        ),
                      ),

                      Container(
                        padding:
                        const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.orange
                              .shade100,
                          borderRadius:
                          BorderRadius.circular(
                            20,
                          ),
                        ),
                        child: const Text(
                          "On The Way",
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 15),

                  const Row(
                    children: [
                      Icon(
                        Icons.location_on_outlined,
                        size: 18,
                      ),
                      SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          "Melaka → Kuala Lumpur",
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 15),

                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      style:
                      ElevatedButton.styleFrom(
                        backgroundColor:
                        primaryColor,
                      ),
                      onPressed: () {},
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
            ),

            const SizedBox(height: 25),

            const Text(
              "Today's Assigned Trips",
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),

            const SizedBox(height: 10),

            _tripCard(
              "BK-2026-002",
              "Melaka → Johor",
              "Assigned",
              Colors.orange,
            ),

            const SizedBox(height: 10),

            _tripCard(
              "BK-2026-003",
              "Melaka → Selangor",
              "Picked Up",
              Colors.blue,
            ),

            const SizedBox(height: 25),

            const Text(
              "Quick Actions",
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),

            const SizedBox(height: 10),

            Row(
              children: [
                Expanded(
                  child: _actionCard(
                    Icons.route,
                    "Trips",
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _actionCard(
                    Icons.camera_alt_outlined,
                    "Upload Proof",
                  ),
                ),
              ],
            ),

            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  static Widget _statCard({
    required String title,
    required String value,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        children: [
          Text(
            value,
            style: TextStyle(
              fontSize: 22,
              color: color,
              fontWeight: FontWeight.bold,
            ),
          ),
          Text(title),
        ],
      ),
    );
  }

  static Widget _tripCard(
      String bookingId,
      String route,
      String status,
      Color color,
      ) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.inventory_2_outlined,
          ),

          const SizedBox(width: 10),

          Expanded(
            child: Column(
              crossAxisAlignment:
              CrossAxisAlignment.start,
              children: [
                Text(
                  bookingId,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(route),
              ],
            ),
          ),

          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: 10,
              vertical: 6,
            ),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius:
              BorderRadius.circular(20),
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
    );
  }

  static Widget _actionCard(
      IconData icon,
      String title,
      ) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        children: [
          Icon(
            icon,
            size: 30,
            color: const Color(0xFF701A35),
          ),
          const SizedBox(height: 10),
          Text(title),
        ],
      ),
    );
  }
}