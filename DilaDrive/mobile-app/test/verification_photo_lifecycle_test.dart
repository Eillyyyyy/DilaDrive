import 'dart:async';
import 'dart:typed_data';

import 'package:diladrive_customer/models/verification_photo.dart';
import 'package:diladrive_customer/screens/driver/upload_verification_photo.dart';
import 'package:diladrive_customer/services/verification_photo_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';

void main() {
  testWidgets('does not upload a picked image after the page is disposed',
      (tester) async {
    final pickedImage = Completer<XFile?>();
    final service = _RecordingPhotoService();

    await tester.pumpWidget(MaterialApp(
      home: UploadVerificationPhotoPage(
        assignmentId: 9,
        allowUpload: true,
        service: service,
        pickImage: (_) => pickedImage.future,
      ),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Add photo'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Pickup verification'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Take a photo'));
    await tester.pump();

    await tester.pumpWidget(const MaterialApp(home: SizedBox()));
    pickedImage.complete(XFile.fromData(
      Uint8List.fromList([1, 2, 3]),
      name: 'pickup.png',
    ));
    await tester.pump();

    expect(service.uploadCount, 0);
    expect(tester.takeException(), isNull);
  });
}

class _RecordingPhotoService extends VerificationPhotoService {
  int uploadCount = 0;

  @override
  Future<List<VerificationPhoto>> list(int assignmentId) async => const [];

  @override
  Future<void> upload(int assignmentId, String type, XFile image) async {
    uploadCount++;
  }

  @override
  void dispose() {}
}
