import 'package:flutter/material.dart';

class RegisterPage extends StatefulWidget {
  const RegisterPage({super.key});

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  bool isCorporate = false;

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF701A35);

    return Scaffold(
      backgroundColor: const Color(0xFFF8F8FA),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Text(
          "Create Account",
          style: TextStyle(
            color: Colors.black,
          ),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [

            const SizedBox(height: 15),

            const Text(
              "Choose Account Type",
              style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.bold,
              ),
            ),

            const SizedBox(height: 20),

            GestureDetector(
              onTap: () {
                setState(() {
                  isCorporate = false;
                });
              },
              child: cardType(
                selected: !isCorporate,
                icon: Icons.person,
                title: "Individual Customer",
              ),
            ),

            const SizedBox(height: 15),

            GestureDetector(
              onTap: () {
                setState(() {
                  isCorporate = true;
                });
              },
              child: cardType(
                selected: isCorporate,
                icon: Icons.business,
                title: "Corporate Customer",
              ),
            ),

            const SizedBox(height: 25),

            buildField("Full Name"),
            buildField("Email Address"),
            buildField("Phone Number"),

            if (isCorporate) ...[
              buildField("Company Name"),
              buildField("Company Registration Number"),
            ],

            buildField("Password", obscure: true),
            buildField("Confirm Password", obscure: true),

            const SizedBox(height: 25),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: primaryColor,
                ),
                onPressed: () {},
                child: const Text(
                  "Register",
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 17,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget buildField(
      String label, {
        bool obscure = false,
      }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 15),
      child: TextField(
        obscureText: obscure,
        decoration: InputDecoration(
          labelText: label,
          filled: true,
          fillColor: Colors.white,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(15),
            borderSide: BorderSide.none,
          ),
        ),
      ),
    );
  }

  Widget cardType({
    required bool selected,
    required IconData icon,
    required String title,
  }) {
    const primaryColor = Color(0xFF701A35);

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: selected
            ? primaryColor.withValues(alpha: .08)
            : Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: selected
              ? primaryColor
              : Colors.grey.shade300,
        ),
      ),
      child: Row(
        children: [
          Icon(
            icon,
            color: primaryColor,
          ),
          const SizedBox(width: 15),
          Text(
            title,
            style: const TextStyle(
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}