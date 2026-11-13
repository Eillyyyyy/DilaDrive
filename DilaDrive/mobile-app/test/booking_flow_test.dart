import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_details_page.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_fare_page.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_location_page.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_review_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  BookingDraft createDraft() => BookingDraft(
        customerName: 'Test Customer',
        phoneNumber: '0123456789',
      );

  testWidgets('location form rejects identical pickup and destination',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: BookingLocationPage(draft: createDraft())),
    );

    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'Melaka Sentral');
    await tester.enterText(fields.at(1), 'melaka sentral');
    await tester.tap(find.text('Continue to trip details'));
    await tester.pumpAndSettle();

    expect(
      find.text('Pickup and destination must be different locations.'),
      findsOneWidget,
    );
    expect(find.text('Where are you going?'), findsOneWidget);
  });

  testWidgets('location fields match the backend address length limit',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: BookingLocationPage(draft: createDraft())),
    );

    final fields = tester.widgetList<TextField>(find.byType(TextField));
    expect(fields, hasLength(2));
    expect(fields.map((field) => field.maxLength), everyElement(500));
  });

  testWidgets('location details remain when navigating back from trip details',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: BookingLocationPage(draft: createDraft())),
    );

    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'Melaka Sentral');
    await tester.enterText(fields.at(1), 'Kuala Lumpur Airport');
    await tester.tap(find.text('Continue to trip details'));
    await tester.pumpAndSettle();

    expect(find.byType(BookingDetailsPage), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();

    final returnedFields = tester.widgetList<TextFormField>(
      find.byType(TextFormField),
    );
    expect(returnedFields.first.controller!.text, 'Melaka Sentral');
    expect(returnedFields.last.controller!.text, 'Kuala Lumpur Airport');
  });

  testWidgets('airport pickup requires a flight number', (tester) async {
    final draft = createDraft()..tripType = BookingTripType.airportPickup;
    await tester.pumpWidget(
      MaterialApp(home: BookingDetailsPage(draft: draft)),
    );

    expect(find.text('Landed time'), findsOneWidget);
    await tester.tap(find.text('Check vehicle availability'));
    await tester.pumpAndSettle();

    expect(find.text('Enter the arriving flight number.'), findsOneWidget);
  });

  testWidgets('flight and notes fields match backend length limits',
      (tester) async {
    final draft = createDraft()..tripType = BookingTripType.airportPickup;
    await tester.pumpWidget(
      MaterialApp(home: BookingDetailsPage(draft: draft)),
    );

    final fields = tester.widgetList<TextField>(find.byType(TextField));
    final flightField = fields
        .singleWhere((field) => field.decoration?.labelText == 'Flight number');
    final notesField = tester.widget<TextField>(find.byType(TextField).last);

    expect(flightField.maxLength, 20);
    expect(notesField.maxLength, 1000);
  });

  testWidgets('fare step explains Admin pricing and continues to review',
      (tester) async {
    final draft = createDraft()
      ..pickupAddress = 'Melaka Sentral'
      ..destinationAddress = 'Kuala Lumpur Airport'
      ..bookingDate = DateTime(2030, 1, 1)
      ..pickupTime = DateTime(2030, 1, 1, 10)
      ..recommendedVehicle = const RecommendedVehicle(
        id: 8,
        model: 'Fleet Sedan',
        type: 'Sedan',
        passengerCapacity: 4,
        luggageCapacity: 2,
      );

    await tester.pumpWidget(
      MaterialApp(home: BookingFarePage(draft: draft)),
    );

    expect(find.textContaining('fare estimate is unavailable'), findsOneWidget);
    expect(find.text('Calculate fare estimate'), findsNothing);
    expect(find.text('Review booking'), findsOneWidget);

    await tester.tap(find.text('Review booking'));
    await tester.pumpAndSettle();

    expect(find.byType(BookingReviewPage), findsOneWidget);
    expect(draft.fareEstimate, isNull);
  });
}
