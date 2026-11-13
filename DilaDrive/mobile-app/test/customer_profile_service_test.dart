import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:diladrive_customer/services/customer_profile_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';

void main() {
  tearDown(MobileSession.clear);

  test('loads profile using the current mobile session', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    late http.Request captured;
    final service = CustomerProfileService(
      client: MockClient((request) async {
        captured = request;
        return http.Response(
          jsonEncode({
            'id': 7,
            'name': 'Ayla Customer',
            'email': 'ayla@example.com',
            'phone': '0123456789',
            'status': 'ACTIVE',
            'type': 'COMPANY',
            'companyName': 'Dila Transport Sdn Bhd',
            'companyRegistrationNumber': '202601234567',
          }),
          200,
        );
      }),
    );
    addTearDown(service.dispose);

    final profile = await service.getMyProfile();

    expect(captured.url.path, '/api/customers/me');
    expect(captured.headers['cookie'], 'JSESSIONID=customer-session');
    expect(profile.id, 7);
    expect(profile.name, 'Ayla Customer');
    expect(profile.email, 'ayla@example.com');
    expect(profile.phone, '0123456789');
    expect(profile.status, 'ACTIVE');
    expect(profile.type, 'COMPANY');
    expect(profile.companyName, 'Dila Transport Sdn Bhd');
    expect(profile.companyRegistrationNumber, '202601234567');
  });

  test('updates only submitted profile fields with the session cookie',
      () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    late http.Request captured;
    final service = CustomerProfileService(
      client: MockClient((request) async {
        captured = request;
        return http.Response(
          jsonEncode({
            'id': 7,
            'name': 'Ayla Updated',
            'email': 'updated@example.com',
            'phone': '0123456789',
            'status': 'ACTIVE',
            'type': 'COMPANY',
            'companyName': 'Ayla Transport',
            'companyRegistrationNumber': 'REG-123',
          }),
          200,
        );
      }),
    );
    addTearDown(service.dispose);

    final profile = await service.updateMyProfile(
      name: ' Ayla Updated ',
      email: ' updated@example.com ',
      phone: '0123456789',
      companyName: ' Ayla Transport ',
      companyRegistrationNumber: ' REG-123 ',
    );

    expect(captured.method, 'PUT');
    expect(captured.url.path, '/api/customers/me');
    expect(captured.headers['cookie'], 'JSESSIONID=customer-session');
    expect(jsonDecode(captured.body), {
      'name': 'Ayla Updated',
      'email': 'updated@example.com',
      'phone': '0123456789',
      'companyName': 'Ayla Transport',
      'companyRegistrationNumber': 'REG-123',
    });
    expect(profile.name, 'Ayla Updated');
  });

  test('shows a backend profile validation message', () async {
    const message =
        'Corporate registration details are only available to company customers.';
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    final service = CustomerProfileService(
      client: MockClient(
        (_) async => http.Response(jsonEncode({'message': message}), 403),
      ),
    );
    addTearDown(service.dispose);

    await expectLater(
      service.updateMyProfile(
        name: 'Ayla Customer',
        email: 'ayla@example.com',
        companyName: 'Ayla Transport',
      ),
      throwsA(isA<CustomerProfileException>().having(
        (error) => error.message,
        'message',
        message,
      )),
    );
  });
}
