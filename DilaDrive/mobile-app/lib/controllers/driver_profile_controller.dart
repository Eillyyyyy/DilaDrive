import 'package:flutter/foundation.dart';
import '../models/driver_profile.dart';
import '../services/driver_profile_service.dart';

class DriverProfileController extends ChangeNotifier {
  DriverProfileController({DriverProfileService? service})
      : _service = service ?? DriverProfileService(),
        _ownsService = service == null;
  final DriverProfileService _service;
  final bool _ownsService;
  DriverProfile? profile;
  DriverProfileException? error;
  bool loading = false;
  bool _disposed = false;
  int _generation = 0;

  Future<void> load() async {
    if (_disposed) return;
    final generation = ++_generation;
    loading = true;
    error = null;
    notifyListeners();
    try {
      final result = await _service.getProfile();
      if (_disposed || generation != _generation) return;
      profile = result;
    } catch (failure) {
      if (_disposed || generation != _generation) return;
      error = failure is DriverProfileException
          ? failure
          : const DriverProfileException(
              'Unable to load your profile. Please retry.');
    } finally {
      if (!_disposed && generation == _generation) {
        loading = false;
        notifyListeners();
      }
    }
  }

  void update(DriverProfile value) {
    if (_disposed) return;
    _generation++;
    profile = value;
    error = null;
    loading = false;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    if (_ownsService) _service.dispose();
    super.dispose();
  }
}
