import 'dart:convert';

import 'package:diladrive_customer/screens/driver/upload_verification_photo.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:diladrive_customer/services/verification_photo_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  tearDown(MobileSession.clear);

  testWidgets('reuses photo content requests when the screen rebuilds',
      (tester) async {
    MobileSession.storeResponseCookies({
      'set-cookie': 'JSESSIONID=driver-session; Path=/; HttpOnly',
    });
    var contentRequests = 0;
    final service = VerificationPhotoService(
      client: MockClient((request) async {
        if (request.url.path.endsWith('/content')) {
          contentRequests++;
          return http.Response.bytes([1, 2, 3], 200);
        }
        return http.Response(
          jsonEncode([
            {
              'photoId': 17,
              'photoType': 'PICKUP',
              'uploadedAt': '2026-11-12T10:00:00',
            },
          ]),
          200,
        );
      }),
    );
    addTearDown(service.dispose);

    Widget buildPage() => MaterialApp(
          home: UploadVerificationPhotoPage(
            assignmentId: 41,
            allowUpload: false,
            service: service,
          ),
        );

    await tester.pumpWidget(buildPage());
    await tester.pumpAndSettle();
    expect(contentRequests, 1);

    await tester.pumpWidget(buildPage());
    await tester.pumpAndSettle();

    expect(contentRequests, 1);

    await tester.pumpWidget(const SizedBox.shrink());
    await expectLater(service.list(41), completes);
  });
}
