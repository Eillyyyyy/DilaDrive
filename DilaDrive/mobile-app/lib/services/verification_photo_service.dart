import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';

import '../config/app_config.dart';
import '../models/verification_photo.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';

class VerificationPhotoService {
  VerificationPhotoService({http.Client? client})
      : _client = client ?? createApiHttpClient();

  final http.Client _client;

  void dispose() => _client.close();

  Future<List<VerificationPhoto>> list(int assignmentId) async {
    final response = await _request(
      () => _client.get(
        Uri.parse(
            '${AppConfig.apiBaseUrl}/api/assignments/$assignmentId/photos'),
        headers: {'Accept': 'application/json', ...MobileSession.headers},
      ),
    );
    final Object? body;
    try {
      body = jsonDecode(response.body);
    } on FormatException {
      throw const VerificationPhotoException(
        'The server returned an invalid photo list.',
      );
    }
    if (body is! List) {
      throw const VerificationPhotoException(
        'The server returned an invalid photo list.',
      );
    }
    try {
      return body.map((entry) {
        if (entry is! Map<String, dynamic>) {
          throw const FormatException('Invalid photo record.');
        }
        return VerificationPhoto.fromJson(entry);
      }).toList(growable: false);
    } on FormatException catch (error) {
      throw VerificationPhotoException(error.message);
    }
  }

  Future<void> upload(int assignmentId, String type, XFile image) async {
    await _request(() async {
      final request = http.MultipartRequest(
        'POST',
        Uri.parse(
            '${AppConfig.apiBaseUrl}/api/assignments/$assignmentId/photos'),
      )
        ..headers.addAll(MobileSession.headers)
        ..fields['photoType'] = type
        ..files.add(http.MultipartFile.fromBytes(
          'file',
          await image.readAsBytes(),
          filename: image.name,
        ));
      return http.Response.fromStream(await _client.send(request));
    });
  }

  Future<List<int>> read(int assignmentId, int photoId) async {
    final response = await _request(
      () => _client.get(
        Uri.parse(
          '${AppConfig.apiBaseUrl}/api/assignments/$assignmentId/photos/$photoId/content',
        ),
        headers: MobileSession.headers,
      ),
    );
    return response.bodyBytes;
  }

  Future<http.Response> _request(
    Future<http.Response> Function() send,
  ) async {
    if (!MobileSession.isAuthenticated) {
      throw const VerificationPhotoException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }

    late final http.Response response;
    try {
      response = await send().timeout(const Duration(seconds: 30));
    } on TimeoutException {
      throw const VerificationPhotoException(
        'The photo service took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const VerificationPhotoException(
        'Cannot connect to DilaDrive. Check the API connection and try again.',
      );
    }

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return response;
    }
    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const VerificationPhotoException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }
    if (response.statusCode == 403) {
      throw const VerificationPhotoException(
        'These photos are only available to the assigned driver.',
      );
    }
    final message = _serverMessage(response.body);
    throw VerificationPhotoException(
      message ??
          'Photo request failed (${response.statusCode}). Please try again.',
    );
  }

  String? _serverMessage(String body) {
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic> && decoded['message'] is String) {
        return decoded['message'] as String;
      }
    } on FormatException {
      return null;
    }
    return null;
  }
}

class VerificationPhotoException implements Exception {
  const VerificationPhotoException(this.message, {this.requiresLogin = false});

  final String message;
  final bool requiresLogin;

  @override
  String toString() => message;
}
