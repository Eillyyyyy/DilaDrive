import 'dart:convert';

import 'package:flutter/material.dart';

import '../config/app_config.dart';
import '../services/api_http_client.dart';
import '../services/mobile_session.dart';

class TripTimeline extends StatefulWidget {
  const TripTimeline({super.key, required this.id, this.customer = false});
  final int id;
  final bool customer;

  @override
  State<TripTimeline> createState() => _TripTimelineState();
}

class _TripTimelineState extends State<TripTimeline> {
  bool _expanded = false;
  late Future<List<Map<String, dynamic>>> _events;

  Future<List<Map<String, dynamic>>> _load() async {
    final client = createApiHttpClient();
    try {
      final resource = widget.customer ? 'bookings' : 'assignments';
      final response = await fetchMobilePages(client,
            Uri.parse(
                '${AppConfig.apiBaseUrl}/api/$resource/${widget.id}/timeline'),
            headers: MobileSession.headers,
          )
          .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) throw StateError('Timeline unavailable');
      final rows = jsonDecode(response.body) as List;
      return rows.cast<Map<String, dynamic>>();
    } finally {
      client.close();
    }
  }

  @override
  Widget build(BuildContext context) => Card(
        child: ExpansionTile(
          title: const Text('Trip timeline'),
          onExpansionChanged: (open) => setState(() {
            _expanded = open;
            if (open) _events = _load();
          }),
          children: [
            if (_expanded)
              FutureBuilder(
                future: _events,
                builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done) {
                    return const LinearProgressIndicator();
                  }
                  final events = snapshot.data;
                  if (snapshot.hasError || events == null) {
                    return TextButton(
                        onPressed: () => setState(() => _events = _load()),
                        child: const Text('Retry trip timeline'));
                  }
                  if (events.isEmpty) {
                    return const ListTile(
                        title: Text('No recorded milestones'));
                  }
                  return Column(
                      children: events.map((event) {
                    final instant = DateTime.tryParse('${event['occurredAt']}');
                    final malaysia =
                        instant?.toUtc().add(const Duration(hours: 8));
                    final local = MaterialLocalizations.of(context);
                    return ListTile(
                      title: Text(switch (event['milestone']) {
                        'OTW' => 'On the way',
                        'WAITING' => 'Waiting for pickup',
                        'PICKUP' => 'Passenger picked up',
                        'DROP' => 'Drop-off',
                        'SUPERSEDED' => 'Reassigned',
                        final value => '$value',
                      }),
                      subtitle: malaysia == null
                          ? null
                          : Text(
                              '${local.formatMediumDate(malaysia)} ${local.formatTimeOfDay(TimeOfDay.fromDateTime(malaysia))} (Malaysia)'),
                    );
                  }).toList());
                },
              ),
          ],
        ),
      );
}
