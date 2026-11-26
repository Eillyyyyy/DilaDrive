import 'package:diladrive_customer/models/business_document.dart';
import 'package:diladrive_customer/screens/customer/business_documents.dart';
import 'package:diladrive_customer/services/business_document_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
      'keeps quotations and invoices while hiding receipts and payment status',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: BusinessDocumentsPage(documentService: _CustomerDocuments()),
    ));
    await tester.pumpAndSettle();
    expect(find.text('QUO-1'), findsOneWidget);
    expect(find.text('INV-2'), findsOneWidget);
    expect(find.text('REC-3'), findsNothing);
    expect(find.text('PAID'), findsNothing);
    await tester.tap(find.text('INV-2'));
    await tester.pumpAndSettle();
    expect(find.text('ISSUED'), findsOneWidget);
    expect(find.text('PAID'), findsNothing);
  });
  testWidgets('retry loads business documents without a setState error',
      (tester) async {
    final service = _RetryingDocumentService();
    await tester.pumpWidget(MaterialApp(
      home: BusinessDocumentsPage(documentService: service),
    ));
    await tester.pumpAndSettle();

    expect(find.text('Try again'), findsOneWidget);
    expect(service.requestCount, 1);

    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();

    expect(service.requestCount, 2);
    expect(find.text('No business documents yet'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

class _RetryingDocumentService extends BusinessDocumentService {
  int requestCount = 0;

  @override
  Future<List<BusinessDocument>> getMyDocuments() async {
    requestCount++;
    if (requestCount == 1) {
      throw const BusinessDocumentException('Temporary service failure.');
    }
    return const [];
  }

  @override
  void dispose() {}
}

class _CustomerDocuments extends BusinessDocumentService {
  @override
  Future<List<BusinessDocument>> getMyDocuments() async => const [
        BusinessDocument(
            id: 1,
            number: 'QUO-1',
            type: 'QUOTATION',
            bookingId: 1,
            amount: 25,
            status: 'APPROVED'),
        BusinessDocument(
            id: 2,
            number: 'INV-2',
            type: 'INVOICE',
            bookingId: 1,
            amount: 25,
            status: 'PAID'),
        BusinessDocument(
            id: 3,
            number: 'REC-3',
            type: 'RECEIPT',
            bookingId: 1,
            amount: 25,
            status: 'PAID'),
      ];
}
