import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

import '../../services/auth_service.dart';
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
  bool isLoading = false;

  static const Color primaryColor = AppTheme.primaryNavy;
  static const Color backgroundColor = AppTheme.lightBlueBackground;

  final AuthService _authService = AuthService();

  final TextEditingController emailController = TextEditingController();

  final TextEditingController passwordController = TextEditingController();

  @override
  void dispose() {
    _authService.dispose();
    emailController.dispose();
    passwordController.dispose();
    super.dispose();
  }

  void _switchRole(bool customer) {
    if (isLoading || isCustomer == customer) return;

    setState(() {
      isCustomer = customer;
      passwordController.clear();
      obscurePassword = true;
    });
  }

  Future<void> _login() async {
    if (isLoading) return;

    final String email = emailController.text.trim();
    final String password = passwordController.text;

    if (email.isEmpty || password.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Please enter your email and password.',
          ),
        ),
      );
      return;
    }

    setState(() {
      isLoading = true;
    });

    try {
      // Login menggunakan Spring Boot backend.
      // Simpan MobileUser yang dipulangkan oleh API.
      final user = await _authService.login(
        email: email,
        password: password,
        role: isCustomer ? 'CUSTOMER' : 'DRIVER',
      );

      if (!mounted) return;

      // Customer masuk ke navigation utama,
      // bukan terus ke CustomerDashboardPage.
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => isCustomer
              ? CustomerNavigationPage(
                  customer: user,
                )
              : DriverNavigationPage(driver: user),
        ),
      );
    } on AuthException catch (exception) {
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(exception.message),
          backgroundColor: primaryColor,
        ),
      );
    } catch (exception) {
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Something went wrong. Please try again.',
          ),
          backgroundColor: primaryColor,
        ),
      );
    } finally {
      if (mounted) {
        setState(() {
          isLoading = false;
        });
      }
    }
  }

  InputDecoration _inputDecoration({
    required String hint,
    required IconData icon,
    Widget? suffixIcon,
  }) {
    return InputDecoration(
      hintText: hint,
      prefixIcon: Icon(
        icon,
        color: primaryColor,
      ),
      suffixIcon: suffixIcon,
      filled: true,
      fillColor: Colors.white,
      contentPadding: const EdgeInsets.symmetric(
        horizontal: 16,
        vertical: 18,
      ),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(15),
        borderSide: BorderSide.none,
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(15),
        borderSide: BorderSide.none,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(15),
        borderSide: const BorderSide(
          color: primaryColor,
          width: 1.5,
        ),
      ),
    );
  }

  Widget _roleButton({
    required String title,
    required bool customer,
  }) {
    final bool selected = isCustomer == customer;

    return Expanded(
      child: GestureDetector(
        onTap: () => _switchRole(customer),
        child: AnimatedContainer(
          duration: const Duration(
            milliseconds: 200,
          ),
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: selected ? primaryColor : Colors.transparent,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(
            title,
            style: TextStyle(
              color: selected ? Colors.white : AppTheme.mainText,
              fontWeight: FontWeight.bold,
              fontSize: 15,
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: backgroundColor,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const SizedBox(height: 50),

              // DilaDrive Logo
              Container(
                width: 90,
                height: 90,
                decoration: BoxDecoration(
                  color: primaryColor.withValues(
                    alpha: 0.1,
                  ),
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
                'DilaDrive',
                style: TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  color: primaryColor,
                ),
              ),

              const SizedBox(height: 8),

              const Text(
                'Transport Management System',
                style: TextStyle(
                  color: AppTheme.secondaryText,
                  fontSize: 14,
                ),
              ),

              const SizedBox(height: 40),

              // Customer / Driver Role Selection
              Container(
                height: 55,
                padding: const EdgeInsets.all(5),
                decoration: BoxDecoration(
                  color: AppTheme.lightBlueBackground,
                  borderRadius: BorderRadius.circular(15),
                ),
                child: Row(
                  children: [
                    _roleButton(
                      title: 'Customer',
                      customer: true,
                    ),
                    _roleButton(
                      title: 'Driver',
                      customer: false,
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 30),

              // Email
              TextField(
                controller: emailController,
                enabled: !isLoading,
                keyboardType: TextInputType.emailAddress,
                textInputAction: TextInputAction.next,
                autocorrect: false,
                decoration: _inputDecoration(
                  hint: 'Email Address',
                  icon: Icons.email_outlined,
                ),
              ),

              const SizedBox(height: 20),

              // Password
              TextField(
                controller: passwordController,
                enabled: !isLoading,
                obscureText: obscurePassword,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) {
                  if (!isLoading) {
                    _login();
                  }
                },
                decoration: _inputDecoration(
                  hint: 'Password',
                  icon: Icons.lock_outline,
                  suffixIcon: IconButton(
                    icon: Icon(
                      obscurePassword
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined,
                      color: AppTheme.secondaryText,
                    ),
                    onPressed: isLoading
                        ? null
                        : () {
                            setState(() {
                              obscurePassword = !obscurePassword;
                            });
                          },
                  ),
                ),
              ),

              const SizedBox(height: 10),

              // Forgot Password
              const Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: null,
                  child: Text(
                    'Forgot Password?',
                    style: TextStyle(
                      color: AppTheme.secondaryText,
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 25),

              // Login Button
              SizedBox(
                width: double.infinity,
                height: 55,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: primaryColor,
                    disabledBackgroundColor: primaryColor.withValues(
                      alpha: 0.6,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(15),
                    ),
                  ),
                  onPressed: isLoading ? null : _login,
                  child: isLoading
                      ? const SizedBox(
                          height: 22,
                          width: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text(
                          'Login',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                ),
              ),

              const SizedBox(height: 20),

              // Customer Registration Only
              if (isCustomer)
                TextButton(
                  onPressed: isLoading
                      ? null
                      : () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => const RegisterPage(),
                            ),
                          );
                        },
                  child: const Text(
                    'Create Customer Account',
                    style: TextStyle(
                      color: primaryColor,
                      fontWeight: FontWeight.w600,
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
