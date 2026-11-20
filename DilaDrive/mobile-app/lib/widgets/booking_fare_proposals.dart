import 'dart:convert';
import 'package:flutter/material.dart';
import '../config/app_config.dart';
import '../services/api_http_client.dart';
import '../services/mobile_session.dart';

class BookingFareProposals extends StatefulWidget {
  const BookingFareProposals(
      {super.key, required this.bookingId, required this.onAccepted});
  final int bookingId;
  final VoidCallback onAccepted;
  @override
  State<BookingFareProposals> createState() => _BookingFareProposalsState();
}

class _BookingFareProposalsState extends State<BookingFareProposals> {
  final _client = createApiHttpClient();
  List<dynamic> _proposals = [];
  String? _error;
  bool _busy = false;
  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _client.close();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final response = await _client
          .get(
              Uri.parse(
                  '${AppConfig.apiBaseUrl}/api/bookings/${widget.bookingId}/fare-proposals'),
              headers: MobileSession.headers)
          .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) {
        throw Exception(
            'Fare proposals could not be loaded. Retry or sign in again.');
      }
      final body = jsonDecode(response.body) as List;
      if (mounted) {
        setState(() {
          _proposals = body;
          _error = null;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    }
  }

  Future<void> _accept(Map proposal) async {
    final accepted = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
              title: const Text('Accept revised fare?'),
              content: Text('Category: ${proposal['vehicle_type']}\n'
                  'Original accepted fare: MYR ${proposal['original_fare']}\n'
                  'Proposed fare: MYR ${proposal['proposed_fare']}\n'
                  'Admin will still need to confirm the vehicle and driver assignment.'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(context, false),
                    child: const Text('Keep current fare')),
                FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Text('Accept revised fare'))
              ],
            ));
    if (accepted != true || !mounted) return;
    setState(() => _busy = true);
    try {
      final response = await _client
          .post(
              Uri.parse(
                  '${AppConfig.apiBaseUrl}/api/bookings/${widget.bookingId}/fare-proposals/${proposal['id']}/accept'),
              headers: MobileSession.headers)
          .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) {
        final body = jsonDecode(response.body);
        throw Exception(body['message'] ??
            'The revised fare could not be accepted. Retry.');
      }
      if (mounted) widget.onAccepted();
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Card(
      child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Fare proposals',
                  style: TextStyle(fontWeight: FontWeight.bold)),
              if (_error != null)
                Text(_error!, style: const TextStyle(color: Colors.red)),
              TextButton(
                  onPressed: _busy ? null : _load,
                  child: const Text('Refresh fare proposals')),
              if (_proposals.isEmpty && _error == null)
                const Text('No proposed fare changes.'),
              for (final proposal in _proposals)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(
                      '${proposal['vehicle_type']} · MYR ${proposal['proposed_fare']}'),
                  subtitle: Text(
                      '${proposal['status']} · previously MYR ${proposal['original_fare']}'),
                  trailing: proposal['status'] == 'PROPOSED'
                      ? TextButton(
                          onPressed:
                              _busy ? null : () => _accept(proposal as Map),
                          child: const Text('Review'))
                      : null,
                ),
            ],
          )));
}
