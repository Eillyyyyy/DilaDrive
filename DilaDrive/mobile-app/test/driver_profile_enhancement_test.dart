import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:diladrive_customer/models/driver_profile.dart';
import 'package:diladrive_customer/widgets/driver_profile_widgets.dart';
import 'package:diladrive_customer/services/driver_profile_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:diladrive_customer/controllers/driver_profile_controller.dart';
import 'package:diladrive_customer/screens/driver/view_account.dart';
import 'package:diladrive_customer/screens/driver/update_account.dart';
import 'package:diladrive_customer/screens/driver/profile.dart';
import 'package:diladrive_customer/models/mobile_user.dart';

const profile = DriverProfile(
    id: 908172,
    name: 'Professional Driver',
    email: 'driver@example.com',
    phone: '0123456789',
    driverType: 'Company Driver',
    status: 'ACTIVE',
    availability: 'AVAILABLE',
    licenseNumber: 'LICENSE',
    licenseExpiry: '2028-12-01',
    vehicleModel: 'Vehicle Model',
    vehiclePlate: 'PLATE7',
    totalTrips: 12,
    completedTrips: 8,
    upcomingTrips: 3);

void main() {
  tearDown(MobileSession.clear);
  test(
      'profile parses additive summary/photo fields and supports older API responses',
      () {
    final result = DriverProfile.fromJson({
      'id': 1,
      'name': 'Driver',
      'email': 'driver@example.com',
      'totalTrips': 9,
      'completedTrips': 4,
      'upcomingTrips': 2,
      'vehiclePlate': 'PLATE',
      'profileImage': null
    });
    expect(result.totalTrips, 9);
    expect(result.vehiclePlate, 'PLATE');
    expect(result.profileImage, isNull);
    expect(
        DriverProfile.fromJson(
                {'id': 1, 'name': 'Driver', 'email': 'driver@example.com'})
            .totalTrips,
        isNull);
  });

  test(
      'photo update and password change use owned endpoints and session cookie',
      () async {
    MobileSession.storeResponseCookies(
        {'set-cookie': 'JSESSIONID=profile-test; Path=/'});
    final service = DriverProfileService(client: MockClient((request) async {
      expect(request.headers['cookie'], 'JSESSIONID=profile-test');
      final body = jsonDecode(request.body) as Map;
      expect(body.containsKey('id'), isFalse);
      expect(body.containsKey('driverType'), isFalse);
      if (request.url.path.endsWith('/password')) {
        expect(request.method, 'POST');
        expect(body, {
          'currentPassword': 'old-password',
          'newPassword': 'new-password123'
        });
        return http.Response('', 204);
      }
      expect(request.url.path, '/api/drivers/me');
      expect(body['profileImage'], 'data:image/png;base64,example');
      return http.Response(
          jsonEncode(
              {'id': 1, 'name': 'Driver', 'email': 'driver@example.com'}),
          200);
    }));
    addTearDown(service.dispose);
    await service.updateProfile(
        name: 'Driver',
        email: 'driver@example.com',
        phone: '0123456789',
        profileImage: 'data:image/png;base64,example');
    await service.changePassword(
        currentPassword: 'old-password', newPassword: 'new-password123');
  });

  test('password error preserves backend validation message', () async {
    MobileSession.storeResponseCookies(
        {'set-cookie': 'JSESSIONID=profile-test; Path=/'});
    final service = DriverProfileService(
        client: MockClient((_) async => http.Response(
            '{"message":"Current password is incorrect."}', 403)));
    addTearDown(service.dispose);
    await expectLater(
        service.changePassword(
            currentPassword: 'wrong', newPassword: 'password123'),
        throwsA(isA<DriverProfileException>().having((error) => error.message,
            'message', 'Current password is incorrect.')));
  });

  test('controller ignores a response arriving after disposal', () async {
    final completer = Completer<DriverProfile>();
    final service = FakeProfileService(response: completer.future);
    final controller = DriverProfileController(service: service);
    final request = controller.load();
    controller.dispose();
    completer.complete(profile);
    await request;
    expect(controller.profile, isNull);
    service.dispose();
  });

  testWidgets(
      'read-only account contains only driver and vehicle sections without statistics',
      (tester) async {
    final service = FakeProfileService();
    addTearDown(service.dispose);
    await tester.pumpWidget(
        MaterialApp(home: DriverViewAccountPage(profileService: service)));
    await tester.pumpAndSettle();
    expect(find.text('Total Trips'), findsNothing);
    expect(find.text('Completed Trips'), findsNothing);
    expect(find.text('Upcoming Trips'), findsNothing);
    expect(find.text('Driver Information'), findsOneWidget);
    expect(find.text('Assigned Vehicle'), findsOneWidget);
    expect(find.byType(DriverProfileAvatar), findsOneWidget);
    expect(find.text('Company Driver'), findsWidgets);
    expect(find.text('Driver ID'), findsNothing);
    expect(find.text('908172'), findsNothing);
    expect(find.byType(TextFormField), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'update profile restricts editing and validates password confirmation',
      (tester) async {
    final service = FakeProfileService();
    addTearDown(service.dispose);
    await tester.pumpWidget(
        MaterialApp(home: DriverUpdateAccountPage(profileService: service)));
    await tester.pumpAndSettle();
    expect(find.text('Choose profile photo'), findsOneWidget);
    expect(find.text('Driver ID'), findsNothing);
    expect(find.byType(TextFormField), findsNWidgets(3));
    final expansion = find.text('Change password');
    await tester.ensureVisible(expansion);
    await tester.tap(expansion);
    await tester.pumpAndSettle();
    Finder field(String label) => find.byWidgetPredicate((widget) =>
        widget is TextField && widget.decoration?.labelText == label);
    await tester.enterText(field('Current password'), 'old-password');
    await tester.enterText(field('New password'), 'password123');
    await tester.enterText(field('Confirm new password'), 'different');
    final submit = find.widgetWithText(FilledButton, 'Change password');
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();
    expect(find.text('Passwords do not match.'), findsOneWidget);
    expect(service.passwordCalls, 0);
    expect(field('Driver Type'), findsNothing);
    expect(field('Account Status'), findsNothing);
  });
  testWidgets(
      'profile menu matches customer navigation and has no dashboard cards',
      (tester) async {
    final service = FakeProfileService();
    addTearDown(service.dispose);
    await tester.pumpWidget(MaterialApp(
        home: DriverProfilePage(
            driver: const MobileUser(
                id: 908172,
                name: 'Driver',
                email: 'driver@example.com',
                role: 'DRIVER'),
            profileService: service)));
    await tester.pumpAndSettle();
    for (final label in [
      'View account',
      'Update account',
      'Vehicle information',
      'Change password',
      'Sign out'
    ]) {
      expect(find.text(label), findsOneWidget);
    }
    for (final label in [
      'Total Trips',
      'Completed Trips',
      'Upcoming Trips',
      'Assigned Vehicle',
      'Driver Information'
    ]) {
      expect(find.text(label), findsNothing);
    }
    await tester.tap(find.text('Vehicle information'));
    await tester.pumpAndSettle();
    expect(find.text('Assigned Vehicle'), findsOneWidget);
    expect(find.text('Driver Information'), findsNothing);
    expect(find.byType(TextFormField), findsNothing);
    expect(find.text('PLATE7'), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sign out'));
    await tester.pumpAndSettle();
    expect(find.text('Sign out?'), findsOneWidget);
    await tester.tap(find.text('Stay'));
    await tester.pumpAndSettle();
    expect(find.text('View account'), findsOneWidget);
  });
  testWidgets(
      'updating contacts returns to profile and refreshes the parent identity',
      (tester) async {
    final service = FakeProfileService();
    addTearDown(service.dispose);
    MobileUser? updated;
    await tester.pumpWidget(MaterialApp(
        home: DriverProfilePage(
            driver: const MobileUser(
                id: 908172,
                name: 'Driver',
                email: 'driver@example.com',
                role: 'DRIVER'),
            profileService: service,
            onDriverUpdated: (value) => updated = value)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Update account'));
    await tester.pumpAndSettle();
    final name = find.byWidgetPredicate((widget) =>
        widget is TextField && widget.decoration?.labelText == 'Full name');
    await tester.enterText(name, 'Updated Driver');
    final save = find.widgetWithText(FilledButton, 'Save changes');
    await tester.ensureVisible(save);
    await tester.tap(save);
    await tester.pumpAndSettle();
    expect(find.text('Updated Driver'), findsOneWidget);
    expect(updated?.name, 'Updated Driver');
    expect(updated?.id, 908172);
    expect(updated?.role, 'DRIVER');
    expect(find.text('View account'), findsOneWidget);
  });
  testWidgets(
      'change password menu saves through the existing service and returns to profile',
      (tester) async {
    final service = FakeProfileService();
    addTearDown(service.dispose);
    await tester.pumpWidget(MaterialApp(
        home: DriverProfilePage(
            driver: const MobileUser(
                id: 908172,
                name: 'Driver',
                email: 'driver@example.com',
                role: 'DRIVER'),
            profileService: service)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Change password'));
    await tester.pumpAndSettle();
    Finder field(String label) => find.byWidgetPredicate((widget) =>
        widget is TextField && widget.decoration?.labelText == label);
    await tester.enterText(field('Current password'), 'old-password');
    await tester.enterText(field('New password'), 'new-password123');
    await tester.enterText(field('Confirm new password'), 'new-password123');
    final save = find.widgetWithText(FilledButton, 'Change password');
    await tester.ensureVisible(save);
    await tester.tap(save);
    await tester.pumpAndSettle();
    expect(service.passwordCalls, 1);
    expect(find.text('View account'), findsOneWidget);
    expect(find.text('Password changed successfully.'), findsOneWidget);
  });
}

class FakeProfileService extends DriverProfileService {
  FakeProfileService({this.response});
  final Future<DriverProfile>? response;
  int passwordCalls = 0;
  @override
  Future<DriverProfile> updateProfile(
          {required String name,
          required String email,
          required String phone,
          String? profileImage}) async =>
      DriverProfile(
          id: profile.id,
          name: name.trim(),
          email: email.trim(),
          phone: phone.trim(),
          status: profile.status,
          driverType: profile.driverType,
          vehicleModel: profile.vehicleModel,
          vehiclePlate: profile.vehiclePlate);
  @override
  Future<DriverProfile> getProfile() => response ?? Future.value(profile);
  @override
  Future<void> changePassword(
      {required String currentPassword, required String newPassword}) async {
    passwordCalls++;
  }
}
