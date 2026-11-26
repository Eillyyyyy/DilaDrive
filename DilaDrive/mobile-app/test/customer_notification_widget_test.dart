import 'package:diladrive_customer/models/customer_notification.dart';
import 'package:diladrive_customer/screens/customer/notification.dart';
import 'package:diladrive_customer/services/customer_notification_service.dart';
import 'package:diladrive_customer/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('verification notices display decision and rejection reason',
      (tester) async {
    final service = _NotificationService(verification: true);
    await tester.pumpWidget(MaterialApp(home: NotificationPage(notificationService: service)));
    await tester.pumpAndSettle();
    expect(find.text('Account Approved'), findsOneWidget);
    expect(find.text('Account Verification Rejected'), findsOneWidget);
    expect(find.text('Your DilaDrive account was rejected. Reason: Registration could not be verified.'), findsOneWidget);
    await tester.tap(find.text('Mark read'));
    await tester.pumpAndSettle();
    expect(service.markedIds, [21]);
  });

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
  _NotificationService({this.verification = false});
  final bool verification;
  final List<int> markedIds = [];
  bool _read = false;

  @override
  Future<List<CustomerNotification>> getMyNotifications() async => [
        CustomerNotification(
          id: 21,
          title: verification ? 'Account Verification Rejected' : 'Booking request received',
          message: verification
              ? 'Your DilaDrive account was rejected. Reason: Registration could not be verified.'
              : 'Your booking is waiting for Admin verification.',
          read: _read,
          type: verification ? 'ACCOUNT_VERIFICATION' : 'BOOKING',
        ),
        if (verification)
          const CustomerNotification(id: 22, title: 'Account Approved',
              message: 'Your DilaDrive account has been approved. You can now make bookings.',
              read: true, type: 'ACCOUNT_VERIFICATION'),
      ];

  @override
  Future<void> markAsRead(int notificationId) async {
    markedIds.add(notificationId);
    _read = true;
  }

  @override
  void dispose() {}
}
