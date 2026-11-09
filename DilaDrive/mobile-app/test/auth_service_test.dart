import 'dart:convert';

import 'package:diladrive_customer/services/auth_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('sends login credentials and parses the authenticated account',
      () async {
    late http.Request sentRequest;
    final service = AuthService(
      client: MockClient((request) async {
        sentRequest = request;
        return http.Response(
          jsonEncode({
            'id': 12,
            'name': 'Customer One',
            'email': 'customer@example.com',
            'role': 'CUSTOMER',
          }),
          200,
        );
      }),
    );
    addTearDown(service.dispose);

    final user = await service.login(
      email: ' customer@example.com ',
      password: 'secret',
      role: 'CUSTOMER',
    );

    expect(sentRequest.url.path, '/api/auth/mobile/login');
    expect(jsonDecode(sentRequest.body), {
      'email': 'customer@example.com',
      'password': 'secret',
      'role': 'CUSTOMER',
    });
    expect(user.id, '12');
    expect(user.role, 'CUSTOMER');
  });

  test('rejects invalid credentials', () async {
    final service = AuthService(
      client: MockClient(
        (_) async => http.Response('Invalid Email or Password', 401),
      ),
    );
    addTearDown(service.dispose);

    await expectLater(
      service.login(
        email: 'customer@example.com',
        password: 'wrong',
        role: 'CUSTOMER',
      ),
      throwsA(
        isA<AuthException>().having(
          (exception) => exception.message,
          'message',
          'Invalid email or password.',
        ),
      ),
    );
  });
}
