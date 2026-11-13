import 'package:flutter/material.dart';

import '../../models/booking_draft.dart';
import '../../models/customer_booking.dart';
import '../../services/booking_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';

class UpdateBookingPage extends StatefulWidget {
  const UpdateBookingPage({super.key, required this.booking});

  final CustomerBooking booking;

  @override
  State<UpdateBookingPage> createState() => _UpdateBookingPageState();
}

class _UpdateBookingPageState extends State<UpdateBookingPage> {
  final _formKey = GlobalKey<FormState>();
  final _bookingService = BookingService();
  late final TextEditingController _pickupController;
  late final TextEditingController _destinationController;
  late final TextEditingController _flightController;
  late final TextEditingController _passengersController;
  late final TextEditingController _luggageController;
  late final TextEditingController _notesController;
  BookingTripType? _tripType;
  DateTime? _date;
  DateTime? _scheduledTime;
  String? _luggageSize;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final booking = widget.booking;
    _pickupController =
        TextEditingController(text: booking.pickupAddress ?? '');
    _destinationController =
        TextEditingController(text: booking.destinationAddress ?? '');
    _flightController = TextEditingController(text: booking.flightNumber ?? '');
    _passengersController =
        TextEditingController(text: booking.passengerCount?.toString() ?? '');
    _luggageController =
        TextEditingController(text: booking.luggageCount?.toString() ?? '');
    _notesController = TextEditingController(text: booking.notes ?? '');
    _tripType = switch (booking.normalizedTripType) {
      'AIRPORT_PICKUP' => BookingTripType.airportPickup,
      'AIRPORT_DROPOFF' => BookingTripType.airportDropoff,
      'NORMAL_TRANSFER' => BookingTripType.normalTransfer,
      _ => null,
    };
    _date = booking.tripDate;
    _scheduledTime = _parseTime(booking.pickupTime);
    final savedLuggageSize = booking.luggageSize?.toUpperCase();
    _luggageSize = const {'SMALL', 'MEDIUM', 'LARGE'}.contains(savedLuggageSize)
        ? savedLuggageSize
        : null;
  }

  @override
  void dispose() {
    _bookingService.dispose();
    _pickupController.dispose();
    _destinationController.dispose();
    _flightController.dispose();
    _passengersController.dispose();
    _luggageController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  DateTime? _parseTime(String? value) {
    if (value == null || value.trim().isEmpty) return null;
    final parts = value.split(':');
    if (parts.length < 2) return null;
    final hour = int.tryParse(parts[0]);
    final minute = int.tryParse(parts[1]);
    if (hour == null || minute == null) return null;
    return DateTime(2000, 1, 1, hour, minute);
  }

  Future<void> _selectDate() async {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final selected = await showDatePicker(
      context: context,
      initialDate: _date != null && !_date!.isBefore(today) ? _date! : today,
      firstDate: today,
      lastDate: DateTime(2100),
    );
    if (selected != null) {
      setState(
          () => _date = DateTime(selected.year, selected.month, selected.day));
    }
  }

  Future<void> _selectTime() async {
    final current = _scheduledTime;
    final selected = await showTimePicker(
      context: context,
      initialTime: current == null
          ? TimeOfDay.fromDateTime(DateTime.now())
          : TimeOfDay.fromDateTime(current),
    );
    if (selected != null) {
      setState(() {
        _scheduledTime = DateTime(2000, 1, 1, selected.hour, selected.minute);
      });
    }
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    final date = _date;
    final time = _scheduledTime;
    final tripType = _tripType;
    if (tripType == null) {
      _showMessage('Select a supported trip type.');
      return;
    }
    if (date == null || time == null) {
      _showMessage('Select a trip date and time.');
      return;
    }
    final vehicleType = widget.booking.type?.trim();
    if (vehicleType == null || vehicleType.isEmpty) {
      _showMessage('The booking has no vehicle type to validate.');
      return;
    }
    if (_pickupController.text.trim().toLowerCase() ==
        _destinationController.text.trim().toLowerCase()) {
      _showMessage('Pickup and destination must be different locations.');
      return;
    }
    if (tripType == BookingTripType.airportPickup &&
        _flightController.text.trim().isEmpty) {
      _showMessage('Enter the flight number for airport pickup.');
      return;
    }
    final passengers = int.tryParse(_passengersController.text.trim());
    final luggage = int.tryParse(_luggageController.text.trim());
    if (passengers == null ||
        passengers < 1 ||
        luggage == null ||
        luggage < 0) {
      _showMessage('Enter a valid passenger and luggage count.');
      return;
    }
    if (luggage > 0 && _luggageSize == null) {
      _showMessage('Select a luggage size.');
      return;
    }

    final draft = BookingDraft(customerName: '', phoneNumber: '')
      ..pickupAddress = _pickupController.text.trim()
      ..destinationAddress = _destinationController.text.trim()
      ..tripType = tripType
      ..bookingDate = date
      ..passengerCount = passengers
      ..luggageCount = luggage
      ..luggageSize = luggage == 0 ? null : _luggageSize
      ..flightNumber = _flightController.text.trim().isEmpty
          ? null
          : _flightController.text.trim()
      ..additionalNotes = _notesController.text.trim().isEmpty
          ? null
          : _notesController.text.trim();
    if (tripType == BookingTripType.airportPickup) {
      draft.landedTime = time;
    } else {
      draft.pickupTime = time;
    }

    setState(() => _saving = true);
    try {
      await _bookingService.updatePendingBooking(
        widget.booking.id,
        draft,
        vehicleType: vehicleType,
      );
      if (mounted) Navigator.pop(context, true);
    } on BookingException catch (error) {
      if (!mounted) return;
      if (error.requiresLogin) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute<void>(builder: (_) => const LoginPage()),
          (_) => false,
        );
      } else {
        _showMessage(error.message);
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final hasLuggage = int.tryParse(_luggageController.text.trim()) != null &&
        int.parse(_luggageController.text.trim()) > 0;
    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      appBar: AppBar(title: const Text('Edit pending booking')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(18, 16, 18, 28),
          children: [
            const Card(
              child: ListTile(
                leading:
                    Icon(Icons.info_outline, color: AppTheme.secondarySkyBlue),
                title: Text('Pending request'),
                subtitle: Text(
                  'Changes are rechecked by DilaDrive. The Admin will confirm the final fare after review.',
                ),
              ),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _pickupController,
              maxLength: 500,
              decoration: const InputDecoration(labelText: 'Pickup location'),
              validator: _required,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _destinationController,
              maxLength: 500,
              decoration: const InputDecoration(labelText: 'Destination'),
              validator: _required,
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<BookingTripType>(
              initialValue: _tripType,
              decoration: const InputDecoration(labelText: 'Trip type'),
              items: BookingTripType.values
                  .map((type) =>
                      DropdownMenuItem(value: type, child: Text(type.label)))
                  .toList(growable: false),
              onChanged: _saving
                  ? null
                  : (value) => setState(() => _tripType = value!),
            ),
            const SizedBox(height: 12),
            Card(
              child: ListTile(
                title: const Text('Vehicle category'),
                subtitle: Text(widget.booking.type ?? 'Not recorded'),
                trailing: const Icon(Icons.verified_outlined),
              ),
            ),
            OutlinedButton.icon(
              onPressed: _saving ? null : _selectDate,
              icon: const Icon(Icons.calendar_month_outlined),
              label: Text(_date == null
                  ? 'Select trip date'
                  : MaterialLocalizations.of(context).formatMediumDate(_date!)),
            ),
            OutlinedButton.icon(
              onPressed: _saving ? null : _selectTime,
              icon: const Icon(Icons.schedule_outlined),
              label: Text(_scheduledTime == null
                  ? 'Select ${_tripType == BookingTripType.airportPickup ? 'landed' : 'pickup'} time'
                  : MaterialLocalizations.of(context).formatTimeOfDay(
                      TimeOfDay.fromDateTime(_scheduledTime!))),
            ),
            if (_tripType == BookingTripType.airportPickup ||
                _tripType == BookingTripType.airportDropoff) ...[
              const SizedBox(height: 12),
              TextFormField(
                controller: _flightController,
                maxLength: 20,
                textCapitalization: TextCapitalization.characters,
                decoration: InputDecoration(
                  labelText: _tripType == BookingTripType.airportPickup
                      ? 'Flight number'
                      : 'Flight number (optional)',
                ),
                validator: (value) {
                  if (_tripType == BookingTripType.airportPickup &&
                      (value == null || value.trim().isEmpty)) {
                    return 'Enter the arriving flight number.';
                  }
                  if ((value?.trim().length ?? 0) > 20) {
                    return 'Flight number must be 20 characters or fewer.';
                  }
                  return null;
                },
              ),
            ],
            const SizedBox(height: 12),
            TextFormField(
              controller: _passengersController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Passengers'),
              validator: (value) {
                final count = int.tryParse(value?.trim() ?? '');
                return count == null || count < 1
                    ? 'Enter at least 1 passenger.'
                    : null;
              },
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _luggageController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Luggage pieces'),
              onChanged: (_) => setState(() {}),
              validator: (value) {
                final count = int.tryParse(value?.trim() ?? '');
                return count == null || count < 0
                    ? 'Enter 0 or more luggage pieces.'
                    : null;
              },
            ),
            if (hasLuggage) ...[
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: _luggageSize,
                decoration: const InputDecoration(labelText: 'Luggage size'),
                items: const [
                  DropdownMenuItem(value: 'SMALL', child: Text('Small')),
                  DropdownMenuItem(value: 'MEDIUM', child: Text('Medium')),
                  DropdownMenuItem(value: 'LARGE', child: Text('Large')),
                ],
                onChanged: _saving
                    ? null
                    : (value) => setState(() => _luggageSize = value),
              ),
            ],
            const SizedBox(height: 12),
            TextFormField(
              controller: _notesController,
              maxLength: 1000,
              maxLines: 3,
              decoration: const InputDecoration(
                  labelText: 'Additional notes (optional)'),
            ),
            const SizedBox(height: 18),
            FilledButton.icon(
              onPressed: _saving ? null : _save,
              icon: _saving
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.save_outlined),
              label: Text(_saving ? 'Saving changes…' : 'Save changes'),
            ),
          ],
        ),
      ),
    );
  }

  String? _required(String? value) =>
      value == null || value.trim().isEmpty ? 'This field is required.' : null;
}
