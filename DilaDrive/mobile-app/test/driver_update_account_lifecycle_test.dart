import 'dart:async';

import 'package:diladrive_customer/models/driver_profile.dart';
import 'package:diladrive_customer/screens/driver/update_account.dart';
import 'package:diladrive_customer/services/driver_profile_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('does not write profile fields after the page is disposed',
      (tester) async {
    final response = Completer<DriverProfile>();
    final service = _DeferredProfileService(response.future);

    await tester.pumpWidget(MaterialApp(
      home: DriverUpdateAccountPage(profileService: service),
    ));
    await tester.pump();

    await tester.pumpWidget(const MaterialApp(home: SizedBox()));
    response.complete(const DriverProfile(
      id: 42,
      name: 'Driver Name',
      email: 'driver@example.com',
      phone: '0123456789',
    ));
    await tester.pump();

    expect(tester.takeException(), isNull);
  });
}

class _DeferredProfileService extends DriverProfileService {
  _DeferredProfileService(this.response);

  final Future<DriverProfile> response;

  @override
  Future<DriverProfile> getProfile() => response;

  @override
  void dispose() {}
}
