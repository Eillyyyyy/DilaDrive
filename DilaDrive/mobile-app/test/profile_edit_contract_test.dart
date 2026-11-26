import 'dart:async';

import 'package:diladrive_customer/models/customer_profile.dart';
import 'package:diladrive_customer/models/driver_profile.dart';
import 'package:diladrive_customer/screens/customer/update_account.dart';
import 'package:diladrive_customer/screens/driver/update_account.dart';
import 'package:diladrive_customer/services/customer_profile_service.dart';
import 'package:diladrive_customer/services/driver_profile_service.dart';
import 'package:diladrive_customer/widgets/profile_edit_fields.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _ProfileService extends CustomerProfileService {
  final result = Completer<CustomerProfile>();
  int reads = 0;
  int saves = 0;
  String? submittedImage;
  String? submittedPhone;

  @override
  Future<CustomerProfile> getMyProfile() async {
    reads++;
    return CustomerProfile(
      id: 1,
      name: 'Current name $reads',
      email: 'customer@example.test',
      phone: '0123456789',
      profileImage: 'data:image/png;base64,***',
      type: 'COMPANY',
      companyName: 'Dila',
      companyRegistrationNumber: 'REG123',
    );
  }

  @override
  Future<CustomerProfile> updateMyProfile({
    required String name,
    required String email,
    String? phone,
    String? companyName,
    String? companyRegistrationNumber,
    String? profileImage,
    bool clearPhone = false,
  }) {
    saves++;
    submittedImage = profileImage;
    submittedPhone = phone;
    return result.future;
  }
}

class _DriverProfileService extends DriverProfileService {
  final result = Completer<DriverProfile>();
  int reads = 0;
  int saves = 0;
  String? submittedImage;

  @override
  Future<DriverProfile> getProfile() async => DriverProfile(
        id: 1,
        name: 'Fresh driver ${++reads}',
        email: 'driver@example.test',
        phone: '0123456789',
        profileImage: 'invalid base64',
      );

  @override
  Future<DriverProfile> updateProfile({
    required String name,
    required String email,
    required String phone,
    String? profileImage,
  }) {
    saves++;
    submittedImage = profileImage;
    return result.future;
  }
}

Future<void> _tap(WidgetTester tester, String label) async {
  final finder = find.text(label).last;
  await tester.ensureVisible(finder);
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  test('Malaysian phone accepts local and +60 formats, rejects letters and short numbers', () {
    for (final phone in ['012-345 6789', '+60 12-3456789', '03 1234 5678', ' 60123456789 ']) {
      expect(isMalaysianProfilePhone(phone), isTrue, reason: phone);
    }
    for (final phone in ['012ABC6789', '01234', '+1 2012345678', '012345678901234']) {
      expect(isMalaysianProfilePhone(phone), isFalse, reason: phone);
    }
  });

  testWidgets('customer edit loads each time, confirms discard and submits once', (tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final service = _ProfileService();
    addTearDown(service.dispose);
    await tester.pumpWidget(MaterialApp(
      home: Builder(builder: (context) => Scaffold(
        body: TextButton(
          onPressed: () => Navigator.push<CustomerProfile>(context, MaterialPageRoute(
            builder: (_) => UpdateAccountPage(profileService: service),
          )),
          child: const Text('Open editor'),
        ),
      )),
    ));

    await _tap(tester, 'Open editor');
    expect(find.text('Current name 1'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await _tap(tester, 'Cancel');
    expect(find.text('Open editor'), findsOneWidget);
    await _tap(tester, 'Open editor');
    expect(find.text('Current name 2'), findsOneWidget);
    final name = find.widgetWithText(TextFormField, 'Full name');
    await tester.enterText(name, 'Unsaved');
    await _tap(tester, 'Cancel');
    expect(find.text('Discard changes?'), findsOneWidget);
    await _tap(tester, 'Keep editing');
    expect(find.text('Unsaved'), findsOneWidget);

    final save = find.widgetWithText(FilledButton, 'Save changes');
    await tester.ensureVisible(save);
    await tester.tap(save);
    await tester.pump();
    expect(service.saves, 1);
    expect(service.submittedImage, isNull);
    expect(service.submittedPhone, isNull);
    expect(tester.widget<FilledButton>(find.byType(FilledButton)).onPressed, isNull);
    service.result.complete(const CustomerProfile(id: 1, name: 'Unsaved', email: 'customer@example.test'));
    await tester.pumpAndSettle();
    expect(find.text('Open editor'), findsOneWidget);
    expect(find.text('Profile updated.'), findsOneWidget);
  });

  testWidgets('Driver edit handles fresh loads, discard and explicit removal',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(800, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final service = _DriverProfileService();
    addTearDown(service.dispose);
    await tester.pumpWidget(MaterialApp(
      home: Builder(builder: (context) => Scaffold(
        body: TextButton(
          onPressed: () => Navigator.push<DriverProfile>(context, MaterialPageRoute(
            builder: (_) => DriverUpdateAccountPage(profileService: service),
          )),
          child: const Text('Open driver editor'),
        ),
      )),
    ));
    await _tap(tester, 'Open driver editor');
    expect(find.text('Fresh driver 1'), findsOneWidget);
    await _tap(tester, 'Cancel');
    expect(find.text('Open driver editor'), findsOneWidget);
    await _tap(tester, 'Open driver editor');
    expect(find.text('Fresh driver 2'), findsOneWidget);
    await _tap(tester, 'Remove photo');
    await _tap(tester, 'Cancel');
    expect(find.text('Discard changes?'), findsOneWidget);
    await _tap(tester, 'Keep editing');

    final save = find.widgetWithText(FilledButton, 'Save changes');
    await tester.ensureVisible(save);
    await tester.tap(save);
    await tester.pump();
    expect(service.saves, 1);
    expect(service.submittedImage, '');
    expect(tester.widget<FilledButton>(find.byType(FilledButton)).onPressed, isNull);
    service.result.complete(const DriverProfile(id: 1, name: 'Fresh driver 2', email: 'driver@example.test'));
    await tester.pumpAndSettle();
    expect(find.text('Open driver editor'), findsOneWidget);
    expect(find.text('Profile updated.'), findsOneWidget);
  });
}
