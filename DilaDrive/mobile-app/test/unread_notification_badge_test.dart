import 'dart:async';

import 'package:diladrive_customer/models/customer_notification.dart';
import 'package:diladrive_customer/widgets/unread_notification_badge.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('counts unread items and refreshes on push and inbox changes',
      (tester) async {
    final changes = ValueNotifier<int>(0);
    final pushes = StreamController<void>.broadcast();
    List<CustomerNotification> inbox = [_message(1), _message(2, read: true)];
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: UnreadNotificationBadge(
          icon: const Icon(Icons.notifications),
          inboxChanges: changes,
          pushUpdates: pushes.stream,
          loadNotifications: () async => inbox,
        ),
      ),
    ));
    await tester.pump();
    expect(find.text('1'), findsOneWidget);

    inbox = [_message(1), _message(2, read: true), _message(3)];
    pushes.add(null);
    await tester.pump();
    await tester.pump();
    expect(find.text('2'), findsOneWidget);

    inbox = inbox.map((item) => _message(item.id, read: true)).toList();
    changes.value++;
    await tester.pump();
    await tester.pump();
    expect(find.byType(Badge), findsNothing);
    expect(find.byIcon(Icons.notifications), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
    await pushes.close();
    changes.dispose();
  });
}

CustomerNotification _message(int id, {bool read = false}) =>
    CustomerNotification(id: id, title: 'Update', message: 'Details', read: read);
