import 'dart:convert';

import 'package:flutter/material.dart';

import '../../services/auth_service.dart';
import '../../theme/app_theme.dart';

class RegisterPage extends StatefulWidget {
  const RegisterPage({super.key, this.authService});

  final AuthService? authService;

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final _formKey = GlobalKey<FormState>();
  late final bool _ownsAuthService = widget.authService == null;
  late final AuthService _authService = widget.authService ?? AuthService();
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _companyName = TextEditingController();
  final _companyNumber = TextEditingController();
  final _password = TextEditingController();
  final _confirmPassword = TextEditingController();
  bool isCorporate = false;
  bool isLoading = false;
  bool obscurePassword = true;
  bool obscureConfirmation = true;

  @override
  void dispose() {
    if (_ownsAuthService) _authService.dispose();
    _name.dispose();
    _email.dispose();
    _phone.dispose();
    _companyName.dispose();
    _companyNumber.dispose();
    _password.dispose();
    _confirmPassword.dispose();
    super.dispose();
  }

  Future<void> _register() async {
    if (isLoading || !_formKey.currentState!.validate()) return;
    setState(() => isLoading = true);
    try {
      await _authService.registerCustomer(
        name: _name.text,
        email: _email.text,
        phone: _phone.text,
        type: isCorporate ? 'COMPANY' : 'NORMAL',
        companyName: isCorporate ? _companyName.text : null,
        companyRegistrationNumber: isCorporate ? _companyNumber.text : null,
        password: _password.text,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Account created. Sign in after Admin verifies your account.',
          ),
        ),
      );
      Navigator.pop(context, true);
    } on AuthException catch (exception) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(exception.message)),
      );
    } finally {
      if (mounted) setState(() => isLoading = false);
    }
  }

  String? _required(String? value, String label) {
    if (value == null || value.trim().isEmpty) return '$label is required.';
    return null;
  }

  Widget _field(
    String label,
    TextEditingController controller, {
    TextInputType keyboardType = TextInputType.text,
    String? Function(String?)? validator,
    int? maxLength,
    bool obscure = false,
    VoidCallback? onToggleObscure,
    TextInputAction textInputAction = TextInputAction.next,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 15),
      child: TextFormField(
        controller: controller,
        enabled: !isLoading,
        keyboardType: keyboardType,
        textInputAction: textInputAction,
        obscureText: obscure,
        validator: validator,
        maxLength: maxLength,
        decoration: InputDecoration(
          labelText: label,
          counterText: '',
          prefixIcon: Icon(_iconFor(label), color: AppTheme.primaryNavy),
          suffixIcon: onToggleObscure == null
              ? null
              : IconButton(
                  onPressed: onToggleObscure,
                  icon: Icon(
                    obscure
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                  ),
                ),
        ),
      ),
    );
  }

  IconData _iconFor(String label) => switch (label) {
        'Full Name' => Icons.person_outline,
        'Email Address' => Icons.email_outlined,
        'Phone Number' => Icons.phone_outlined,
        'Company Name' => Icons.business_outlined,
        'Company Registration Number' => Icons.badge_outlined,
        _ => Icons.lock_outline,
      };

  Widget _accountType({
    required bool selected,
    required IconData icon,
    required String title,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: isLoading ? null : onTap,
      borderRadius: BorderRadius.circular(16),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: selected ? AppTheme.paleBlue : AppTheme.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: selected ? AppTheme.secondarySkyBlue : AppTheme.border,
            width: selected ? 1.5 : 1,
          ),
        ),
        child: Row(
          children: [
            Icon(icon, color: AppTheme.primaryNavy),
            const SizedBox(width: 14),
            Expanded(
              child: Text(title,
                  style: const TextStyle(fontWeight: FontWeight.w600)),
            ),
            if (selected)
              const Icon(Icons.check_circle, color: AppTheme.secondarySkyBlue),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Create Account')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
            children: [
              const Text(
                'Join DilaDrive',
                style: TextStyle(
                  color: AppTheme.primaryNavy,
                  fontSize: 25,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 6),
              const Text(
                'New customer accounts need Admin verification before booking.',
                style: TextStyle(color: AppTheme.secondaryText),
              ),
              const SizedBox(height: 20),
              _accountType(
                selected: !isCorporate,
                icon: Icons.person_outline,
                title: 'Individual Customer',
                onTap: () => setState(() => isCorporate = false),
              ),
              const SizedBox(height: 12),
              _accountType(
                selected: isCorporate,
                icon: Icons.business_outlined,
                title: 'Corporate Customer',
                onTap: () => setState(() => isCorporate = true),
              ),
              const SizedBox(height: 20),
              _field('Full Name', _name,
                  maxLength: 120,
                  validator: (value) => _required(value, 'Full name')),
              _field(
                'Email Address',
                _email,
                maxLength: 160,
                keyboardType: TextInputType.emailAddress,
                validator: (value) {
                  final requiredError = _required(value, 'Email');
                  if (requiredError != null) return requiredError;
                  return RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$')
                          .hasMatch(value!.trim())
                      ? null
                      : 'Enter a valid email address.';
                },
              ),
              _field('Phone Number', _phone,
                  maxLength: 30, keyboardType: TextInputType.phone),
              if (isCorporate) ...[
                _field('Company Name', _companyName,
                    maxLength: 150,
                    validator: (value) => _required(value, 'Company name')),
                _field(
                  'Company Registration Number',
                  _companyNumber,
                  maxLength: 30,
                  validator: (value) => _required(value, 'Registration number'),
                ),
              ],
              _field(
                'Password',
                _password,
                maxLength: 72,
                obscure: obscurePassword,
                onToggleObscure: () =>
                    setState(() => obscurePassword = !obscurePassword),
                validator: (value) {
                  final requiredError = _required(value, 'Password');
                  if (requiredError != null) return requiredError;
                  if (value!.length < 8) return 'Use at least 8 characters.';
                  if (utf8.encode(value).length > 72) {
                    return 'Password must not exceed 72 UTF-8 bytes.';
                  }
                  return null;
                },
              ),
              _field(
                'Confirm Password',
                _confirmPassword,
                obscure: obscureConfirmation,
                onToggleObscure: () => setState(
                  () => obscureConfirmation = !obscureConfirmation,
                ),
                textInputAction: TextInputAction.done,
                validator: (value) =>
                    value == _password.text ? null : 'Passwords do not match.',
              ),
              const SizedBox(height: 8),
              SizedBox(
                height: 52,
                child: ElevatedButton(
                  onPressed: isLoading ? null : _register,
                  child: isLoading
                      ? const SizedBox.square(
                          dimension: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text('Create Account'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
