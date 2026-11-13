import 'package:flutter_test/flutter_test.dart';

import 'package:diladrive_customer/models/customer_booking.dart';
import 'package:diladrive_customer/models/customer_booking_ordering.dart';

void main() {
  final now = DateTime(2026, 11, 12, 12);

  CustomerBooking booking(
    int id, {
    String status = 'ASSIGNED',
    String? tripDate,
    String? pickupTime,
    String? createdAt,
  }) =>
      CustomerBooking(
        id: id,
        status: status,
        tripDate: tripDate == null ? null : DateTime.parse(tripDate),
        pickupTime: pickupTime,
        createdAt: createdAt == null ? null : DateTime.parse(createdAt),
      );

  test('active bookings prioritize the nearest upcoming trip', () {
    final tomorrow = booking(
      2,
      tripDate: '2026-11-13',
      pickupTime: '08:30:00',
    );
    final laterToday = booking(
      3,
      tripDate: '2026-11-12',
      pickupTime: '15:00:00',
    );

    expect(
      CustomerBookingOrdering.activeBySchedule(laterToday, tomorrow, now: now),
      lessThan(0),
    );
  });

  test('upcoming trips sort before stale active trips', () {
    final stale = booking(
      1,
      tripDate: '2026-11-10',
      pickupTime: '10:00:00',
    );
    final upcoming = booking(
      2,
      tripDate: '2026-11-13',
      pickupTime: '08:30:00',
    );

    expect(
      CustomerBookingOrdering.activeBySchedule(stale, upcoming, now: now),
      greaterThan(0),
    );
  });

  test('recent bookings sort by server creation time', () {
    final older = booking(4, createdAt: '2026-11-10T12:00:00');
    final newer = booking(3, createdAt: '2026-11-11T12:00:00');

    expect(CustomerBookingOrdering.newestFirst(newer, older), lessThan(0));
  });
}
