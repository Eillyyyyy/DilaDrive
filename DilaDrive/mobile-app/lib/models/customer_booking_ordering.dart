import 'customer_booking.dart';

abstract final class CustomerBookingOrdering {
  static int newestFirst(CustomerBooking first, CustomerBooking second) {
    final firstDate = first.createdAt;
    final secondDate = second.createdAt;
    if (firstDate != null && secondDate != null) {
      final byCreatedAt = secondDate.compareTo(firstDate);
      if (byCreatedAt != 0) return byCreatedAt;
    } else if (firstDate != null) {
      return -1;
    } else if (secondDate != null) {
      return 1;
    }
    return second.id.compareTo(first.id);
  }

  static int activeBySchedule(
    CustomerBooking first,
    CustomerBooking second, {
    required DateTime now,
  }) {
    final firstSchedule = _scheduledAt(first);
    final secondSchedule = _scheduledAt(second);
    if (firstSchedule != null && secondSchedule != null) {
      final firstIsUpcoming = !firstSchedule.isBefore(now);
      final secondIsUpcoming = !secondSchedule.isBefore(now);
      if (firstIsUpcoming != secondIsUpcoming) {
        return firstIsUpcoming ? -1 : 1;
      }
      final bySchedule = firstIsUpcoming
          ? firstSchedule.compareTo(secondSchedule)
          : secondSchedule.compareTo(firstSchedule);
      if (bySchedule != 0) return bySchedule;
    } else if (firstSchedule != null) {
      return -1;
    } else if (secondSchedule != null) {
      return 1;
    }
    return newestFirst(first, second);
  }

  static DateTime? _scheduledAt(CustomerBooking booking) {
    final tripDate = booking.tripDate;
    if (tripDate == null) return null;

    final timeParts = booking.pickupTime?.split(':');
    final hour =
        timeParts == null || timeParts.isEmpty ? 0 : int.tryParse(timeParts[0]);
    final minute = timeParts == null || timeParts.length < 2
        ? 0
        : int.tryParse(timeParts[1]);
    if (hour == null ||
        minute == null ||
        hour < 0 ||
        hour > 23 ||
        minute < 0 ||
        minute > 59) {
      return null;
    }
    return DateTime(tripDate.year, tripDate.month, tripDate.day, hour, minute);
  }
}
