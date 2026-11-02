import 'package:flutter/material.dart';
import 'upload_receipt.dart';

class PaymentPage extends StatelessWidget {
  const PaymentPage({super.key});

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF701A35);

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),

      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text("Payment"),
        backgroundColor: Colors.white,
        elevation: 0,
      ),

      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: primaryColor,
                borderRadius: BorderRadius.circular(20),
              ),
              child: const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    "PAYMENT INFORMATION",
                    style: TextStyle(
                      color: Colors.white70,
                    ),
                  ),
                  SizedBox(height: 10),
                  Text(
                    "Complete your payment and upload the payment receipt.",
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            _infoCard(
              title: "Booking ID",
              value: "BK-2026-001",
            ),

            _infoCard(
              title: "Customer",
              value: "Nur Ayla Izaty",
            ),

            _infoCard(
              title: "Payment Status",
              value: "Pending Payment",
              valueColor: Colors.orange,
            ),

            _infoCard(
              title: "Amount",
              value: "RM 105.00",
              valueColor: Colors.green,
            ),

            const SizedBox(height: 20),

            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(18),
              ),
              child: const Column(
                crossAxisAlignment:
                    CrossAxisAlignment.start,
                children: [
                  Text(
                    "Bank Transfer Details",
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                    ),
                  ),

                  SizedBox(height: 15),

                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          "Bank Name",
                          style: TextStyle(
                            color: Colors.grey,
                          ),
                        ),
                      ),
                      Text("Maybank"),
                    ],
                  ),

                  Divider(),

                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          "Account Name",
                          style: TextStyle(
                            color: Colors.grey,
                          ),
                        ),
                      ),
                      Text("DilaDrive Sdn Bhd"),
                    ],
                  ),

                  Divider(),

                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          "Account Number",
                          style: TextStyle(
                            color: Colors.grey,
                          ),
                        ),
                      ),
                      Text("1234567890"),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 25),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: ElevatedButton.icon(
                icon: const Icon(
                  Icons.upload_file,
                  color: Colors.white,
                ),
                label: const Text(
                  "Upload Receipt",
                  style: TextStyle(
                    color: Colors.white,
                  ),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: primaryColor,
                ),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) =>
                          const UploadReceiptPage(),
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

  static Widget _infoCard({
    required String title,
    required String value,
    Color valueColor = Colors.black,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: const TextStyle(
                color: Colors.grey,
              ),
            ),
          ),
          Text(
            value,
            style: TextStyle(
              fontWeight: FontWeight.bold,
              color: valueColor,
            ),
          ),
        ],
      ),
    );
  }
}