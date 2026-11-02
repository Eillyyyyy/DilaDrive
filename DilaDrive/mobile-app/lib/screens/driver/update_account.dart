import 'package:flutter/material.dart';

class DriverUpdateAccountPage extends StatefulWidget {
  const DriverUpdateAccountPage({super.key});

  @override
  State<DriverUpdateAccountPage> createState() =>
      _DriverUpdateAccountPageState();
}

class _DriverUpdateAccountPageState
    extends State<DriverUpdateAccountPage> {

  final nameController =
      TextEditingController(text: "Ahmad Faiz");

  final emailController =
      TextEditingController(text: "driver@diladrive.com");

  final phoneController =
      TextEditingController(text: "0123456789");

  final licenseController =
      TextEditingController(text: "D123456789");

  final vehicleTypeController =
      TextEditingController(text: "Van");

  final plateNumberController =
      TextEditingController(text: "WXY1234");

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF701A35);

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),

      appBar: AppBar(
        title: const Text("Update Account"),
        backgroundColor: Colors.white,
        elevation: 0,
      ),

      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [

            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: primaryColor,
                borderRadius: BorderRadius.circular(20),
              ),
              child: const Column(
                children: [
                  CircleAvatar(
                    radius: 40,
                    backgroundColor: Colors.white,
                    child: Icon(
                      Icons.person,
                      size: 45,
                      color: primaryColor,
                    ),
                  ),

                  SizedBox(height: 15),

                  Text(
                    "Update Driver Information",
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

            TextField(
              controller: nameController,
              decoration: inputDecoration(
                "Full Name",
                Icons.person_outline,
              ),
            ),

            const SizedBox(height: 15),

            TextField(
              controller: emailController,
              decoration: inputDecoration(
                "Email Address",
                Icons.email_outlined,
              ),
            ),

            const SizedBox(height: 15),

            TextField(
              controller: phoneController,
              decoration: inputDecoration(
                "Phone Number",
                Icons.phone_outlined,
              ),
            ),

            const SizedBox(height: 15),

            TextField(
              controller: licenseController,
              decoration: inputDecoration(
                "Driving License Number",
                Icons.badge_outlined,
              ),
            ),

            const SizedBox(height: 15),

            TextField(
              controller: vehicleTypeController,
              decoration: inputDecoration(
                "Vehicle Type",
                Icons.local_shipping_outlined,
              ),
            ),

            const SizedBox(height: 15),

            TextField(
              controller: plateNumberController,
              decoration: inputDecoration(
                "Vehicle Plate Number",
                Icons.directions_car_outlined,
              ),
            ),

            const SizedBox(height: 30),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: ElevatedButton.icon(
                icon: const Icon(
                  Icons.save,
                  color: Colors.white,
                ),
                label: const Text(
                  "Update Account",
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                  ),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: primaryColor,
                  shape: RoundedRectangleBorder(
                    borderRadius:
                        BorderRadius.circular(15),
                  ),
                ),
                onPressed: () {

                  ScaffoldMessenger.of(context)
                      .showSnackBar(
                    const SnackBar(
                      content: Text(
                        "Driver account updated successfully.",
                      ),
                    ),
                  );
                },
              ),
            ),

            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  InputDecoration inputDecoration(
    String label,
    IconData icon,
  ) {
    return InputDecoration(
      labelText: label,
      prefixIcon: Icon(icon),
      filled: true,
      fillColor: Colors.white,
      border: OutlineInputBorder(
        borderRadius:
            BorderRadius.circular(15),
        borderSide: BorderSide.none,
      ),
    );
  }
}