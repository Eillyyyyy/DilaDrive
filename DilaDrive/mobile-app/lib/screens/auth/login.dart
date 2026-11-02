import 'package:flutter/material.dart';

import '../customer/customer_navigation.dart';
import '../driver/driver_navigation.dart';
import 'register.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  bool isCustomer = true;
  bool obscurePassword = true;

  final TextEditingController emailController =
  TextEditingController();

  final TextEditingController passwordController =
  TextEditingController();

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF701A35);

    return Scaffold(
      backgroundColor: const Color(0xFFF8F8FA),

      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),

          child: Column(
            children: [
              const SizedBox(height: 50),

              Container(
                width: 90,
                height: 90,
                decoration: BoxDecoration(
                  color: primaryColor.withValues(alpha: 0.1),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.local_shipping,
                  color: primaryColor,
                  size: 45,
                ),
              ),

              const SizedBox(height: 20),

              const Text(
                "DilaDrive",
                style: TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  color: primaryColor,
                ),
              ),

              const SizedBox(height: 8),

              const Text(
                "Transport Management System",
                style: TextStyle(
                  color: Colors.grey,
                ),
              ),

              const SizedBox(height: 40),

              Container(
                height: 55,
                padding: const EdgeInsets.all(5),
                decoration: BoxDecoration(
                  color: Colors.grey.shade200,
                  borderRadius:
                  BorderRadius.circular(15),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: GestureDetector(
                        onTap: () {
                          setState(() {
                            isCustomer = true;
                          });
                        },
                        child: AnimatedContainer(
                          duration:
                          const Duration(
                            milliseconds: 200,
                          ),
                          decoration: BoxDecoration(
                            color: isCustomer
                                ? primaryColor
                                : Colors.transparent,
                            borderRadius:
                            BorderRadius.circular(
                              12,
                            ),
                          ),
                          alignment:
                          Alignment.center,
                          child: Text(
                            "Customer",
                            style: TextStyle(
                              color: isCustomer
                                  ? Colors.white
                                  : Colors.black87,
                              fontWeight:
                              FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                    ),

                    Expanded(
                      child: GestureDetector(
                        onTap: () {
                          setState(() {
                            isCustomer = false;
                          });
                        },
                        child: AnimatedContainer(
                          duration:
                          const Duration(
                            milliseconds: 200,
                          ),
                          decoration: BoxDecoration(
                            color: !isCustomer
                                ? primaryColor
                                : Colors.transparent,
                            borderRadius:
                            BorderRadius.circular(
                              12,
                            ),
                          ),
                          alignment:
                          Alignment.center,
                          child: Text(
                            "Driver",
                            style: TextStyle(
                              color: !isCustomer
                                  ? Colors.white
                                  : Colors.black87,
                              fontWeight:
                              FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 30),

              TextField(
                controller: emailController,
                decoration: InputDecoration(
                  hintText: "Email Address",
                  prefixIcon: const Icon(
                    Icons.email_outlined,
                  ),
                  filled: true,
                  fillColor: Colors.white,
                  border: OutlineInputBorder(
                    borderRadius:
                    BorderRadius.circular(15),
                    borderSide:
                    BorderSide.none,
                  ),
                ),
              ),

              const SizedBox(height: 20),

              TextField(
                controller:
                passwordController,
                obscureText:
                obscurePassword,
                decoration: InputDecoration(
                  hintText: "Password",
                  prefixIcon: const Icon(
                    Icons.lock_outline,
                  ),
                  suffixIcon: IconButton(
                    icon: Icon(
                      obscurePassword
                          ? Icons.visibility
                          : Icons.visibility_off,
                    ),
                    onPressed: () {
                      setState(() {
                        obscurePassword =
                        !obscurePassword;
                      });
                    },
                  ),
                  filled: true,
                  fillColor: Colors.white,
                  border: OutlineInputBorder(
                    borderRadius:
                    BorderRadius.circular(
                      15,
                    ),
                    borderSide:
                    BorderSide.none,
                  ),
                ),
              ),

              const SizedBox(height: 10),

              Align(
                alignment:
                Alignment.centerRight,
                child: TextButton(
                  onPressed: () {},
                  child: const Text(
                    "Forgot Password?",
                  ),
                ),
              ),

              const SizedBox(height: 25),

              SizedBox(
                width: double.infinity,
                height: 55,
                child: ElevatedButton(
                  style:
                  ElevatedButton.styleFrom(
                    backgroundColor:
                    primaryColor,
                    shape:
                    RoundedRectangleBorder(
                      borderRadius:
                      BorderRadius
                          .circular(15),
                    ),
                  ),
                  onPressed: () {
                    if (isCustomer) {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) =>
                          const CustomerNavigationPage(),
                        ),
                      );
                    } else {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) =>
                          const DriverNavigationPage(),
                        ),
                      );
                    }
                  },
                  child: const Text(
                    "Login",
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight:
                      FontWeight.bold,
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 20),

              if (isCustomer)
                TextButton(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) =>
                        const RegisterPage(),
                      ),
                    );
                  },
                  child: const Text(
                    "Create Customer Account",
                    style: TextStyle(
                      fontWeight:
                      FontWeight.w600,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}