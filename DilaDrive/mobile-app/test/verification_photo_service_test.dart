import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:diladrive_customer/services/verification_photo_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  tearDown(MobileSession.clear);

  test('photo listing uses the assigned trip endpoint and driver session',
      () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=driver-session; Path=/; HttpOnly',
    });
    late http.Request captured;
    final service = VerificationPhotoService(
      client: MockClient((request) async {
        captured = request;
        return http.Response('[]', 200);
      }),
    );
    addTearDown(service.dispose);

    final photos = await service.list(41);

    expect(captured.url.path, '/api/assignments/41/photos');
    expect(captured.headers['cookie'], 'JSESSIONID=driver-session');
    expect(photos, isEmpty);
  });

  test('clears an expired session and marks photo failure for login', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=expired; Path=/; HttpOnly',
    });
    final service = VerificationPhotoService(
      client: MockClient((_) async => http.Response('Unauthorized', 401)),
    );
    addTearDown(service.dispose);

    await expectLater(
      service.list(41),
      throwsA(isA<VerificationPhotoException>().having(
        (error) => error.requiresLogin,
        'requiresLogin',
        isTrue,
      )),
    );
    expect(MobileSession.isAuthenticated, isFalse);
  });

  test('surfaces backend photo validation message', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=driver-session; Path=/; HttpOnly',
    });
    final service = VerificationPhotoService(
      client: MockClient((_) async => http.Response(
            '{"message":"Only JPEG and PNG images are supported."}',
            400,
          )),
    );
    addTearDown(service.dispose);

    await expectLater(
      service.list(41),
      throwsA(isA<VerificationPhotoException>().having(
        (error) => error.message,
        'message',
        'Only JPEG and PNG images are supported.',
      )),
    );
  });
}
