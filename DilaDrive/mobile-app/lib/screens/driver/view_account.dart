import 'package:flutter/material.dart';

class DriverViewAccountPage extends StatelessWidget {
  const DriverViewAccountPage({super.key});

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF701A35);

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),

      appBar: AppBar(
        title: const Text("Driver Account"),
        backgroundColor: Colors.white,
        elevation: 0,
      ),

      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(25),
              decoration: BoxDecoration(
                color: primaryColor,
                borderRadius: BorderRadius.circular(20),
              ),
              child: const Column(
                children: [
                  CircleAvatar(
                    radius: 45,
                    backgroundColor: Colors.white,
                    child: Icon(
                      Icons.person,
                      size: 50,
                      color: primaryColor,
                    ),
                  ),

                  SizedBox(height: 15),

                  Text(
                    "Ahmad Faiz",
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 22,
                      fontWeight: FontWeight.bold,
                    ),
                  ),

                  SizedBox(height: 5),

                  Text(
                    "Driver",
                    style: TextStyle(
                      color: Colors.white70,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            buildInfoCard(
              "Driver ID",
              "DRV-001",
            ),

            buildInfoCard(
              "Full Name",
              "Ahmad Faiz",
            ),

            buildInfoCard(
              "Email Address",
              "driver@diladrive.com",
            ),

            buildInfoCard(
              "Phone Number",
              "0123456789",
            ),

            buildInfoCard(
              "Driving License No",
              "D123456789",
            ),

            buildInfoCard(
              "Vehicle Type",
              "Van",
            ),

            buildInfoCard(
              "Vehicle Plate Number",
              "WXY1234",
            ),

            buildInfoCard(
              "Account Status",
              "Active",
              valueColor: Colors.green,
            ),

            buildInfoCard(
              "Registration Date",
              "01/01/2026",
            ),
          ],
        ),
      ),
    );
  }

  Widget buildInfoCard(
    String title,
    String value, {
    Color valueColor = Colors.black87,
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