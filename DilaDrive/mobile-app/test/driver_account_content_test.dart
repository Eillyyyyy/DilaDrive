import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:diladrive_customer/models/driver_profile.dart';
import 'package:diladrive_customer/widgets/driver_profile_widgets.dart';

void main() {
  testWidgets(
      'account content shows exactly two read-only sections at phone width',
      (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    const profile = DriverProfile(
        id: 3,
        name: 'Driver',
        email: 'driver@example.test',
        vehicleAssigned: true,
        vehicleModel: 'Linked Model',
        vehiclePlate: 'LINKED7',
        vehicleType: 'MPV',
        vehicleCapacity: 7,
        vehicleRoadTaxExpiry: '2028-12-01',
        vehicleStatus: 'MAINTENANCE',
        totalTrips: 40,
        completedTrips: 30,
        upcomingTrips: 10);
    await tester.pumpWidget(const MaterialApp(
        home: Scaffold(
            body: SingleChildScrollView(
                child: DriverProfileContent(profile: profile)))));
    expect(find.byType(DriverProfileAvatar), findsOneWidget);
    expect(find.byType(Card), findsNWidgets(14));
    expect(find.text('Driver Information'), findsOneWidget);
    expect(find.text('Assigned Vehicle'), findsOneWidget);
    for (final label in [
      'Total Trips',
      'Completed Trips',
      'Upcoming Trips',
      'Driver ID',
      'No assigned vehicle'
    ]) {
      expect(find.text(label), findsNothing);
    }
    final values = tester
        .widgetList<SelectableText>(find.byType(SelectableText))
        .map((widget) => widget.data)
        .toList();
    expect(
        values,
        containsAll([
          'Linked Model',
          'LINKED7',
          'MPV',
          '7',
          '2028-12-01',
          'MAINTENANCE'
        ]));
    expect(find.byType(TextField), findsNothing);
    expect(find.byType(TextFormField), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
