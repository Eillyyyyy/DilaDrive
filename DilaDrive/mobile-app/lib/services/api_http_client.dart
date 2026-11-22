import 'dart:convert';

import 'package:http/http.dart' as http;

import 'api_http_client_stub.dart'
    if (dart.library.js_interop) 'api_http_client_web.dart' as platform;
import 'mobile_session.dart';

http.Client createApiHttpClient({http.Client? client}) =>
    _SessionAwareClient(client ?? platform.createApiHttpClient());

class _SessionAwareClient extends http.BaseClient {
  _SessionAwareClient(this._client);

  final http.Client _client;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final generation = MobileSession.generation;
    final response = await _client.send(request);
    if (response.statusCode != 403) return response;

    final buffered = await http.Response.fromStream(response);
    try {
      final error = jsonDecode(buffered.body);
      if (error is Map<String, dynamic> &&
          error['code'] == 'DRIVER_ACCOUNT_DEACTIVATED') {
        final message = error['message'];
        MobileSession.revokeDriverSession(
          generation,
          message is String && message.isNotEmpty
              ? message
              : 'Your driver account has been deactivated. Please contact the administrator.',
        );
      }
    } on FormatException {
      // The calling service still receives the original error body.
    }
    return http.StreamedResponse(
      Stream.value(buffered.bodyBytes),
      buffered.statusCode,
      request: response.request,
      headers: buffered.headers,
      contentLength: response.contentLength,
      isRedirect: response.isRedirect,
      persistentConnection: response.persistentConnection,
      reasonPhrase: response.reasonPhrase,
    );
  }

  @override
  void close() => _client.close();
}
