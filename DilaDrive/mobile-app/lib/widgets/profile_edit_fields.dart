import 'package:flutter/material.dart';

bool isMalaysianProfilePhone(String input) {
  final phone = input.trim();
  return phone.length <= 30 &&
      RegExp(r'^\+?[0-9](?:[0-9 -]*[0-9])?$').hasMatch(phone) &&
      RegExp(r'^(?:0|\+60|60)[1-9][0-9]{7,9}$')
          .hasMatch(phone.replaceAll(RegExp(r'[ -]'), ''));
}

Future<bool> confirmDiscardProfileChanges(BuildContext context) async {
  return await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Discard changes?'),
          content: const Text('Your unsaved changes will be lost.'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Keep editing'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Discard'),
            ),
          ],
        ),
      ) ??
      false;
}
