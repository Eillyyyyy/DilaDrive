import 'dart:convert';

import 'package:diladrive_customer/models/customer_notification.dart';
import 'package:diladrive_customer/services/customer_notification_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  tearDown(MobileSession.clear);

  test('loads notifications for the authenticated customer', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    late http.Request capturedRequest;
    final service = CustomerNotificationService(
      client: MockClient((request) async {
        capturedRequest = request;
        return http.Response(
          jsonEncode([
            {
              'notificationId': 21,
              'title': 'Booking request received',
              'message': 'Your booking is waiting for Admin verification.',
              'notificationType': 'BOOKING',
              'read': false,
              'sentAt': '2026-11-12T09:30:00',
              'recipientRole': 'CUSTOMER',
              'recipientId': 42,
            },
          ]),
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final notifications = await service.getMyNotifications();

    expect(capturedRequest.url.path, '/api/notifications/customer/mine');
    expect(capturedRequest.headers['cookie'], 'JSESSIONID=customer-session');
    expect(notifications, hasLength(1));
    expect(notifications.single, isA<CustomerNotification>());
    expect(notifications.single.id, 21);
    expect(notifications.single.read, isFalse);
    expect(notifications.single.type, 'BOOKING');
    service.dispose();
  });

  test('marks a notification read through the owner-scoped endpoint', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    late http.Request capturedRequest;
    final service = CustomerNotificationService(
      client: MockClient((request) async {
        capturedRequest = request;
        return http.Response('{}', 200);
      }),
    );

    await service.markAsRead(21);

    expect(capturedRequest.method, 'PATCH');
    expect(capturedRequest.url.path, '/api/notifications/21/read');
    expect(capturedRequest.headers['cookie'], 'JSESSIONID=customer-session');
    service.dispose();
  });

  test('clears the customer session when the API returns unauthorized',
      () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=expired; Path=/; HttpOnly',
    });
    final service = CustomerNotificationService(
      client: MockClient((_) async => http.Response('', 401)),
    );

    await expectLater(
      service.getMyNotifications(),
      throwsA(isA<CustomerNotificationException>().having(
        (error) => error.requiresLogin,
        'requiresLogin',
        isTrue,
      )),
    );
    expect(MobileSession.isAuthenticated, isFalse);
    service.dispose();
  });
}
