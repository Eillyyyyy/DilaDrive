import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:diladrive_customer/services/auth_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';

void main() {
  tearDown(MobileSession.clear);

  test('customer login sends the selected role and preserves the session',
      () async {
    final service = AuthService(client: MockClient((request) async {
      expect(request.url.path, '/api/auth/mobile/login');
      expect(jsonDecode(request.body), {
        'email': 'customer@example.test',
        'password': 'password',
        'role': 'CUSTOMER',
      });
      return http.Response(
          '{"id":17,"name":"Customer","email":"customer@example.test","role":"CUSTOMER"}',
          200,
          headers: {
            'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly'
          });
    }));
    addTearDown(service.dispose);
    final customer = await service.login(
        email: ' customer@example.test ',
        password: 'password',
        role: 'CUSTOMER');
    expect(customer.id, 17);
    expect(MobileSession.isAuthenticated, isTrue);
  });

  test('a 401 on login is an invalid credentials response', () async {
    final service =
        AuthService(client: MockClient((_) async => http.Response('', 401)));
    addTearDown(service.dispose);
    await expectLater(
      service.login(
          email: 'customer@example.test', password: 'wrong', role: 'CUSTOMER'),
      throwsA(isA<AuthException>().having(
          (error) => error.message, 'message', 'Invalid email or password')),
    );
    expect(MobileSession.isAuthenticated, isFalse);
  });
  test('invalidates the backend session and clears the mobile cookie',
      () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=driver-session; Path=/; HttpOnly',
    });
    late http.Request capturedRequest;
    final service = AuthService(
      client: MockClient((request) async {
        capturedRequest = request;
        return http.Response('', 204);
      }),
    );
    addTearDown(service.dispose);

    await service.logout();

    expect(capturedRequest.method, 'POST');
    expect(capturedRequest.url.path, '/api/auth/logout');
    expect(capturedRequest.headers['cookie'], 'JSESSIONID=driver-session');
    expect(MobileSession.isAuthenticated, isFalse);
  });

  test('clears the mobile cookie when the backend cannot be reached', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=offline-session; Path=/; HttpOnly',
    });
    final service = AuthService(
      client: MockClient((_) async => throw http.ClientException('offline')),
    );
    addTearDown(service.dispose);

    await service.logout();

    expect(MobileSession.isAuthenticated, isFalse);
  });
}
