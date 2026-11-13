import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:diladrive_customer/services/customer_payment_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:diladrive_customer/services/money_validation.dart';

void main() {
  tearDown(MobileSession.clear);

  test('validates decimal money inputs without floating-point errors', () {
    expect(isValidMoneyInput('1.15'), isTrue);
    expect(isValidMoneyInput('100'), isTrue);
    expect(isValidMoneyInput('0.01'), isTrue);
    expect(isValidMoneyInput('1.001'), isFalse);
    expect(isValidMoneyInput('0'), isFalse);
    expect(isValidMoneyInput('NaN'), isFalse);
  });

  test('loads only the signed-in customer payment endpoint', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    late http.Request capturedRequest;
    final service = CustomerPaymentService(
      client: MockClient((request) async {
        capturedRequest = request;
        return http.Response(
          jsonEncode([
            {
              'paymentId': 7,
              'bookingId': 12,
              'bookingReference': 'BK-000012',
              'bookingFare': 220,
              'amount': 100,
              'surchargeAmount': 6,
              'paymentMethod': 'CARD',
              'paymentStatus': 'VERIFIED',
              'submittedAt': '2026-11-13T02:00:00Z',
            },
          ]),
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final payments = await service.getMyPayments();

    expect(capturedRequest.url.path, '/api/payments/mine');
    expect(capturedRequest.headers['cookie'], 'JSESSIONID=customer-session');
    expect(payments, hasLength(1));
    expect(payments.single.bookingReference, 'BK-000012');
    expect(payments.single.totalAmount, 106);
    service.dispose();
  });

  test('requires a customer session before loading payments', () async {
    final service = CustomerPaymentService(
      client: MockClient((_) async => http.Response('[]', 200)),
    );

    await expectLater(
      service.getMyPayments(),
      throwsA(isA<CustomerPaymentException>().having(
        (error) => error.requiresLogin,
        'requiresLogin',
        isTrue,
      )),
    );
    service.dispose();
  });

  test('clears an expired session and surfaces backend rejection details',
      () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=expired-session; Path=/; HttpOnly',
    });
    final service = CustomerPaymentService(
      client: MockClient((_) async => http.Response(
            jsonEncode(
                {'message': 'Payment is not available for this booking.'}),
            401,
          )),
    );

    await expectLater(
      service.getMyPayments(),
      throwsA(isA<CustomerPaymentException>().having(
        (error) => error.requiresLogin,
        'requiresLogin',
        isTrue,
      )),
    );
    expect(MobileSession.isAuthenticated, isFalse);
    service.dispose();
  });

  test('rejects payment amounts with more than two decimals before upload',
      () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    var requests = 0;
    final service = CustomerPaymentService(
      client: MockClient((_) async {
        requests++;
        return http.Response('{}', 201);
      }),
    );
    addTearDown(service.dispose);

    await expectLater(
      service.submit(
        bookingId: 12,
        amount: 10.001,
        paymentMethod: 'BANK_TRANSFER',
        proofBytes: Uint8List.fromList([1, 2, 3]),
        proofFileName: 'proof.png',
      ),
      throwsA(isA<CustomerPaymentException>().having(
        (error) => error.message,
        'message',
        contains('at most two decimal places'),
      )),
    );
    expect(requests, 0);
  });

  test('submits a valid 1.15 payment amount', () async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    late http.Request capturedRequest;
    final service = CustomerPaymentService(
      client: MockClient((request) async {
        capturedRequest = request;
        return http.Response(
          jsonEncode({
            'paymentId': 8,
            'bookingId': 12,
            'bookingReference': 'BK-000012',
            'amount': 1.15,
            'surchargeAmount': 0,
            'paymentMethod': 'BANK_TRANSFER',
            'paymentStatus': 'PENDING',
            'submittedAt': '2026-11-13T02:00:00Z',
          }),
          201,
          headers: {'content-type': 'application/json'},
        );
      }),
    );
    addTearDown(service.dispose);

    await service.submit(
      bookingId: 12,
      amount: 1.15,
      paymentMethod: 'BANK_TRANSFER',
      proofBytes: Uint8List.fromList([1, 2, 3]),
      proofFileName: 'proof.png',
    );

    expect(capturedRequest.body, contains('name="amount"\r\n\r\n1.15'));
  });
}
