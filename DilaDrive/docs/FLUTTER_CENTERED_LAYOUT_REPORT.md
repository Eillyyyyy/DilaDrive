# DilaDrive centered Flutter layout

## Shared layout

The Flutter screens use a centered content column with a maximum width of 620 logical pixels. The shared DilaDrivePageLayout sizes its child to the available phone width on smaller screens. DilaDriveScaffold places that layout inside the existing Scaffold while retaining the page background, app bar, bottom accessories and existing scroll view. Screen padding uses a consistent phone-style gutter and accounts for device insets.

The Customer and Driver navigation bars use the same centered content width and retain their original components. The Customer booking button remains center-docked. On Create Booking, the map retains the full viewport and the draggable panel is centered at up to 620 pixels. The panel owns its existing scroll controller. Its action stays in the panel footer.

## Customer Dashboard and Profile

The Dashboard uses the original single-column component order from the repository. The welcome card contains pickup and destination, followed by three booking statistics in a row, Active Booking and Recent Bookings. Desktop columns have been removed from the edit forms and booking stages.

The Customer Profile page continues to omit its separate account status card. View Account retains the account status. The profile header, avatar, category and menu are preserved.

## Screen coverage and files

| Area inspected | Files updated within mobile-app/lib |
| --- | --- |
| Shared layout | widgets/responsive_page.dart |
| Splash and authentication | screens/splash.dart, screens/auth/login.dart and screens/auth/register.dart |
| Customer | screens/customer/dashboard.dart, bookings.dart, booking_details.dart, notification.dart, profile.dart, view_account.dart, update_account.dart, business_documents.dart, payment.dart, upload_receipt.dart, update_booking.dart, create_booking.dart and customer_navigation.dart |
| Customer booking flow | screens/customer/booking/booking_page_layout.dart, booking_map_sheet.dart, booking_location_view.dart, booking_location_search_page.dart, booking_time_sheet.dart, booking_trip_details_sheet.dart, booking_vehicle_page.dart and booking_review_page.dart |
| Driver | screens/driver/dashboard.dart, trips.dart, trip_details.dart, verification.dart, upload_verification_photo.dart, notification.dart, profile.dart, view_account.dart, update_account.dart, change_password.dart and driver_navigation.dart |

Business document details and the PDF viewer use the same centered constraint. Booking success uses the shared review sheet. The existing trip details handle Driver status updates and access to proof photos. No separate forgot-password, reset-password or incident-report screens were found in the inspected Flutter screen directory.

The layout changes are in Flutter presentation code. Existing service calls, booking rules, database records and the React Admin application are outside the change.

## Verification

The responsive tests cover 360 × 800, 390 × 844, 412 × 915, 840 × 866, 1280 × 800 and 1649 × 928. They cover the centered content width, Dashboard order, account status placement, navigation, and the full-width booking map with a scrollable centered panel. The viewport tests are responsive_customer_driver_pages_test.dart and booking_review_sheet_layout_test.dart. The update_booking_test.dart assertion now scrolls the form before verifying the optional flight number.

- Flutter analysis passed with no issues.
- All 21 focused tests passed, including the six viewport sizes, the original Dashboard order, Customer and Driver navigation, the full-width map and the centered booking sheet.
- The full suite in the temporary copy finished with 182 passing tests and one existing failure. The Android emulator API test expects 10.0.2.2 while the current configuration defaults to 127.0.0.1.

## Remaining issues

The six viewports are simulated in Flutter widget tests. Physical Android and live Chrome checks remain manual. The standard Windows test command stops at the existing PDFium build hook without a supported C compiler. The suite ran in a temporary project copy with the PDF-related build hooks omitted. The original Dashboard in the repository was the reference used to restore the component order.
