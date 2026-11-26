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

Future<http.Response> fetchMobilePages(
  http.Client client,
  Uri uri, {
  Map<String, String>? headers,
}) async {
  http.Response response = await client.get(uri, headers: headers);
  if (response.statusCode != 200 || !response.headers.containsKey('x-next-page')) {
    return response;
  }

  final items = <dynamic>[];
  int page = int.tryParse(uri.queryParameters['page'] ?? '') ?? 0;
  while (true) {
    if (response.statusCode != 200) return response;
    final Object? data;
    try {
      data = jsonDecode(response.body);
    } on FormatException {
      return response;
    }
    if (data is! List) return response;
    items.addAll(data);

    final next = response.headers['x-next-page'];
    if (next == null) break;
    if (int.tryParse(next) != page + 1 || data.isEmpty) {
      throw http.ClientException('Invalid pagination response', uri);
    }
    page++;
    response = await client.get(
      uri.replace(queryParameters: {...uri.queryParameters, 'page': next}),
      headers: headers,
    );
  }
  return http.Response(jsonEncode(items), 200, headers: response.headers);
}
