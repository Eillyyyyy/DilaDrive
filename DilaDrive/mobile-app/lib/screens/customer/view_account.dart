import 'package:flutter/material.dart';

class ViewAccountPage extends StatelessWidget {
  const ViewAccountPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),
      appBar: AppBar(
        title: const Text("Account Information"),
        backgroundColor: Colors.white,
        elevation: 0,
      ),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [

            const CircleAvatar(
              radius: 45,
              child: Icon(
                Icons.person,
                size: 50,
              ),
            ),

            const SizedBox(height: 25),

            infoCard(
              "Full Name",
              "Nur Ayla Izaty",
            ),

            infoCard(
              "Email",
              "customer@email.com",
            ),

            infoCard(
              "Phone Number",
              "0123456789",
            ),

            infoCard(
              "Customer Type",
              "Corporate",
            ),

            infoCard(
              "Company Name",
              "ABC Logistics Sdn Bhd",
            ),
          ],
        ),
      ),
    );
  }

  Widget infoCard(String title, String value) {
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
            style: const TextStyle(
              fontWeight: FontWeight.bold,
            ),
          )
        ],
      ),
    );
  }
}