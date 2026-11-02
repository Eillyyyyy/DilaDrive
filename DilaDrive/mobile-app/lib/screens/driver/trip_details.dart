import 'package:flutter/material.dart';
import 'upload_verification_photo.dart';

class DriverTripDetailsPage extends StatefulWidget {
  const DriverTripDetailsPage({super.key});

  @override
  State<DriverTripDetailsPage> createState() =>
      _DriverTripDetailsPageState();
}

class _DriverTripDetailsPageState
    extends State<DriverTripDetailsPage> {
  String selectedStatus = "Assigned";

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),

      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text("Trip Details"),
        backgroundColor: Colors.white,
        elevation: 0,
      ),

      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            info("Booking ID", "BK-2026-001"),
            info("Customer", "Nur Ayla Izaty"),
            info("Pickup Location", "Melaka"),
            info("Drop-off Location", "Kuala Lumpur"),
            info("Vehicle", "Van"),
            info("Capacity", "Medium"),
            info("Date", "21 Oct 2026"),
            info("Time", "10:00 AM"),

            const SizedBox(height: 20),

            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
              ),
              child: DropdownButtonFormField<String>(
                initialValue: selectedStatus,
                decoration: const InputDecoration(
                  labelText: "Trip Status",
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(
                    value: "Assigned",
                    child: Text("Assigned"),
                  ),
                  DropdownMenuItem(
                    value: "On The Way",
                    child: Text("On The Way"),
                  ),
                  DropdownMenuItem(
                    value: "Picked Up",
                    child: Text("Picked Up"),
                  ),
                  DropdownMenuItem(
                    value: "In Transit",
                    child: Text("In Transit"),
                  ),
                  DropdownMenuItem(
                    value: "Delivered",
                    child: Text("Delivered"),
                  ),
                  DropdownMenuItem(
                    value: "Completed",
                    child: Text("Completed"),
                  ),
                ],
                onChanged: (value) {
                  setState(() {
                    selectedStatus = value!;
                  });
                },
              ),
            ),

            const SizedBox(height: 20),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: ElevatedButton.icon(
                icon: const Icon(
                  Icons.update,
                  color: Colors.white,
                ),
                label: const Text(
                  "Update Status",
                  style: TextStyle(
                    color: Colors.white,
                  ),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF701A35),
                ),
                onPressed: () {
                  ScaffoldMessenger.of(context)
                      .showSnackBar(
                    SnackBar(
                      content: Text(
                        "Status updated to $selectedStatus. Notification sent to customer.",
                      ),
                    ),
                  );
                },
              ),
            ),

            const SizedBox(height: 15),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: OutlinedButton.icon(
                icon: const Icon(Icons.camera_alt_outlined),
                label: const Text(
                  "Upload Verification Photo",
                ),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) =>
                          const UploadVerificationPhotoPage(),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget info(String title, String value) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
      ),
      child: ListTile(
        title: Text(title),
        subtitle: Text(value),
      ),
    );
  }
}