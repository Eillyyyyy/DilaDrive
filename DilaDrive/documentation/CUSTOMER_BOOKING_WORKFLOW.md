# Customer workflow during the payment pause

The Flutter layout, theme, navigation positions and booking screens remain as before. There are four destination tabs: Home, My Bookings, Notifications and Profile. The existing centered plus button opens Create Booking.

## Workflow

1. Register a Normal or Corporate account and sign in.
2. Admin reviews the account. A customer can use Home and view their verification state. Only a verified customer can submit a booking.
3. Open Create Booking, select pickup and destination, and enter the trip information and luggage.
4. Check vehicle capacity and actual driver/vehicle availability, then select an eligible category.
5. Review the route and estimated fare and submit a pending booking.
6. Admin verifies the booking and confirms an available driver–vehicle pair. A replacement assignment uses the existing reassignment flow and its notifications.
7. Open My Bookings to see the confirmed trip, driver and vehicle. Open the trip timeline to see recorded milestones.
8. The driver progresses through ASSIGNED → OTW → WAITING → PICKUP → DROP → COMPLETED. The backend rejects skipped transitions. Completion depends on reaching DROP and works with zero payment records. The Admin completion action uses the same rule.
9. View the completed trip in My Bookings. Profile retains Business Documents for Corporate customers. Quotation, purchase-order and invoice access remains available regardless of payment status.

Customers can view, edit or cancel bookings where the existing booking-state rules permit those actions. Estimated and final fares and issued invoice amounts remain informational.

## Temporarily excluded from the active Customer scope

The existing Payment and Upload Receipt screens remain in the source tree without a navigation entry. The active booking flow has no Pay Now, proof upload, payment verification, payment history or outstanding-balance steps. Customer Business Documents hides receipt entries and payment badges. Customer invoice PDFs keep the invoice and charges and omit payment status and method fields. Customer inbox and unread count omit payment and receipt notices, while booking, account, assignment, milestone and quotation/invoice notices remain. Existing payment endpoints, tables, records, Admin payment operations, Driver cash recording and push infrastructure are preserved for future use. Payment and receipt notices are still stored, but customer push delivery for them is paused.

See [use case list](USE_CASE_LIST.md) and [traceability](USE_CASE_TRACEABILITY.md). Live customer demonstration requires approved test accounts and a registered fleet. The inspected local database was empty of customer, vehicle and booking records, so integration tests use disposable PostgreSQL data.

## Files changed for this scope

| Area | Files |
| --- | --- |
| Flutter | `lib/screens/customer/customer_navigation.dart`, `lib/screens/customer/business_documents.dart`, `lib/services/customer_notification_service.dart` |
| Flutter tests | `test/business_documents_retry_test.dart`, `test/customer_notification_service_test.dart`, `test/responsive_customer_driver_pages_test.dart` |
| Spring | `service/AssignmentServiceImpl.java`, `service/DocumentPdfService.java`, `service/NotificationService.java`, `controller/DocumentController.java` |
| Spring tests | `CustomerTripPostgresIT.java`, `BookingConfirmationPostgresIT.java`, `service/AssignmentServiceTest.java`, `service/DocumentPdfServiceTest.java`, `service/NotificationServiceTest.java`, `controller/DocumentControllerTest.java` |
| React Admin | `src/pages/Assignments.jsx`, `src/pages/Bookings.jsx` |
| Documentation | `CUSTOMER_BOOKING_WORKFLOW.md`, `USE_CASE_LIST.md`, `USE_CASE_TRACEABILITY.md`, `BUSINESS_RULES.md` |

## Verification

- Maven passed 272 tests with 5 opt-in tests skipped. This includes 55 PostgreSQL booking cases. The completion regression rejected an early Admin completion, then allowed the driver to finish the sequence with zero payment records. The PDF and notification tests also passed.
- React production build and all 36 tests passed.
- Flutter analysis reported no issues. All 193 Flutter tests passed in the temporary copy with Windows PDF native hooks omitted. The Android debug APK built with the normal application dependencies.
- A read-only check of local PostgreSQL still returned zero customers, vehicles, bookings, payments and documents. The application workflow has not been exercised on a physical device with the local database since the reset.
