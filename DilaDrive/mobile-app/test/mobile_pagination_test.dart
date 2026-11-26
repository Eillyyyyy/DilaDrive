import 'dart:convert';

import 'package:diladrive_customer/services/api_http_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('follows next page and retains the session header', () async {
    final requestedPages = <String?>[];
    final client = MockClient((request) async {
      expect(request.headers['cookie'], 'JSESSIONID=test-session');
      final page = request.url.queryParameters['page'];
      requestedPages.add(page);
      if (page == null) {
        return http.Response('[{"id":1}]', 200, headers: {'x-next-page': '1'});
      }
      return http.Response('[{"id":2}]', 200);
    });

    final response = await fetchMobilePages(
      client,
      Uri.parse('https://example.test/api/bookings/mine'),
      headers: {'cookie': 'JSESSIONID=test-session'},
    );

    expect(requestedPages, [null, '1']);
    expect(jsonDecode(response.body), [
      {'id': 1},
      {'id': 2},
    ]);
  });
}
