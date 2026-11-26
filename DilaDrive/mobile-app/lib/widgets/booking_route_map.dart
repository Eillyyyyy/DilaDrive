import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';
import '../models/booking_draft.dart';
import '../theme/app_theme.dart';

class BookingRouteMap extends StatefulWidget {
  const BookingRouteMap(
      {super.key,
      required this.draft,
      required this.onSelect,
      this.tileProvider,
      this.cameraPadding = const EdgeInsets.all(42)});
  final BookingDraft draft;
  final ValueChanged<BookingCoordinates> onSelect;
  final TileProvider? tileProvider;
  final EdgeInsets cameraPadding;

  @override
  State<BookingRouteMap> createState() => _BookingRouteMapState();
}

class _BookingRouteMapState extends State<BookingRouteMap> {
  final _controller = MapController();
  final _tileReset = StreamController<void>.broadcast();
  late final _tileProvider = widget.tileProvider ?? NetworkTileProvider();
  bool _tileUnavailable = false;
  bool _tileNoticeScheduled = false;

  static const _tileUrl = String.fromEnvironment(
    'MAP_TILE_URL',
    defaultValue: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  );
  static const _providerCredit = String.fromEnvironment('MAP_TILE_ATTRIBUTION');
  bool _ready = false;
  String? _cameraSignature;
  Timer? _cameraResize;
  BookingDraft get draft => widget.draft;

  @override
  void didUpdateWidget(BookingRouteMap oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.cameraPadding != widget.cameraPadding) {
      _cameraResize?.cancel();
      _cameraResize = Timer(const Duration(milliseconds: 140), _fitCamera);
    }
  }

  @override
  void dispose() {
    _cameraResize?.cancel();
    _tileReset.close();
    _controller.dispose();
    super.dispose();
  }

  void _onTileError(TileImage tile, Object error, StackTrace? stackTrace) {
    if (_tileUnavailable || _tileNoticeScheduled) return;
    _tileNoticeScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _tileNoticeScheduled = false;
      if (mounted) setState(() => _tileUnavailable = true);
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  void _retryTiles() {
    setState(() => _tileUnavailable = false);
    _tileReset.add(null);
  }

  void _fitCamera() {
    if (!mounted || !_ready) return;
    final points = [draft.pickupCoordinates, draft.destinationCoordinates]
        .whereType<BookingCoordinates>()
        .where((p) => p.isValid)
        .map((p) => LatLng(p.latitude, p.longitude))
        .toList();
    if (points.isEmpty ||
        context.size == null ||
        context.size!.height <= widget.cameraPadding.vertical + 8) {
      return;
    }
    if (points.length == 1) {
      _controller.move(points.first, 15,
          offset: Offset(
              0, (widget.cameraPadding.top - widget.cameraPadding.bottom) / 2));
      return;
    }
    final geometry = draft.roadRoute?['geometry'] as List?;
    if (geometry != null) {
      points.addAll(geometry.map(
          (p) => LatLng((p[1] as num).toDouble(), (p[0] as num).toDouble())));
    }
    _controller.fitCamera(CameraFit.bounds(
        bounds: LatLngBounds.fromPoints(points),
        padding: widget.cameraPadding,
        maxZoom: 16));
  }

  @override
  Widget build(BuildContext context) {
    final pins = [draft.pickupCoordinates, draft.destinationCoordinates]
        .whereType<BookingCoordinates>()
        .toList();
    final geometry = (draft.roadRoute?['geometry'] as List?) ?? [];
    final signature =
        '${pins.map((p) => '${p.latitude},${p.longitude}').join(';')}|${identityHashCode(draft.roadRoute)}';
    if (signature != _cameraSignature) {
      _cameraSignature = signature;
      WidgetsBinding.instance.addPostFrameCallback((_) => _fitCamera());
    }
    return Stack(children: [
      FlutterMap(
        mapController: _controller,
        options: MapOptions(
          onMapReady: () {
            _ready = true;
            WidgetsBinding.instance.addPostFrameCallback((_) => _fitCamera());
          },
          initialCenter: pins.isEmpty
              ? const LatLng(3.139, 101.6869)
              : LatLng(pins.first.latitude, pins.first.longitude),
          initialZoom: 11,
          onTap: (_, p) => widget.onSelect(
              BookingCoordinates(latitude: p.latitude, longitude: p.longitude)),
        ),
        children: [
          TileLayer(
              urlTemplate: _tileUrl,
              tileProvider: _tileProvider,
              reset: _tileReset.stream,
              errorTileCallback: _onTileError,
              evictErrorTileStrategy: EvictErrorTileStrategy.dispose,
              // NetworkTileProvider uses flutter_map's persistent, HTTP-header-aware
              // native cache by default. Chrome uses the browser HTTP cache.
              // Do not override freshness, disable caches, or bulk-prefetch tiles.
              userAgentPackageName: 'com.example.diladrive_customer',
              subdomains: const [],
              panBuffer: 0,
              maxNativeZoom: 19,
              retinaMode: false),
          if (geometry.isNotEmpty)
            PolylineLayer(polylines: [
              Polyline(
                  points: geometry
                      .map((p) => LatLng(
                          (p[1] as num).toDouble(), (p[0] as num).toDouble()))
                      .toList(),
                  color: AppTheme.primaryNavy,
                  strokeWidth: 5)
            ]),
          MarkerLayer(markers: [
            if (draft.pickupCoordinates?.isValid == true)
              _marker(draft.pickupCoordinates!, pickup: true),
            if (draft.destinationCoordinates?.isValid == true)
              _marker(draft.destinationCoordinates!, pickup: false),
          ]),
        ],
      ),
      if (_tileUnavailable)
        Positioned(
          left: 10,
          bottom: (widget.cameraPadding.bottom - 18).clamp(2, double.infinity),
          child: Material(
            borderRadius: BorderRadius.circular(20),
            child: IconButton(
              tooltip: 'Map tiles unavailable. Retry map tiles',
              onPressed: _retryTiles,
              icon: const Icon(Icons.refresh),
            ),
          ),
        ),
      Positioned(
          bottom: (widget.cameraPadding.bottom - 18).clamp(2, double.infinity),
          right: 4,
          child: Material(
              color: Colors.white,
              child: InkWell(
                  onTap: () => launchUrl(
                      Uri.parse('https://www.openstreetmap.org/copyright')),
                  child: Padding(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 4, vertical: 3),
                    child: Text(
                      _providerCredit.isEmpty
                          ? '\u00a9 OpenStreetMap contributors'
                          : '\u00a9 OpenStreetMap contributors \u00b7 $_providerCredit',
                      style: const TextStyle(fontSize: 11),
                    ),
                  )))),
    ]);
  }

  Marker _marker(BookingCoordinates coordinates, {required bool pickup}) =>
      Marker(
          point: LatLng(coordinates.latitude, coordinates.longitude),
          width: 44,
          height: 44,
          child: Icon(pickup ? Icons.trip_origin : Icons.location_on,
              key: ValueKey(
                  pickup ? 'pickup-map-marker' : 'destination-map-marker'),
              size: 36,
              color: pickup ? Colors.green.shade700 : Colors.red));
}
