# DilaDrive Flutter responsive layout

## Width and spacing

Customer Home originally capped the entire scroll view at 760 logical pixels. The cap has been removed. The welcome and location entry sit next to each other when the available width is sufficient. The booking totals use a responsive grid, and Active and Recent bookings share a row on wider displays. At smaller widths, sections stack and the page remains scrollable.

The Create Booking map remains full screen. The draggable sheet body and legacy booking pages use the available width after padding. The previous 720 and 760 pixel page caps have been removed. Trip Details, Vehicle Selection and Review use adaptive cards. The individual booking action is limited to 320 pixels at desktop widths and stays pinned in the sheet footer.

The shared responsive_page.dart helper provides horizontal gutters of 16, 24 or 32 logical pixels, plus the relevant safe-area insets. Its ResponsiveColumns widget fits cards into the available width and lets them take their natural height instead of forcing a fixed grid height. Existing component constraints, including status labels and image picker dimensions, are retained.

My Bookings filters wrap across lines. Customer navigation reserves room for the floating booking button and fits labels within their tabs at phone widths.

## Customer Profile

The separate Account status card below the Profile header was removed. The profile photo, name, email, customer category and account menu are retained. View Account still displays the saved status. Booking eligibility, customer verification, authentication and Admin approval continue through the existing services.

## Modified files

| Area | Files within mobile-app |
| --- | --- |
| Shared layout | lib/widgets/responsive_page.dart |
| Customer pages | lib/screens/customer/dashboard.dart, customer_navigation.dart, profile.dart, view_account.dart, update_account.dart, bookings.dart, booking_details.dart, notification.dart, business_documents.dart, update_booking.dart, payment.dart and upload_receipt.dart |
| Booking stages | lib/screens/customer/booking/booking_map_sheet.dart, booking_page_layout.dart, booking_location_view.dart, booking_trip_details_sheet.dart, booking_vehicle_page.dart and booking_review_page.dart |
| Driver pages | lib/screens/driver/dashboard.dart, trips.dart, trip_details.dart, notification.dart, profile.dart, view_account.dart, update_account.dart, change_password.dart and upload_verification_photo.dart |
| Tests | test/responsive_customer_driver_pages_test.dart, customer_dashboard_test.dart, booking_review_sheet_layout_test.dart and booking_trip_details_sheet_test.dart |

## Verification

The widget tests cover 360 × 800, 390 × 844, 412 × 915, 840 × 866, 1280 × 800 and 1649 × 928. They check full-width Home, Profile and Driver Dashboard, booking and trip data fixtures, the Profile menu, access to the retained status in View Account, and a scrollable booking review with a reachable action. Additional tests exercise both bottom navigation flows, the floating booking button and the existing verification restrictions.

- Flutter analysis passed with no issues.
- All 29 focused widget tests passed, including both navigation bars, the floating booking button, the six viewport sizes, verification gating and the existing dashboard data and refresh checks.
- The full Flutter suite in the temporary copy returned 182 passes and one existing failure. The remaining emulator API test expects 10.0.2.2 while the current local configuration defaults to 127.0.0.1.

## Remaining issues

The standard Windows Flutter test command is blocked by the existing PDFium native build hook, which requires a supported C compiler. Tests were also run from a temporary project copy with the PDF-related native hooks omitted. The widget tests simulate viewport sizes. Live Chrome and physical Android layout checks were outside this verification run.
