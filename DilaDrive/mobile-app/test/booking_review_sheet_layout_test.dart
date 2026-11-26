import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_map_sheet.dart';
import 'package:diladrive_customer/screens/customer/booking/booking_review_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  for (final size in const [
    Size(360, 800),
    Size(390, 844),
    Size(412, 915),
    Size(840, 866),
    Size(1280, 800),
    Size(1649, 928),
  ]) {
    testWidgets('review remains scrollable with pinned action at $size',
        (tester) async {
      tester.view
        ..physicalSize = size
        ..devicePixelRatio = 1
        ..padding = const FakeViewPadding(top: 24, bottom: 34);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPadding);
      addTearDown(tester.view.resetViewInsets);

      final draft =
          BookingDraft(customerName: 'Customer Name', phoneNumber: '0123456789')
            ..pickupAddress =
                List.filled(10, 'Pickup street and building').join(' ')
            ..destinationAddress =
                List.filled(10, 'Destination street and building').join(' ')
            ..bookingDate = DateTime(2035, 5, 1)
            ..pickupTime = DateTime(2035, 5, 1, 8, 30)
            ..flightNumber = 'MH123'
            ..flightDate = DateTime(2035, 5, 1)
            ..flightTime = '07:17'
            ..additionalNotes = List.filled(15, 'Long customer note').join(' ')
            ..smallCount = 1
            ..mediumCount = 2
            ..largeCount = 3
            ..recommendedVehicle = const RecommendedVehicle(
                id: 1,
                model: 'ALZA',
                type: 'MPV',
                passengerCapacity: 6,
                luggageCapacity: 15)
            ..fareEstimate = const FareEstimate(
                distanceKm: 25,
                baseFare: 0,
                distanceCharge: 50,
                additionalCharges: [],
                total: 50,
                ratePerKm: 2);
      draft.availabilityCheckedFor = draft.availabilityKey;
      await tester.pumpWidget(MaterialApp(
        home: BookingMapSheet(
          expanded: true,
          mapBuilder: (_) => const ColoredBox(
              key: ValueKey('review-map'), color: Colors.lightBlue),
          child: BookingReviewPage(draft: draft, onSubmitted: () {}),
        ),
      ));

      final action = find.text('Request Booking');
      expect(action.hitTestable(), findsOneWidget);
      final map = find.byKey(const ValueKey('review-map'));
      expect(tester.getSize(map).width, size.width);
      final sheet = find.byType(ListView);
      final sheetWidth = size.width.clamp(0.0, 620.0);
      expect(tester.getSize(sheet).width, sheetWidth);
      expect(tester.getTopLeft(sheet).dx, (size.width - sheetWidth) / 2);
      expect(tester.takeException(), isNull);
      final list = find.byType(ListView);
      await tester.drag(list, const Offset(0, -350));
      await tester.pumpAndSettle();
      final controller = tester.widget<ListView>(list).controller!;
      controller.jumpTo(controller.position.maxScrollExtent);
      await tester.pumpAndSettle();
      expect(find.text('Estimated total').hitTestable(), findsOneWidget);
      expect(action.hitTestable(), findsOneWidget);

      await tester.drag(list, const Offset(0, 2400));
      await tester.pumpAndSettle();
      tester.view.viewInsets = FakeViewPadding(bottom: size.height - 80);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(tester.getBottomRight(action).dy, lessThanOrEqualTo(80));
    });
  }
}
