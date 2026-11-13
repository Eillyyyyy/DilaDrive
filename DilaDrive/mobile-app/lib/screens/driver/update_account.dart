import 'package:flutter/material.dart';

import '../../models/driver_profile.dart';
import '../../services/driver_profile_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';

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

  void _openLogin() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: const Text('Update Account')),
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
              child: ListView(
                padding: const EdgeInsets.all(18),
                children: [
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(18),
                      child: Column(children: [
                        _field(_name, 'Full name', Icons.person_outline),
                        const SizedBox(height: 14),
                        _field(_email, 'Email address', Icons.email_outlined,
                            keyboardType: TextInputType.emailAddress),
                        const SizedBox(height: 14),
                        _field(_phone, 'Phone number', Icons.phone_outlined,
                            keyboardType: TextInputType.phone),
                      ]),
                    ),
                  ),
                  const SizedBox(height: 16),
                  SizedBox(
                    height: 50,
                    child: ElevatedButton.icon(
                      onPressed: _saving ? null : _save,
                      icon: _saving
                          ? const SizedBox.square(
                              dimension: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.save_outlined),
                      label: Text(_saving ? 'Saving…' : 'Save changes'),
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      );

  Widget _field(TextEditingController controller, String label, IconData icon,
          {TextInputType? keyboardType}) =>
      TextFormField(
        controller: controller,
        keyboardType: keyboardType,
        textInputAction: TextInputAction.next,
        decoration: InputDecoration(labelText: label, prefixIcon: Icon(icon)),
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
          if (label == 'Email address' &&
              !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(text)) {
            return 'Enter a valid email address.';
          }
          return null;
        },
      );
}
