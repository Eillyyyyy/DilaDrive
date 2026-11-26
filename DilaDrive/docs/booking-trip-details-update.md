# DilaDrive Create Booking trip details update

## Implementation

The map-first Create Booking flow keeps its existing map, route and draggable sheet. The Trip Details sheet now uses separate compact cards for pickup scheduling, optional flight details, passenger count, luggage and total luggage points. The action remains fixed below the scrollable form.

- Create Booking supplies NORMAL_TRANSFER internally. The customer no longer selects a trip type, and the new Review sheet omits that field. Existing pending bookings retain their original edit flow and trip type.
- Pickup Date & Time uses a date picker and 48 half-hour choices displayed in 12-hour format. Time checks use Malaysia time regardless of the device time zone. The date picker starts at Malaysia's current date, and elapsed slots are disabled. An off-slot value requires an explicit new selection.
- Add Flight Details reveals three independently optional fields. The number is trimmed, and the flight date and time never modify pickup. Turning the switch off clears the fields and prevents stale values from reaching the API.
- Passenger and luggage counters retain the API bounds. Small, medium and large luggage contribute one, two and three points. The existing availability service checks the fleet's configured capacity. Changes to trip inputs invalidate the previous availability and fare.

## Files

| Area | New or updated files |
| --- | --- |
| Flutter form and scheduling | New booking_trip_details_sheet.dart and booking_schedule.dart. Updated booking_location_page.dart, booking_review_page.dart, booking_draft.dart and booking_service.dart. |
| Saved bookings | Updated customer_booking.dart and customer booking_details.dart to read and display the optional fields. Updated update_booking.dart to carry them through complete pending-booking edits. |
| Backend | Updated TripQuoteRequest.java, CustomerBookingResponse.java, Booking.java and CustomerTripService.java. Existing endpoints, ownership, quote validation and submission are reused. |
| Database | New 029_booking_flight_schedule.sql, plus updates to BASELINE_CURRENT.sql and the schema README. The new DATE and TIME columns are nullable. Existing installations must apply 029 after 028 before starting the updated Hibernate-validated backend. |
| Automated tests | New booking_trip_details_sheet_test.dart. Updated booking_map_sheet_flow_test.dart, booking_flow_test.dart, booking_location_material_test.dart and CustomerTripPostgresIT.java. |

## Verification

- Maven passed 63 tests, including 44 integration cases on a disposable PostgreSQL schema. The flight test verified the pending booking keeps its original pickup time while the separate flight schedule and trimmed flight number are persisted.
- The flight migration was applied twice to a disposable schema containing an existing booking. The row was preserved and both new fields remained null.
- Flutter analysis passed with no issues. All seven focused Trip Details and map-flow tests passed, including 48 slots, Malaysia date rollover, disabled past slots, explicit replacement of off-slot times, optional flight fields, luggage points, simulated keyboard insets and submission.
- The standard Windows Flutter test command stopped in the existing PDFium native build hook because a supported C compiler is unavailable. The full suite ran in a temporary copy with the PDF-related build hooks omitted. It finished with 164 passes and three failures involving the existing emulator address assertion and two dashboard assertions.

## Remaining checks

The migration was exercised on a disposable schema. The development database requires migration 029 before using the updated backend. Verify the new form on a physical Android device, including date/time dialogs, keyboard insets, sheet dragging, going back from Vehicle Selection, and the final booking reference. The new flight schedule is available in the customer booking response. Existing Admin and Driver screens continue to use their existing fields.
