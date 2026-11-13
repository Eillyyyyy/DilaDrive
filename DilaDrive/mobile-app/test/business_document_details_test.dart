import 'package:diladrive_customer/models/business_document.dart';
import 'package:diladrive_customer/screens/customer/business_documents.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows persisted business document details and remarks',
      (tester) async {
    const document = BusinessDocument(
      id: 81,
      number: 'INV-0081',
      type: 'INVOICE',
      bookingId: 13,
      sourceDocumentId: 68,
      amount: 125.5,
      status: 'ISSUED',
      remarks: 'Corporate account invoice',
    );

    await tester.pumpWidget(const MaterialApp(
      home: BusinessDocumentDetailsPage(document: document),
    ));

    expect(find.text('INV-0081'), findsOneWidget);
    expect(find.text('BK-000013'), findsOneWidget);
    expect(find.text('RM 125.50'), findsOneWidget);
    expect(find.text('#68'), findsOneWidget);
    expect(find.text('Corporate account invoice'), findsOneWidget);
  });
}
