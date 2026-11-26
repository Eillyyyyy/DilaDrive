import 'package:flutter/material.dart';

import '../../../models/booking_draft.dart';
import 'booking_availability_page.dart';
import 'booking_page_layout.dart';
import 'booking_time_sheet.dart';
import 'boot_capacity_reference.dart';

class BookingDetailsPage extends StatefulWidget {
  const BookingDetailsPage({super.key, required this.draft, this.onContinue});

  final BookingDraft draft;
  final VoidCallback? onContinue;

  @override
  State<BookingDetailsPage> createState() => _BookingDetailsPageState();
}

class _BookingDetailsPageState extends State<BookingDetailsPage> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  late final TextEditingController _phoneController;
  late final TextEditingController _flightController;
  late final TextEditingController _notesController;
  late BookingTripType _tripType;
  DateTime? _date;
  DateTime? _pickupTime;
  DateTime? _landedTime;
  int _passengerCount = 1;
  int _small = 0, _medium = 0, _large = 0;

  @override
  void initState() {
    super.initState();
    final draft = widget.draft;
    _nameController = TextEditingController(text: draft.customerName);
    _phoneController = TextEditingController(text: draft.phoneNumber);
    _flightController = TextEditingController(text: draft.flightNumber ?? '');
    _notesController = TextEditingController(text: draft.additionalNotes ?? '');
    _tripType = draft.tripType;
    _date = draft.bookingDate;
    _pickupTime = draft.pickupTime;
    _landedTime = draft.landedTime;
    _passengerCount = draft.passengerCount;
    _small = draft.smallCount;
    _medium = draft.mediumCount;
    _large = draft.largeCount;
    if (widget.onContinue != null) {
      _flightController.addListener(_saveDraft);
      _notesController.addListener(_saveDraft);
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    _phoneController.dispose();
    _flightController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _selectDate() async {
    final now = DateTime.now();
    final selected = await showDatePicker(
      context: context,
      initialDate:
          _date?.isAfter(now) == true ? _date : DateUtils.dateOnly(now),
      firstDate: DateTime(now.year, now.month, now.day),
      lastDate: DateTime(2100),
    );
    if (!mounted || selected == null) return;
    _update(() {
      _date = DateTime(selected.year, selected.month, selected.day);
      _pickupTime = _withDate(_pickupTime, _date!);
      _landedTime = _withDate(_landedTime, _date!);
    });
  }

  Future<void> _selectTime({required bool landedTime}) async {
    final bookingDate = _date;
    if (bookingDate == null) {
      showBookingMessage(context, 'Select the booking date first.');
      return;
    }

    final current = landedTime ? _landedTime : _pickupTime;
    final selected = await showBookingClockTime(
      context,
      title: landedTime ? 'Landed time' : 'Pickup time',
      initial: current == null
          ? TimeOfDay.fromDateTime(DateTime.now())
          : TimeOfDay.fromDateTime(current),
    );
    if (!mounted || selected == null) return;

    _update(() {
      final value = DateTime(
        bookingDate.year,
        bookingDate.month,
        bookingDate.day,
        selected.hour,
        selected.minute,
      );
      if (landedTime) {
        _landedTime = value;
      } else {
        _pickupTime = value;
      }
    });
  }

  DateTime? _withDate(DateTime? time, DateTime date) {
    if (time == null) return null;
    return DateTime(date.year, date.month, date.day, time.hour, time.minute);
  }

  String? _validateScheduledTime(DateTime? time, String label) {
    if (time == null) return 'Select the $label.';
    final now = DateTime.now();
    if (time.isBefore(now)) return 'The $label cannot be in the past.';
    return null;
  }

  void _update(VoidCallback change) {
    setState(change);
    if (widget.onContinue != null) _saveDraft();
  }

  void _saveDraft() {
    final draft = widget.draft;
    final previousKey = draft.availabilityKey;
    draft
      ..tripType = _tripType
      ..bookingDate = _date
      ..pickupTime = _pickupTime
      ..landedTime = _landedTime
      ..flightNumber = _flightController.text.trim().isEmpty
          ? null
          : _flightController.text.trim()
      ..passengerCount = _passengerCount
      ..smallCount = _small
      ..mediumCount = _medium
      ..largeCount = _large
      ..additionalNotes = _notesController.text.trim().isEmpty
          ? null
          : _notesController.text.trim();
    if (draft.availabilityKey != previousKey) draft.clearAvailability();
  }

  void _continue() {
    if (!_formKey.currentState!.validate()) return;

    final date = _date;
    if (date == null) {
      showBookingMessage(context, 'Select a booking date.');
      return;
    }
    final now = DateTime.now();
    if (DateTime(date.year, date.month, date.day)
        .isBefore(DateTime(now.year, now.month, now.day))) {
      showBookingMessage(context, 'The booking date cannot be in the past.');
      return;
    }

    final requiredTime =
        _tripType == BookingTripType.airportPickup ? _landedTime : _pickupTime;
    final timeError = _validateScheduledTime(
      requiredTime,
      _tripType == BookingTripType.airportPickup
          ? 'landed time'
          : 'pickup time',
    );
    if (timeError != null) {
      showBookingMessage(context, timeError);
      return;
    }
    if (_nameController.text.trim().isEmpty ||
        _phoneController.text.trim().isEmpty) {
      showBookingMessage(
        context,
        'Customer name or phone number is missing from your account.',
      );
      return;
    }

    _saveDraft();
    final onContinue = widget.onContinue;
    if (onContinue != null) {
      onContinue();
      return;
    }

    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => BookingAvailabilityPage(draft: widget.draft),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isAirportPickup = _tripType == BookingTripType.airportPickup;
    final requiredTime = isAirportPickup ? _landedTime : _pickupTime;
    final requiredTimeLabel = isAirportPickup ? 'Landed time' : 'Pickup time';
    final requiredTimeIcon =
        isAirportPickup ? Icons.flight_land_outlined : Icons.access_time;

    return PopScope<void>(
        onPopInvokedWithResult: (didPop, result) {
          if (didPop) _saveDraft();
        },
        child: BookingPageLayout(
          title: 'Tell us about your trip',
          subtitle:
              'Contact details are taken from the account you signed in with.',
          currentStep: 1,
          actionLabel: widget.onContinue == null
              ? 'Check vehicle availability'
              : 'Find Available Vehicles',
          onAction: _continue,
          child: Form(
            key: _formKey,
            child: Column(
              children: [
                BookingCard(
                  child: Column(
                    children: [
                      TextFormField(
                        controller: _nameController,
                        readOnly: true,
                        decoration: bookingInputDecoration(
                          label: 'Customer name',
                          icon: Icons.person_outline,
                        ),
                        validator: (value) => value == null ||
                                value.trim().isEmpty
                            ? 'Your account does not include a customer name.'
                            : null,
                      ),
                      const SizedBox(height: 14),
                      TextFormField(
                        controller: _phoneController,
                        readOnly: true,
                        keyboardType: TextInputType.phone,
                        decoration: bookingInputDecoration(
                          label: 'Phone number',
                          icon: Icons.phone_outlined,
                        ),
                        validator: (value) => value == null ||
                                value.trim().isEmpty
                            ? 'Add a phone number to your account before booking.'
                            : null,
                      ),
                      const SizedBox(height: 14),
                      DropdownButtonFormField<BookingTripType>(
                        initialValue: _tripType,
                        isExpanded: true,
                        decoration: bookingInputDecoration(
                          label: 'Trip type',
                          icon: Icons.route_outlined,
                        ),
                        items: BookingTripType.values
                            .map(
                              (type) => DropdownMenuItem(
                                value: type,
                                child: Text(type.label,
                                    overflow: TextOverflow.ellipsis),
                              ),
                            )
                            .toList(),
                        onChanged: (value) {
                          if (value == null) return;
                          _update(() => _tripType = value);
                        },
                      ),
                      const SizedBox(height: 14),
                      _PickerField(
                        label: 'Booking date',
                        value: bookingDateLabel(_date),
                        icon: Icons.calendar_month_outlined,
                        onTap: _selectDate,
                      ),
                      const SizedBox(height: 14),
                      _PickerField(
                        label: requiredTimeLabel,
                        value: bookingTimeLabel(context, requiredTime),
                        icon: requiredTimeIcon,
                        onTap: () => _selectTime(landedTime: isAirportPickup),
                      ),
                      if (isAirportPickup ||
                          _tripType == BookingTripType.airportDropoff) ...[
                        const SizedBox(height: 14),
                        TextFormField(
                          controller: _flightController,
                          maxLength: 20,
                          textCapitalization: TextCapitalization.characters,
                          decoration: bookingInputDecoration(
                            label: 'Flight number (optional)',
                            icon: Icons.flight_outlined,
                          ),
                          validator: (value) {
                            if ((value?.trim().length ?? 0) > 20) {
                              return 'Flight number must be 20 characters or fewer.';
                            }
                            return null;
                          },
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                BookingCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Passengers',
                        style: TextStyle(
                          color: bookingNavy,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 6),
                      _Counter(
                        value: _passengerCount,
                        minimum: 1,
                        onChanged: (value) =>
                            _update(() => _passengerCount = value),
                      ),
                      const Divider(height: 24),
                      const Text(
                        'Boot Capacity (Luggage Units)',
                        style: TextStyle(
                          color: bookingNavy,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 6),
                      const Text(
                        'Small bag = 1 unit · Medium bag = 2 units · Large bag = 3 units',
                      ),
                      const Text('Small luggage'),
                      _Counter(
                          value: _small,
                          minimum: 0,
                          onChanged: (v) => _update(() => _small = v)),
                      const Text('Medium luggage'),
                      _Counter(
                          value: _medium,
                          minimum: 0,
                          onChanged: (v) => _update(() => _medium = v)),
                      const Text('Large luggage'),
                      _Counter(
                          value: _large,
                          minimum: 0,
                          onChanged: (v) => _update(() => _large = v)),
                      Text(
                        'Total Boot Usage: ${_small + 2 * _medium + 3 * _large} units',
                      ),
                      const BootCapacityReference(),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                BookingCard(
                  child: TextField(
                    controller: _notesController,
                    maxLength: 1000,
                    minLines: 2,
                    maxLines: 4,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: bookingInputDecoration(
                      label: 'Additional notes (optional)',
                      icon: Icons.notes_outlined,
                      hint:
                          'Share any information that may help with your trip',
                    ),
                  ),
                ),
              ],
            ),
          ),
        ));
  }
}

class _PickerField extends StatelessWidget {
  const _PickerField({
    required this.label,
    required this.value,
    required this.icon,
    required this.onTap,
  });

  final String label;
  final String value;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: '$label, $value',
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(15),
        child: InputDecorator(
          decoration: bookingInputDecoration(label: label, icon: icon),
          child: Row(
            children: [
              Expanded(child: Text(value)),
              const Icon(Icons.expand_more, color: bookingNavy),
            ],
          ),
        ),
      ),
    );
  }
}

class _Counter extends StatelessWidget {
  const _Counter({
    required this.value,
    required this.minimum,
    required this.onChanged,
  });

  final int value;
  final int minimum;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(
          '$value',
          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
        ),
        const Spacer(),
        IconButton(
          onPressed: value <= minimum ? null : () => onChanged(value - 1),
          icon: const Icon(Icons.remove_circle_outline),
          color: bookingNavy,
          tooltip: 'Decrease',
        ),
        IconButton(
          onPressed: value >= 100 ? null : () => onChanged(value + 1),
          icon: const Icon(Icons.add_circle_outline),
          color: bookingBlue,
          tooltip: 'Increase',
        ),
      ],
    );
  }
}
