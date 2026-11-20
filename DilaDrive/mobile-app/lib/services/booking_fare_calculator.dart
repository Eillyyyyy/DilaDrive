import '../models/booking_draft.dart';

/// Legacy entry point retained to prevent accidental client-side fare fallbacks.
/// The five-step flow uses BookingService.estimateFare and server road routing.
class BookingFareCalculator {
  const BookingFareCalculator._();

  static FareEstimate calculate({
    required BookingCoordinates pickup,
    required BookingCoordinates destination,
    required RecommendedVehicle vehicle,
  }) {
    throw const FormatException(
      'Local fare calculation is no longer supported. Request a road-based fare from the DilaDrive API.',
    );
  }
}
