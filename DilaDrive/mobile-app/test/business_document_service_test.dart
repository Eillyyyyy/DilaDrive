import 'dart:convert';

import 'package:diladrive_customer/services/business_document_service.dart';
import 'package:diladrive_customer/services/mobile_session.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  setUp(() => MobileSession.storeResponseCookies(
      {'set-cookie': 'JSESSIONID=company-session; Path=/'}));
  tearDown(MobileSession.clear);

  test('loads company documents from the signed-in customer endpoint',
      () async {
    final service = BusinessDocumentService(
      client: MockClient((request) async {
        expect(request.url.path, '/api/documents/customer/mine');
        expect(request.headers['cookie'], 'JSESSIONID=company-session');
        return http.Response(
          jsonEncode([
            {
              'documentId': 81,
              'documentNumber': 'INV-0081',
              'documentType': 'INVOICE',
              'bookingId': 13,
              'sourceDocumentId': 68,
              'amount': 125.5,
              'status': 'ISSUED',
              'remarks': 'Corporate account invoice',
              'createdAt': '2026-11-12T12:30:00',
            }
          ]),
          200,
        );
      }),
    );
    addTearDown(service.dispose);

    final documents = await service.getMyDocuments();
    expect(documents, hasLength(1));
    expect(documents.single.number, 'INV-0081');
    expect(documents.single.bookingReference, 'BK-000013');
    expect(documents.single.sourceDocumentId, 68);
    expect(documents.single.amount, 125.5);
    expect(documents.single.remarks, 'Corporate account invoice');
  });

  test('loads an owned document PDF with the authenticated session', () async {
    final service = BusinessDocumentService(
      client: MockClient((request) async {
        expect(request.url.path, '/api/documents/customer/81/pdf');
        expect(request.headers['cookie'], 'JSESSIONID=company-session');
        expect(request.headers['accept'], 'application/pdf');
        return http.Response.bytes(
          '%PDF-test'.codeUnits,
          200,
          headers: {'content-type': 'application/pdf'},
        );
      }),
    );
    addTearDown(service.dispose);

    expect(await service.getDocumentPdf(81), '%PDF-test'.codeUnits);
  });
}
