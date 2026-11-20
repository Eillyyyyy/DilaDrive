import 'package:flutter/material.dart';
import '../../../models/booking_draft.dart';
import '../../../services/booking_location_service.dart';
import '../../../services/booking_service.dart';
import 'booking_details_page.dart';
import 'booking_location_view.dart';
export 'booking_location_view.dart'
    show BookingLocationPin, BookingLocationMapBuilder, BookingLocationView;

class BookingLocationPage extends StatelessWidget {
  const BookingLocationPage(
      {super.key,
      required this.draft,
      this.locationProvider,
      this.mapBuilder,
      this.bookingService});
  final BookingDraft draft;
  final BookingLocationProvider? locationProvider;
  final BookingLocationMapBuilder? mapBuilder;
  final BookingService? bookingService;

  @override
  Widget build(BuildContext context) => BookingLocationView(
        draft: draft,
        locationProvider: locationProvider,
        mapBuilder: mapBuilder,
        bookingService: bookingService,
        onContinue: () => Navigator.of(context).push(MaterialPageRoute<void>(
            builder: (_) => BookingDetailsPage(draft: draft))),
      );
}
