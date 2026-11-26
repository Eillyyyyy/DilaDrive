import 'package:flutter/material.dart';

import '../../../models/booking_draft.dart';
import '../../../models/booking_schedule.dart';
import 'booking_page_layout.dart';
import 'booking_time_sheet.dart';

class BookingTripDetailsSheet extends StatefulWidget {
  const BookingTripDetailsSheet({
    super.key,
    required this.draft,
    required this.onContinue,
    this.openDatePicker = false,
  });

  final BookingDraft draft;
  final VoidCallback onContinue;
  final bool openDatePicker;

  @override
  State<BookingTripDetailsSheet> createState() =>
      _BookingTripDetailsSheetState();
}

class _BookingTripDetailsSheetState extends State<BookingTripDetailsSheet> {
  final _form = GlobalKey<FormState>();
  BookingDraft get draft => widget.draft;

  @override
  void initState() {
    super.initState();
    if (widget.openDatePicker) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _chooseDate(flight: false);
      });
    }
  }

  void _update(VoidCallback edit) {
    final key = draft.availabilityKey;
    setState(() {
      edit();
      if (draft.availabilityKey != key) draft.clearAvailability();
    });
  }

  Future<void> _chooseDate({required bool flight}) async {
    final today = bookingCalendarDate(malaysiaNow());
    final first = flight ? DateTime(1900) : today;
    final saved = flight ? draft.flightDate : draft.bookingDate;
    final initial = saved == null || bookingCalendarDate(saved).isBefore(first)
        ? today
        : saved;
    final selected = await showDatePicker(
      context: context,
      currentDate: today,
      firstDate: first,
      lastDate: DateTime(2100, 12, 31),
      initialDate: initial,
    );
    if (!mounted || selected == null) return;
    _update(() {
      final day = bookingCalendarDate(selected);
      if (flight) {
        draft.flightDate = day;
      } else {
        draft.bookingDate = day;
        if (draft.pickupTime case final time?) {
          draft.pickupTime = bookingWallTime(day, time.hour, time.minute).add(
              Duration(
                  seconds: time.second,
                  milliseconds: time.millisecond,
                  microseconds: time.microsecond));
        }
      }
    });
  }

  Future<void> _choosePickupTime() async {
    final date = draft.bookingDate;
    if (date == null) {
      showBookingMessage(context, 'Select a pickup date first.');
      return;
    }
    final slots = pickupSlots(date).toList(growable: false);
    if (!slots.last.isAfter(malaysiaNow())) {
      showBookingMessage(
          context, 'No pickup slots remain today. Choose another date.');
      return;
    }
    final selected = await showBookingPickupSlots(
      context,
      slots: slots,
      selected: draft.pickupTime,
    );
    if (mounted && selected != null) _update(() => draft.pickupTime = selected);
  }

  Future<void> _chooseFlightTime() async {
    final saved = parseBookingFlightTime(draft.flightTime) ?? malaysiaNow();
    final time = await showBookingClockTime(
      context,
      title: 'Flight time',
      initial: TimeOfDay(hour: saved.hour, minute: saved.minute),
    );
    if (!mounted || time == null) return;
    _update(() => draft.flightTime = '${time.hour.toString().padLeft(2, '0')}:'
        '${time.minute.toString().padLeft(2, '0')}');
  }

  void _setFlightEnabled(bool enabled) => _update(() {
        draft.showFlightDetails = enabled;
        if (!enabled) {
          draft
            ..flightNumber = null
            ..flightDate = null
            ..flightTime = null;
        }
      });

  void _continue() {
    if (!_form.currentState!.validate()) return;
    final date = draft.bookingDate;
    final time = draft.pickupTime;
    final now = malaysiaNow();
    final String? error;
    if (date == null) {
      error = 'Select a pickup date.';
    } else if (bookingCalendarDate(date).isBefore(bookingCalendarDate(now))) {
      error = 'The booking date cannot be in the past.';
    } else if (time == null || !isPickupSlot(time)) {
      error = 'Choose a pickup time in a 30-minute slot.';
    } else if (!bookingWallTime(date, time.hour, time.minute).isAfter(now)) {
      error = 'Choose a pickup time that is still available today.';
    } else if (draft.customerName.trim().isEmpty ||
        draft.phoneNumber.trim().isEmpty) {
      error = 'Add your name and phone number to your account before booking.';
    } else {
      error = null;
    }
    if (error != null) {
      showBookingMessage(context, error);
      return;
    }
    _update(() {
      final schedule = bookingWallTime(date!, time!.hour, time.minute);
      draft.pickupTime = schedule;
      draft.landedTime =
          draft.tripType == BookingTripType.airportPickup ? schedule : null;
    });
    widget.onContinue();
  }

  @override
  Widget build(BuildContext context) {
    final pickup = draft.pickupTime;
    final airportPickup = draft.tripType == BookingTripType.airportPickup;
    final flight = parseBookingFlightTime(draft.flightTime);
    return BookingPageLayout(
      title: 'Trip details',
      subtitle: '${draft.customerName} · ${draft.phoneNumber}',
      currentStep: 1,
      actionLabel: 'Find Available Vehicles',
      onAction: _continue,
      child: Form(
        key: _form,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          spacing: 12,
          children: [
            DropdownButtonFormField<BookingTripType>(
              isExpanded: true,
              initialValue: draft.tripType,
              decoration: bookingInputDecoration(
                  label: 'Trip type', icon: Icons.route_outlined),
              items: [
                for (final type in BookingTripType.values)
                  DropdownMenuItem(value: type, child: Text(type.label))
              ],
              onChanged: (type) {
                if (type != null) _update(() => draft.tripType = type);
              },
            ),
            _TripSection(
              title: airportPickup
                  ? 'Flight landing date and time'
                  : 'Pickup Date & Time',
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _ScheduleFields(
                    dateLabel: airportPickup ? 'Landing date' : 'Pickup Date',
                    timeLabel: airportPickup ? 'Landing time' : 'Pickup Time',
                    date: draft.bookingDate,
                    time: pickup == null || !isPickupSlot(pickup)
                        ? null
                        : bookingClockLabel(pickup.hour, pickup.minute),
                    onDate: () => _chooseDate(flight: false),
                    onTime: _choosePickupTime,
                  ),
                  if (pickup != null && !isPickupSlot(pickup))
                    const Text('Choose a pickup time in a 30-minute slot.'),
                  const SizedBox(height: 4),
                  const Text('Malaysia time'),
                ],
              ),
            ),
            _TripSection(
              title: 'Flight Details (Optional)',
              child: Column(
                children: [
                  Material(
                    type: MaterialType.transparency,
                    child: SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('Add Flight Details'),
                      value: draft.showFlightDetails,
                      onChanged: _setFlightEnabled,
                    ),
                  ),
                  if (draft.showFlightDetails) ...[
                    TextFormField(
                      initialValue: draft.flightNumber,
                      maxLength: 20,
                      textCapitalization: TextCapitalization.characters,
                      decoration: bookingInputDecoration(
                        label: 'Flight Number',
                        hint: 'MH123 or AK5123',
                        icon: Icons.flight_outlined,
                      ),
                      onChanged: (value) => _update(() => draft.flightNumber =
                          value.trim().isEmpty ? null : value.trim()),
                      validator: (value) {
                        final number = value?.trim() ?? '';
                        if (number.isEmpty) return null;
                        if (number.length > 20 ||
                            !RegExp(r'^[a-zA-Z0-9][a-zA-Z0-9 -]*$')
                                .hasMatch(number)) {
                          return 'Use up to 20 letters, numbers, spaces or hyphens.';
                        }
                        return null;
                      },
                    ),
                    _ScheduleFields(
                      dateLabel: 'Flight Date',
                      timeLabel: 'Flight Time',
                      date: draft.flightDate,
                      time: flight == null
                          ? null
                          : bookingClockLabel(flight.hour, flight.minute),
                      onDate: () => _chooseDate(flight: true),
                      onTime: _chooseFlightTime,
                    ),
                  ],
                ],
              ),
            ),
            _TripSection(
              title: 'Passengers',
              child: _QuantityRow(
                label: 'Passenger',
                icon: Icons.person_outline,
                count: draft.passengerCount,
                minimum: 1,
                onChanged: (value) =>
                    _update(() => draft.passengerCount = value),
              ),
            ),
            _TripSection(
              title: 'Luggage',
              child: Column(
                children: [
                  _QuantityRow(
                    label: 'Small Bag',
                    description: 'Backpack or handbag',
                    icon: Icons.backpack_outlined,
                    count: draft.smallCount,
                    onChanged: (value) =>
                        _update(() => draft.smallCount = value),
                  ),
                  const Divider(),
                  _QuantityRow(
                    label: 'Medium Luggage',
                    description: 'Cabin suitcase',
                    icon: Icons.work_outline,
                    count: draft.mediumCount,
                    onChanged: (value) =>
                        _update(() => draft.mediumCount = value),
                  ),
                  const Divider(),
                  _QuantityRow(
                    label: 'Large Luggage',
                    description: 'Check-in suitcase',
                    icon: Icons.luggage_outlined,
                    count: draft.largeCount,
                    onChanged: (value) =>
                        _update(() => draft.largeCount = value),
                  ),
                ],
              ),
            ),
            _TripSection(
              title: 'Total luggage',
              child: Text('${draft.totalBagCount} bags',
                  style: Theme.of(context).textTheme.titleLarge),
            ),
            TextFormField(
              initialValue: draft.additionalNotes,
              onChanged: (value) => _update(() => draft.additionalNotes =
                  value.trim().isEmpty ? null : value.trim()),
              maxLength: 1000,
              minLines: 2,
              maxLines: 4,
              decoration: bookingInputDecoration(
                label: 'Additional notes (optional)',
                icon: Icons.notes_outlined,
              ),
            ),
            TextFormField(
              initialValue: draft.additionalNotes,
              maxLength: 1000,
              maxLines: 2,
              decoration: bookingInputDecoration(
                  label: 'Additional trip requirements (optional)',
                  icon: Icons.notes_outlined),
              onChanged: (value) => _update(() => draft.additionalNotes =
                  value.trim().isEmpty ? null : value.trim()),
            ),
          ],
        ),
      ),
    );
  }
}

class _TripSection extends StatelessWidget {
  const _TripSection({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: BookingCard(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              child,
            ],
          ),
        ),
      );
}

class _ScheduleFields extends StatelessWidget {
  const _ScheduleFields({
    required this.date,
    required this.time,
    required this.onDate,
    required this.onTime,
    this.dateLabel = 'Pickup Date',
    this.timeLabel = 'Pickup Time',
  });

  final DateTime? date;
  final String? time;
  final VoidCallback onDate;
  final VoidCallback onTime;
  final String dateLabel;
  final String timeLabel;

  @override
  Widget build(BuildContext context) {
    final dateField = OutlinedButton(
      onPressed: onDate,
      child: Text('$dateLabel\n${bookingDateLabel(date)}',
          textAlign: TextAlign.center),
    );
    final timeField = OutlinedButton(
      onPressed: onTime,
      child: Text('$timeLabel\n${time ?? 'Select a time'}',
          textAlign: TextAlign.center),
    );
    return LayoutBuilder(
      builder: (context, bounds) => bounds.maxWidth >= 320
          ? Row(
              children: [
                Expanded(child: dateField),
                const SizedBox(width: 8),
                Expanded(child: timeField),
              ],
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [dateField, timeField],
            ),
    );
  }
}

class _QuantityRow extends StatelessWidget {
  const _QuantityRow({
    required this.label,
    required this.icon,
    required this.count,
    required this.onChanged,
    this.description,
    this.minimum = 0,
  });

  final String label;
  final String? description;
  final IconData icon;
  final int count;
  final int minimum;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Icon(icon, color: bookingNavy),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: Theme.of(context).textTheme.titleSmall),
                if (description case final text?)
                  Text(text, style: Theme.of(context).textTheme.bodySmall),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Decrease $label',
            onPressed: count > minimum ? () => onChanged(count - 1) : null,
            icon: const Icon(Icons.remove_circle_outline),
          ),
          Text('$count', style: Theme.of(context).textTheme.titleMedium),
          IconButton(
            tooltip: 'Increase $label',
            onPressed: count < 100 ? () => onChanged(count + 1) : null,
            icon: const Icon(Icons.add_circle_outline),
          ),
        ],
      );
}
