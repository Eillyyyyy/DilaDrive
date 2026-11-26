import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import '../../../theme/app_theme.dart';
import '../../../widgets/responsive_page.dart';

class BookingMapSheet extends StatefulWidget {
  const BookingMapSheet({
    super.key,
    required this.mapBuilder,
    required this.child,
    required this.expanded,
    this.onBack,
    this.onLocate,
  });

  final Widget Function(EdgeInsets) mapBuilder;
  final Widget child;
  final bool expanded;
  final VoidCallback? onBack;
  final VoidCallback? onLocate;

  @override
  State<BookingMapSheet> createState() => _BookingMapSheetState();
}

class _BookingMapSheetState extends State<BookingMapSheet> {
  static const _compact = .48;
  static const _expanded = .65;
  final _sheet = DraggableScrollableController();
  double get _initial => widget.expanded ? _expanded : _compact;

  @override
  void didUpdateWidget(BookingMapSheet oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.child.key != widget.child.key && _sheet.isAttached) {
      _sheet.animateTo(_initial,
          duration: const Duration(milliseconds: 220), curve: Curves.easeOut);
    }
  }

  @override
  void dispose() {
    _sheet.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final safePadding = MediaQuery.paddingOf(context);
    final top = safePadding.top;
    return Scaffold(
      body: LayoutBuilder(
        builder: (context, constraints) {
          final height = constraints.maxHeight;
          if (height <= 0) return const SizedBox.shrink();
          final minExtent =
              ((72 + safePadding.bottom) / height).clamp(.30, 1.0);
          final mapClearance = (top + 120).clamp(0.0, height * .45);
          final maxExtent =
              ((height - mapClearance) / height).clamp(minExtent, 1.0);
          final initialExtent = _initial.clamp(minExtent, maxExtent);
          return Stack(
            fit: StackFit.expand,
            children: [
              ListenableBuilder(
                listenable: _sheet,
                builder: (context, _) => widget.mapBuilder(EdgeInsets.fromLTRB(
                  32,
                  (top + 60).clamp(0.0, height * .25),
                  32,
                  height *
                          (_sheet.isAttached
                              ? _sheet.size.clamp(minExtent, maxExtent)
                              : initialExtent) +
                      22,
                )),
              ),
              Positioned(
                top: top + 8,
                left: 12,
                right: 12,
                child: Row(
                  children: [
                    Material(
                      shape: const CircleBorder(),
                      elevation: 2,
                      child: BackButton(
                        onPressed: widget.onBack ??
                            () => Navigator.of(context).maybePop(),
                      ),
                    ),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Chip(
                          label: Text('Create Booking',
                              overflow: TextOverflow.ellipsis),
                        ),
                      ),
                    ),
                    if (widget.onLocate != null)
                      Material(
                        borderRadius: BorderRadius.circular(24),
                        elevation: 2,
                        child: IconButton(
                          tooltip: 'Use Current Location',
                          onPressed: widget.onLocate,
                          icon: const Icon(Icons.my_location),
                        ),
                      ),
                  ],
                ),
              ),
              DraggableScrollableSheet(
                controller: _sheet,
                initialChildSize: initialExtent,
                minChildSize: minExtent,
                maxChildSize: maxExtent,
                builder: (context, scrollController) => DilaDrivePageLayout(
                    child: Material(
                  color: Theme.of(context).colorScheme.surface,
                  elevation: 12,
                  borderRadius:
                      const BorderRadius.vertical(top: Radius.circular(26)),
                  clipBehavior: Clip.antiAlias,
                  child: SafeArea(
                    top: false,
                    child: BookingSheetScroll(
                      controller: scrollController,
                      child: widget.child,
                    ),
                  ),
                )),
              ),
            ],
          );
        },
      ),
    );
  }
}

class BookingSheetScroll extends InheritedWidget {
  const BookingSheetScroll({
    super.key,
    required this.controller,
    required super.child,
  });

  final ScrollController controller;

  static bool isEmbedded(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<BookingSheetScroll>() != null;

  @override
  bool updateShouldNotify(BookingSheetScroll oldWidget) =>
      controller != oldWidget.controller;
}

class BookingSheetContent extends StatelessWidget {
  const BookingSheetContent({
    super.key,
    required this.child,
    required this.action,
  });

  final Widget child;
  final Widget action;

  @override
  Widget build(BuildContext context) {
    final scroll =
        context.dependOnInheritedWidgetOfExactType<BookingSheetScroll>()!;
    return LayoutBuilder(builder: (context, constraints) {
      final footer = constraints.maxHeight.clamp(0.0, 60.0);
      final spacing = (footer - 48).clamp(0.0, 12.0);
      return Column(
        children: [
          Expanded(
            child: ScrollConfiguration(
              behavior: ScrollConfiguration.of(context).copyWith(dragDevices: {
                ...ScrollConfiguration.of(context).dragDevices,
                PointerDeviceKind.mouse,
              }),
              child: ListView(
                controller: scroll.controller,
                primary: false,
                keyboardDismissBehavior:
                    ScrollViewKeyboardDismissBehavior.onDrag,
                padding: pagePadding(context, top: 9, bottom: 12),
                children: [
                  Center(
                    child: Container(
                      width: 36,
                      height: 4,
                      decoration: BoxDecoration(
                        color: AppTheme.border,
                        borderRadius: BorderRadius.circular(4),
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  child,
                ],
              ),
            ),
          ),
          SizedBox(
            height: footer,
            child: Padding(
              padding: pagePadding(context,
                  top: spacing / 3, bottom: spacing * 2 / 3),
              child: SizedBox(
                  width: double.infinity,
                  height: footer - spacing,
                  child: action),
            ),
          ),
        ],
      );
    });
  }
}
