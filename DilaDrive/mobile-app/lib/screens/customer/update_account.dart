import 'package:flutter/material.dart';
import 'dart:convert';
import 'dart:typed_data';
import 'package:image_picker/image_picker.dart';

import '../../models/customer_profile.dart';
import '../../services/customer_profile_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';

class UpdateAccountPage extends StatefulWidget {
  const UpdateAccountPage({super.key, required this.profile});

  final CustomerProfile profile;

  @override
  State<UpdateAccountPage> createState() => _UpdateAccountPageState();
}

class _UpdateAccountPageState extends State<UpdateAccountPage> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _phoneController = TextEditingController();
  final _companyNameController = TextEditingController();
  final _companyRegistrationController = TextEditingController();
  final CustomerProfileService _service = CustomerProfileService();
  bool _saving = false;
  String? _profileImage;
  final ImagePicker _imagePicker = ImagePicker();

  @override
  void initState() {
    super.initState();
    _profileImage = widget.profile.profileImage;
    _nameController.text = widget.profile.name;
    _emailController.text = widget.profile.email;
    _phoneController.text = widget.profile.phone ?? '';
    _companyNameController.text = widget.profile.companyName ?? '';
    _companyRegistrationController.text =
        widget.profile.companyRegistrationNumber ?? '';
  }

  Future<void> _chooseProfileImage() async {
    final file = await _imagePicker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 640,
      maxHeight: 640,
      imageQuality: 75,
    );
    if (file == null) return;
    final bytes = await file.readAsBytes();
    if (bytes.length > 900 * 1024) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Choose an image smaller than 900 KB.')),
      );
      return;
    }
    final extension = file.name.toLowerCase().split('.').last;
    final mime = extension == 'png' ? 'png' : extension == 'webp' ? 'webp' : 'jpeg';
    setState(() => _profileImage = 'data:image/$mime;base64,${base64Encode(bytes)}');
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _companyNameController.dispose();
    _companyRegistrationController.dispose();
    _service.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      final updated = await _service.updateMyProfile(
        name: _nameController.text,
        email: _emailController.text,
        phone: _phoneController.text,
        profileImage: _profileImage ?? '',
        companyName: _isCompany ? _companyNameController.text : null,
        companyRegistrationNumber:
            _isCompany ? _companyRegistrationController.text : null,
      );
      if (mounted) Navigator.pop(context, updated);
    } on CustomerProfileException catch (error) {
      if (!mounted) return;
      if (error.requiresLogin) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute<void>(builder: (_) => const LoginPage()),
          (_) => false,
        );
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.message),
        ),
      );
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      appBar: AppBar(title: const Text('Update account')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(18),
          children: [
            Center(child: Column(children: [
              CircleAvatar(radius: 48, backgroundColor: AppTheme.paleBlue,
                backgroundImage: _profileImage == null ? null : MemoryImage(_imageBytes(_profileImage!)),
                child: _profileImage == null ? const Icon(Icons.person, size: 46, color: AppTheme.primaryNavy) : null),
              TextButton.icon(onPressed: _chooseProfileImage,
                icon: const Icon(Icons.photo_library_outlined), label: const Text('Choose profile photo')),
              if (_profileImage != null) TextButton(onPressed: () => setState(() => _profileImage = null), child: const Text('Remove photo')),
            ])),
            const SizedBox(height: 12),
            _input(
              controller: _nameController,
              label: 'Full name',
              icon: Icons.person_outline,
              validator: (value) {
                final name = value?.trim() ?? '';
                if (name.isEmpty) return 'Enter your name.';
                if (name.length > 120) {
                  return 'Name must be 120 characters or fewer.';
                }
                return null;
              },
            ),
            const SizedBox(height: 14),
            _input(
              controller: _emailController,
              label: 'Email address',
              icon: Icons.email_outlined,
              keyboardType: TextInputType.emailAddress,
              validator: (value) {
                final email = value?.trim() ?? '';
                if (email.isEmpty) return 'Enter your email address.';
                if (email.length > 160) {
                  return 'Email must be 160 characters or fewer.';
                }
                if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(email)) {
                  return 'Enter a valid email address.';
                }
                return null;
              },
            ),
            const SizedBox(height: 14),
            _input(
              controller: _phoneController,
              label: 'Phone number',
              icon: Icons.phone_outlined,
              keyboardType: TextInputType.phone,
              validator: (value) => (value?.trim().length ?? 0) > 30
                  ? 'Phone number must be 30 characters or fewer.'
                  : null,
            ),
            if (_isCompany) ...[
              const SizedBox(height: 14),
              _input(
                controller: _companyNameController,
                label: 'Company name',
                icon: Icons.business_outlined,
                validator: (value) => (value?.trim().length ?? 0) > 150
                    ? 'Company name must be 150 characters or fewer.'
                    : null,
              ),
              const SizedBox(height: 14),
              _input(
                controller: _companyRegistrationController,
                label: 'Company registration number',
                icon: Icons.badge_outlined,
                validator: (value) => (value?.trim().length ?? 0) > 30
                    ? 'Registration number must be 30 characters or fewer.'
                    : null,
              ),
            ],
            const SizedBox(height: 23),
            SizedBox(
              height: 52,
              child: FilledButton(
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox(
                        width: 21,
                        height: 21,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text('Save changes'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _input({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    required String? Function(String?) validator,
    TextInputType? keyboardType,
  }) =>
      TextFormField(
        controller: controller,
        keyboardType: keyboardType,
        validator: validator,
        textInputAction: TextInputAction.next,
        decoration: InputDecoration(
          labelText: label,
          prefixIcon: Icon(icon),
          filled: true,
          fillColor: AppTheme.white,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: BorderSide.none,
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: AppTheme.border),
          ),
        ),
      );

  bool get _isCompany => widget.profile.type?.toUpperCase() == 'COMPANY';
}

Uint8List _imageBytes(String value) => base64Decode(value.substring(value.indexOf(',') + 1));
