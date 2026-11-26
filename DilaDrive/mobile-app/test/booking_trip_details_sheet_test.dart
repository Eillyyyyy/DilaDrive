import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/models/booking_schedule.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_trip_details_sheet.dart';
import 'package:diladrive_customer/services/booking_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Malaysia calendar rollover and all 48 half-hour slots', () {
    final now = malaysiaNow(DateTime.utc(2032, 4, 5, 16, 5));
    expect(now, DateTime.utc(2032, 4, 6, 0, 5));
    final slots = pickupSlots(now).toList();
    expect(slots, hasLength(48));
    expect(slots.every(isPickupSlot), isTrue);
    expect(slots.first.isAfter(now), isFalse);
    expect(slots[1].isAfter(now), isTrue);
    expect(bookingClockLabel(slots.first.hour, slots.first.minute), '12:00 AM');
    expect(bookingClockLabel(slots.last.hour, slots.last.minute), '11:30 PM');
    expect(isPickupSlot(DateTime.utc(2032, 4, 6, 8, 17)), isFalse);
  });

  testWidgets('pickup date starts today and elapsed slots are disabled',
      (tester) async {
    final today = bookingCalendarDate(malaysiaNow());
    final draft = BookingDraft(customerName: 'Customer', phoneNumber: '0123')
      ..bookingDate = today;
    await tester.pumpWidget(MaterialApp(
      home: BookingTripDetailsSheet(draft: draft, onContinue: () {}),
    ));
    expect(find.text('Trip type'), findsOneWidget);
    await tester.tap(find.textContaining('Pickup Date\n'));
    await tester.pumpAndSettle();
    final picker =
        tester.widget<DatePickerDialog>(find.byType(DatePickerDialog));
    expect(bookingCalendarDate(picker.firstDate), today);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    await tester.tap(find.textContaining('Pickup Time\n'));
    await tester.pumpAndSettle();
    await tester.drag(
        find.byWidgetPredicate(
            (widget) => widget is ListView && widget.itemExtent == 48),
        const Offset(0, 3000));
    await tester.pumpAndSettle();
    final midnight =
        tester.widget<ListTile>(find.widgetWithText(ListTile, '12:00 AM'));
    expect(midnight.enabled, isFalse);
    expect(midnight.onTap, isNull);
  });

  testWidgets('an existing off-slot time must be replaced explicitly',
      (tester) async {
    final date =
        bookingCalendarDate(malaysiaNow().add(const Duration(days: 7)));
    final draft = BookingDraft(customerName: 'Customer', phoneNumber: '0123')
      ..bookingDate = date
      ..pickupTime = bookingWallTime(date, 8, 17);
    var continued = false;
    await tester.pumpWidget(MaterialApp(
      home: BookingTripDetailsSheet(
          draft: draft, onContinue: () => continued = true),
    ));
    await tester.tap(find.text('Find Available Vehicles'));
    await tester.pump();
    expect(continued, isFalse);
    expect(draft.pickupTime?.minute, 17);
    ScaffoldMessenger.of(tester.element(find.byType(BookingTripDetailsSheet)))
        .hideCurrentSnackBar();
    await tester.pumpAndSettle();
    await tester.tap(find.textContaining('Pickup Time\n'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('12:30 AM'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Find Available Vehicles'));
    expect(draft.pickupTime, bookingWallTime(date, 0, 30));
    expect(continued, isTrue);
    await tester.tap(find.textContaining('Pickup Time\n'));
    await tester.pumpAndSettle();
    expect(
        tester
            .widget<ListTile>(find.widgetWithText(ListTile, '12:30 AM'))
            .selected,
        isTrue);
  });
  testWidgets('flight time is optional and independent, and toggle clears it',
      (tester) async {
    final date = malaysiaNow().add(const Duration(days: 7));
    final pickup = bookingWallTime(date, 9, 30);
    final draft = BookingDraft(customerName: 'Customer', phoneNumber: '0123')
      ..tripType = BookingTripType.airportPickup
      ..pickupAddress = 'Pickup'
      ..destinationAddress = 'Destination'
      ..pickupCoordinates =
          const BookingCoordinates(latitude: 3, longitude: 101)
      ..destinationCoordinates =
          const BookingCoordinates(latitude: 4, longitude: 102)
      ..bookingDate = bookingCalendarDate(date)
      ..pickupTime = pickup;
    var continued = 0;
    await tester.pumpWidget(MaterialApp(
      home:
          BookingTripDetailsSheet(draft: draft, onContinue: () => continued++),
    ));

    Future<void> tap(Finder finder) async {
      await tester.ensureVisible(finder);
      await tester.pumpAndSettle();
      await tester.tap(finder);
      await tester.pumpAndSettle();
    }

    expect(find.text('Flight Number'), findsNothing);
    await tap(find.text('Add Flight Details'));
    await tap(find.text('Find Available Vehicles'));
    expect(continued, 1);
    expect(draft.tripType, BookingTripType.airportPickup);
    expect(draft.landedTime, pickup);

    final number = find.widgetWithText(TextFormField, 'Flight Number');
    await tester.ensureVisible(number);
    await tester.enterText(number, ' MH123 ');
    final flightDate = find.textContaining('Flight Date\n');
    await tap(flightDate);
    Navigator.of(tester.element(find.byType(DatePickerDialog)))
        .pop(DateTime(2031, 1, 5));
    await tester.pumpAndSettle();
    await tap(find.textContaining('Flight Time\n'));
    expect(find.byType(TimePickerDialog), findsNothing);
    for (final values in {'Hour': 7, 'Minute': 17, 'AM/PM': 0}.entries) {
      final wheel = tester.widget<ListWheelScrollView>(
          find.byKey(ValueKey('flight-${values.key}')));
      (wheel.controller! as FixedExtentScrollController)
          .jumpToItem(values.value);
    }
    await tester.pumpAndSettle();
    await tap(find.text('Set flight time'));

    final service = BookingService();
    addTearDown(service.dispose);
    expect(draft.pickupTime, pickup);
    expect(service.tripPayload(draft), containsPair('flightNumber', 'MH123'));
    expect(
        service.tripPayload(draft), containsPair('flightDate', '2031-01-05'));
    expect(service.tripPayload(draft), containsPair('flightTime', '08:17'));
    expect(
        service.tripPayload(draft)['scheduledAt'], endsWith('T09:30:00.000'));

    await tap(find.text('Add Flight Details'));
    expect(find.text('Flight Number'), findsNothing);
    expect(service.tripPayload(draft)['flightNumber'], isNull);
    expect(service.tripPayload(draft).containsKey('flightDate'), isFalse);
    expect(service.tripPayload(draft).containsKey('flightTime'), isFalse);
  });

  testWidgets('quantity bounds and points invalidate a previous quote',
      (tester) async {
    final draft = BookingDraft(customerName: 'Customer', phoneNumber: '0123')
      ..availabilityCheckedFor = 'previous';
    await tester.pumpWidget(MaterialApp(
      home: BookingTripDetailsSheet(draft: draft, onContinue: () {}),
    ));
    for (final label in [
      'Passenger',
      'Small Bag',
      'Medium Luggage',
      'Large Luggage'
    ]) {
      final decrease = find.byWidgetPredicate((widget) =>
          widget is IconButton && widget.tooltip == 'Decrease $label');
      expect(tester.widget<IconButton>(decrease).onPressed, isNull);
    }
    for (final label in ['Small Bag', 'Medium Luggage', 'Large Luggage']) {
      final increase = find.byTooltip('Increase $label');
      await tester.ensureVisible(increase);
      await tester.pumpAndSettle();
      await tester.tap(increase);
      await tester.pumpAndSettle();
    }
    expect(draft.passengerCount, 1);
    expect(draft.luggageUnits, 6);
    expect(find.text('3 bags'), findsOneWidget);
    expect(find.textContaining('points'), findsNothing);
    expect(draft.availabilityCheckedFor, isNull);
  });
}
