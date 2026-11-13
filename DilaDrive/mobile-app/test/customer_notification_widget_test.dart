import 'package:diladrive_customer/models/customer_notification.dart';
import 'package:diladrive_customer/screens/customer/notification.dart';
import 'package:diladrive_customer/services/customer_notification_service.dart';
import 'package:diladrive_customer/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('marking a notification read refreshes its displayed state',
      (tester) async {
    final service = _NotificationService();
    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.lightTheme,
      home: NotificationPage(notificationService: service),
    ));
    await tester.pumpAndSettle();

    expect(find.text('1 unread notification'), findsOneWidget);
    expect(find.text('Booking request received'), findsOneWidget);

    await tester.tap(find.text('Mark read'));
    await tester.pumpAndSettle();

    expect(service.markedIds, [21]);
    expect(find.text('All notifications have been read'), findsOneWidget);
    expect(find.text('Mark read'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}

class _NotificationService extends CustomerNotificationService {
  final List<int> markedIds = [];
  bool _read = false;

  @override
  Future<List<CustomerNotification>> getMyNotifications() async => [
        CustomerNotification(
          id: 21,
          title: 'Booking request received',
          message: 'Your booking is waiting for Admin verification.',
          read: _read,
          type: 'BOOKING',
        ),
      ];

  @override
  Future<void> markAsRead(int notificationId) async {
    markedIds.add(notificationId);
    _read = true;
  }

  @override
  void dispose() {}
}
