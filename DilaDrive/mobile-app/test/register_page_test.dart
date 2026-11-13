import 'dart:convert';

import 'package:diladrive_customer/screens/auth/register.dart';
import 'package:diladrive_customer/services/auth_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  testWidgets('registration fields match backend size limits', (tester) async {
    final service = AuthService(
      client: MockClient((_) async => http.Response('', 201)),
    );
    await tester
        .pumpWidget(MaterialApp(home: RegisterPage(authService: service)));

    expect(_maxLength(tester, 'Full Name'), 120);
    expect(_maxLength(tester, 'Email Address'), 160);
    expect(_maxLength(tester, 'Phone Number'), 30);
    expect(_maxLength(tester, 'Password'), 72);

    await tester.tap(find.text('Corporate Customer'));
    await tester.pumpAndSettle();

    expect(_maxLength(tester, 'Company Name'), 150);
    await _scrollToField(tester, 'Company Registration Number');
    expect(_maxLength(tester, 'Company Registration Number'), 30);

    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
      'successful registration posts a customer request and shows its result',
      (tester) async {
    http.Request? capturedRequest;
    final service = AuthService(
      client: MockClient((request) async {
        capturedRequest = request;
        return http.Response('Account created', 201);
      }),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => Navigator.of(context).push<void>(
                MaterialPageRoute<void>(
                  builder: (_) => RegisterPage(authService: service),
                ),
              ),
              child: const Text('Open registration'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open registration'));
    await tester.pumpAndSettle();

    await _enterField(tester, 'Full Name', 'Ayla Customer');
    await _enterField(tester, 'Email Address', 'ayla@example.com');
    await _enterField(tester, 'Phone Number', '0123456789');
    await _enterField(tester, 'Password', 'secure-pass-123');
    await _enterField(tester, 'Confirm Password', 'secure-pass-123');
    await tester.tap(find.widgetWithText(ElevatedButton, 'Create Account'));
    await tester.pumpAndSettle();

    expect(capturedRequest?.method, 'POST');
    expect(capturedRequest?.url.path, '/api/auth/mobile/register');
    expect(jsonDecode(capturedRequest!.body), {
      'name': 'Ayla Customer',
      'email': 'ayla@example.com',
      'phone': '0123456789',
      'type': 'NORMAL',
      'companyName': null,
      'companyRegistrationNumber': null,
      'password': 'secure-pass-123',
    });
    expect(
      find.text('Account created. Sign in after Admin verifies your account.'),
      findsOneWidget,
    );

    await tester.pumpWidget(const SizedBox.shrink());
  });
}

int? _maxLength(WidgetTester tester, String label) => tester
    .widgetList<TextField>(find.byType(TextField))
    .singleWhere((field) => field.decoration?.labelText == label)
    .maxLength;

Future<void> _enterField(
  WidgetTester tester,
  String label,
  String value,
) async {
  final field = await _scrollToField(tester, label);
  await tester.ensureVisible(field);
  await tester.pumpAndSettle();
  await tester.enterText(field, value);
}

Future<Finder> _scrollToField(WidgetTester tester, String label) async {
  final field = find.byWidgetPredicate(
    (widget) => widget is TextField && widget.decoration?.labelText == label,
  );
  for (var attempt = 0; attempt < 8 && field.evaluate().isEmpty; attempt++) {
    await tester.drag(find.byType(ListView).first, const Offset(0, -320));
    await tester.pumpAndSettle();
  }
  expect(field, findsOneWidget, reason: 'Expected to find the $label field.');
  return field;
}
