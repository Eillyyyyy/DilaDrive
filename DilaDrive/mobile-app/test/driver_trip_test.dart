import 'package:flutter_test/flutter_test.dart';
import 'package:diladrive_customer/models/driver_trip.dart';

void main() {
  test('parses assignment response fields and next permitted status', () {
    final trip = DriverTrip.fromJson({
      'assignmentId': 14,
      'bookingId': 27,
      'customer': 'Customer Name',
      'tripDate': '2026-11-12',
      'pickupTime': '09:30:00',
      'tripType': 'AIRPORT_PICKUP',
      'flightNo': 'MH 123',
      'pickupAddress': 'Pickup',
      'destinationAddress': 'Destination',
      'passengerCount': 3,
      'luggageCount': 2,
      'luggageSize': 'MEDIUM',
      'vehiclePlate': 'ABC 1234',
      'assignmentStatus': 'ASSIGNED',
    });

    expect(trip.bookingReference, 'BK-000027');
    expect(trip.customer, 'Customer Name');
    expect(trip.isAirportPickup, isTrue);
    expect(trip.tripTypeLabel, 'Airport pickup');
    expect(trip.flightNo, 'MH 123');
    expect(trip.luggageCount, 2);
    expect(trip.luggageSize, 'MEDIUM');
    expect(trip.nextStatus, 'OTW');
    expect(trip.isActive, isTrue);
  });

  test('does not allow a next status after completion or cancellation', () {
    for (final status in ['COMPLETED', 'CANCELLED']) {
      final trip = DriverTrip.fromJson({
        'assignmentId': 1,
        'bookingId': 2,
        'assignmentStatus': status,
      });

      expect(trip.nextStatus, isNull);
      expect(trip.isActive, isFalse);
    }
  });

  test('fails closed for unknown assignment statuses', () {
    final trip = DriverTrip.fromJson({
      'assignmentId': 1,
      'bookingId': 2,
      'assignmentStatus': 'UNRECOGNIZED',
    });

    expect(trip.nextStatus, isNull);
    expect(trip.isActive, isFalse);
  });

  test('matches the backend trip status transition sequence', () {
    const transitions = {
      'ASSIGNED': 'OTW',
      'OTW': 'WAITING',
      'WAITING': 'PICKUP',
      'PICKUP': 'DROP',
      'DROP': 'COMPLETED',
    };

    for (final entry in transitions.entries) {
      final trip = DriverTrip.fromJson({
        'assignmentId': 1,
        'bookingId': 2,
        'assignmentStatus': entry.key,
      });
      expect(trip.nextStatus, entry.value, reason: entry.key);
    }
  });

  test('dashboard prioritizes trips underway then the nearest assignment', () {
    DriverTrip trip(String status, String date, String time) =>
        DriverTrip.fromJson({
          'assignmentId': 1,
          'bookingId': 2,
          'assignmentStatus': status,
          'tripDate': date,
          'pickupTime': time,
        });

    final underway = trip('OTW', '2026-11-20', '12:00:00');
    final soon = trip('ASSIGNED', '2026-11-13', '09:00:00');
    final later = trip('ASSIGNED', '2026-11-22', '09:00:00');

    expect(compareDriverTripsForDashboard(underway, soon), lessThan(0));
    expect(compareDriverTripsForDashboard(soon, later), lessThan(0));
  });

  test('rejects incomplete assignment responses', () {
    expect(
      () => DriverTrip.fromJson({'bookingId': 1}),
      throwsFormatException,
    );
  });
}
