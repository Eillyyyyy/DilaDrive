import 'dart:convert';

import 'package:diladrive_customer/services/driver_profile_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  setUp(() => MobileSession.storeResponseCookies(
      {'set-cookie': 'JSESSIONID=driver-profile-session; Path=/'}));
  tearDown(MobileSession.clear);

  test('loads actual profile details through the driver session', () async {
    final service = DriverProfileService(
      client: MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/drivers/me');
        expect(request.headers['cookie'], 'JSESSIONID=driver-profile-session');
        return http.Response(
            jsonEncode({
              'id': 42,
              'name': 'Driver Name',
              'email': 'driver@example.com',
              'phone': '0123456789',
              'status': 'ACTIVE',
              'licenseNumber': 'D-123',
              'licenseExpiry': '2028-12-31',
              'driverType': 'FULL_TIME',
              'availability': 'AVAILABLE',
            }),
            200);
      }),
    );
    addTearDown(service.dispose);

    final profile = await service.getProfile();
    expect(profile.id, 42);
    expect(profile.licenseNumber, 'D-123');
    expect(profile.availability, 'AVAILABLE');
  });

  test('updates only name, email and phone via the owned profile endpoint',
      () async {
    final service = DriverProfileService(
      client: MockClient((request) async {
        expect(request.method, 'PUT');
        expect(request.url.path, '/api/drivers/me');
        expect(jsonDecode(request.body), {
          'name': 'New Name',
          'email': 'new@example.com',
          'phone': '0190000000',
        });
        return http.Response(
            jsonEncode({
              'id': 42,
              'name': 'New Name',
              'email': 'new@example.com',
              'phone': '0190000000',
              'status': 'ACTIVE',
            }),
            200);
      }),
    );
    addTearDown(service.dispose);

    final updated = await service.updateProfile(
      name: ' New Name ',
      email: ' new@example.com ',
      phone: ' 0190000000 ',
    );
    expect(updated.name, 'New Name');
    expect(updated.email, 'new@example.com');
    expect(updated.phone, '0190000000');
    expect(updated.status, 'ACTIVE');
  });

  test('loads a legacy driver profile without a phone number', () async {
    final service = DriverProfileService(
      client: MockClient((request) async => http.Response(
            jsonEncode({
              'id': 43,
              'name': 'Legacy Driver',
              'email': 'legacy@example.com',
              'phone': null,
              'status': 'ACTIVE',
            }),
            200,
          )),
    );
    addTearDown(service.dispose);

    final profile = await service.getProfile();

    expect(profile.phone, isNull);
  });
}
