import 'package:diladrive_customer/models/customer_booking.dart';
import 'package:diladrive_customer/models/customer_profile.dart';
import 'package:diladrive_customer/models/driver_trip.dart';
import 'package:diladrive_customer/models/mobile_user.dart';
import 'package:diladrive_customer/screens/auth/login.dart';
import 'package:diladrive_customer/screens/auth/register.dart';
import 'package:diladrive_customer/widgets/responsive_page.dart';
import 'package:diladrive_customer/screens/customer/create_booking.dart';
import 'package:diladrive_customer/screens/customer/customer_navigation.dart';
import 'package:diladrive_customer/screens/customer/dashboard.dart';
import 'package:diladrive_customer/screens/customer/profile.dart';
import 'package:diladrive_customer/screens/driver/dashboard.dart';
import 'package:diladrive_customer/screens/driver/driver_navigation.dart';
import 'package:diladrive_customer/services/customer_booking_service.dart';
import 'package:diladrive_customer/services/customer_profile_service.dart';
import 'package:diladrive_customer/services/driver_trip_service.dart';
import 'package:diladrive_customer/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

const _customer = MobileUser(
    id: 1,
    name: 'Test Customer',
    email: 'customer@example.test',
    role: 'CUSTOMER');
const _driver = MobileUser(
    id: 2, name: 'Test Driver', email: 'driver@example.test', role: 'DRIVER');

void main() {
  for (final viewport in const [
    Size(360, 800),
    Size(390, 844),
    Size(412, 915),
    Size(840, 866),
    Size(1280, 800),
    Size(1649, 928),
  ]) {
    testWidgets('Auth, Customer and Driver content is centered at $viewport',
        (tester) async {
      tester.view
        ..physicalSize = viewport
        ..devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final bookings = _Bookings();
      final profile = _Profile();
      final trips = _Trips();
      addTearDown(bookings.dispose);
      addTearDown(profile.dispose);
      addTearDown(trips.dispose);

      Future<void> show(Widget page) async {
        await tester
            .pumpWidget(MaterialApp(theme: AppTheme.lightTheme, home: page));
        await tester.pumpAndSettle();
        final content = find
            .descendant(
              of: find.byType(DilaDrivePageLayout).first,
              matching: find.byType(SizedBox),
            )
            .first;
        final width = viewport.width.clamp(0.0, 620.0);
        expect(tester.getSize(content).width, width);
        expect(tester.getTopLeft(content).dx, (viewport.width - width) / 2);
        expect(tester.takeException(), isNull);
      }

      await show(const LoginPage());
      await show(const RegisterPage());

      var opened = false;
      await show(CustomerDashboardPage(
          customer: _customer,
          bookingService: bookings,
          onCreateBooking: () => opened = true));
      final pickup = find.text('Enter pickup location');
      expect(pickup, findsOneWidget);
      await tester.tap(pickup);
      expect(opened, isTrue);
      final stats = ['Pending', 'Active', 'Completed']
          .map((label) => tester.getCenter(find.text(label)).dy)
          .toSet();
      expect(stats, hasLength(1));
      await tester.scrollUntilVisible(find.text('Recent bookings'), 120);
      if (viewport.width >= 1280) {
        final active = tester.getTopLeft(find.text('Active booking'));
        final recent = tester.getTopLeft(find.text('Recent bookings'));
        expect(recent.dy, greaterThan(active.dy));
        expect(recent.dx, active.dx);
      }
      await tester.scrollUntilVisible(find.text('BK-000007'), 100);
      expect(find.text('BK-000007'), findsOneWidget);
      expect(tester.takeException(), isNull);

      await show(ProfilePage(customer: _customer, profileService: profile));
      expect(find.text(_customer.name), findsOneWidget);
      expect(find.text(_customer.email), findsOneWidget);
      expect(find.text('Corporate customer'), findsOneWidget);
      expect(find.text('Account status'), findsNothing);
      expect(find.text('Verified / Active'), findsNothing);
      expect(find.text('Update account'), findsOneWidget);
      expect(find.text('Business documents'), findsOneWidget);
      expect(find.text('Sign out'), findsOneWidget);
      expect(find.text('View account'), findsNothing);
      await tester.tap(find.text('Update account'));
      await tester.pumpAndSettle();
      expect(find.text('Verified / Active'), findsOneWidget);
      await tester.pageBack();
      await tester.pumpAndSettle();

      await show(DriverDashboardPage(driver: _driver, tripService: trips));
      expect(find.text('Welcome, Test Driver'), findsOneWidget);
      expect(find.text('Current trip'), findsOneWidget);
      expect(find.text('BK-000009'), findsWidgets);
    });
  }

  testWidgets('bottom navigation and floating booking open at phone width',
      (tester) async {
    tester.view
      ..physicalSize = const Size(360, 800)
      ..devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
        const MaterialApp(home: CustomerNavigationPage(customer: _customer)));
    await tester.pumpAndSettle();
    final bar = find.byType(BottomAppBar);
    await tester
        .tap(find.descendant(of: bar, matching: find.text('My Bookings')));
    await tester.pumpAndSettle();
    expect(find.widgetWithText(AppBar, 'My Bookings'), findsOneWidget);
    await tester.tap(find.byType(FloatingActionButton));
    await tester.pumpAndSettle();
    expect(find.byType(CreateBookingPage), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(
        const MaterialApp(home: DriverNavigationPage(driver: _driver)));
    await tester.pumpAndSettle();
    final navigation = find.byType(BottomNavigationBar);
    await tester
        .tap(find.descendant(of: navigation, matching: find.text('Trips')));
    await tester.pumpAndSettle();
    expect(tester.widget<BottomNavigationBar>(navigation).currentIndex, 1);
    expect(tester.takeException(), isNull);
  });
}

class _Bookings extends CustomerBookingService {
  @override
  Future<List<CustomerBooking>> getMyBookings() async => const [
        CustomerBooking(
            id: 7,
            status: 'PENDING',
            pickupAddress: 'Pickup',
            destinationAddress: 'Destination'),
      ];
}

class _Profile extends CustomerProfileService {
  @override
  Future<CustomerProfile> getMyProfile() async => const CustomerProfile(
      id: 1,
      name: 'Test Customer',
      email: 'customer@example.test',
      status: 'ACTIVE',
      type: 'COMPANY');
}

class _Trips extends DriverTripService {
  @override
  Future<List<DriverTrip>> getMyTrips() async => const [
        DriverTrip(
            assignmentId: 1,
            bookingId: 9,
            assignmentStatus: 'ASSIGNED',
            pickupAddress: 'Pickup',
            destinationAddress: 'Destination'),
      ];
}
