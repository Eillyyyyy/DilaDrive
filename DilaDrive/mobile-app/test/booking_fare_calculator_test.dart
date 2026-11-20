import 'package:diladrive_customer/models/booking_draft.dart';
import 'package:diladrive_customer/services/booking_fare_calculator.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('legacy client calculator cannot fabricate a road distance or price',
      () {
    expect(
      () => BookingFareCalculator.calculate(
        pickup: const BookingCoordinates(latitude: 3, longitude: 101),
        destination: const BookingCoordinates(latitude: 4, longitude: 102),
        vehicle: const RecommendedVehicle(
            id: 1,
            model: 'Test vehicle',
            type: 'Database category',
            passengerCapacity: 4,
            luggageCapacity: 2),
      ),
      throwsA(isA<FormatException>()
          .having((e) => e.message, 'message', contains('DilaDrive API'))),
    );
  });
}
