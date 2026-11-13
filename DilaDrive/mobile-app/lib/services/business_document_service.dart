import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/business_document.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';

class BusinessDocumentService {
  BusinessDocumentService({http.Client? client})
      : _client = client ?? createApiHttpClient();

  final http.Client _client;
  void dispose() => _client.close();

  Future<List<BusinessDocument>> getMyDocuments() async {
    if (!MobileSession.isAuthenticated) {
      throw const BusinessDocumentException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    late final http.Response response;
    try {
      response = await _client.get(
        Uri.parse('${AppConfig.apiBaseUrl}/api/documents/customer/mine'),
        headers: {'Accept': 'application/json', ...MobileSession.headers},
      ).timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const BusinessDocumentException(
          'The server took too long to respond. Please try again.');
    } on http.ClientException {
      throw const BusinessDocumentException(
          'Cannot connect to DilaDrive. Check the API connection and try again.');
    }
    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const BusinessDocumentException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    if (response.statusCode == 403) {
      throw const BusinessDocumentException(
          'Business documents are only available to company customers.');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw BusinessDocumentException(
          'Documents could not be loaded (${response.statusCode}).');
    }
    final Object? payload;
    try {
      payload = jsonDecode(response.body);
    } on FormatException {
      throw const BusinessDocumentException(
          'The server returned an invalid documents response.');
    }
    if (payload is! List) {
      throw const BusinessDocumentException(
          'The server returned an invalid documents response.');
    }
    try {
      return payload.map((item) {
        if (item is! Map<String, dynamic>) {
          throw const FormatException('Invalid document.');
        }
        return BusinessDocument.fromJson(item);
      }).toList(growable: false);
    } on FormatException catch (error) {
      throw BusinessDocumentException(error.message);
    }
  }

  Future<Uint8List> getDocumentPdf(int documentId) async {
    if (!MobileSession.isAuthenticated) {
      throw const BusinessDocumentException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    late final http.Response response;
    try {
      response = await _client.get(
        Uri.parse(
            '${AppConfig.apiBaseUrl}/api/documents/customer/$documentId/pdf'),
        headers: {'Accept': 'application/pdf', ...MobileSession.headers},
      ).timeout(const Duration(seconds: 30));
    } on TimeoutException {
      throw const BusinessDocumentException(
          'The PDF took too long to load. Please try again.');
    } on http.ClientException {
      throw const BusinessDocumentException(
          'Cannot connect to DilaDrive. Check the API connection and try again.');
    }
    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const BusinessDocumentException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    if (response.statusCode == 403) {
      throw const BusinessDocumentException(
          'This document is not available to your account.');
    }
    if (response.statusCode == 404) {
      throw const BusinessDocumentException(
          'This document could not be found.');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw BusinessDocumentException(
          'The PDF could not be loaded (${response.statusCode}).');
    }
    if (!response.headers['content-type']
            .toString()
            .toLowerCase()
            .startsWith('application/pdf') ||
        response.bodyBytes.length < 5 ||
        String.fromCharCodes(response.bodyBytes.take(5)) != '%PDF-') {
      throw const BusinessDocumentException(
          'The server returned an invalid PDF.');
    }
    return response.bodyBytes;
  }
}

class BusinessDocumentException implements Exception {
  const BusinessDocumentException(this.message, {this.requiresLogin = false});
  final String message;
  final bool requiresLogin;
  @override
  String toString() => message;
}
