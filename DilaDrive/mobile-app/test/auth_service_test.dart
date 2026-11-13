import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:diladrive_customer/services/auth_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';

void main() {
  tearDown(MobileSession.clear);

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
