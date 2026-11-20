import 'dart:convert';
import 'package:flutter/material.dart';
import '../../services/driver_profile_service.dart';
import '../../theme/app_theme.dart';
import '../../widgets/driver_profile_widgets.dart';
import '../auth/login.dart';

class DriverChangePasswordPage extends StatefulWidget {
  const DriverChangePasswordPage({super.key, this.profileService});
  final DriverProfileService? profileService;
  @override
  State<DriverChangePasswordPage> createState() =>
      _DriverChangePasswordPageState();
}

class _DriverChangePasswordPageState extends State<DriverChangePasswordPage> {
  late final DriverProfileService _service =
      widget.profileService ?? DriverProfileService();
  final _formKey = GlobalKey<FormState>();
  final _current = TextEditingController(),
      _next = TextEditingController(),
      _confirm = TextEditingController();
  bool _saving = false;
  @override
  void dispose() {
    _current.dispose();
    _next.dispose();
    _confirm.dispose();
    if (widget.profileService == null) _service.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving || !_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      await _service.changePassword(
          currentPassword: _current.text, newPassword: _next.text);
      if (!mounted) return;
      _current.clear();
      _next.clear();
      _confirm.clear();
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Password changed successfully.')));
      Navigator.pop(context);
    } on DriverProfileException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error.message)));
      if (error.requiresLogin) {
        Navigator.of(context).pushAndRemoveUntil(
            MaterialPageRoute<void>(builder: (_) => const LoginPage()),
            (_) => false);
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: const Text('Change password')),
        body: Form(
            key: _formKey,
            child: ListView(padding: const EdgeInsets.all(18), children: [
              for (final field in [
                (_current, 'Current password'),
                (_next, 'New password'),
                (_confirm, 'Confirm new password')
              ]) ...[
                TextFormField(
                    controller: field.$1,
                    enabled: !_saving,
                    obscureText: true,
                    autocorrect: false,
                    enableSuggestions: false,
                    decoration:
                        driverProfileInput(field.$2, Icons.lock_outline),
                    textInputAction: field.$1 == _confirm
                        ? TextInputAction.done
                        : TextInputAction.next,
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return '${field.$2} is required.';
                      }
                      if (field.$1 == _next &&
                          (value.length < 8 ||
                              utf8.encode(value).length > 72)) {
                        return 'Use at least 8 characters and no more than 72 UTF-8 bytes.';
                      }
                      if (field.$1 == _confirm && value != _next.text) {
                        return 'Passwords do not match.';
                      }
                      return null;
                    }),
                const SizedBox(height: 14),
              ],
              const SizedBox(height: 9),
              SizedBox(
                  height: 52,
                  child: FilledButton(
                      onPressed: _saving ? null : _save,
                      child: _saving
                          ? const SizedBox(
                              width: 21,
                              height: 21,
                              child: CircularProgressIndicator(
                                  strokeWidth: 2, color: Colors.white))
                          : const Text('Change password'))),
            ])),
      );
}
