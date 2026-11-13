import 'package:flutter/material.dart';

import '../../../models/booking_draft.dart';
import 'booking_details_page.dart';
import 'booking_page_layout.dart';

class BookingLocationPage extends StatefulWidget {
  const BookingLocationPage({super.key, required this.draft});

  final BookingDraft draft;

  @override
  State<BookingLocationPage> createState() => _BookingLocationPageState();
}

class _BookingLocationPageState extends State<BookingLocationPage> {
  final _formKey = GlobalKey<FormState>();
  final _pickupController = TextEditingController();
  final _destinationController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _pickupController.text = widget.draft.pickupAddress;
    _destinationController.text = widget.draft.destinationAddress;
  }

  @override
  void dispose() {
    _pickupController.dispose();
    _destinationController.dispose();
    super.dispose();
  }

  void _continue() {
    if (!_formKey.currentState!.validate()) return;

    final pickup = _pickupController.text.trim();
    final destination = _destinationController.text.trim();
    if (pickup.toLowerCase() == destination.toLowerCase()) {
      showBookingMessage(
        context,
        'Pickup and destination must be different locations.',
      );
      return;
    }

    widget.draft
      ..pickupAddress = pickup
      ..destinationAddress = destination;
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => BookingDetailsPage(draft: widget.draft),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return BookingPageLayout(
      title: 'Where are you going?',
      subtitle: 'Enter the pickup and destination for your trip.',
      currentStep: 0,
      actionLabel: 'Continue to trip details',
      onAction: _continue,
      child: Form(
        key: _formKey,
        child: Column(
          children: [
            BookingCard(
              child: Column(
                children: [
                  TextFormField(
                    controller: _pickupController,
                    maxLength: 500,
                    textCapitalization: TextCapitalization.words,
                    textInputAction: TextInputAction.next,
                    decoration: bookingInputDecoration(
                      label: 'Pickup location',
                      hint: 'Address, airport, hotel or landmark',
                      icon: Icons.trip_origin,
                    ),
                    validator: (value) {
                      final pickup = value?.trim() ?? '';
                      if (pickup.isEmpty) return 'Enter a pickup location.';
                      if (pickup.length > 500) {
                        return 'Pickup location must be 500 characters or fewer.';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 14),
                  TextFormField(
                    controller: _destinationController,
                    maxLength: 500,
                    textCapitalization: TextCapitalization.words,
                    textInputAction: TextInputAction.done,
                    decoration: bookingInputDecoration(
                      label: 'Destination',
                      hint: 'Address, airport, hotel or landmark',
                      icon: Icons.location_on_outlined,
                    ),
                    validator: (value) {
                      final destination = value?.trim() ?? '';
                      if (destination.isEmpty) return 'Enter a destination.';
                      if (destination.length > 500) {
                        return 'Destination must be 500 characters or fewer.';
                      }
                      return null;
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
