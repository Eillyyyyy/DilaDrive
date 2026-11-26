import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import '../../../models/booking_draft.dart';
import '../../../services/booking_location_service.dart';
import '../../../services/booking_service.dart';
import '../../../theme/app_theme.dart';
import '../../../widgets/booking_route_map.dart';
import '../../../widgets/responsive_page.dart';
import 'booking_location_search_page.dart';
import 'booking_map_sheet.dart';
import 'booking_page_layout.dart';

class BookingLocationPin {
  const BookingLocationPin(
      {required this.label, required this.address, required this.coordinates});
  final String label;
  final String address;
  final BookingCoordinates coordinates;
}

typedef BookingLocationMapBuilder = Widget Function(
    BuildContext context, List<BookingLocationPin> pins);

/// Owns location input and the map shared by the booking stages.
class BookingLocationView extends StatefulWidget {
  const BookingLocationView(
      {super.key,
      required this.draft,
      required this.onContinue,
      this.locationProvider,
      this.bookingService,
      this.mapBuilder,
      this.tileProvider,
      this.mapFlow = false,
      this.stageContent,
      this.onStageBack});
  final BookingDraft draft;
  final bool mapFlow;
  final Widget? stageContent;
  final VoidCallback? onStageBack;
  final VoidCallback onContinue;
  final BookingLocationProvider? locationProvider;
  final BookingService? bookingService;
  final BookingLocationMapBuilder? mapBuilder;
  final TileProvider? tileProvider;
  @override
  State<BookingLocationView> createState() => _BookingLocationViewState();
}

class _BookingLocationViewState extends State<BookingLocationView> {
  late final _provider =
      widget.locationProvider ?? createBookingLocationProvider();
  late final _bookings = widget.bookingService ?? BookingService();
  final _versions = <bool, int>{true: 0, false: 0};
  final _resolving = <bool>{};
  final _lookupErrors = <bool, String>{};
  bool _pickupMode = true, _locating = false, _routing = false;
  bool _distanceReviewAvailable = false;
  int _gpsVersion = 0, _routeVersion = 0;
  Timer? _routeTimer;
  String? _error, _routeError;
  BookingDraft get draft => widget.draft;

  @override
  void initState() {
    super.initState();
    if (_ready && draft.roadRoute == null) _scheduleRoute();
  }

  @override
  void dispose() {
    _routeTimer?.cancel();
    _gpsVersion++;
    _routeVersion++;
    if (widget.locationProvider == null &&
        _provider is DisposableBookingLocationProvider) {
      (_provider as DisposableBookingLocationProvider).dispose();
    }
    if (widget.bookingService == null) _bookings.dispose();
    super.dispose();
  }

  BookingCoordinates? _point(bool pickup) =>
      pickup ? draft.pickupCoordinates : draft.destinationCoordinates;
  String _address(bool pickup) =>
      pickup ? draft.pickupAddress : draft.destinationAddress;
  bool _valid(bool pickup) =>
      _point(pickup)?.isValid == true &&
      _address(pickup).trim().length >= 3 &&
      _address(pickup).length <= 500;
  bool get _ready => _valid(true) && _valid(false) && _resolving.isEmpty;

  void _invalidateRoute() {
    _routeVersion++;
    _routeTimer?.cancel();
    _routing = false;
    _distanceReviewAvailable = false;
    _routeError = null;
    draft.roadRoute = null;
    draft.clearAvailability();
  }

  void _write(bool pickup, BookingCoordinates coordinates, String address,
      {bool manual = false}) {
    if (pickup) {
      draft.pickupCoordinates = coordinates;
      draft.pickupAddress = address;
      draft.pickupSearchQuery = address;
      draft.pickupManuallyConfirmed = manual;
    } else {
      draft.destinationCoordinates = coordinates;
      draft.destinationAddress = address;
      draft.destinationSearchQuery = address;
      draft.destinationManuallyConfirmed = manual;
    }
  }

  Future<void> _selectPin(BookingCoordinates coordinates, bool pickup,
      {bool fromGps = false}) async {
    if (!coordinates.isValid) return;
    final version = _versions[pickup]! + 1;
    setState(() {
      _versions[pickup] = version;
      if (pickup && !fromGps) {
        _gpsVersion++;
        _locating = false;
      }
      _write(pickup, coordinates,
          ''); // Keep the real pin even if the lookup fails.
      _resolving.add(pickup);
      _lookupErrors.remove(pickup);
      _error = null;
      _invalidateRoute();
    });
    try {
      final address =
          (await _provider.addressForCoordinates(coordinates)).trim();
      if (address.length < 3 || address.length > 500) {
        throw const BookingLocationException(
            'No readable address was found for this pin.');
      }
      if (!mounted || _versions[pickup] != version) return;
      setState(() => _write(pickup, coordinates, address));
    } catch (error) {
      if (!mounted || _versions[pickup] != version) return;
      setState(() => _lookupErrors[pickup] = error is BookingLocationException
          ? error.message
          : 'Could not look up this pin. Retry or confirm its address.');
    } finally {
      if (mounted && _versions[pickup] == version) {
        setState(() => _resolving.remove(pickup));
        _scheduleRoute();
      }
    }
  }

  void _accept(BookingLocationResult result, bool pickup) {
    final point = result.coordinates;
    if (point?.isValid != true || result.address.trim().length < 3) {
      setState(() => _error =
          'Choose a search suggestion or place a map pin first. Text alone is not a resolved location.');
      return;
    }
    setState(() {
      _versions[pickup] = _versions[pickup]! + 1;
      if (pickup) {
        _gpsVersion++;
        _locating = false;
      }
      _resolving.remove(pickup);
      _lookupErrors.remove(pickup);
      _error = null;
      _write(pickup, point!, result.address.trim(),
          manual: result.manuallyConfirmed);
      _invalidateRoute();
    });
    _scheduleRoute();
  }

  Future<void> _search(bool pickup) async {
    setState(() => _pickupMode = pickup);
    final search = BookingLocationSearchPage(
      title: pickup ? 'Choose pickup' : 'Choose drop-off',
      provider: _provider,
      initialQuery:
          pickup ? draft.pickupSearchQuery : draft.destinationSearchQuery,
      onQueryChanged: (value) {
        if (pickup) {
          draft.pickupSearchQuery = value;
        } else {
          draft.destinationSearchQuery = value;
        }
      },
    );
    final result = widget.mapFlow
        ? await showModalBottomSheet<BookingLocationResult>(
            context: context,
            useSafeArea: true,
            constraints: const BoxConstraints(maxWidth: dilaDrivePageWidth),
            isScrollControlled: true,
            builder: (context) => FractionallySizedBox(
              heightFactor: .85,
              child: search,
            ),
          )
        : await Navigator.of(context).push<BookingLocationResult>(
            MaterialPageRoute(builder: (_) => search),
          );
    if (result != null && mounted) _accept(result, pickup);
  }

  Future<void> _confirmAddress(bool pickup) async {
    final version = _versions[pickup];
    final point = _point(pickup);
    final controller = TextEditingController(text: _address(pickup));
    final address = await showDialog<String>(
        context: context,
        builder: (dialogContext) => AlertDialog(
                title: Text(pickup
                    ? 'Confirm pickup address'
                    : 'Confirm drop-off address'),
                content: TextField(
                    controller: controller,
                    autofocus: true,
                    maxLength: 500,
                    decoration: const InputDecoration(
                        labelText: 'Readable address for the selected pin')),
                actions: [
                  TextButton(
                      onPressed: () => Navigator.pop(dialogContext),
                      child: const Text('Cancel')),
                  FilledButton(
                      onPressed: () {
                        if (controller.text.trim().length >= 3) {
                          Navigator.pop(dialogContext, controller.text.trim());
                        }
                      },
                      child: const Text('Confirm address'))
                ]));
    // The dialog's exit animation may still use its controller after the result resolves.
    await Future<void>.delayed(const Duration(milliseconds: 300));
    controller.dispose();
    if (address != null &&
        mounted &&
        point != null &&
        version == _versions[pickup]) {
      _accept(
          BookingLocationResult(
              address: address, coordinates: point, manuallyConfirmed: true),
          pickup);
    }
  }

  Future<void> _current() async {
    if (_locating) return;
    final version = ++_gpsVersion;
    setState(() {
      _locating = true;
      _pickupMode = true;
      _error = null;
    });
    try {
      final coordinates = await _provider.currentCoordinates();
      if (!coordinates.isValid) {
        throw const BookingLocationException(
            'The device returned invalid coordinates. Search or pin your pickup instead.');
      }
      if (!mounted || version != _gpsVersion) return;
      setState(() => _locating = false);
      await _selectPin(coordinates, true, fromGps: true);
    } catch (error) {
      if (mounted && version == _gpsVersion) {
        setState(() => _error = error is BookingLocationException
            ? error.message
            : 'Current location is unavailable. Search or pin your pickup instead.');
      }
    } finally {
      if (mounted && version == _gpsVersion) setState(() => _locating = false);
    }
  }

  void _scheduleRoute() {
    _routeTimer?.cancel();
    if (!_ready) return;
    _routeTimer = Timer(const Duration(milliseconds: 300), _calculateRoute);
  }

  void _swap() {
    setState(() {
      final address = draft.pickupAddress, query = draft.pickupSearchQuery;
      final point = draft.pickupCoordinates,
          manual = draft.pickupManuallyConfirmed;
      draft.pickupAddress = draft.destinationAddress;
      draft.pickupSearchQuery = draft.destinationSearchQuery;
      draft.pickupCoordinates = draft.destinationCoordinates;
      draft.pickupManuallyConfirmed = draft.destinationManuallyConfirmed;
      draft.destinationAddress = address;
      draft.destinationSearchQuery = query;
      draft.destinationCoordinates = point;
      draft.destinationManuallyConfirmed = manual;
      _versions[true] = _versions[true]! + 1;
      _versions[false] = _versions[false]! + 1;
      _gpsVersion++;
      _locating = false;
      _resolving.clear();
      _lookupErrors.clear();
      _error = null;
      _invalidateRoute();
    });
    _scheduleRoute();
  }

  Future<void> _calculateRoute() async {
    if (!_ready) return;
    final a = draft.pickupCoordinates!, b = draft.destinationCoordinates!;
    if ((a.latitude - b.latitude).abs() < .000001 &&
        (a.longitude - b.longitude).abs() < .000001) {
      setState(() =>
          _routeError = 'Pickup and drop-off must be different locations.');
      return;
    }
    final version = ++_routeVersion;
    setState(() {
      _routing = true;
      _distanceReviewAvailable = false;
      _routeError = null;
      draft.roadRoute = null;
      draft.clearAvailability();
    });
    try {
      final route = await _bookings.previewRoute(draft);
      final distance = route['distanceKm'],
          duration = route['durationSeconds'],
          geometry = route['geometry'];
      if (distance is! num ||
          !distance.isFinite ||
          distance <= 0 ||
          duration is! num ||
          !duration.isFinite ||
          duration <= 0 ||
          geometry is! List ||
          geometry.length < 2 ||
          geometry.any((p) =>
              p is! List ||
              p.length < 2 ||
              p[0] is! num ||
              p[1] is! num ||
              !BookingCoordinates(
                      latitude: (p[1] as num).toDouble(),
                      longitude: (p[0] as num).toDouble())
                  .isValid)) {
        throw const BookingException(
            'The routing service returned an invalid driving route. Please retry.');
      }
      if (!mounted || version != _routeVersion) return;
      setState(() => draft.roadRoute = route);
    } catch (error) {
      if (mounted && version == _routeVersion) {
        setState(() {
          _distanceReviewAvailable = draft.existingBookingId == null &&
              error is BookingException &&
              error.code == 'ROAD_DISTANCE_REVIEW_AVAILABLE';
          _routeError = widget.mapFlow && _distanceReviewAvailable
              ? 'The driving route is temporarily unavailable. Retry to continue.'
              : error is BookingException
                  ? error.message
                  : 'The driving route could not be loaded. Please retry.';
        });
      }
    } finally {
      if (mounted && version == _routeVersion) setState(() => _routing = false);
    }
  }

  Widget _location(bool pickup) {
    final point = _point(pickup);
    return ListTile(
        key: ValueKey(pickup ? 'pickup-location' : 'destination-location'),
        contentPadding: EdgeInsets.zero,
        leading: Icon(Icons.location_on,
            color: pickup ? Colors.green.shade700 : Colors.red),
        title: Text(pickup ? 'Pickup location' : 'Drop-off location',
            style:
                const TextStyle(fontSize: 12, color: AppTheme.secondaryText)),
        subtitle:
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(
              _address(pickup).isNotEmpty
                  ? _address(pickup)
                  : _resolving.contains(pickup)
                      ? 'Finding address…'
                      : 'Search an address or tap the map',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                  fontWeight: FontWeight.w600, color: AppTheme.primaryNavy)),
          if (point != null)
            Text(
                '${point.latitude.toStringAsFixed(5)}, ${point.longitude.toStringAsFixed(5)}',
                style: const TextStyle(fontSize: 11)),
        ]),
        trailing: _resolving.contains(pickup)
            ? const SizedBox.square(
                dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
            : const Icon(Icons.search),
        onTap: () => _search(pickup));
  }

  Widget _card(Widget child) => Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
      clipBehavior: Clip.antiAlias,
      child: Padding(padding: const EdgeInsets.all(16), child: child));

  @override
  Widget build(BuildContext context) {
    final pins = [
      for (final pickup in [true, false])
        if (_point(pickup) != null)
          BookingLocationPin(
              label: pickup ? 'Pickup' : 'Drop-off',
              address: _address(pickup),
              coordinates: _point(pickup)!)
    ];
    final canContinue = _ready &&
        !_locating &&
        !_routing &&
        (draft.roadRoute != null ||
            (!widget.mapFlow && _distanceReviewAvailable));
    final routeSummary = draft.roadRoute == null
        ? null
        : Text(
            '${bookingDistanceLabel(draft.routeDistanceKm!)} \u00b7 '
            '${(draft.routeDurationSeconds! / 60).ceil()} minutes',
            key: const ValueKey('route-summary'),
            style: Theme.of(context).textTheme.titleMedium,
          );
    final locations =
        _card(Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Row(
        children: [
          Expanded(
            child: Text('Where are you going?',
                style: Theme.of(context).textTheme.titleLarge),
          ),
          IconButton(
            tooltip: 'Swap pickup and drop-off',
            onPressed:
                _point(true) == null && _point(false) == null ? null : _swap,
            icon: const Icon(Icons.swap_vert, color: AppTheme.primaryNavy),
          ),
        ],
      ),
      _location(true),
      const Divider(height: 1),
      _location(false),
      if (widget.mapFlow && routeSummary != null) routeSummary,
      Wrap(spacing: 10, children: [
        ChoiceChip(
            key: const ValueKey('pickup-mode'),
            label: const Text('Pin pickup'),
            selected: _pickupMode,
            avatar: Icon(Icons.location_on, color: Colors.green.shade700),
            onSelected: (_) => setState(() => _pickupMode = true)),
        ChoiceChip(
            key: const ValueKey('dropoff-mode'),
            label: const Text('Pin drop-off'),
            selected: !_pickupMode,
            avatar: const Icon(Icons.location_on, color: Colors.red),
            onSelected: (_) => setState(() => _pickupMode = false)),
      ]),
      if (!widget.mapFlow)
        TextButton.icon(
            onPressed: _locating ? null : _current,
            icon: const Icon(Icons.my_location),
            label: Text(_locating
                ? 'Getting current location...'
                : 'Use Current Location')),
      if (widget.mapFlow && _locating)
        const Text('Getting current location...'),
      Text('Tap the map to set ${_pickupMode ? 'pickup' : 'drop-off'}.',
          style: const TextStyle(color: AppTheme.secondaryText)),
    ]));
    final route =
        _card(Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      if (_error != null)
        Text(_error!, style: TextStyle(color: Colors.red.shade800)),
      for (final pickup in [true, false])
        if (_lookupErrors.containsKey(pickup)) ...[
          Text('${pickup ? 'Pickup' : 'Drop-off'}: ${_lookupErrors[pickup]}',
              style: TextStyle(color: Colors.red.shade800)),
          Wrap(children: [
            TextButton(
                onPressed: () => _selectPin(_point(pickup)!, pickup),
                child: Text('Retry ${pickup ? 'pickup' : 'drop-off'} address')),
            TextButton(
                onPressed: () => _confirmAddress(pickup),
                child:
                    Text('Confirm ${pickup ? 'pickup' : 'drop-off'} address'))
          ]),
        ],
      if (_routing) ...[
        const LinearProgressIndicator(),
        const SizedBox(height: 8),
        const Text('Calculating driving route...')
      ],
      if (_routeError != null) ...[
        Text(_routeError!, style: TextStyle(color: Colors.red.shade800)),
        TextButton(
            onPressed: _routing ? null : _calculateRoute,
            child: const Text('Retry route'))
      ],
      if (!widget.mapFlow && routeSummary != null) routeSummary,
      if (!_ready)
        const Text('Choose a readable address and map pin for both locations.',
            style: TextStyle(color: AppTheme.secondaryText)),
    ]));
    final action = FilledButton(
        key: const ValueKey('continue-trip-details'),
        onPressed: canContinue ? widget.onContinue : null,
        style: FilledButton.styleFrom(
            backgroundColor: AppTheme.primaryNavy,
            minimumSize: const Size.fromHeight(50)),
        child: const Text('Continue to trip details'));
    Widget map(EdgeInsets padding) => SizedBox.expand(
          key: const ValueKey('booking-location-map'),
          child: widget.mapBuilder?.call(context, pins) ??
              BookingRouteMap(
                draft: draft,
                tileProvider: widget.tileProvider,
                cameraPadding: padding,
                onSelect: (coordinates) {
                  if (widget.stageContent == null) {
                    _selectPin(coordinates, _pickupMode);
                  }
                },
              ),
        );
    if (widget.mapFlow) {
      return BookingMapSheet(
        mapBuilder: map,
        onBack: widget.onStageBack,
        onLocate: widget.stageContent == null && !_locating ? _current : null,
        expanded: widget.stageContent != null,
        child: widget.stageContent ??
            BookingSheetContent(
              action: action,
              child: Column(children: [
                locations,
                if (!_ready ||
                    _routing ||
                    _error != null ||
                    _routeError != null ||
                    _lookupErrors.isNotEmpty)
                  route,
              ]),
            ),
      );
    }
    return DilaDriveScaffold(
      appBar: AppBar(title: const Text('Create Booking')),
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) => ListView(
            padding: pagePadding(context),
            children: [
              locations,
              const SizedBox(height: 14),
              SizedBox(
                  height: (constraints.maxHeight * .48).clamp(240, 420),
                  child: map(const EdgeInsets.all(42))),
              const SizedBox(height: 14),
              route,
              action,
            ],
          ),
        ),
      ),
    );
  }
}
