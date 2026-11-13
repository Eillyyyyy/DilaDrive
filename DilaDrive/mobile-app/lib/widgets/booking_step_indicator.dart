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
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
      child: Row(
        children: List.generate(_steps.length, (index) {
          final isComplete = index < currentStep;
          final isCurrent = index == currentStep;
          final color = isComplete || isCurrent ? _blue : AppTheme.border;

          return Expanded(
            child: Column(
              children: [
                Row(
                  children: [
                    if (index > 0)
                      Expanded(
                        child: Container(
                          height: 2,
                          color: index <= currentStep ? _blue : AppTheme.border,
                        ),
                      ),
                    Container(
                      width: 28,
                      height: 28,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: isComplete || isCurrent ? _navy : Colors.white,
                        border: Border.all(color: color, width: 2),
                      ),
                      child: isComplete
                          ? const Icon(Icons.check,
                              size: 15, color: Colors.white)
                          : Text(
                              '${index + 1}',
                              style: TextStyle(
                                color: isCurrent ? Colors.white : color,
                                fontWeight: FontWeight.w700,
                                fontSize: 12,
                              ),
                            ),
                    ),
                    if (index < _steps.length - 1)
                      Expanded(
                        child: Container(
                          height: 2,
                          color: index < currentStep ? _blue : AppTheme.border,
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  _steps[index],
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: isCurrent ? _navy : AppTheme.secondaryText,
                    fontSize: 10,
                    fontWeight: isCurrent ? FontWeight.w700 : FontWeight.w500,
                  ),
                ),
              ],
            ),
          );
        }),
      ),
    );
  }
}
