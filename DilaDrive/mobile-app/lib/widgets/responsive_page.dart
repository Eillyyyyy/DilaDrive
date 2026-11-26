import 'package:flutter/material.dart';

const dilaDrivePageWidth = 620.0;

EdgeInsets pagePadding(BuildContext context,
    {double top = 16, double bottom = 24}) {
  final safe = MediaQuery.paddingOf(context);
  return EdgeInsets.fromLTRB(
      16 + safe.left, top, 16 + safe.right, bottom + safe.bottom);
}

class DilaDrivePageLayout extends StatelessWidget {
  const DilaDrivePageLayout({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => Align(
        heightFactor: 1,
        child: SizedBox(
          width: dilaDrivePageWidth,
          child: child,
        ),
      );
}

class DilaDriveScaffold extends StatelessWidget {
  const DilaDriveScaffold({
    super.key,
    required this.body,
    this.appBar,
    this.backgroundColor,
    this.bottomNavigationBar,
    this.resizeToAvoidBottomInset,
  });

  final Widget body;
  final PreferredSizeWidget? appBar;
  final Color? backgroundColor;
  final Widget? bottomNavigationBar;
  final bool? resizeToAvoidBottomInset;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: appBar,
        backgroundColor: backgroundColor,
        resizeToAvoidBottomInset: resizeToAvoidBottomInset,
        body: DilaDrivePageLayout(child: body),
        bottomNavigationBar: bottomNavigationBar == null
            ? null
            : DilaDrivePageLayout(child: bottomNavigationBar!),
      );
}
