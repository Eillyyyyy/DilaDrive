import 'package:diladrive_customer/config/app_config.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  tearDown(() => debugDefaultTargetPlatformOverride = null);

  test('desktop default uses localhost without an /api suffix', () {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    expect(AppConfig.apiBaseUrl, 'http://localhost:8080');
  });

  test('Android emulator default uses the host machine address', () {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    expect(AppConfig.apiBaseUrl, 'http://10.0.2.2:8080');
  });
}
