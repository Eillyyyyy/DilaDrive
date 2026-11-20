import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../../widgets/driver_profile_widgets.dart';

import '../../models/driver_profile.dart';
import '../../services/driver_profile_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';
import 'change_password.dart';

class DriverUpdateAccountPage extends StatefulWidget {
  const DriverUpdateAccountPage({super.key, this.profileService});

  final DriverProfileService? profileService;

  @override
  State<DriverUpdateAccountPage> createState() =>
      _DriverUpdateAccountPageState();
}

class _DriverUpdateAccountPageState extends State<DriverUpdateAccountPage> {
  late final bool _ownsService = widget.profileService == null;
  late final DriverProfileService _service =
      widget.profileService ?? DriverProfileService();
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  late Future<DriverProfile> _profile;
  bool _saving = false;
  bool _picking = false;
  String? _photo;
  bool _photoChanged = false;
  final _picker = ImagePicker();

  @override
  void initState() {
    super.initState();
    _profile = _loadProfile();
  }

  Future<DriverProfile> _loadProfile() async {
    final profile = await _service.getProfile();
    if (!mounted) return profile;
    _name.text = profile.name;
    _email.text = profile.email;
    _phone.text = profile.phone ?? '';
    _photo = profile.profileImage;
    _photoChanged = false;
    return profile;
  }

  @override
  void dispose() {
    if (_ownsService) _service.dispose();
    _name.dispose();
    _email.dispose();
    _phone.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate() || _saving) return;
    setState(() => _saving = true);
    try {
      final updated = await _service.updateProfile(
        name: _name.text,
        email: _email.text,
        phone: _phone.text,
        profileImage: _photoChanged ? (_photo ?? '') : null,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Profile updated.')),
        );
        Navigator.pop(context, updated);
      }
    } on DriverProfileException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error.message)));
        if (error.requiresLogin) _openLogin();
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _message(String text) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
    }
  }

  Future<void> _pickPhoto() async {
    setState(() => _picking = true);
    try {
      final file = await _picker.pickImage(
          source: ImageSource.gallery,
          maxWidth: 640,
          maxHeight: 640,
          imageQuality: 80);
      if (file == null) return;
      final bytes = await file.readAsBytes();
      if (!mounted) return;
      if (bytes.length > 900 * 1024) {
        _message('Choose an image smaller than 900 KB.');
        return;
      }
      final mime = bytes.length >= 8 && bytes[0] == 137 && bytes[1] == 80
          ? 'png'
          : bytes.length >= 3 && bytes[0] == 255 && bytes[1] == 216
              ? 'jpeg'
              : bytes.length >= 12 &&
                      ascii.decode(bytes.sublist(0, 4), allowInvalid: true) ==
                          'RIFF' &&
                      ascii.decode(bytes.sublist(8, 12), allowInvalid: true) ==
                          'WEBP'
                  ? 'webp'
                  : null;
      if (mime == null) {
        _message('Choose a PNG, JPEG or WebP image.');
        return;
      }
      setState(() {
        _photo = 'data:image/$mime;base64,${base64Encode(bytes)}';
        _photoChanged = true;
      });
    } catch (_) {
      _message('Could not select the photo. Please try again.');
    } finally {
      if (mounted) setState(() => _picking = false);
    }
  }

  void _previewPhoto() {
    final bytes = driverPhotoBytes(_photo);
    if (bytes == null) return;
    showDialog<void>(
        context: context,
        builder: (_) => Dialog(
            child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  Flexible(
                      child: InteractiveViewer(
                          child: Image.memory(bytes,
                              errorBuilder: (_, __, ___) =>
                                  const Icon(Icons.broken_image_outlined)))),
                  TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('Close')),
                ]))));
  }

  void _openLogin() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: const Text('Update account')),
        body: FutureBuilder<DriverProfile>(
          future: _profile,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              final requiresLogin = snapshot.error is DriverProfileException &&
                  (snapshot.error as DriverProfileException).requiresLogin;
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Text(snapshot.error.toString(),
                        textAlign: TextAlign.center),
                    TextButton.icon(
                      onPressed: requiresLogin
                          ? _openLogin
                          : () => setState(() {
                                _profile = _loadProfile();
                              }),
                      icon: const Icon(Icons.refresh),
                      label: Text(requiresLogin ? 'Sign in' : 'Retry'),
                    ),
                  ]),
                ),
              );
            }
            return Form(
                key: _formKey,
                child: ListView(padding: const EdgeInsets.all(18), children: [
                  Center(
                      child: Column(children: [
                    DriverProfileAvatar(image: _photo, size: 96),
                    TextButton.icon(
                        onPressed: _saving || _picking ? null : _pickPhoto,
                        icon: const Icon(Icons.photo_library_outlined),
                        label: Text(_picking
                            ? 'Opening gallery...'
                            : 'Choose profile photo')),
                    if (_photo != null)
                      Wrap(alignment: WrapAlignment.center, children: [
                        TextButton(
                            onPressed:
                                _saving || _picking ? null : _previewPhoto,
                            child: const Text('Preview')),
                        TextButton(
                            onPressed: _saving || _picking
                                ? null
                                : () => setState(() {
                                      _photo = null;
                                      _photoChanged = true;
                                    }),
                            child: const Text('Remove photo')),
                      ]),
                  ])),
                  const SizedBox(height: 12),
                  _field(_name, 'Full name', Icons.person_outline),
                  const SizedBox(height: 14),
                  _field(_email, 'Email address', Icons.email_outlined,
                      keyboardType: TextInputType.emailAddress),
                  const SizedBox(height: 14),
                  _field(_phone, 'Phone number', Icons.phone_outlined,
                      keyboardType: TextInputType.phone),
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
                                      strokeWidth: 2, color: Colors.white))
                              : const Text('Save changes'))),
                  TextButton(
                      onPressed: _saving || _picking
                          ? null
                          : () => Navigator.push<void>(
                              context,
                              MaterialPageRoute(
                                  builder: (_) => DriverChangePasswordPage(
                                      profileService: _service))),
                      child: const Text('Change password')),
                ]));
          },
        ),
      );

  Widget _field(TextEditingController controller, String label, IconData icon,
          {TextInputType? keyboardType}) =>
      TextFormField(
        controller: controller,
        enabled: !_saving && !_picking,
        keyboardType: keyboardType,
        textInputAction: TextInputAction.next,
        decoration: driverProfileInput(label, icon),
        validator: (value) {
          final text = value?.trim() ?? '';
          if (text.isEmpty) return '$label is required.';
          final maxLength = switch (label) {
            'Full name' => 120,
            'Email address' => 160,
            'Phone number' => 30,
            _ => null,
          };
          if (maxLength != null && text.length > maxLength) {
            return '$label must be $maxLength characters or fewer.';
          }
          if (label == 'Phone number') {
            final digits = text.replaceAll(RegExp(r'\D'), '');
            if (digits.length < 9 || digits.length > 15) {
              return 'Enter a valid phone number (9 to 15 digits).';
            }
          }
          if (label == 'Email address' &&
              !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(text)) {
            return 'Enter a valid email address.';
          }
          return null;
        },
      );
}
