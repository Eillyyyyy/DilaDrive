import 'dart:async';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../services/booking_location_service.dart';
import '../../../theme/app_theme.dart';

class BookingLocationSearchPage extends StatefulWidget {
  const BookingLocationSearchPage(
      {super.key,
      required this.title,
      required this.provider,
      required this.initialQuery,
      required this.onQueryChanged});
  final String title;
  final BookingLocationProvider provider;
  final String initialQuery;
  final ValueChanged<String> onQueryChanged;
  @override
  State<BookingLocationSearchPage> createState() =>
      _BookingLocationSearchPageState();
}

class _BookingLocationSearchPageState extends State<BookingLocationSearchPage> {
  late final _query = TextEditingController(text: widget.initialQuery);
  final _formKey = GlobalKey<FormState>();
  List<BookingLocationResult> _results = [];
  bool _loading = false;
  bool _searched = false;
  String? _error;
  int _generation = 0;
  Timer? _debounce;
  @override
  void dispose() {
    _generation++;
    _debounce?.cancel();
    _query.dispose();
    super.dispose();
  }

  Future<void> _search({bool autocomplete = false}) async {
    _debounce?.cancel();
    if (autocomplete
        ? _query.text.trim().length < 3
        : !_formKey.currentState!.validate()) {
      return;
    }
    if (!autocomplete) FocusScope.of(context).unfocus();
    final generation = ++_generation;
    final query = _query.text.trim();
    setState(() {
      _loading = true;
      _error = null;
      _results = [];
      _searched = true;
    });
    try {
      final provider = widget.provider;
      final results = autocomplete &&
              provider is BookingAddressAutocompleteProvider
          ? await (provider as BookingAddressAutocompleteProvider)
              .suggestAddresses(query)
          : provider is BookingAddressSearchProvider
              ? await (provider as BookingAddressSearchProvider)
                  .searchAddresses(query)
              : [
                  BookingLocationResult(
                      address: query,
                      coordinates: await provider.coordinatesForAddress(query))
                ];
      if (!mounted || generation != _generation) return;
      setState(() => _results = results
          .where((result) =>
              result.address.trim().isNotEmpty &&
              result.address.length <= 500 &&
              result.coordinates?.isValid == true)
          .toList());
    } catch (error) {
      if (!mounted || generation != _generation) return;
      setState(() => _error = error is BookingLocationException
          ? error.message
          : 'Address search is unavailable. Retry or confirm the address manually.');
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _manual() async {
    if (!_formKey.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    final address = _query.text.trim();
    final confirmed = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
                title: const Text('Confirm this address'),
                content: Text(
                    '$address\n\nThis address has no verified map pin. Distance and fare may be unavailable until Admin checks the booking.'),
                actions: [
                  TextButton(
                      onPressed: () => Navigator.pop(context, false),
                      child: const Text('Keep editing')),
                  FilledButton(
                      onPressed: () => Navigator.pop(context, true),
                      child: const Text('Confirm address'))
                ]));
    if (confirmed == true && mounted) {
      Navigator.pop(context,
          BookingLocationResult(address: address, manuallyConfirmed: true));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        resizeToAvoidBottomInset: true,
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: Text(widget.title)),
        body: SafeArea(
            child: Column(children: [
          Padding(
              padding: const EdgeInsets.all(16),
              child: Form(
                  key: _formKey,
                  child: TextFormField(
                    key: const ValueKey('location-search'),
                    controller: _query,
                    autofocus: true,
                    maxLength: 500,
                    textCapitalization: TextCapitalization.words,
                    textInputAction: TextInputAction.search,
                    onFieldSubmitted: (_) => _search(),
                    onChanged: (value) {
                      widget.onQueryChanged(value);
                      _generation++;
                      setState(() {
                        _results = [];
                        _error = null;
                        _searched = false;
                        _loading = false;
                      });
                      _debounce?.cancel();
                      final provider = widget.provider;
                      if (value.trim().length >= 3 &&
                          value.trim().length <= 500 &&
                          provider is BookingAddressAutocompleteProvider &&
                          (provider as BookingAddressAutocompleteProvider)
                              .supportsAutocomplete) {
                        _debounce = Timer(const Duration(milliseconds: 1200),
                            () => _search(autocomplete: true));
                      }
                    },
                    decoration: InputDecoration(
                        labelText: 'Search address',
                        hintText: 'Street, hotel, airport or landmark',
                        prefixIcon: const Icon(Icons.search),
                        suffixIcon: IconButton(
                            tooltip: 'Search addresses',
                            onPressed: _loading ? null : _search,
                            icon: const Icon(Icons.arrow_forward))),
                    validator: (value) {
                      final text = value?.trim() ?? '';
                      if (text.length < 3) {
                        return 'Enter a specific place (at least 3 characters).';
                      }
                      if (text.length > 500) {
                        return 'Use 500 characters or fewer.';
                      }
                      return null;
                    },
                  ))),
          if (_loading) const LinearProgressIndicator(),
          Expanded(
              child: ListView(
                  key: const ValueKey('location-results'),
                  keyboardDismissBehavior:
                      ScrollViewKeyboardDismissBehavior.onDrag,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  children: [
                if (_error != null)
                  Card(
                      child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(_error!),
                                TextButton.icon(
                                    onPressed: _search,
                                    icon: const Icon(Icons.refresh),
                                    label: const Text('Retry search'))
                              ]))),
                if (!_loading && _error == null && _results.isEmpty)
                  Padding(
                      padding: const EdgeInsets.all(20),
                      child: Text(
                          _searched
                              ? 'No matching addresses. Add a city or postcode, or try the Search button.'
                              : 'Type at least 3 characters and pause for suggestions, or tap Search.',
                          textAlign: TextAlign.center)),
                ..._results.map((result) => Card(
                    clipBehavior: Clip.antiAlias,
                    child: ListTile(
                        leading: const Icon(Icons.location_on,
                            color: AppTheme.primaryNavy),
                        title: Text(result.address),
                        subtitle: Text(
                            '${result.coordinates!.latitude.toStringAsFixed(5)}, ${result.coordinates!.longitude.toStringAsFixed(5)}'),
                        onTap: () => Navigator.pop(context, result)))),
              ])),
          Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              child: SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                      onPressed: _loading ? null : _manual,
                      icon: const Icon(Icons.edit_location_alt_outlined),
                      label: const Text('Enter address manually')))),
          TextButton(
              onPressed: () => launchUrl(
                  Uri.parse('https://www.openstreetmap.org/copyright')),
              child: const Text('Address data: OpenStreetMap contributors',
                  style:
                      TextStyle(fontSize: 11, color: AppTheme.secondaryText))),
        ])),
      );
}
