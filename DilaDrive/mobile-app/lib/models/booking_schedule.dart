/// Malaysia wall-clock values labelled as UTC for calendar comparisons.
/// The booking API expects local date/time without a UTC suffix.
DateTime malaysiaNow([DateTime? instant]) =>
    (instant ?? DateTime.now()).toUtc().add(const Duration(hours: 8));

DateTime bookingCalendarDate(DateTime date) =>
    DateTime.utc(date.year, date.month, date.day);

DateTime bookingWallTime(DateTime date, int hours, int minutes) =>
    DateTime.utc(date.year, date.month, date.day, hours, minutes);

String bookingIsoDate(DateTime date) =>
    bookingCalendarDate(date).toIso8601String().split('T').first;

Iterable<DateTime> pickupSlots(DateTime date) sync* {
  for (var slot = 0; slot < 48; slot++) {
    yield bookingWallTime(date, slot ~/ 2, slot.isEven ? 0 : 30);
  }
}

bool isPickupSlot(DateTime time) =>
    time.minute % 30 == 0 &&
    time.second == 0 &&
    time.millisecond == 0 &&
    time.microsecond == 0;

String bookingClockLabel(int hour, int minute) {
  final displayHour =
      (hour % 12 == 0 ? 12 : hour % 12).toString().padLeft(2, '0');
  final displayMinute = minute.toString().padLeft(2, '0');
  return '$displayHour:$displayMinute ${hour < 12 ? 'AM' : 'PM'}';
}

DateTime? parseBookingFlightTime(String? time) =>
    time == null ? null : DateTime.tryParse('2000-01-01T$time');
