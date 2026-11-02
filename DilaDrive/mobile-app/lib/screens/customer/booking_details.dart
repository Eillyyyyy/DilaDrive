import 'package:flutter/material.dart';

import 'update_booking.dart';
import 'upload_receipt.dart';

class BookingDetailsPage extends StatelessWidget {
  const BookingDetailsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text("Booking Details"),
        backgroundColor: Colors.white,
        elevation: 0,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [

            buildInfo("Booking ID", "BK-2026-001"),
            buildInfo("Pickup", "Melaka"),
            buildInfo("Drop-off", "Kuala Lumpur"),
            buildInfo("Vehicle", "Van"),
            buildInfo("Capacity", "Medium"),
            buildInfo("Fare", "RM 105.00"),
            buildInfo("Status", "Pending"),

            const SizedBox(height: 30),

            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) =>
                          const UpdateBookingPage(),
                    ),
                  );
                },
                child: const Text("Update Booking"),
              ),
            ),

            const SizedBox(height: 10),

            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.red,
                ),
                onPressed: () {
                  showDialog(
                    context: context,
                    builder: (_) => AlertDialog(
                      title: const Text("Cancel Booking"),
                      content: const Text(
                        "Are you sure you want to cancel this booking?",
                      ),
                      actions: [
                        TextButton(
                          onPressed: () {
                            Navigator.pop(context);
                          },
                          child: const Text("No"),
                        ),
                        ElevatedButton(
                          onPressed: () {
                            Navigator.pop(context);

                            ScaffoldMessenger.of(context)
                                .showSnackBar(
                              const SnackBar(
                                content: Text(
                                  "Booking cancelled successfully",
                                ),
                              ),
                            );
                          },
                          child: const Text("Yes"),
                        ),
                      ],
                    ),
                  );
                },
                child: const Text(
                  "Cancel Booking",
                  style: TextStyle(
                    color: Colors.white,
                  ),
                ),
              ),
            ),

            const SizedBox(height: 10),

            SizedBox(
              width: double.infinity,
              child: OutlinedButton(
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) =>
                          const UploadReceiptPage(),
                    ),
                  );
                },
                child: const Text(
                  "Upload Payment Receipt",
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget buildInfo(String title, String value) {
    return Card(
      child: ListTile(
        title: Text(title),
        subtitle: Text(value),
      ),
    );
  }
}