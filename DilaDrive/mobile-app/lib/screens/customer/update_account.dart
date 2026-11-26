import 'package:flutter/material.dart';

import '../../widgets/responsive_page.dart';
import 'dart:convert';
import 'package:image_picker/image_picker.dart';

import '../../models/customer_profile.dart';
import '../../services/customer_profile_service.dart';
import '../../theme/app_theme.dart';
import '../../widgets/customer_profile_widgets.dart';
import '../../widgets/profile_edit_fields.dart';
import '../auth/login.dart';

class UpdateAccountPage extends StatefulWidget {
  const UpdateAccountPage({super.key, required this.profileService});

  final CustomerProfileService profileService;

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
  CustomerProfileService get _service => widget.profileService;
  late Future<CustomerProfile> _profile;
  CustomerProfile? _original;
  CustomerProfile? _verificationProfile;
  bool _saving = false;
  bool _picking = false;
  bool _discarding = false;
  bool _checkingStatus = false;
  String? _profileImage;
  final ImagePicker _imagePicker = ImagePicker();

  @override
  void initState() {
    super.initState();
    _profile = _load();
  }

  Future<CustomerProfile> _load() async {
    final profile = await _service.getMyProfile();
    if (!mounted) return profile;
    _original = profile;
    _verificationProfile = profile;
    _profileImage = profile.profileImage;
    _nameController.text = profile.name;
    _emailController.text = profile.email;
    _phoneController.text = profile.phone ?? '';
    _companyNameController.text = profile.companyName ?? '';
    _companyRegistrationController.text =
        profile.companyRegistrationNumber ?? '';
    return profile;
  }

  Future<void> _checkStatus() async {
    if (_checkingStatus || _saving || _picking) return;
    setState(() => _checkingStatus = true);
    try {
      final profile = await _service.getMyProfile();
      if (mounted) setState(() => _verificationProfile = profile);
    } on CustomerProfileException catch (error) {
      if (!mounted) return;
      if (error.requiresLogin) {
        _openLogin();
      } else {
        _message(error.message);
      }
    } finally {
      if (mounted) setState(() => _checkingStatus = false);
    }
  }

  Future<void> _chooseProfileImage() async {
    if (_picking || _saving) return;
    setState(() => _picking = true);
    try {
      final file = await _imagePicker.pickImage(
        source: ImageSource.gallery,
        maxWidth: 640,
        maxHeight: 640,
        imageQuality: 75,
      );
      if (file == null || !mounted) return;
      final bytes = await file.readAsBytes();
      if (!mounted) return;
      if (bytes.length > 900 * 1024) {
        _message('Choose an image smaller than 900 KB.');
        return;
      }
      final mime = bytes.length >= 8 &&
              base64Encode(bytes.sublist(0, 8)) == 'iVBORw0KGgo='
          ? 'png'
          : bytes.length >= 3 &&
                  bytes[0] == 255 &&
                  bytes[1] == 216 &&
                  bytes[2] == 255
              ? 'jpeg'
              : bytes.length >= 12 &&
                      String.fromCharCodes(bytes.sublist(0, 4)) == 'RIFF' &&
                      String.fromCharCodes(bytes.sublist(8, 12)) == 'WEBP'
                  ? 'webp'
                  : null;
      if (mime == null) {
        _message('Choose a valid PNG, JPEG or WebP image.');
        return;
      }
      setState(() =>
          _profileImage = 'data:image/$mime;base64,${base64Encode(bytes)}');
    } catch (_) {
      if (mounted) _message('Could not select the photo. Please try again.');
    } finally {
      if (mounted) setState(() => _picking = false);
    }
  }

  void _message(String message) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  void _openLogin() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }

  String? _changed(TextEditingController controller, String? original) {
    final value = controller.text.trim();
    return value == (original?.trim() ?? '') ? null : value;
  }

  Future<void> _cancel() async {
    if (_saving || _picking || _discarding) return;
    final original = _original;
    final changed = original != null &&
        (_changed(_nameController, original.name) != null ||
            _changed(_emailController, original.email) != null ||
            _changed(_phoneController, original.phone) != null ||
            _changed(_companyNameController, original.companyName) != null ||
            _changed(_companyRegistrationController,
                    original.companyRegistrationNumber) !=
                null ||
            _profileImage != original.profileImage);
    _discarding = true;
    final discard = !changed || await confirmDiscardProfileChanges(context);
    _discarding = false;
    if (discard && mounted) Navigator.pop(context);
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _companyNameController.dispose();
    _companyRegistrationController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving || _picking || !_formKey.currentState!.validate()) return;
    final original = _original!;
    setState(() => _saving = true);
    try {
      final updated = await _service.updateMyProfile(
        name: _nameController.text,
        email: _emailController.text,
        phone: _phoneController.text.trim().isEmpty
            ? null
            : _changed(_phoneController, original.phone),
        profileImage:
            _profileImage == original.profileImage ? null : _profileImage ?? '',
        companyName: _isCompany
            ? _changed(_companyNameController, original.companyName)
            : null,
        companyRegistrationNumber: _isCompany
            ? _changed(_companyRegistrationController,
                original.companyRegistrationNumber)
            : null,
      );
      if (mounted) {
        _message('Profile updated.');
        Navigator.pop(context, updated);
      }
    } on CustomerProfileException catch (error) {
      if (!mounted) return;
      if (error.requiresLogin) {
        _openLogin();
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
    return PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, result) {
          if (!didPop) _cancel();
        },
        child: DilaDriveScaffold(
          backgroundColor: AppTheme.lightBlueBackground,
          appBar: AppBar(
              title: const Text('Update account'),
              leading: BackButton(onPressed: _cancel)),
          body: FutureBuilder<CustomerProfile>(
            future: _profile,
            builder: (context, snapshot) {
              if (snapshot.connectionState == ConnectionState.waiting) {
                return const Center(child: CircularProgressIndicator());
              }
              if (!snapshot.hasData) {
                final error = snapshot.error;
                final requiresLogin =
                    error is CustomerProfileException && error.requiresLogin;
                return Center(
                    child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(error is CustomerProfileException
                        ? error.message
                        : 'Unable to load your profile.'),
                    TextButton(
                      onPressed: requiresLogin
                          ? _openLogin
                          : () {
                              setState(() {
                                _profile = _load();
                              });
                            },
                      child: Text(requiresLogin ? 'Sign in' : 'Retry'),
                    ),
                  ],
                ));
              }
              return _editForm();
            },
          ),
        ));
  }

  Widget _editForm() => Form(
        key: _formKey,
        child: ListView(
          padding: pagePadding(context),
          children: [
            Center(
                child: Column(children: [
              CustomerProfileAvatar(image: _profileImage, radius: 48),
              TextButton.icon(
                  onPressed: _saving || _picking ? null : _chooseProfileImage,
                  icon: const Icon(Icons.photo_library_outlined),
                  label: const Text('Choose profile photo')),
              if (_profileImage != null)
                TextButton(
                    onPressed: _saving || _picking
                        ? null
                        : () => setState(() => _profileImage = null),
                    child: const Text('Remove photo')),
            ])),
            const SizedBox(height: 12),
            CustomerVerificationStatus(
              profile: _verificationProfile!,
              onRefresh:
                  _checkingStatus || _saving || _picking ? null : _checkStatus,
            ),
            const SizedBox(height: 12),
            Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                spacing: 12,
                children: [
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
                      if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$')
                          .hasMatch(email)) {
                        return 'Enter a valid email address.';
                      }
                      return null;
                    },
                  ),
                  _input(
                    controller: _phoneController,
                    label: 'Phone number',
                    icon: Icons.phone_outlined,
                    keyboardType: TextInputType.phone,
                    validator: (value) {
                      final phone = value?.trim() ?? '';
                      return phone.isNotEmpty &&
                              _changed(_phoneController, _original?.phone) !=
                                  null &&
                              !isMalaysianProfilePhone(phone)
                          ? 'Enter a valid Malaysian phone number.'
                          : null;
                    },
                  ),
                  if (_isCompany) ...[
                    _input(
                      controller: _companyNameController,
                      label: 'Company name',
                      icon: Icons.business_outlined,
                      validator: (value) => (value?.trim().isEmpty ?? true)
                          ? 'Enter your company name.'
                          : (value?.trim().length ?? 0) > 150
                              ? 'Company name must be 150 characters or fewer.'
                              : null,
                    ),
                    _input(
                      controller: _companyRegistrationController,
                      label: 'Company registration number (optional)',
                      icon: Icons.badge_outlined,
                      validator: (value) => (value?.trim().length ?? 0) > 30
                          ? 'Registration number must be 30 characters or fewer.'
                          : null,
                    ),
                  ],
                ]),
            const SizedBox(height: 23),
            SizedBox(
              height: 52,
              child: FilledButton(
                onPressed: _saving || _picking ? null : _save,
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
            TextButton(
              onPressed: _saving || _picking ? null : _cancel,
              child: const Text('Cancel'),
            ),
          ],
        ),
      );

  Widget _input({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    required String? Function(String?) validator,
    TextInputType? keyboardType,
  }) =>
      TextFormField(
        controller: controller,
        enabled: !_saving && !_picking,
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

  bool get _isCompany => _original?.type?.toUpperCase() == 'COMPANY';
}
