import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:diladrive_customer/models/driver_profile.dart';
import 'package:diladrive_customer/widgets/driver_profile_widgets.dart';

void main() {
  final assigned = DriverProfile.fromJson({
    'id': 7,
    'name': 'Driver',
    'email': 'driver@example.com',
    'vehicleAssigned': true,
    'vehicleModel': 'Transport Model',
    'vehiclePlate': 'PLATE7',
    'vehicleType': 'MPV',
    'vehicleCapacity': 7,
    'vehicleRoadTaxExpiry': '2028-12-01',
    'vehicleStatus': 'AVAILABLE',
  });
  test(
      'profile response maps assigned vehicle fields without making them updateable',
      () {
    expect(assigned.hasAssignedVehicle, isTrue);
    expect(assigned.vehicleType, 'MPV');
    expect(assigned.vehicleCapacity, 7);
    expect(assigned.vehicleRoadTaxExpiry, '2028-12-01');
    expect(assigned.vehicleStatus, 'AVAILABLE');
    expect(
        const DriverProfile(id: 1, name: 'Legacy', email: 'legacy@example.com')
            .hasAssignedVehicle,
        isFalse);
  });
  testWidgets('vehicle information is displayed as read-only values',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
        home: Scaffold(body: DriverAssignedVehicleCard(profile: assigned))));
    for (final label in [
      'Vehicle Model',
      'Plate Number',
      'Vehicle Type',
      'Passenger Capacity',
      'Road Tax Expiry Date',
      'Vehicle Status'
    ]) {
      expect(find.text(label), findsOneWidget);
    }
    final values = tester
        .widgetList<SelectableText>(find.byType(SelectableText))
        .map((widget) => widget.data)
        .toList();
    expect(
        values,
        containsAll([
          'Transport Model',
          'PLATE7',
          'MPV',
          '7',
          '2028-12-01',
          'AVAILABLE'
        ]));
    expect(find.byType(TextField), findsNothing);
    expect(find.byType(TextFormField), findsNothing);
    expect(tester.takeException(), isNull);
  });
  testWidgets('missing vehicle is explicit and overrides stale optional fields',
      (tester) async {
    const missing = DriverProfile(
        id: 1,
        name: 'Legacy',
        email: 'legacy@example.com',
        vehicleAssigned: false,
        vehicleCapacity: 7);
    expect(missing.hasAssignedVehicle, isFalse);
    await tester.pumpWidget(const MaterialApp(
        home: Scaffold(body: DriverAssignedVehicleCard(profile: missing))));
    final values = tester
        .widgetList<SelectableText>(find.byType(SelectableText))
        .map((widget) => widget.data);
    expect(values, contains('No assigned vehicle'));
    expect(find.byType(TextField), findsNothing);
  });
  testWidgets(
      'omitted vehicle data is unavailable, never a confirmed missing assignment',
      (tester) async {
    final incomplete = DriverProfile.fromJson(
        {'id': 3, 'name': 'Driver', 'email': 'driver@example.com'});
    expect(incomplete.vehicleAssignmentKnown, isFalse);
    await tester.pumpWidget(MaterialApp(
        home: Scaffold(body: DriverAssignedVehicleCard(profile: incomplete))));
    final values = tester
        .widgetList<SelectableText>(find.byType(SelectableText))
        .map((widget) => widget.data)
        .toList();
    expect(
        values,
        contains(
            'Vehicle information is unavailable. Refresh your account to try again.'));
    expect(values, isNot(contains('No assigned vehicle')));
  });
}
