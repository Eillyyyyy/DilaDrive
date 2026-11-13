import 'dart:convert';

import 'package:diladrive_customer/services/driver_notification_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  setUp(() => MobileSession.storeResponseCookies(
      {'set-cookie': 'JSESSIONID=driver-session; Path=/'}));
  tearDown(MobileSession.clear);

  test('loads the signed-in driver notification endpoint', () async {
    final service = DriverNotificationService(
      client: MockClient((request) async {
        expect(request.url.path, '/api/notifications/driver/42');
        expect(request.headers['cookie'], 'JSESSIONID=driver-session');
        return http.Response(
          jsonEncode([
            {
              'notificationId': 8,
              'title': 'Trip assigned',
              'message': 'Assignment created.',
              'read': false,
              'notificationType': 'ASSIGNMENT',
              'sentAt': '2026-11-12T10:00:00',
            }
          ]),
          200,
        );
      }),
    );
    addTearDown(service.dispose);

    final notifications = await service.getNotifications(42);
    expect(notifications, hasLength(1));
    expect(notifications.single.title, 'Trip assigned');
    expect(notifications.single.read, isFalse);
  });

  test('marks a notification read using the owner checked endpoint', () async {
    final service = DriverNotificationService(
      client: MockClient((request) async {
        expect(request.method, 'PATCH');
        expect(request.url.path, '/api/notifications/8/read');
        expect(request.headers['cookie'], 'JSESSIONID=driver-session');
        return http.Response('', 200);
      }),
    );
    addTearDown(service.dispose);

    await expectLater(service.markAsRead(8), completes);
  });
}
