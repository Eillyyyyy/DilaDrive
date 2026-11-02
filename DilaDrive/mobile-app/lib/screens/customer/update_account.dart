import 'package:flutter/material.dart';

class UpdateAccountPage extends StatefulWidget {
  const UpdateAccountPage({super.key});

  @override
  State<UpdateAccountPage> createState() =>
      _UpdateAccountPageState();
}

class _UpdateAccountPageState
    extends State<UpdateAccountPage> {

  final nameController =
      TextEditingController(text: "Nur Ayla Izaty");

  final emailController =
      TextEditingController(text: "customer@email.com");

  final phoneController =
      TextEditingController(text: "0123456789");

  final companyController =
      TextEditingController(text: "ABC Logistics Sdn Bhd");

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

            TextField(
              controller: nameController,
              decoration: inputDecoration(
                "Full Name",
                Icons.person,
              ),
            ),

            const SizedBox(height: 15),

            TextField(
              controller: emailController,
              decoration: inputDecoration(
                "Email Address",
                Icons.email,
              ),
            ),

            const SizedBox(height: 15),

            TextField(
              controller: phoneController,
              decoration: inputDecoration(
                "Phone Number",
                Icons.phone,
              ),
            ),

            const SizedBox(height: 15),

            TextField(
              controller: companyController,
              decoration: inputDecoration(
                "Company Name",
                Icons.business,
              ),
            ),

            const SizedBox(height: 30),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: primaryColor,
                ),
                onPressed: () {

                  ScaffoldMessenger.of(context)
                      .showSnackBar(
                    const SnackBar(
                      content: Text(
                        "Profile updated successfully",
                      ),
                    ),
                  );
                },
                child: const Text(
                  "Update Profile",
                  style: TextStyle(
                    color: Colors.white,
                  ),
                ),
              ),
            )
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
        borderRadius: BorderRadius.circular(16),
        borderSide: BorderSide.none,
      ),
    );
  }
}