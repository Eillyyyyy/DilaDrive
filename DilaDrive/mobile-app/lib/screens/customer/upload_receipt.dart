import 'package:flutter/material.dart';

class UploadReceiptPage extends StatelessWidget {
  const UploadReceiptPage({super.key});

  @override
  Widget build(BuildContext context) {

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),

      appBar: AppBar(
        title: const Text(
          "Upload Receipt",
        ),
        backgroundColor: Colors.white,
      ),

      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [

            const Card(
              child: ListTile(
                title: Text("Booking ID"),
                subtitle: Text("BK-2026-001"),
              ),
            ),

            const Card(
              child: ListTile(
                title: Text("Amount"),
                subtitle: Text("RM 105.00"),
              ),
            ),

            const SizedBox(height: 30),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: OutlinedButton.icon(
                icon: const Icon(Icons.upload_file),
                label: const Text(
                  "Choose Receipt",
                ),
                onPressed: () {},
              ),
            ),

            const SizedBox(height: 20),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: ElevatedButton(
                onPressed: () {

                  ScaffoldMessenger.of(context)
                      .showSnackBar(
                    const SnackBar(
                      content: Text(
                        "Receipt uploaded successfully",
                      ),
                    ),
                  );
                },
                child: const Text(
                  "Upload Receipt",
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}