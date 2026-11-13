import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:diladrive_customer/models/customer_booking.dart';
import 'package:diladrive_customer/screens/customer/payment.dart';
import 'package:diladrive_customer/services/customer_payment_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:diladrive_customer/theme/app_theme.dart';

void main() {
  tearDown(MobileSession.clear);

  testWidgets('shows a pending proof and prevents duplicate submission',
      (tester) async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    await tester.pumpWidget(_paymentApp([
      _payment(id: 3, status: 'PENDING_VERIFICATION', amount: 50),
    ]));
    await tester.pumpAndSettle();

    expect(find.text('Your proof is with Admin for review.'), findsOneWidget);
    await tester.dragUntilVisible(
      find.text('Waiting for Admin review'),
      find.byType(ListView),
      const Offset(0, -400),
    );
    await tester.pumpAndSettle();
    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Waiting for Admin review'),
    );
    expect(button.onPressed, isNull);
  });

  testWidgets('shows the live remaining fare and payment status',
      (tester) async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    await tester.pumpWidget(_paymentApp([
      _payment(id: 4, status: 'VERIFIED', amount: 80, surcharge: 4.8),
    ]));
    await tester.pumpAndSettle();

    expect(find.text('RM 80.00'), findsOneWidget);
    expect(find.text('RM 120.00'), findsOneWidget);
    expect(find.text('Verified'), findsOneWidget);
    expect(find.textContaining('RM 4.80 card surcharge'), findsOneWidget);
    await tester.dragUntilVisible(
      find.text('Upload payment proof'),
      find.byType(ListView),
      const Offset(0, -400),
    );
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Upload payment proof'),
          )
          .onPressed,
      isNotNull,
    );
  });

  testWidgets('disables payment until Admin sets the booking fare',
      (tester) async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=customer-session; Path=/; HttpOnly',
    });
    await tester.pumpWidget(_paymentApp(const [], fare: null));
    await tester.pumpAndSettle();

    expect(find.text('Admin has not set the final fare yet.'), findsOneWidget);
    await tester.dragUntilVisible(
      find.text('Upload payment proof'),
      find.byType(ListView),
      const Offset(0, -400),
    );
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Upload payment proof'),
          )
          .onPressed,
      isNull,
    );
  });
}

Widget _paymentApp(List<Map<String, Object?>> payments, {double? fare = 200}) {
  final service = CustomerPaymentService(
    client: MockClient((request) async {
      expect(request.url.path, '/api/payments/mine');
      return http.Response(jsonEncode(payments), 200);
    }),
  );
  return MaterialApp(
    theme: AppTheme.lightTheme,
    home: PaymentPage(
      booking: CustomerBooking(id: 12, status: 'CONFIRMED', fare: fare),
      paymentService: service,
    ),
  );
}

Map<String, Object?> _payment({
  required int id,
  required String status,
  required double amount,
  double surcharge = 0,
}) =>
    {
      'paymentId': id,
      'bookingId': 12,
      'bookingReference': 'BK-000012',
      'bookingFare': 200,
      'amount': amount,
      'surchargeAmount': surcharge,
      'paymentMethod': surcharge > 0 ? 'CARD' : 'BANK_TRANSFER',
      'paymentStatus': status,
      'submittedAt': '2026-11-13T02:00:00Z',
    };
