# DilaDrive customer booking map redesign

## Implemented

The customer app keeps one interactive Flutter map mounted during Location, Trip Details, Vehicle Selection and Review. A draggable sheet contains the active stage. The primary action stays below the scrolling content, within the safe area. Search opens over the map, and the camera fits the selected points and driving path above the sheet.

| Stage | Behaviour |
| --- | --- |
| Location | Search and select geocoded addresses, choose pins, use GPS or swap pickup and drop-off. The sheet displays road distance and duration. Continue requires two resolved locations and a valid driving route. |
| Trip Details | Show the signed-in customer's name and phone. Collect the trip type, future service date and time, optional flight number, passengers, luggage and notes. Airport pickup uses the landed time. |
| Vehicle Selection | Check the live fleet, request an estimate for each eligible category and show only categories with successful quotes. Cards show the representative model, category, capacities and estimate. Retry the check or revise the trip from the empty state. |
| Review | Show the customer, itinerary, luggage, chosen model and category, road distance, duration and estimated fare. Request Booking submits the chosen category and its quote token. The success sheet displays the persisted booking reference. |

Flutter uses local StatefulWidget state, an enum for the current stage and a shared BookingDraft. Returning to a previous stage retains the trip inputs. Changes to a location or trip detail clear previous availability and fare. Review and Vehicle Selection use the model returned in the backend vehicle object and the fare returned for that category.

## Files

- New Flutter files are booking_map_sheet.dart for the persistent map and sheet layout, and booking_vehicle_page.dart for live category quotes and selection. Both are in mobile-app/lib/screens/customer/booking.
- Existing Flutter files updated include create_booking.dart, update_booking.dart, booking_draft.dart, booking_route_map.dart and booking_service.dart. The booking directory changes cover booking_location_page.dart, booking_location_view.dart, booking_page_layout.dart, booking_details_page.dart, booking_availability_page.dart, booking_fare_page.dart and booking_review_page.dart.
- The new end-to-end widget test is booking_map_sheet_flow_test.dart. Related booking flow, location, API contract and session-expiry tests cover navigation and the current backend contract.

## API and business rules

The app uses the existing signed-in session, geocoding gateway and driving router. It calls POST /api/bookings/route for the preview, POST /api/bookings/availability for live eligibility, POST /api/bookings/estimate for category-specific quotes and POST /api/bookings/customer/complete to submit. The customer reference on the draft is local context. The backend derives booking ownership from the session.

Vehicle eligibility uses the backend passenger limit, luggage points, operating status, maintenance and scheduled booking or assignment conflicts. Small, medium and large bags count as one, two and three points. Each passenger and luggage quantity observes the backend range. Location resolution, date and time, account verification, contact details and service-specific fields are checked before proceeding.

The existing pricing settings default to RM2.00 per road kilometre and RM60.00 per approved extra hour. The app displays the actual quote returned by the backend, which multiplies road distance by the effective rate with BigDecimal and rounds the money amount half-up to two decimal places. Actual tolls, parking and approved extra hours may be added to the final fare. The estimate displays the quoted distance and charge. The final fare includes approved hours and actual expenses once those amounts are recorded.

On submission, the backend rechecks account status, vehicle availability and the accepted quote before saving a pending booking. The UI locks the submit action in flight and uses the existing idempotency header for retries. Admin approval still controls vehicle and driver assignment and uses the existing notification workflow. Schema migrations and data updates required for this redesign are zero.

## Verification

- Flutter analysis passed with no issues.
- Forty-one focused Maven tests passed for booking creation, controllers, routing, pricing and trip finance.
- Forty-three CustomerTripPostgresIT cases passed on a disposable PostgreSQL schema. These covered verification, eligibility and scheduling conflicts, fare recalculation, persisted pending requests, idempotency and the Admin assignment workflow. The test suite supplied controlled route responses.
- The normal Windows Flutter test command stopped at the existing PDFium native build hook because the host has no supported C compiler. The Flutter suite was run separately in a temporary copy with the PDF-related native build hooks omitted. The project dependencies were retained.
- The temporary Flutter suite finished with 159 passing tests and three failures. The failing assertions concern the default Android API address and two dashboard labels. The booking tests passed, including the shared map, selected quote, single submission, pending response and simulated keyboard inset.

## Remaining limitations

- A temporary routing failure blocks the map-first Create Booking flow until a valid route can be retrieved. The separate legacy distance-review API remains available. The new screen does not use straight-line distance.
- Category quotes are individual requests to the existing endpoint. A category can disappear between checks if availability changes or pricing becomes unavailable. The backend checks eligibility again at submission.
- Existing pending booking edits retain their separate flow. Real-device GPS, live map tiles, keyboard behaviour and notifications require Android verification.

## Manual Android checklist

- Run the app against the development backend. For a standard Android emulator, pass --dart-define=API_BASE_URL=http://10.0.2.2:8080 if the host API is on port 8080.
- Sign in with active and unverified test accounts. Verify that only the active account can enter the flow.
- Try both address searches, pin selection, swap and GPS. Test denied permission, disabled GPS and retrying a geocoding or routing failure.
- Drag the sheet with a short viewport and with the keyboard visible. Check the visible route, back action, map control and persistent primary button.
- Test a past service time, an airport pickup with landed time, and mixed baggage. Go back, change the details and confirm the next vehicle check reflects the changes.
- Use a time slot with a vehicle conflict and an oversized party. Confirm the empty state offers retry and changes to trip details.
- Compare the selected model, category and fare between Vehicle Selection and Review. Submit once and check the pending reference, My Bookings and the Admin inbox. Confirm that Admin can assign a vehicle and driver through the existing flow.
