import 'package:flutter/material.dart';
import '../../../models/booking_draft.dart';
import '../../../services/booking_location_service.dart';
import '../../../services/booking_service.dart';
import 'booking_details_page.dart';
import 'booking_location_view.dart';
import 'booking_review_page.dart';
import 'booking_trip_details_sheet.dart';
import 'booking_vehicle_page.dart';
export 'booking_location_view.dart'
    show BookingLocationPin, BookingLocationMapBuilder, BookingLocationView;

class BookingLocationPage extends StatefulWidget {
  const BookingLocationPage(
      {super.key,
      required this.draft,
      this.locationProvider,
      this.mapBuilder,
      this.bookingService,
      this.mapFlow = true});
  final BookingDraft draft;
  final BookingLocationProvider? locationProvider;
  final BookingLocationMapBuilder? mapBuilder;
  final BookingService? bookingService;

  final bool mapFlow;

  @override
  State<BookingLocationPage> createState() => _BookingLocationPageState();
}

enum _BookingStage { location, tripDetails, vehicleSelection, review }

class _BookingLocationPageState extends State<BookingLocationPage> {
  _BookingStage _stage = _BookingStage.location;
  bool _submitting = false;
  bool _submitted = false;
  final _submissionKeys = <String, String>{};
  BookingDraft get draft => widget.draft;

  bool _openDatePicker = false;

  void _go(_BookingStage stage, {bool openDatePicker = false}) {
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _stage = stage;
      _openDatePicker = openDatePicker;
    });
  }

  void _back() {
    if (_submitting) return;
    if (_submitted) {
      Navigator.of(context).popUntil((route) => route.isFirst);
      return;
    }
    _go(_BookingStage.values[_stage.index - 1]);
  }

  @override
  Widget build(BuildContext context) {
    final stageContent = switch (_stage) {
      _BookingStage.location => null,
      _BookingStage.tripDetails => BookingTripDetailsSheet(
          key: const ValueKey(_BookingStage.tripDetails),
          draft: draft,
          openDatePicker: _openDatePicker,
          onContinue: () => _go(_BookingStage.vehicleSelection)),
      _BookingStage.vehicleSelection => BookingVehiclePage(
          key: const ValueKey(_BookingStage.vehicleSelection),
          draft: draft,
          service: widget.bookingService,
          onChangeTrip: () => _go(_BookingStage.tripDetails),
          onChangeTime: () =>
              _go(_BookingStage.tripDetails, openDatePicker: true),
          onReview: () => _go(_BookingStage.review)),
      _BookingStage.review => BookingReviewPage(
          key: const ValueKey(_BookingStage.review),
          draft: draft,
          bookingService: widget.bookingService,
          idempotencyKey: (scope, create) =>
              _submissionKeys.putIfAbsent(scope, create),
          onSubmitting: (value) => setState(() => _submitting = value),
          onChooseRide: () => _go(_BookingStage.vehicleSelection),
          onSubmitted: () => setState(() => _submitted = true)),
    };
    return PopScope<void>(
      canPop: _stage == _BookingStage.location && !_submitting,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _stage != _BookingStage.location) _back();
      },
      child: BookingLocationView(
        draft: draft,
        mapFlow: widget.mapFlow,
        locationProvider: widget.locationProvider,
        mapBuilder: widget.mapBuilder,
        bookingService: widget.bookingService,
        stageContent: stageContent,
        onStageBack: _stage == _BookingStage.location ? null : _back,
        onContinue: () {
          if (widget.mapFlow) {
            _go(_BookingStage.tripDetails);
          } else {
            Navigator.of(context).push(MaterialPageRoute<void>(
                builder: (_) => BookingDetailsPage(draft: draft)));
          }
        },
      ),
    );
  }
}
