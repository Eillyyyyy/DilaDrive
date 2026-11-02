import 'package:flutter/material.dart';

class UpdateBookingPage extends StatefulWidget {
  const UpdateBookingPage({super.key});

  @override
  State<UpdateBookingPage> createState() =>
      _UpdateBookingPageState();
}

class _UpdateBookingPageState
    extends State<UpdateBookingPage> {

  final pickupController =
      TextEditingController(text: "Melaka");

  final dropoffController =
      TextEditingController(text: "Kuala Lumpur");

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),

      appBar: AppBar(
        title: const Text("Update Booking"),
        backgroundColor: Colors.white,
      ),

      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [

            TextField(
              controller: pickupController,
              decoration: const InputDecoration(
                labelText: "Pickup Location",
              ),
            ),

            const SizedBox(height: 15),

            TextField(
              controller: dropoffController,
              decoration: const InputDecoration(
                labelText: "Drop-off Location",
              ),
            ),

            const SizedBox(height: 25),

            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () {

                  ScaffoldMessenger.of(context)
                      .showSnackBar(
                    const SnackBar(
                      content: Text(
                        "Booking updated successfully",
                      ),
                    ),
                  );
                },
                child: const Text(
                  "Update Booking",
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}