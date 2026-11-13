import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/customer_payment.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';
import 'money_validation.dart';

class CustomerPaymentService {
  CustomerPaymentService({http.Client? client})
      : _client = client ?? createApiHttpClient();

  final http.Client _client;

  void dispose() => _client.close();

  Future<List<CustomerPayment>> getMyPayments() async {
    final response = await _send(() => _client.get(
          Uri.parse('${AppConfig.apiBaseUrl}/api/payments/mine'),
          headers: {'Accept': 'application/json', ...MobileSession.headers},
        ));
    final Object? payload;
    try {
      payload = jsonDecode(response.body);
    } on FormatException {
      throw const CustomerPaymentException(
          'The server returned an invalid payments response.');
    }
    if (payload is! List) {
      throw const CustomerPaymentException(
          'The server returned an invalid payments response.');
    }
    try {
      return payload.map((item) {
        if (item is! Map<String, dynamic>) {
          throw const FormatException('Invalid payment record.');
        }
        return CustomerPayment.fromJson(item);
      }).toList(growable: false);
    } on FormatException catch (error) {
      throw CustomerPaymentException(error.message);
    }
  }

  Future<CustomerPayment> submit({
    required int bookingId,
    required double amount,
    required String paymentMethod,
    required Uint8List proofBytes,
    required String proofFileName,
  }) async {
    if (!MobileSession.isAuthenticated) {
      throw const CustomerPaymentException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    if (bookingId <= 0 || !hasAtMostTwoDecimalPlaces(amount)) {
      throw const CustomerPaymentException(
          'Enter a valid payment amount with at most two decimal places.');
    }
    final method = paymentMethod.trim().toUpperCase();
    if (!const {'BANK_TRANSFER', 'CARD'}.contains(method)) {
      throw const CustomerPaymentException(
          'Choose a supported payment method.');
    }
    if (proofBytes.isEmpty || proofBytes.length > 10 * 1024 * 1024) {
      throw const CustomerPaymentException(
          'Choose a payment proof smaller than 10 MB.');
    }
    final request = http.MultipartRequest(
      'POST',
      Uri.parse('${AppConfig.apiBaseUrl}/api/payments/bookings/$bookingId'),
    )
      ..headers.addAll({'Accept': 'application/json', ...MobileSession.headers})
      ..fields['amount'] = amount.toStringAsFixed(2)
      ..fields['paymentMethod'] = method
      ..files.add(http.MultipartFile.fromBytes(
        'proof',
        proofBytes,
        filename: proofFileName,
      ));

    late final http.StreamedResponse streamed;
    try {
      streamed =
          await _client.send(request).timeout(const Duration(seconds: 30));
    } on TimeoutException {
      throw const CustomerPaymentException(
          'The server took too long to respond. Please try again.');
    } on http.ClientException {
      throw const CustomerPaymentException(
          'Cannot connect to DilaDrive. Check the API connection and try again.');
    }
    final response = await http.Response.fromStream(streamed);
    _checkStatus(response);
    final Object? payload;
    try {
      payload = jsonDecode(response.body);
    } on FormatException {
      throw const CustomerPaymentException(
          'The server returned an invalid payment response.');
    }
    if (payload is! Map<String, dynamic>) {
      throw const CustomerPaymentException(
          'The server returned an invalid payment response.');
    }
    try {
      return CustomerPayment.fromJson(payload);
    } on FormatException catch (error) {
      throw CustomerPaymentException(error.message);
    }
  }

  Future<http.Response> _send(Future<http.Response> Function() send) async {
    if (!MobileSession.isAuthenticated) {
      throw const CustomerPaymentException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    late final http.Response response;
    try {
      response = await send().timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const CustomerPaymentException(
          'The server took too long to respond. Please try again.');
    } on http.ClientException {
      throw const CustomerPaymentException(
          'Cannot connect to DilaDrive. Check the API connection and try again.');
    }
    _checkStatus(response);
    return response;
  }

  void _checkStatus(http.Response response) {
    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const CustomerPaymentException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    if (response.statusCode == 403) {
      throw const CustomerPaymentException(
          'Payments are only available to customer accounts.');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      var message = 'Payment request failed (${response.statusCode}).';
      try {
        final payload = jsonDecode(response.body);
        if (payload is Map<String, dynamic> && payload['message'] is String) {
          message = payload['message'] as String;
        }
      } on FormatException {
        // Keep the status-based message for non-JSON server responses.
      }
      throw CustomerPaymentException(message);
    }
  }
}

class CustomerPaymentException implements Exception {
  const CustomerPaymentException(this.message, {this.requiresLogin = false});

  final String message;
  final bool requiresLogin;

  @override
  String toString() => message;
}
