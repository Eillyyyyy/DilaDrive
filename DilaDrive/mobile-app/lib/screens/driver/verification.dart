import 'package:flutter/material.dart';

import '../../theme/app_theme.dart';

class VerificationPage extends StatelessWidget {
  const VerificationPage({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: const Text('Verification photos')),
        body: const Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Card(
              child: Padding(
                padding: EdgeInsets.all(20),
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  Text(
                    'Open an assigned trip to add pickup and drop-off verification photos.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppTheme.secondaryText),
                  ),
                  SizedBox(height: 12),
                  Icon(Icons.photo_camera_outlined,
                      color: AppTheme.secondarySkyBlue, size: 36),
                ]),
              ),
            ),
          ),
        ),
      );
}
