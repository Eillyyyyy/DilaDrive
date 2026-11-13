import 'package:flutter/material.dart';

import '../../models/customer_notification.dart';
import '../../models/mobile_user.dart';
import '../../services/driver_notification_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';

class DriverNotificationPage extends StatefulWidget {
  const DriverNotificationPage({super.key, required this.driver});
  final MobileUser driver;

  @override
  State<DriverNotificationPage> createState() => _DriverNotificationPageState();
}

class _DriverNotificationPageState extends State<DriverNotificationPage> {
  final DriverNotificationService _service = DriverNotificationService();
  late Future<List<CustomerNotification>> _notifications;

  @override
  void initState() {
    super.initState();
    _notifications = _service.getNotifications(widget.driver.id);
  }

  @override
  void dispose() {
    _service.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    final future = _service.getNotifications(widget.driver.id);
    setState(() {
      _notifications = future;
    });
    try {
      await future;
    } catch (_) {
      // The FutureBuilder displays the retryable error state.
    }
  }

  Future<void> _markRead(CustomerNotification notification) async {
    try {
      await _service.markAsRead(notification.id);
      await _refresh();
    } on DriverNotificationException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error.message)));
      if (error.requiresLogin) _openLogin();
    }
  }

  void _openLogin() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(
          automaticallyImplyLeading: false,
          title: const Text('Notifications'),
        ),
        body: FutureBuilder<List<CustomerNotification>>(
          future: _notifications,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              final error = snapshot.error;
              final requiresLogin =
                  error is DriverNotificationException && error.requiresLogin;
              return _MessageState(
                icon: Icons.cloud_off_outlined,
                message: error is DriverNotificationException
                    ? error.message
                    : 'Please check your connection and try again.',
                action: FilledButton.tonal(
                  onPressed: requiresLogin ? _openLogin : _refresh,
                  child: Text(requiresLogin ? 'Sign in' : 'Try again'),
                ),
              );
            }

            final notifications =
                snapshot.data ?? const <CustomerNotification>[];
            final unread = notifications.where((item) => !item.read).length;
            return RefreshIndicator(
              onRefresh: _refresh,
              child: notifications.isEmpty
                  ? ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.all(24),
                      children: const [
                        SizedBox(height: 100),
                        Icon(Icons.notifications_none_rounded,
                            size: 48, color: AppTheme.secondarySkyBlue),
                        SizedBox(height: 14),
                        Text('No notifications yet',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                                fontSize: 17, fontWeight: FontWeight.w700)),
                        SizedBox(height: 6),
                        Text('Trip and assignment updates will appear here.',
                            textAlign: TextAlign.center,
                            style: TextStyle(color: AppTheme.secondaryText)),
                      ],
                    )
                  : ListView.separated(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.all(16),
                      itemCount: notifications.length + 1,
                      separatorBuilder: (_, __) => const SizedBox(height: 10),
                      itemBuilder: (context, index) {
                        if (index == 0) {
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 2),
                            child: Text(unread == 0
                                ? 'All notifications have been read'
                                : '$unread unread ${unread == 1 ? 'notification' : 'notifications'}'),
                          );
                        }
                        final notification = notifications[index - 1];
                        return _NotificationCard(
                          notification: notification,
                          onTap: notification.read
                              ? null
                              : () => _markRead(notification),
                        );
                      },
                    ),
            );
          },
        ),
      );
}

class _NotificationCard extends StatelessWidget {
  const _NotificationCard({required this.notification, required this.onTap});

  final CustomerNotification notification;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Card(
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              CircleAvatar(
                backgroundColor: (notification.read
                        ? AppTheme.secondaryText
                        : AppTheme.secondarySkyBlue)
                    .withValues(alpha: .13),
                child: Icon(
                  notification.read
                      ? Icons.notifications_none
                      : Icons.notifications_active_outlined,
                  color: notification.read
                      ? AppTheme.secondaryText
                      : AppTheme.primaryNavy,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(notification.title,
                          style: TextStyle(
                              fontWeight: notification.read
                                  ? FontWeight.w500
                                  : FontWeight.w700)),
                      const SizedBox(height: 4),
                      Text(notification.message),
                      if (notification.sentAt != null) ...[
                        const SizedBox(height: 7),
                        Text(
                          _sentTime(context, notification.sentAt!),
                          style: const TextStyle(
                              color: AppTheme.secondaryText, fontSize: 12),
                        ),
                      ],
                      if (!notification.read) ...[
                        const SizedBox(height: 6),
                        const Text('Tap to mark as read',
                            style: TextStyle(
                                color: AppTheme.primaryNavy, fontSize: 12)),
                      ],
                    ]),
              ),
              if (!notification.read)
                const Padding(
                  padding: EdgeInsets.only(left: 8, top: 5),
                  child: Icon(Icons.circle,
                      size: 9, color: AppTheme.secondarySkyBlue),
                ),
            ]),
          ),
        ),
      );
}

String _sentTime(BuildContext context, DateTime sentAt) {
  final localTime = sentAt.toLocal();
  final localizations = MaterialLocalizations.of(context);
  return '${localizations.formatMediumDate(localTime)}, '
      '${localizations.formatTimeOfDay(TimeOfDay.fromDateTime(localTime))}';
}

class _MessageState extends StatelessWidget {
  const _MessageState(
      {required this.icon, required this.message, required this.action});

  final IconData icon;
  final String message;
  final Widget action;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 42, color: AppTheme.secondaryText),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            action,
          ]),
        ),
      );
}
