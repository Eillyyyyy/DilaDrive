import 'package:flutter/material.dart';

import '../../models/customer_notification.dart';
import '../../services/customer_notification_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';

class NotificationPage extends StatefulWidget {
  const NotificationPage({super.key, this.notificationService});

  final CustomerNotificationService? notificationService;

  @override
  State<NotificationPage> createState() => _NotificationPageState();
}

class _NotificationPageState extends State<NotificationPage> {
  late final bool _ownsService = widget.notificationService == null;
  late final CustomerNotificationService _service =
      widget.notificationService ?? CustomerNotificationService();
  late Future<List<CustomerNotification>> _notificationsFuture;

  @override
  void initState() {
    super.initState();
    _notificationsFuture = _service.getMyNotifications();
  }

  @override
  void dispose() {
    if (_ownsService) _service.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    final future = _service.getMyNotifications();
    setState(() {
      _notificationsFuture = future;
    });
    try {
      await future;
    } catch (_) {
      // The FutureBuilder renders the retry state.
    }
  }

  Future<void> _markRead(CustomerNotification notification) async {
    try {
      await _service.markAsRead(notification.id);
      await _refresh();
    } on CustomerNotificationException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message)),
        );
        if (error.requiresLogin) _openLogin();
      }
    }
  }

  void _openLogin() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text('Notifications'),
      ),
      body: FutureBuilder<List<CustomerNotification>>(
        future: _notificationsFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            final error = snapshot.error;
            final requiresLogin =
                error is CustomerNotificationException && error.requiresLogin;
            return _MessageState(
              icon: Icons.cloud_off_outlined,
              title: 'Notifications are unavailable',
              message: error is CustomerNotificationException
                  ? error.message
                  : 'Please check your connection and try again.',
              actionLabel: requiresLogin ? 'Sign in' : 'Try again',
              onAction: requiresLogin ? _openLogin : _refresh,
            );
          }

          final notifications = snapshot.data ?? const <CustomerNotification>[];
          final unreadCount = notifications.where((item) => !item.read).length;
          return RefreshIndicator(
            onRefresh: _refresh,
            child: notifications.isEmpty
                ? ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(24),
                    children: const [
                      SizedBox(height: 110),
                      Icon(Icons.notifications_none_rounded,
                          size: 48, color: AppTheme.secondarySkyBlue),
                      SizedBox(height: 14),
                      Text(
                        'You’re all caught up',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                            fontSize: 17, fontWeight: FontWeight.w700),
                      ),
                      SizedBox(height: 6),
                      Text(
                        'New updates about your bookings will appear here.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: AppTheme.secondaryText),
                      ),
                    ],
                  )
                : ListView.separated(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(18, 16, 18, 28),
                    itemCount: notifications.length + 1,
                    separatorBuilder: (_, __) => const SizedBox(height: 11),
                    itemBuilder: (context, index) {
                      if (index == 0) {
                        return _UnreadSummary(count: unreadCount);
                      }
                      final notification = notifications[index - 1];
                      return _NotificationCard(
                        notification: notification,
                        onMarkRead: () => _markRead(notification),
                      );
                    },
                  ),
          );
        },
      ),
    );
  }
}

class _UnreadSummary extends StatelessWidget {
  const _UnreadSummary({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 3),
        child: Text(
          count == 0
              ? 'All notifications have been read'
              : '$count unread ${count == 1 ? 'notification' : 'notifications'}',
          style: const TextStyle(
            color: AppTheme.secondaryText,
            fontWeight: FontWeight.w600,
          ),
        ),
      );
}

class _NotificationCard extends StatelessWidget {
  const _NotificationCard({
    required this.notification,
    required this.onMarkRead,
  });

  final CustomerNotification notification;
  final VoidCallback onMarkRead;

  @override
  Widget build(BuildContext context) {
    final color = _notificationColor(notification.type);
    return Container(
      padding: const EdgeInsets.all(17),
      decoration: BoxDecoration(
        color: notification.read ? AppTheme.white : AppTheme.paleBlue,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppTheme.border),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 22,
            backgroundColor: color.withValues(alpha: .12),
            child: Icon(_notificationIcon(notification.type), color: color),
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        notification.title,
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                    if (!notification.read)
                      const Icon(Icons.circle,
                          size: 9, color: AppTheme.secondarySkyBlue),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  notification.message,
                  style: const TextStyle(
                      color: AppTheme.secondaryText, height: 1.4),
                ),
                const SizedBox(height: 9),
                Text(
                  _sentTime(context, notification.sentAt),
                  style: const TextStyle(
                      color: AppTheme.secondaryText, fontSize: 12),
                ),
                if (!notification.read)
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton.icon(
                      onPressed: onMarkRead,
                      icon: const Icon(Icons.done, size: 17),
                      label: const Text('Mark read'),
                      style: TextButton.styleFrom(
                        foregroundColor: AppTheme.primaryNavy,
                        visualDensity: VisualDensity.compact,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _MessageState extends StatelessWidget {
  const _MessageState({
    required this.icon,
    required this.title,
    required this.message,
    required this.actionLabel,
    required this.onAction,
  });

  final IconData icon;
  final String title;
  final String message;
  final String actionLabel;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 42, color: AppTheme.secondaryText),
              const SizedBox(height: 12),
              Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
              const SizedBox(height: 6),
              Text(message, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton.tonal(onPressed: onAction, child: Text(actionLabel)),
            ],
          ),
        ),
      );
}

IconData _notificationIcon(String? type) {
  final value = type?.toUpperCase() ?? '';
  if (value.contains('BOOKING') || value.contains('APPROV')) {
    return Icons.event_available_outlined;
  }
  if (value.contains('DRIVER') || value.contains('ASSIGN')) {
    return Icons.local_shipping_outlined;
  }
  if (value.contains('PAYMENT') || value.contains('RECEIPT')) {
    return Icons.payments_outlined;
  }
  return Icons.notifications_none_rounded;
}

Color _notificationColor(String? type) {
  final value = type?.toUpperCase() ?? '';
  if (value.contains('PAYMENT')) return Colors.green;
  if (value.contains('PENDING')) return Colors.orange;
  return AppTheme.secondarySkyBlue;
}

String _sentTime(BuildContext context, DateTime? sentAt) {
  if (sentAt == null) return 'Time unavailable';
  final localTime = sentAt.toLocal();
  final localizations = MaterialLocalizations.of(context);
  return '${localizations.formatMediumDate(localTime)}, '
      '${localizations.formatTimeOfDay(TimeOfDay.fromDateTime(localTime))}';
}
