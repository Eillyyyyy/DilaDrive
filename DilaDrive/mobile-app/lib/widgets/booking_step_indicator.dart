import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class BookingStepIndicator extends StatelessWidget {
  const BookingStepIndicator({
    super.key,
    required this.currentStep,
  });

  final int currentStep;

  static const _steps = [
    'Location',
    'Details',
    'Availability',
    'Fare',
    'Review',
  ];

  static const _navy = AppTheme.primaryNavy;
  static const _blue = AppTheme.secondarySkyBlue;

  @override
  Widget build(BuildContext context) {
    final step = currentStep.clamp(0, _steps.length - 1);
    return Padding(
        padding: const EdgeInsets.fromLTRB(20, 10, 20, 12),
        child: Column(children: [
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            Text('Step ${step + 1} of ${_steps.length} ? ${_steps[step]}',
                style: const TextStyle(
                    color: _navy, fontWeight: FontWeight.w600, fontSize: 12)),
            if (step + 1 < _steps.length)
              Text('Next: ${_steps[step + 1]}',
                  style: const TextStyle(
                      color: AppTheme.secondaryText, fontSize: 11)),
          ]),
          const SizedBox(height: 7),
          ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                  value: (step + 1) / _steps.length,
                  minHeight: 4,
                  color: _blue,
                  backgroundColor: AppTheme.border)),
        ]));
  }
}
