import 'package:flutter/material.dart';

import '../../../theme/app_theme.dart';

import '../../../widgets/booking_step_indicator.dart';
import '../../../widgets/responsive_page.dart';
import 'booking_map_sheet.dart';

const bookingNavy = AppTheme.primaryNavy;
const bookingBlue = AppTheme.secondarySkyBlue;
const bookingBackground = AppTheme.lightBlueBackground;

class BookingPageLayout extends StatelessWidget {
  const BookingPageLayout({
    super.key,
    required this.title,
    required this.subtitle,
    required this.currentStep,
    required this.child,
    required this.actionLabel,
    required this.onAction,
    this.isLoading = false,
    this.actionEnabled = true,
  });

  final String title;
  final String subtitle;
  final int currentStep;
  final Widget child;
  final String actionLabel;
  final VoidCallback? onAction;
  final bool isLoading;
  final bool actionEnabled;

  @override
  Widget build(BuildContext context) {
    if (BookingSheetScroll.isEmbedded(context)) {
      return BookingSheetContent(
        action: FilledButton(
          onPressed: actionEnabled && !isLoading ? onAction : null,
          child: isLoading
              ? const SizedBox.square(
                  dimension: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : Text(actionLabel),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 4),
            Text(subtitle),
            const SizedBox(height: 12),
            child,
          ],
        ),
      );
    }
    return DilaDriveScaffold(
      backgroundColor: bookingBackground,
      appBar: AppBar(
        title: const Text('Create Booking'),
        backgroundColor: Colors.white,
        foregroundColor: bookingNavy,
        elevation: 0,
      ),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            Padding(
              padding: pagePadding(context, top: 0, bottom: 0),
              child: BookingStepIndicator(currentStep: currentStep),
            ),
            Expanded(
              child: ListView(
                padding: pagePadding(context, top: 0, bottom: 20),
                children: [
                  Text(title, style: Theme.of(context).textTheme.headlineSmall),
                  const SizedBox(height: 6),
                  Text(subtitle,
                      style: const TextStyle(color: AppTheme.secondaryText)),
                  const SizedBox(height: 20),
                  child,
                ],
              ),
            ),
            Container(
              padding: pagePadding(context, top: 12, bottom: 16),
              decoration: const BoxDecoration(
                color: Colors.white,
                boxShadow: [
                  BoxShadow(
                    color: Color(0x12002B49),
                    blurRadius: 16,
                    offset: Offset(0, -4),
                  ),
                ],
              ),
              child: SizedBox(
                width: double.infinity,
                height: 54,
                child: ElevatedButton(
                  onPressed: actionEnabled && !isLoading ? onAction : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: bookingNavy,
                    disabledBackgroundColor: AppTheme.border,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(15),
                    ),
                  ),
                  child: isLoading
                      ? const SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.5,
                            color: Colors.white,
                          ),
                        )
                      : Text(
                          actionLabel,
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

InputDecoration bookingInputDecoration({
  required String label,
  required IconData icon,
  String? hint,
  String? helper,
}) {
  return InputDecoration(
    labelText: label,
    hintText: hint,
    helperText: helper,
    prefixIcon: Icon(icon, color: bookingBlue),
    filled: true,
    fillColor: Colors.white,
    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(15),
      borderSide: const BorderSide(color: AppTheme.border),
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(15),
      borderSide: const BorderSide(color: AppTheme.border),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(15),
      borderSide: const BorderSide(color: bookingBlue, width: 1.5),
    ),
  );
}

class BookingCard extends StatelessWidget {
  const BookingCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(18),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: padding,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppTheme.border),
      ),
      child: child,
    );
  }
}

void showBookingMessage(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}

String bookingDateLabel(DateTime? date) {
  if (date == null) return 'Select a date';
  return '${date.day.toString().padLeft(2, '0')}/'
      '${date.month.toString().padLeft(2, '0')}/${date.year}';
}

String bookingTimeLabel(BuildContext context, DateTime? time) {
  if (time == null) return 'Select a time';
  return TimeOfDay.fromDateTime(time).format(context);
}

/// Preserve the routing provider's sub-metre precision for fare review.
String bookingDistanceLabel(double kilometres) {
  final distance = kilometres.toStringAsFixed(4).replaceFirstMapped(
      RegExp(r'(\.\d{2}\d*?)0+$'), (match) => match.group(1)!);
  return '$distance km';
}
