import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:diladrive_customer/models/customer_profile.dart';
import 'package:diladrive_customer/screens/customer/update_account.dart';
import 'package:diladrive_customer/services/api_http_client.dart';
import 'package:diladrive_customer/services/customer_profile_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';

class _ProfileService extends CustomerProfileService {
  final requests = <Completer<CustomerProfile>>[];

  @override
  Future<CustomerProfile> getMyProfile() {
    final request = Completer<CustomerProfile>();
    requests.add(request);
    return request.future;
  }
}

void main() {
  tearDown(() {
    MobileSession.clear();
    MobileSession.onDriverDeactivated = null;
  });

  testWidgets(
      'update account reloads and refreshes verification without resetting edits',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final service = _ProfileService();
    addTearDown(service.dispose);
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (context) => Scaffold(
          body: TextButton(
            onPressed: () => Navigator.of(context).push<void>(
              MaterialPageRoute(
                builder: (_) => UpdateAccountPage(profileService: service),
              ),
            ),
            child: const Text('Open account'),
          ),
        ),
      ),
    ));

    await tester.tap(find.text('Open account'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 350));
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(service.requests, hasLength(1));
    service.requests[0].completeError(
      const CustomerProfileException('Service unavailable'),
    );
    await tester.pumpAndSettle();
    expect(find.text('Service unavailable'), findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pump();
    service.requests[1].complete(const CustomerProfile(
      id: 1,
      name: 'Registered Company',
      email: 'company@example.test',
      type: 'COMPANY',
      companyName: 'Fresh Company',
      profileImage: 'data:image/png;base64,***',
    ));
    await tester.pumpAndSettle();
    expect(find.text('Fresh Company'), findsOneWidget);
    expect(find.byIcon(Icons.person), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Open account'));
    await tester.pump();
    service.requests[2].complete(const CustomerProfile(
      id: 1,
      name: 'Updated Individual',
      email: 'individual@example.test',
      type: 'NORMAL',
      status: 'PENDING',
    ));
    await tester.pumpAndSettle();
    expect(service.requests, hasLength(3));
    expect(find.text('Updated Individual'), findsOneWidget);
    expect(find.text('Pending Verification'), findsOneWidget);
    expect(find.text('Company name'), findsNothing);
    expect(find.textContaining(RegExp('clear phone', caseSensitive: false)),
        findsNothing);
    final nameField = find.widgetWithText(TextFormField, 'Full name');
    await tester.ensureVisible(nameField);
    await tester.enterText(nameField, 'Unsaved name');

    await tester.ensureVisible(find.text('Check status'));
    await tester.tap(find.text('Check status'));
    await tester.pump();
    expect(service.requests, hasLength(4));
    service.requests[3].complete(const CustomerProfile(
      id: 1,
      name: 'Updated Individual',
      email: 'individual@example.test',
      status: 'REJECTED',
      rejectionReason: 'Registration details could not be verified.',
    ));
    await tester.pumpAndSettle();
    expect(find.text('Verification Rejected'), findsOneWidget);
    expect(find.text('Registration details could not be verified.'),
        findsOneWidget);
    expect(find.text('Unsaved name'), findsOneWidget);
    await tester.ensureVisible(find.text('Check status'));
    await tester.tap(find.text('Check status'));
    await tester.pump();
    service.requests[4].complete(const CustomerProfile(
      id: 1,
      name: 'Updated Individual',
      email: 'individual@example.test',
      status: 'ACTIVE',
    ));
    await tester.pumpAndSettle();
    expect(find.text('Verified / Active'), findsOneWidget);
    expect(
        find.text('Registration details could not be verified.'), findsNothing);
  });

  test('only the specific driver rejection clears the mobile session',
      () async {
    MobileSession.storeResponseCookies({'set-cookie': 'JSESSIONID=driver'});
    final messages = <String>[];
    MobileSession.onDriverDeactivated = messages.add;
    final replies = [
      http.Response('{"message":"Forbidden"}', 403),
      http.Response('{"message":"Database unavailable"}', 503),
      http.Response(
        '{"code":"DRIVER_ACCOUNT_DEACTIVATED","message":"Contact the administrator."}',
        403,
      ),
    ];
    final client = createApiHttpClient(
      client: MockClient((_) async => replies.removeAt(0)),
    );
    addTearDown(client.close);
    final endpoint = Uri.parse('http://localhost/api/assignments/mine');
    await client.get(endpoint);
    await client.get(endpoint);
    expect(MobileSession.isAuthenticated, isTrue);
    expect(messages, isEmpty);
    final response = await client.get(endpoint);
    expect(response.body, contains('DRIVER_ACCOUNT_DEACTIVATED'));
    expect(MobileSession.isAuthenticated, isFalse);
    expect(MobileSession.headers, isEmpty);
    expect(messages, ['Contact the administrator.']);
  });
}
