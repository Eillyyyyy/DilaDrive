import 'dart:async';

import 'package:flutter/material.dart';

import '../models/customer_notification.dart';

class UnreadNotificationBadge extends StatefulWidget {
  const UnreadNotificationBadge({
    super.key,
    required this.icon,
    required this.loadNotifications,
    required this.inboxChanges,
    required this.pushUpdates,
  });

  final Widget icon;
  final Future<List<CustomerNotification>> Function() loadNotifications;
  final Listenable inboxChanges;
  final Stream<void> pushUpdates;

  @override
  State<UnreadNotificationBadge> createState() => _UnreadNotificationBadgeState();
}

class _UnreadNotificationBadgeState extends State<UnreadNotificationBadge> {
  late final AppLifecycleListener _lifecycle;
  late final StreamSubscription<void> _pushUpdates;
  int _unread = 0;
  int _version = 0;

  @override
  void initState() {
    super.initState();
    widget.inboxChanges.addListener(_refresh);
    _pushUpdates = widget.pushUpdates.listen((_) => _refresh());
    _lifecycle = AppLifecycleListener(onResume: _refresh);
    _refresh();
  }

  Future<void> _refresh() async {
    final version = ++_version;
    try {
      final notifications = await widget.loadNotifications();
      if (!mounted || version != _version) return;
      final unread = notifications.where((item) => !item.read).length;
      if (unread != _unread) setState(() => _unread = unread);
    } catch (_) {
      // Keep the last count when the inbox is temporarily unavailable.
    }
  }

  @override
  void dispose() {
    widget.inboxChanges.removeListener(_refresh);
    unawaited(_pushUpdates.cancel());
    _lifecycle.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_unread == 0) return widget.icon;
    return Semantics(
      label: '$_unread unread notifications',
      child: Badge.count(
        count: _unread,
        maxCount: 99,
        backgroundColor: Colors.red.shade700,
        textColor: Colors.white,
        child: widget.icon,
      ),
    );
  }
}
