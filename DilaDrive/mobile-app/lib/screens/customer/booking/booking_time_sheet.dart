import 'package:flutter/material.dart';

import '../../../widgets/responsive_page.dart';

import '../../../models/booking_schedule.dart';

Future<DateTime?> showBookingPickupSlots(
  BuildContext context, {
  required List<DateTime> slots,
  required DateTime? selected,
}) =>
    _showTimeSheet(context, _PickupSlots(slots: slots, selected: selected));

Future<TimeOfDay?> showBookingClockTime(
  BuildContext context, {
  required TimeOfDay initial,
  required String title,
}) =>
    _showTimeSheet(context, _ClockTime(initial: initial, title: title));

Future<T?> _showTimeSheet<T>(BuildContext context, Widget child) =>
    showModalBottomSheet<T>(
      context: context,
      useSafeArea: true,
      constraints: const BoxConstraints(maxWidth: dilaDrivePageWidth),
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => SafeArea(
        top: false,
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * .58,
          child: child,
        ),
      ),
    );

class _PickupSlots extends StatefulWidget {
  const _PickupSlots({required this.slots, required this.selected});

  final List<DateTime> slots;
  final DateTime? selected;

  @override
  State<_PickupSlots> createState() => _PickupSlotsState();
}

class _PickupSlotsState extends State<_PickupSlots> {
  static const _rowHeight = 48.0;
  late final ScrollController _scroll;

  bool _isSelected(DateTime slot) {
    final selected = widget.selected;
    return selected != null &&
        isPickupSlot(selected) &&
        selected.hour == slot.hour &&
        selected.minute == slot.minute;
  }

  @override
  void initState() {
    super.initState();
    final selectedIndex = widget.slots.indexWhere(_isSelected);
    final now = malaysiaNow();
    final firstAvailable = widget.slots.indexWhere((slot) => slot.isAfter(now));
    final index = selectedIndex >= 0 ? selectedIndex : firstAvailable;
    _scroll = ScrollController(
        initialScrollOffset: (index - 2).clamp(0, 47) * _rowHeight);
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Text('Pickup time (Malaysia)',
              style: Theme.of(context).textTheme.titleLarge),
          Expanded(
            child: ListView.builder(
              controller: _scroll,
              itemExtent: _rowHeight,
              itemCount: widget.slots.length,
              itemBuilder: (context, index) {
                final slot = widget.slots[index];
                final available = slot.isAfter(malaysiaNow());
                final selected = _isSelected(slot);
                return ListTile(
                  title: Text(bookingClockLabel(slot.hour, slot.minute)),
                  selected: selected,
                  selectedTileColor:
                      Theme.of(context).colorScheme.primaryContainer,
                  trailing: selected ? const Icon(Icons.check) : null,
                  enabled: available,
                  onTap: available
                      ? () {
                          if (slot.isAfter(malaysiaNow())) {
                            Navigator.pop(context, slot);
                          } else {
                            setState(() {});
                          }
                        }
                      : null,
                );
              },
            ),
          ),
        ],
      );
}

class _ClockTime extends StatefulWidget {
  const _ClockTime({required this.initial, required this.title});
  final TimeOfDay initial;
  final String title;

  @override
  State<_ClockTime> createState() => _ClockTimeState();
}

class _ClockTimeState extends State<_ClockTime> {
  late final _hour =
      FixedExtentScrollController(initialItem: (widget.initial.hour + 11) % 12);
  late final _minute =
      FixedExtentScrollController(initialItem: widget.initial.minute);
  late final _period =
      FixedExtentScrollController(initialItem: widget.initial.hour ~/ 12);

  @override
  void dispose() {
    _hour.dispose();
    _minute.dispose();
    _period.dispose();
    super.dispose();
  }

  Widget _wheel(String label, FixedExtentScrollController controller,
          List<String> values) =>
      Expanded(
        child: Column(
          children: [
            Text(label),
            Expanded(
              child: ListWheelScrollView.useDelegate(
                key: ValueKey('flight-$label'),
                controller: controller,
                itemExtent: 44,
                physics: const FixedExtentScrollPhysics(),
                useMagnifier: true,
                magnification: 1.1,
                childDelegate: ListWheelChildBuilderDelegate(
                  childCount: values.length,
                  builder: (context, index) => Center(
                    child: InkWell(
                      onTap: () => controller.animateToItem(index,
                          duration: const Duration(milliseconds: 150),
                          curve: Curves.easeOut),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 8),
                        child: Text(values[index]),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(widget.title, style: Theme.of(context).textTheme.titleLarge),
            Expanded(
              child: Row(
                children: [
                  _wheel('Hour', _hour, List.generate(12, (i) => '${i + 1}')),
                  _wheel('Minute', _minute,
                      List.generate(60, (i) => i.toString().padLeft(2, '0'))),
                  _wheel('AM/PM', _period, const ['AM', 'PM']),
                ],
              ),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(
                context,
                TimeOfDay(
                  hour:
                      (_hour.selectedItem + 1) % 12 + _period.selectedItem * 12,
                  minute: _minute.selectedItem,
                ),
              ),
              child: Text('Set ${widget.title.toLowerCase()}'),
            ),
          ],
        ),
      );
}
