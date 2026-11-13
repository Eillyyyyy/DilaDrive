import 'package:diladrive_customer/models/customer_booking.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parses the customer booking response fields used by the API', () {
    final booking = CustomerBooking.fromJson({
      'bookingId': 17,
      'bookingType': 'MPV',
      'tripType': 'airportPickup',
      'notes': 'Meet at the arrivals hall',
      'tripDate': '2026-12-01',
      'pickupTime': '09:30:00',
      'pickupAddress': 'Kuala Lumpur International Airport',
      'destinationAddress': 'Melaka Sentral',
      'flightNo': 'AB123',
      'passengerCount': 3,
      'luggageCount': 2,
      'luggageSize': 'MEDIUM',
      'distanceKm': 145.5,
      'waitingHour': 0.5,
      'tripFare': 240.75,
      'status': 'CONFIRMED',
      'createdAt': '2026-11-13T09:15:00',
    });

    expect(booking.id, 17);
    expect(booking.type, 'MPV');
    expect(booking.tripType, 'airportPickup');
    expect(booking.notes, 'Meet at the arrivals hall');
    expect(booking.tripDate, DateTime(2026, 12, 1));
    expect(booking.pickupTime, '09:30:00');
    expect(booking.pickupAddress, 'Kuala Lumpur International Airport');
    expect(booking.destinationAddress, 'Melaka Sentral');
    expect(booking.flightNumber, 'AB123');
    expect(booking.passengerCount, 3);
    expect(booking.luggageCount, 2);
    expect(booking.luggageSize, 'MEDIUM');
    expect(booking.distanceKm, 145.5);
    expect(booking.waitingHours, 0.5);
    expect(booking.fare, 240.75);
    expect(booking.status, 'CONFIRMED');
    expect(booking.createdAt, DateTime(2026, 11, 13, 9, 15));
  });

  test('normalizes trip types for customer booking labels', () {
    const airportPickup = CustomerBooking(
      id: 1,
      status: 'ASSIGNED',
      tripType: 'airportPickup',
    );
    const hyphenatedPickup = CustomerBooking(
      id: 2,
      status: 'ASSIGNED',
      tripType: 'airport-pickup',
    );
    const airportDropoff = CustomerBooking(
      id: 3,
      status: 'ASSIGNED',
      tripType: 'airportDropoff',
    );

    expect(airportPickup.normalizedTripType, 'AIRPORT_PICKUP');
    expect(airportPickup.isAirportPickup, isTrue);
    expect(airportPickup.tripTypeLabel, 'Airport pickup');
    expect(hyphenatedPickup.isAirportPickup, isTrue);
    expect(airportDropoff.tripTypeLabel, 'Airport drop-off');
  });
}
