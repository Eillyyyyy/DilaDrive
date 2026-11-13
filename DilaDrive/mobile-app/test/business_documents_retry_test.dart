import 'package:diladrive_customer/models/business_document.dart';
import 'package:diladrive_customer/screens/customer/business_documents.dart';
import 'package:diladrive_customer/services/business_document_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
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
