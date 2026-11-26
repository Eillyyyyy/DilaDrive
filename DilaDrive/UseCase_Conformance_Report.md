# DilaDrive use case conformance report

## Confirmed decisions

- **Cancellation and rescheduling.** `booking.cancellation.min-notice-hours` defaults to **24** and can be set through `BOOKING_CANCELLATION_MIN_NOTICE_HOURS`. Rescheduling requires that both the existing and proposed pickups meet the notice period. A successful cancellation keeps the assignment row and cancellation milestone. Admin, driver and customer notices are persisted once. The slot is available to another booking using the same pair. Pending cancellation keeps its existing rules.
- **Card surcharge.** A read-only count of `public.payment` found **0 rows** with `surcharge_amount > 0`. Keep the column and any historical nonzero values as audit data. New payments store zero, review uses the base amount, and receipts issued under the new rule use the base amount even for legacy pending payments. Existing issued receipts keep their original snapshot. The Flutter and React payment views use the base amount, and the payment API no longer exposes the surcharge field.
- **Purchase orders.** The routed React Documents page uses `record-po` with a client reference. Removed stale placeholder actions from the unused Invoices and Purchase Orders pages and corrected the PO references in supporting documentation. The retired endpoint is referenced by regression tests solely to assert HTTP 410.
- **Client verification.** The React production build passed. Browser flows, Flutter widget and device flows, and live push delivery are **UNVERIFIED**. The Flutter dependency cache was inaccessible in this environment.

### Maven results

| Run | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| Full Maven `test` | 313 | 0 | 0 | 9 |
| `UseCaseConformanceTest` separately | 55 | 0 | 0 | 0 |

The commands were `mvn -o -Dsurefire.reportNameSuffix=decisionsFull test` and `mvn -o -Dsurefire.reportNameSuffix=decisionsConformance -Dtest=UseCaseConformanceTest test`, with the local Maven repository configured as documented below. Both used the disposable PostgreSQL setup described below, including migration 037, and exercised the 24-hour default. The full run included the 55 conformance tests. Its nine skips match the opt-in list in the previous execution section. The schema was dropped after testing.

## Master scope alignment (previous scope review)

| Section | Change or finding | Verification |
|---|---|---|
| 3.4 Confirmed booking date or time | Customer Booking Details uses `PATCH /api/bookings/{id}/customer/schedule`. The API checks the same assigned driver and vehicle against the new window, excluding this booking. It keeps the assignment ID, updates the pickup and trip end, and retains the previous pickup in durable Admin and driver notifications. A conflict returns 409 before changing the booking. Another customer receives 404. Cancellation rules are unchanged. | **PASS** backend. `confirmedBookingCanMoveWithinItsOwnWindowAndKeepsItsAssignment`, `confirmedBookingRescheduleRejectsBusyPairWithoutChangingTheTrip`, `confirmedBookingRescheduleReturns404ToAnotherCustomer`. Flutter date and time picker flow **UNVERIFIED**. |
| 3.3 Category passenger limits | Sedan **3**, MPV **5**, 10-Seater Van **8**, 14-Seater Van **11**, 18-Seater Van **15**. The baseline and forward migration 037 set the category limits. Booking and assignment checks also cap legacy vehicles at the scope limit while respecting a lower stored capacity. | **PASS** for all five through `categoryEndpointReportsAllFiveScopeLimits` and `VehicleCategoryCapacityTest#appliesScopeLimitToKnownLegacyVehicles`. Migration 037 was exercised on a disposable schema with the previous values. Deployment to the shared dev schema **UNVERIFIED**. |
| 3.1 Company registration number | The registration API and Flutter registration form required it. The self-service and Admin profile update paths also required it for Corporate details. The field is now optional across those API and form paths and retains the existing maximum length when supplied. | **PASS** backend. `corporateRegistrationAndProfileAllowMissingRegistrationNumber` covers registration, customer update and Admin update. Flutter interaction **UNVERIFIED**. |
| 4.2 Dashboard Available Drivers | The dashboard formerly displayed **Active Drivers**, which counted every driver with an ACTIVE account. The new **Available Drivers** card counts ACTIVE accounts that lack an `OTW`, `WAITING`, `PICKUP` or `DROP` assignment for an active booking. A future `ASSIGNED` trip alone leaves the driver in the count. The API still returns the previous `activeDrivers` count for compatibility. | **PASS** API with `dashboardAvailabilityExcludesOnlyActiveDriversCurrentlyOnATrip`. React production build **PASS**. Browser display **UNVERIFIED**. |

### Scope inventory

This source review covers the named out-of-scope items. A full comparison against any master scope text beyond the sections and items supplied in the request is **UNVERIFIED**.

| Feature | Current screen or code location | Existing flag and data retention |
|---|---|---|
| Commission and driver earnings | No active earnings screen or commission display found in Flutter `lib` or React `src`. SQL migration 026 retains commission settings, booking snapshots, `driver_trip_commission` and `driver_commission_audit`. | No screen needs hiding in the reviewed source. No earnings visibility flag found. Existing database structures and data were kept. |
| Fare proposals | Flutter Customer **Booking Details** shows the proposal review. React Admin **Bookings** confirm-and-assign and **Assignments** reassignment can show the proposal panel through the resource picker. | `BOOKING_ALLOW_REASSIGNMENT_UPGRADES` controls a particular reassignment path. It is not a general fare-proposal visibility switch. No flag found that hides the feature across both clients. A display-only change could retain revision history, but hiding approval would require reviewing the affected vehicle-change flow. |
| 6% card surcharge | Flutter **Payment** and **Upload Receipt** show the surcharge. React Admin **Payments** includes it in the list totals and shows it in payment details. The payment service calculates it and generated receipts may mention it. | The rate is hardcoded. No visibility or rate flag found. Hiding the label would leave the surcharge in calculations and historical payment data. |
| Fleet documents | React Admin **Drivers** create, edit and details show IC, licence and linked vehicle road-tax documents. **Vehicles** edit and details show road-tax upload or preview. No Flutter fleet-document screen found in the reviewed source. | `FLEET_DOCUMENT_STORAGE_DIR` controls storage location. No visibility flag found. Existing stored files can be retained under a future display-only change. Driver creation currently requires licence and road-tax uploads, so hiding those controls would affect that flow. |

### Scope verification run

- Unfiltered Maven `test` with `-Dsurefire.reportNameSuffix=scopeAlignment` and the disposable PostgreSQL schema initialized as below plus migration 037. **317 discovered, 308 passed, 0 failed, 0 errors, 9 skipped**. All **50 conformance tests passed**. The nine skips match the historical unfiltered run listed below.
- React Admin Vite production build **PASS**. Browser interaction was not exercised.
- Flutter **UNVERIFIED**. Static analysis could not resolve the cached `http` and `image_picker` dependencies in the local environment. Widget and device flows were not verified.

The execution and schema parity entries below record the earlier follow-up. The scope changes were verified on a disposable schema without modifying the shared dev schema.

## Execution (previous follow-up)

The conformance tests used Spring Boot MockMvc with real Admin, Customer and Driver logins against a disposable `test_uc_*` schema in local development PostgreSQL. Tests rolled back their transactions, uploads used JUnit temporary storage and the disposable schema was dropped after the run.

From `diladrive/backend/diladrive-api`, the unfiltered Maven test command was:

```powershell
& 'C:/Users/syafi/.maven/maven-3.9.15/bin/mvn.cmd' -o '-Dmaven.repo.local=C:/Users/syafi/.m2/repository' '-Dsurefire.reportNameSuffix=conformanceFull' test
```

**Maven test:** 306 discovered, 295 passed, 2 failed, 0 errors, 9 skipped. All 44 conformance tests passed. The two failures are in the existing vehicle category unit tests. They instantiate `Vehicle` directly, and this follow-up changed only quotation and receipt behavior in production.

Supply `DATABASE_USERNAME` and `DATABASE_PASSWORD` through the environment. JDK 25 ran the Maven command. Set `DILADRIVE_CONFORMANCE_URL=jdbc:postgresql://localhost:5432/DilaDrive?currentSchema=<dedicated-schema>` to enable the conformance tests. Initialize only the dedicated schema with `BASELINE_CURRENT.sql`, followed by migrations 020–026, 028–032 and 034 from `database/schema`. The baseline already defines the driver identity, boot capacity and notification structures from 033, 035 and 036. Set the setup connection's `search_path` to the dedicated schema. `RUN_DEV_HARDENING_IT` remained unset because that legacy suite hardcodes the shared development database.

### Maven failures

| Test | Expected | Observed | Follow-up |
|---|---|---|---|
| `VehicleCategoryCapacityTest#knownLegacyCategoryStillCapsCapacityWhenNoCategoryRowIsLoaded` | 18-Seater Van limited to 15 passengers | Returned 18 | Align the legacy category fallback with the configured limit. |
| `VehicleCategoryCapacityTest#passengerCapacityCannotExceedConfiguredCategoryLimit` | Fixture 10-Seater Van limited to 8 passengers | Returned 12 | Confirm the expected capacity against the current category configuration, then align the fallback and assertion. |

### Maven skips

| Test | Reason |
|---|---|
| `DevHardeningPostgresTest#driverCannotReadOrChangeAnotherDriversAssignmentPhotosOrNotifications` | Shared-dev fixture suite requires `RUN_DEV_HARDENING_IT=true`. |
| `DevHardeningPostgresTest#customerCannotReadAnotherCustomersBookingTimelineDocumentsOrProof` | Shared-dev fixture suite requires `RUN_DEV_HARDENING_IT=true`. |
| `DevHardeningPostgresTest#pushTokenCannotBeReboundOrDeletedByAnotherRecipient` | Shared-dev fixture suite requires `RUN_DEV_HARDENING_IT=true`. |
| `DevHardeningPostgresTest#profileAndPasswordRoutesIgnoreProtectedFields` | Shared-dev fixture suite requires `RUN_DEV_HARDENING_IT=true`. |
| `LiveRoadRouteTest#uitmToKliaThroughAuthenticatedHttpEndpoint` | Live provider opt-in `RUN_LIVE_ROAD_ROUTE` unset. |
| `LiveJasinRoutingTest#actualJasinToKliaProviders` | Live provider opt-in `RUN_LIVE_JASIN_ROUTE` unset. |
| `LiveLocationFlowTest#KLIAtoKLSentralUsesRealSuggestionsAndDrivingGeometry` | Live provider opt-in `RUN_LIVE_LOCATION_FLOW` unset. |
| `LiveLocationGatewayTest#liveSearchAndReverse` | Live provider opt-in `RUN_LIVE_GEOCODING_TEST` unset. |
| `StrictAvailabilityAuditTest#traceActualFleetRequestWithoutChangingCompanyData` | Dev fleet and live provider opt-in `RUN_STRICT_AVAILABILITY_AUDIT` unset. |

### Expanded discovery including `*IT`

I also ran all Maven test naming patterns plus the repository's separately named `*IT` classes. `DATABASE_URL` and `DILADRIVE_CONFORMANCE_URL` both pointed to the disposable schema and `JPA_DDL_AUTO=validate`.

```powershell
& 'C:/Users/syafi/.maven/maven-3.9.15/bin/mvn.cmd' -o '-Dmaven.repo.local=C:/Users/syafi/.m2/repository' '-Dsurefire.reportNameSuffix=conformanceExpanded' '-Dtest=*Test,*Tests,*TestCase,*IT' test
```

**Expanded run:** 413 discovered, 296 passed, 3 failed, 3 errors, 111 skipped. All 44 conformance tests passed again. The expanded run repeated the two vehicle category failures above and exposed these additional legacy integration test issues.

| Test | Expected | Observed | Follow-up |
|---|---|---|---|
| `DriverVehicleReadOnlyPostgresIT#existingDriversVehiclesAndAssignmentsLoadWithoutChangingRecords` | HTTP 200 when reading the fleet | HTTP 401 | Establish a real Admin session for the protected route. |
| `DriverProfilePostgresIT#pictureAndContactsPersistButAdminFieldsStayReadOnly`, `#rejectsInvalidPhotosAndCrossRoleProfileAccess`, `#passwordChangeValidatesCurrentPasswordAndNewPasswordWorks` | Exercise profile and password behavior after creating a driver fixture | All three error during setup because the synthetic IC does not meet current format validation | Bring the fixture in line with current IC validation or omit the optional IC. |

<details>
<summary>All 111 expanded-run skips and reasons</summary>

**BookingConfirmationPostgresIT**

- `actualHttpLoginConfirmAndFreshAssignmentListPersistTogether` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `invalidPairAccountCapacityAndMissingEndAreRejectedWithoutSaving` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `authenticationAndRequiredFieldsAreEnforced` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `conflictRollsBackFareConfirmationPointersAndNotifications` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `databaseIndexRejectsDuplicateActiveAssignmentWithoutDeletingHistory` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `pendingConfirmPersistsAndAllApisShowSameAssignmentAndDriverOwnership` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `adjacentPeriodsAreAllowedButUnknownLegacyEndIsConservative` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `databaseInsertFailureRollsBackEntireConfirmation` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `legacyConfirmedRepairIsExplicitAndPendingPointersDoNotAutoConfirm` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `concurrentOverlappingBookingsCannotReserveSamePair` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `actualHttpDistinguishesUnmappedRouteFromBookingAndDriverNotFound` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `reassignUpdatesSameRecordAndTerminalAssignmentsAreProtected` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist
- `retryAndConcurrentRequestsProduceExactlyOneAssignment` — Environment variable [BOOKING_ASSIGNMENT_TEST_SCHEMA] does not exist

**CustomerBookingsPostgresIT**

- `customerSessionReturnsItsPersistedProfile` — Requires at least one existing customer row
- `customerSessionReturnsOnlyPersistedBookingsInApiOrder` — Requires at least one existing customer row
- `customerSessionReturnsOnlyItsPersistedNotifications` — Requires at least one existing customer row
- `driverSessionReturnsOnlyItsPersistedAssignments` — Requires at least one existing driver row
- `driverSessionReturnsItsPersistedProfile` — Requires at least one existing driver row

**CustomerTripPostgresIT**

- `verifiedPaymentsAloneCoverInvoiceAndCountInReceipts` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `existingDriverTripBlocksOtherwiseFreeCurrentVehicle` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `concurrentDecisionsProduceOneHistoryAndOneInboxRecord` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `rollsBackDecisionWhenInboxInsertFails` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `recordsMilestonesAndIncidentsThroughAuthorizedEndpoints` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `expiryReleasesAvailabilityAndRequiresFreshAdminReservation` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `rejectionStoresReasonAndReleases` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `canonicalApiCreatesOwnedPendingBookingAndMineReturnsSavedSummary` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `concurrentSubmissionsCanUseTwoFreePairsInTheSelectedCategory` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `reassignmentTripProgressAndBothReminderWindowsPersistExactlyOnce` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `submissionRechecksAfterPreviouslySuccessfulAvailability` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `maintenanceExcluded` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `pendingAndRejectedCustomersCannotCreateThroughAnyCustomerRoute` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `dashboardKeepsOperationalMetricsAfterEarningsRemoval` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `prePickupCheckFlagsMissingAndUnavailableAssignmentsOnce` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `driverDisabledAfterSessionEstablishmentCannotReachProtectedHandlers` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `pendingRequestHoldsPairAndCancellationReleasesIt` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `maintenancePeriodExcluded` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `serviceFailureIsHttp503RatherThanScheduleUnavailability` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `concurrentAdminConfirmationsAreSerializedAndOnlyOneWins` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `excessivePassengersExcluded` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `quotationApprovalNotifiesOnceAndOnlyClientPurchaseOrdersCanBeRecorded` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `routeFailureCannotCreateBooking` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `approvesBothCategoriesAndBothActiveAdminRoles(String, String)` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `driverSelfUpdatePreservesOperationalDataAndRejectsDeactivatedSession` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `structuredAvailabilityDistinguishesCapacityScheduleAndConfiguration` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `adminInboxLinksNewAccountsAndBookingsAndKeepsIndependentReadState` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `unavailableRoutingCreatesOnlyAnIdempotentPendingDistanceReview` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `midnightOverlapAdjacentTripsAndCompletionRelease` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `rejectsBothCategoriesAndKeepsReasonsAndInboxPrivate(String)` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `inactiveDriverIsExcludedButLegacyOnTripFlagUsesTheSchedule` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `calendarSourcesExposeScheduledPickupAndAssignedResources` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `differentVehiclesAllowSameTimeAndSmallestAvailableTypeWins` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `optionalPushFailureOccursAfterDecisionAndInboxCommit` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `excessiveLuggageExcluded` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `exactPreferencePersistsAndAdminPreviewExplainsAnUnavailableDriver` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `unknownCompletedLegacyEndDoesNotPermanentlyBlockFutureTrips` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `categoryRecommendationUsesActualFleetCapacityAndSchedule` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `recommendationIsIndependentOfPricingAndDoesNotAssign` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `driverUnavailableIsExcludedEvenWhenVehicleFits` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `pendingAndRetryPersistExactlyOnce` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `lastSuperAdminIsProtectedDuringConcurrentRemoval` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `pendingCustomerKeepsOptionalAndProtectedFieldsAndCorporateBlanksAreRejected` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `driverCreationValidatesCapacityAndRollsBackInvalidUploads` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `ancientHoldsAndPendingAssignmentRowsDoNotOccupyAvailability` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `adminAssignmentChecksBothResourcesAndUpdatesBooking` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `categoryBootCapacityAndPassengerCapacityAreIndependent` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `rejectsBadRequestsAndCannotBypassVerificationThroughProfileEdits` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `emergencyReassignmentKeepsFareAndRejectsInvalidReplacements` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `fleetExpiryRemindersLinkResourcesAndDeduplicateEachThreshold` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `changedTimeRefreshesAvailabilityAndFare` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `selfProfileReadsUseCurrentDatabaseRowsAndHideCredentials` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `bothCustomerTypesCanDownloadOnlyTheirVerifiedReceipts` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `periodCreationAndConfirmationCannotBothWinForTheSameDriver` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `optionalFlightSchedulePersistsWithoutChangingPickup` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `concurrentPendingRequestsDoNotReserveSameVehicle` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `adminCanManageUnavailablePeriodsAndReceivesConflictingBookingIds` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `assignmentPreviewSeparatesRequestErrorsAndCalculatesExactEnd` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `differentCategoryRequiresExistingExplicitFareApprovalWhenCompanyUpgradesAreDisabled` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `editRefreshesSavedFareScheduleAndHold` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `capacityAndRoadFare` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `overlappingSuggestedReplacementPreservesTheExistingHold` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `twoCustomersCanRequestSameSlotThenAdminConfirmsAlternativeAndHistoryIsPreserved` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `estimateRequiresSessionAndValidCounts` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist
- `adminSelfUpdatePreservesProtectedAndOmittedFieldsAndSupportsExplicitClear` — Environment variable [RUN_CUSTOMER_TRIP_IT] does not exist

**DevHardeningPostgresTest**

- `driverCannotReadOrChangeAnotherDriversAssignmentPhotosOrNotifications` — Environment variable [RUN_DEV_HARDENING_IT] does not exist
- `customerCannotReadAnotherCustomersBookingTimelineDocumentsOrProof` — Environment variable [RUN_DEV_HARDENING_IT] does not exist
- `pushTokenCannotBeReboundOrDeletedByAnotherRecipient` — Environment variable [RUN_DEV_HARDENING_IT] does not exist
- `profileAndPasswordRoutesIgnoreProtectedFields` — Environment variable [RUN_DEV_HARDENING_IT] does not exist

**LiveRoadRouteTest**

- `uitmToKliaThroughAuthenticatedHttpEndpoint` — Environment variable [RUN_LIVE_ROAD_ROUTE] does not exist

**OptionAPostgresIT**

- `databaseDeferredCheckRejectsCreatingDriverWithoutVehicle` — Environment variable [OPTION_A_TEST_SCHEMA] does not exist
- `activeLoginAndDisabledLoginKeepVehicleIndependent` — Environment variable [OPTION_A_TEST_SCHEMA] does not exist
- `databasePreventsSecondCurrentVehicleAndUnpairedNewDriver` — Environment variable [OPTION_A_TEST_SCHEMA] does not exist
- `duplicatePlateRollsBackTheDriverAndVehicleTogether` — Environment variable [OPTION_A_TEST_SCHEMA] does not exist
- `legacyCompletionThroughDriverUpdateIsAtomicAndExplicit` — Environment variable [OPTION_A_TEST_SCHEMA] does not exist
- `createsActiveHashedPairAndLoadsAndEditsSameVehicleViaHttp` — Environment variable [OPTION_A_TEST_SCHEMA] does not exist
- `schedulesNonOverlappingBookingsAndRejectsOverlapAndUnsafeVehicleEdits` — Environment variable [OPTION_A_TEST_SCHEMA] does not exist
- `linkedDriverEditUpdatesExistingVehicleAndPreservesPair` — Environment variable [OPTION_A_TEST_SCHEMA] does not exist
- `legacyRegistrationIsExplicitAndNeverCreatesSecondVehicle` — Environment variable [OPTION_A_TEST_SCHEMA] does not exist
- `reassignsDriverAndTheirLinkedVehicleTogetherWithoutDeletingHistory` — Environment variable [OPTION_A_TEST_SCHEMA] does not exist

**PricingPostgresIT**

- `fareChangesRequireReviewBeforeAnyBookingIsSaved` — Environment variable [RUN_PRICING_IT] does not exist
- `submissionRechecksPhysicalAvailability` — Environment variable [RUN_PRICING_IT] does not exist
- `adminCrudValidationAndCategoryNormalization` — Environment variable [RUN_PRICING_IT] does not exist
- `realRoadFareAndPendingSnapshotWithReservation` — Environment variable [RUN_PRICING_IT] does not exist
- `validZeroRatesRemainValidThroughConfirmationAndDocuments` — Environment variable [RUN_PRICING_IT] does not exist
- `categoryChangeRequiresCustomerAcceptanceAndPreservesHistory` — Environment variable [RUN_PRICING_IT] does not exist
- `availabilityIndependentOfMissingAndInactivePricing` — Environment variable [RUN_PRICING_IT] does not exist
- `sameCategoryAssignmentAndDocumentsKeepAcceptedFareAfterRateUpdate` — Environment variable [RUN_PRICING_IT] does not exist

**LiveJasinRoutingTest**

- `actualJasinToKliaProviders` — Environment variable [RUN_LIVE_JASIN_ROUTE] does not exist

**LiveLocationFlowTest**

- `KLIAtoKLSentralUsesRealSuggestionsAndDrivingGeometry` — Environment variable [RUN_LIVE_LOCATION_FLOW] does not exist

**LiveLocationGatewayTest**

- `liveSearchAndReverse` — Environment variable [RUN_LIVE_GEOCODING_TEST] does not exist

**TripFinancePostgresIT**

- `preservesBookingChargesAndIssuedInvoiceAmounts` — Environment variable [RUN_TRIP_FINANCE_IT] does not exist

**StrictAvailabilityAuditTest**

- `traceActualFleetRequestWithoutChangingCompanyData` — Environment variable [RUN_STRICT_AVAILABILITY_AUDIT] does not exist

</details>

## Use cases

| ID | Use case or check | Actor | Endpoints | Test class and method | Positive result | Negative result | Status | Notes |
|---|---|---|---|---|---|---|---|---|
| UC001 | Create account | Super Admin, Admin, Customer | POST /api/admins, /api/auth/mobile/register, /api/drivers | `UseCaseConformanceTest#uc001CreateAccounts` | Created an Admin, both customer types, and a Driver with a vehicle | Customer cannot create an Admin | PASS | â€” |
| UC002 | View account | Admin, Customer, Driver | GET /api/auth/session, /api/customers/me, /api/drivers/me | `UseCaseConformanceTest#uc002ViewAccounts` | Loaded profile for each role and both customer types | Anonymous request returns 401 | PASS | â€” |
| UC003 | Update account | Admin, Customer, Driver | PUT /api/auth/profile, /api/customers/me, /api/drivers/me | `UseCaseConformanceTest#uc003UpdateAccounts` | Updated all four actor profiles | Invalid customer update returns 400 | PASS | â€” |
| UC004 | Verify account | Admin | PATCH /api/customers/{id}/verification | `UseCaseConformanceTest#uc004VerifyAccounts` | Approved pending Normal and rejected pending Corporate accounts with a reason | Second decision returns 409 | PASS | â€” |
| UC005 | Create vehicle | Admin | POST /api/drivers | `UseCaseConformanceTest#uc005CreateVehicle` | Multipart driver creation inserted the linked vehicle | Customer request returns 403 | PASS | â€” |
| UC006 | View vehicle | Admin | GET /api/vehicles/{id} | `UseCaseConformanceTest#uc006ViewVehicle` | Fetched the fixture vehicle | Unknown ID returns 404 | PASS | â€” |
| UC007 | Update vehicle | Admin | PUT /api/vehicles/{id} | `UseCaseConformanceTest#uc007UpdateVehicle` | Updated the vehicle using its full editable form payload | Customer request returns 403 | PASS | â€” |
| UC008 | Create booking | Customer | POST /api/bookings/estimate, /api/bookings/customer/complete | `UseCaseConformanceTest#uc008CreateBooking` | Active customer created a booking from an accepted quote | Pending customer returns 403 | PASS | â€” |
| UC009 | View booking | Admin, Customer | GET /api/bookings, /api/bookings/mine/{id} | `UseCaseConformanceTest#uc009ViewBooking` | Admin list and owner booking detail returned 200 | Another customer returns 404 | PASS | â€” |
| UC010 | Update booking and confirmed trip schedule | Customer | PUT /api/bookings/{id}/customer/complete, PATCH /api/bookings/{id}/customer/schedule | `uc010UpdateBooking`, `confirmedBookingCanMoveWithinItsOwnWindowAndKeepsItsAssignment`, `confirmedBookingRescheduleRejectsBusyPairWithoutChangingTheTrip`, `confirmedBookingRescheduleReturns404ToAnotherCustomer`, `confirmedReschedulingRequiresNoticeForOldAndNewPickup` | Pending edit still works. Confirmed trip keeps its assignment and updates the window | Busy pair or insufficient notice returns 409 with no mutation. Another customer returns 404 | PASS | Admin and driver notices retain the previous and new pickup times. |
| UC011 | Cancel booking | Customer | POST /api/bookings/{id}/cancel | `uc011CancelBooking`, `uc011ConfirmedCancellationFreesPairAndOnlyNotifiesOnce`, `uc011ConfirmedCancellationInsideNoticeLeavesTripUnchanged`, `uc011ConfirmedCancellationRejectsAnotherCustomerAndStartedTrip` | Pending cancel still works. Eligible confirmed cancel preserves assignment and milestone history, removes hold, and frees the pair in the same slot | Insufficient notice, started and terminal trips return 409. Another customer returns 404 | PASS | Retrying successful confirmed cancellation returns 200 and emits no duplicate notices. |
| UC012 | Verify booking | Admin | POST /api/bookings/{id}/confirm-and-assign, /api/bookings/{id}/reject | `UseCaseConformanceTest#uc012VerifyBooking` | Admin confirmed one booking and rejected another | Customer cannot reject | PASS | â€” |
| UC013 | Assign driver and vehicle | Admin | POST /api/assignments | `UseCaseConformanceTest#uc013AssignDriverVehicle` | Created an assignment for a confirmed booking | Second active assignment returns 409 | PASS | â€” |
| UC014 | Reassign trip | Admin | PUT /api/assignments/{id}/reassign | `UseCaseConformanceTest#uc014ReassignTrip` | Admin reassigned to another linked pair | Customer request returns 403 | PASS | â€” |
| UC015 | View assigned trip | Driver | GET /api/assignments/mine/{id} | `UseCaseConformanceTest#uc015ViewAssignedTrip` | Assigned driver loaded the trip | Another driver returns 404 | PASS | â€” |
| UC016 | View trip details | Admin, Customer | GET /api/assignments/{id}, /api/bookings/{id}/timeline | `UseCaseConformanceTest#uc016ViewTripDetails` | Admin trip details and owner timeline returned 200 | Another customer returns 404 | PASS | â€” |
| UC017 | Update trip status | Driver | PATCH /api/assignments/{id}/status | `UseCaseConformanceTest#uc017UpdateTripStatus` | Assigned driver advanced to OTW and WAITING | PICKUP without a photo returns 409 | PASS | â€” |
| UC018 | Create quotation | Admin | POST /api/documents/quotation | `UseCaseConformanceTest#uc018CreateQuotation` | Corporate booking quotation returned 201 | Normal booking returns 409 with the Corporate-only message | PASS | No quotation row is created for the Normal booking. |
| UC019 | View quotation | Admin, Corporate | GET /api/documents/{id}, /api/documents/customer/{id}/pdf | `UseCaseConformanceTest#uc019ViewQuotation` | Admin details and Corporate PDF returned 200 | Normal customer returns 403 | PASS | â€” |
| UC020 | Update quotation | Admin | PUT /api/documents/{id} | `UseCaseConformanceTest#uc020UpdateQuotation` | Draft quotation update returned 200 | Approved quotation returns 409 | PASS | â€” |
| UC021 | Record purchase order | Admin | POST /api/documents/{id}/record-po | `UseCaseConformanceTest#uc021RecordPo` | Records the client's PO reference against an approved quotation | Retired endpoint returns 410 | PASS | React Documents has only the record action for POs. |
| UC022 | Generate invoice | Admin | POST /api/documents/{id}/generate-invoice | `UseCaseConformanceTest#uc022GenerateInvoice` | Completed Corporate booking produced an invoice | Duplicate generation returns 409 | PASS | â€” |
| UC023 | View invoice | Admin, Corporate | GET /api/documents/{id}/pdf, /api/documents/customer/{id}/pdf | `UseCaseConformanceTest#uc023ViewInvoice` | Admin and owning Corporate customer retrieved the PDF | Normal customer returns 403 | PASS | â€” |
| UC024 | View notification | Admin, Customer, Driver | GET /api/notifications/inbox, /api/notifications/customer/mine, /api/notifications/driver/{id} | `UseCaseConformanceTest#uc024ViewNotifications` | All scoped feeds responded for the matching actors | Another driver returns 403 | PASS | â€” |
| UC025 | Upload payment proof | Customer | POST /api/payments/bookings/{id} | `uc025UploadPaymentProof`, `cardProofReviewAndNewReceiptUseOnlyPaymentAmount` | Completed trip accepts multipart proof. Card proof stores the base amount with zero surcharge | Pending trip returns 409 | PASS | Payment API omits surcharge. |
| UC026 | Verify payment | Admin | PUT /api/payments/{id}/review | `uc026VerifyPayment`, `cardProofReviewAndNewReceiptUseOnlyPaymentAmount` | Base amount determines verification and a newly issued receipt, including a legacy card payment with a stored surcharge | Second review returns 409 | PASS | Historical surcharge value remains stored. |
| UC027 | View payment history | Admin, Customer | GET /api/payments, /api/payments/mine, /api/payments/{id}/proof | `UseCaseConformanceTest#uc027ViewPaymentHistory` | Admin list, owner history and owner proof returned 200 | Another customer returns 404 | PASS | â€” |
| UC028 | Generate receipt | Admin | PUT /api/payments/{id}/review, POST /api/documents/{id}/generate-receipt | `UseCaseConformanceTest#uc028GenerateReceipt` | Verification generated a receipt for the booking | Absent before verification; manual route returns 410 | PASS | â€” |
| UC029 | View receipt | Admin, Customer | GET /api/documents/{id}/pdf, /api/documents/customer/receipts, /api/documents/customer/receipts/{id}/pdf | `UseCaseConformanceTest#uc029ViewReceipt` | Admin, Normal owner and Corporate owner retrieved PDFs | Cross-customer receipt downloads return 404 | PASS | â€” |
| UC030 | Record cash payment | Admin, Driver | POST /api/payments/bookings/{id}/cash | `UseCaseConformanceTest#uc030RecordCash` | Admin and assigned driver recorded cash on completed trips | Customer request returns 403 | PASS | â€” |

## Supporting functions

| ID | Use case or check | Actor | Endpoints | Test class and method | Positive result | Negative result | Status | Notes |
|---|---|---|---|---|---|---|---|---|
| F1 | Resource and capacity availability | Customer, Admin | POST /api/bookings/availability, /api/bookings/{id}/confirm-and-assign | `UseCaseConformanceTest#supportAvailabilityAndRecommendation`, `UseCaseConformanceTest#scenario3OverlapAndReassignment` | Overlapping second booking succeeds with a different pair | Same-pair conflict returns 409; five large bags exceed capacity | PASS | The available MPV accepted 12 units and rejected 15 units. |
| F2 | Recommend smallest fitting category | Customer | POST /api/bookings/availability | `UseCaseConformanceTest#supportAvailabilityAndRecommendation` | Nine and 12 units recommended MPV | Five large bags return unavailable | PASS | One passenger received an MPV recommendation with four large bags under the 12-unit MPV policy. |
| F3 | Detect overlapping booking | Admin | POST /api/bookings/{id}/confirm-and-assign, PUT /api/assignments/{id}/reassign | `UseCaseConformanceTest#scenario3OverlapAndReassignment` | Different resource pairs can serve the same window | Conflicting assignment and busy reassignment return 409 | PASS | â€” |
| F4 | Calculate fare and extra charges | Customer, Admin | POST /api/bookings/estimate, /api/admin/bookings/{id}/charges/preview | `UseCaseConformanceTest#supportFareAndCharges` | Uses fixed 25 km route; verifies extra hour, toll and parking in total | Negative hours returns 400 | PASS | â€” |
| F5 | Upload verification photo | Driver, Admin | POST/GET /api/assignments/{id}/photos | `UseCaseConformanceTest#supportPhotosAndIncidents` | Driver uploads and Admin lists the pickup photo | Other driver upload returns 404 | PASS | â€” |
| F6 | Report trip issue | Driver, Admin | POST /api/assignments/{id}/incidents, GET /api/assignments/incidents | `UseCaseConformanceTest#supportPhotosAndIncidents` | Driver submits a delay and Admin lists incidents | Another driver returns 404 | PASS | Another driver cannot report an incident for this assignment. |
| F7 | Send notification | Admin, Driver | POST /api/notifications, GET /api/notifications/driver/{id} | `UseCaseConformanceTest#supportCalendarAndSendNotification` | Admin sends a message and driver can load inbox | Unknown recipient returns 404 | PASS | The driver event was persisted. An unknown recipient returns 404. |
| F8 | View calendar data | Admin | GET /api/assignments/schedule | `UseCaseConformanceTest#supportCalendarAndSendNotification` | Requested driver schedule for the trip date | Missing required parameters return 400 | PASS | â€” |

## End-to-end scenarios

| ID | Use case or check | Actor | Endpoints | Test class and method | Positive result | Negative result | Status | Notes |
|---|---|---|---|---|---|---|---|---|
| S1 | Corporate lifecycle | Corporate, Admin, Driver | POST /api/auth/mobile/register, /api/bookings/customer/complete, /api/documents/quotation, /api/documents/{id}/record-po, /api/bookings/{id}/confirm-and-assign, /api/assignments/{id}/photos, /api/documents/{id}/generate-invoice, /api/payments/bookings/{id}; PATCH /api/customers/{id}/verification, /api/assignments/{id}/status; PUT /api/documents/{id}/approve, /api/payments/{id}/review; GET /api/documents/customer/{id}/pdf, /api/documents/customer/receipts/{id}/pdf | `UseCaseConformanceTest#scenario1Corporate` | Walks from registration through receipt, quote and invoice downloads | Negative authorization isolated in S4 | PASS | â€” |
| S2 | Normal lifecycle | Normal, Admin, Driver | POST /api/auth/mobile/register, /api/bookings/customer/complete, /api/bookings/{id}/confirm-and-assign, /api/assignments/{id}/photos, /api/payments/bookings/{id}; PATCH /api/customers/{id}/verification, /api/assignments/{id}/status; PUT /api/payments/{id}/review; GET /api/documents/customer/receipts/{id}/pdf | `UseCaseConformanceTest#scenario2Normal` | Walks from registration through verified payment and receipt download | Negative authorization isolated in S4 | PASS | â€” |
| S3 | Conflicts and reassignment | Customer, Admin | POST /api/bookings/{id}/confirm-and-assign, PUT /api/assignments/{id}/reassign, GET /api/assignments/{id}/timeline | `UseCaseConformanceTest#scenario3OverlapAndReassignment` | Different pairs succeed; replacement points to the superseded assignment on the same booking | Same pair conflict and busy replacement both return 409 | PASS | â€” |
| S4 | Ownership and verification | Customer, Driver | POST /api/bookings/customer/complete; GET /api/bookings/mine/{id}, /api/bookings/{id}/timeline, /api/documents/customer/{id}/pdf, /api/payments/{id}/proof, /api/assignments/mine/{id} | `UseCaseConformanceTest#scenario4AccessControl` | Verified owner can book and Admin can assign | Pending/rejected blocked; cross-owner resources return 404 | PASS | â€” |
| S5 | Notification recipient and event | Admin, Customer, Driver | POST /api/auth/mobile/register, /api/bookings/customer/complete, /api/bookings/{id}/confirm-and-assign, /api/bookings/{id}/reject, /api/assignments/{id}/incidents, /api/documents/quotation, /api/documents/{id}/generate-invoice, /api/payments/bookings/{id}; PATCH /api/customers/{id}/verification, /api/assignments/{id}/status; PUT /api/assignments/{id}/reassign, /api/documents/{id}/approve, /api/payments/{id}/review; GET /api/notifications/inbox, /api/notifications/customer/mine, /api/notifications/driver/{id} | `UseCaseConformanceTest#scenario5Notifications` | Operational events match recipient and type, including the receipt | Other customer cannot list or mark the receipt notice read | PASS | The receipt notification includes the booking reference. |

## Additional Admin conformance cases

| ID | Use case or check | Actor | Endpoints | Test class and method | Positive result | Negative result | Status | Notes |
|---|---|---|---|---|---|---|---|---|
| A8 | Manage driver and vehicle unavailable periods | Admin | POST/GET /api/admin/unavailability/{resource}/{resourceId}; DELETE /api/admin/unavailability/{resource}/{resourceId}/{periodId} | `UseCaseConformanceTest#adminManagesDriverAndVehicleUnavailablePeriods` | Creates, lists, and deletes a period for each resource type | Both assigned resources return 409 with the conflicting booking ID; Customer is denied | PASS | Uses a real active assignment |
| A9 | Protect the last active Super Admin | Super Admin | PUT /api/admins/{id} | `UseCaseConformanceTest#lastActiveSuperAdminCannotBeDemotedOrDeactivated` | Demotes a second active Super Admin | Self-demotion and deactivation of the only active Super Admin each return 409 | PASS | Verifies one active Super Admin remains |
| A10 | Reject an existing session after Admin deactivation | Admin, Super Admin | POST /api/auth/login; GET /api/bookings; PUT /api/admins/{id} | `UseCaseConformanceTest#deactivatedAdminCannotUseAnExistingSession` | Active Admin can use their login session | Same session returns 401 after deactivation | PASS | Reuses the original session across requests |

## Notification events

| Event | Implemented | Test method | Result |
|---|---|---|---|
| New customer registration → Admin | Yes | `UseCaseConformanceTest#scenario5Notifications` | PASS |
| Account decision → Customer | Yes | `UseCaseConformanceTest#scenario5Notifications` | PASS |
| New booking → Admin | Yes | `UseCaseConformanceTest#scenario5Notifications` | PASS |
| Booking confirmed or rejected → Customer | Yes | `UseCaseConformanceTest#scenario5Notifications` | PASS |
| Booking cancelled by customer → Admin, assigned Driver and Customer | Yes | `UseCaseConformanceTest#uc011ConfirmedCancellationFreesPairAndOnlyNotifiesOnce` | PASS; one notice per recipient after retry |
| Confirmed booking rescheduled → Admin and assigned Driver | Yes | `UseCaseConformanceTest#confirmedBookingCanMoveWithinItsOwnWindowAndKeepsItsAssignment` | PASS; prior and new pickup appear in both notices |
| New assignment → Driver | Yes | `UseCaseConformanceTest#scenario5Notifications` | PASS |
| Reassignment → Admin, Customer, previous Driver and new Driver | Yes | `UseCaseConformanceTest#scenario5Notifications` | PASS |
| Pickup reminders at 24 hours and 2 hours → Customer and Driver | Yes | `UseCaseConformanceTest#upcomingRemindersReachOnlyTheAssignedPairOncePerWindow` | PASS at both boundaries; repeated scheduler runs produced one event per recipient and window |
| Trip status update → Admin and Customer | Yes | `UseCaseConformanceTest#scenario5Notifications` | PASS |
| Trip issue → Admin | Yes | `UseCaseConformanceTest#scenario5Notifications` | PASS |
| Quotation approved → Customer | Yes | `UseCaseConformanceTest#scenario5Notifications` | PASS |
| Invoice → Customer | Yes | `UseCaseConformanceTest#scenario5Notifications` | PASS |
| Receipt → owning Normal or Corporate Customer | Yes | `UseCaseConformanceTest#scenario5Notifications`, `UseCaseConformanceTest#receiptNotificationIsVisibleOnlyToTheBookingOwner` | PASS; contains booking reference, other customer cannot list or mark it read, repeat review adds no duplicate |

The table includes the original 12 event groups plus customer cancellation and confirmed rescheduling. It groups both reminder windows in one row. Results cover persisted events and owner-scoped HTTP access. The scheduler test uses a fixed clock, and FCM delivery remains unverified.

## Resolved findings

- **UC018:** The API rejects Normal bookings with HTTP 409 and a Corporate-only message. Corporate quotation creation still returns HTTP 201.
- **S5:** Verification emits one `RECEIPT_AVAILABLE` notification when it creates the receipt. Both account types receive their own notices. Another customer cannot access them.

## Manual gaps

| Area | Status | Manual check |
|---|---|---|
| Flutter customer and driver screens | UNVERIFIED | Run S1 and S2 on devices. Check form validation, booking and trip state refresh, photo and proof selection, and PDF opening. |
| React Admin pages | UNVERIFIED | Walk approval, assignment, reassignment, calendar, payments and notifications in the browser. Check that the displayed state and links match API results. |
| FCM delivery | UNVERIFIED | Register a test device token and confirm the corresponding push arrives with the app foregrounded and backgrounded. |
| Live routing provider | UNVERIFIED | Request a known route in development and compare its distance and fare with the provider result. The suite substitutes a deterministic 25 km route at the provider boundary. |

## Schema parity

I compared `public` with a new schema built by the same baseline and migration list as the test run. The comparison used PostgreSQL catalogs and normalized local schema qualifiers. Columns include type, nullability, default, identity, generation and collation. Constraint comparison includes the name, definition and validation state. Index comparison includes the name, definition and validity. A dash denotes a missing object. Name-only differences appear as separate entries. Counts include child objects on the seven legacy tables and PostgreSQL 18 named NOT NULL constraints.

**244 differences** across 7 tables, 96 columns, 110 constraints and 31 indexes. All seven table differences are legacy tables present in `public`. No changes were applied to `public`.

| Drift area | Baseline or migration to review |
|---|---|
| Legacy `assignment`, `quotation`, `purchase_order`, `invoice`, `receipt`, `trip_status` and `verification_photo`; legacy customer, driver and payment metadata | `BASELINE_CURRENT.sql` describes a clean install. Keep compatibility metadata where operationally required and document the intentional legacy-only objects. Use a new forward migration for any approved convergence. Historical feature migrations 002–004, 011, 013 and 018 describe these paths. |
| Shared columns with serial/identity, nullability, length, defaults and timestamps | Review `BASELINE_CURRENT.sql` against intended contracts. Use a new forward migration for intentional parity on upgraded databases. In particular, the upgrade branch of 018 preserves the legacy payment default or timestamp type. Review its successor for `payment_status` and `submitted_at`. Review 007 for admin timestamps and defaults, 028 for customer rejection reason and 016/023 for booking fields. |
| Shared foreign keys, checks and supporting indexes | Review `BASELINE_CURRENT.sql` and forward migrations 003/004 (documents), 012/023 (booking indexes), 015/020 (normalized account and fleet uniqueness), 027 (admin/audit foreign keys), 032 (trip photo constraints), 033 (fleet document checks), 035 (vehicle category checks) and 036 (notifications). Preserve equivalent renamed definitions or normalize them only if operational tooling requires the same names. |


### Complete catalog differences

| Object type | Object | Dev public | Disposable test |
|---|---|---|---|
| COLUMN | `admin.admin_createdat` | `timestamp without time zone NULL` | `timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| COLUMN | `admin.admin_id` | `bigint NOT NULL DEFAULT nextval('admin_admin_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `admin.admin_phone` | `character varying(50) NULL` | `character varying(30) NULL` |
| COLUMN | `admin.admin_role` | `character varying(20) NULL` | `character varying(20) NOT NULL DEFAULT 'ADMIN'::character varying` |
| COLUMN | `admin.admin_status` | `character varying(20) NULL` | `character varying(20) NOT NULL DEFAULT 'ACTIVE'::character varying` |
| COLUMN | `admin.admin_updatedat` | `timestamp without time zone NULL` | `timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| COLUMN | `assignment.assignment_date` | `timestamp without time zone NULL DEFAULT CURRENT_TIMESTAMP` | — |
| COLUMN | `assignment.assignment_id` | `bigint NOT NULL DEFAULT nextval('assignment_assignment_id_seq'::regclass)` | — |
| COLUMN | `assignment.booking_id` | `bigint NOT NULL` | — |
| COLUMN | `assignment.driver_id` | `bigint NOT NULL` | — |
| COLUMN | `assignment.trip_status` | `character varying(50) NULL DEFAULT 'ASSIGNED'::character varying` | — |
| COLUMN | `assignment.vehicle_id` | `bigint NOT NULL` | — |
| COLUMN | `assignment_verification_photo.photo_id` | `bigint NOT NULL DEFAULT nextval('assignment_verification_photo_photo_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `booking.booking_createdat` | `timestamp without time zone NULL DEFAULT CURRENT_TIMESTAMP` | `timestamp without time zone NULL` |
| COLUMN | `booking.booking_destinationaddress` | `character varying(500) NOT NULL` | `character varying(500) NULL` |
| COLUMN | `booking.booking_id` | `bigint NOT NULL DEFAULT nextval('booking_booking_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `booking.booking_passengercount` | `integer NOT NULL` | `integer NULL` |
| COLUMN | `booking.booking_pickupaddress` | `character varying(500) NOT NULL` | `character varying(500) NULL` |
| COLUMN | `booking.booking_pickuptime` | `time without time zone NOT NULL` | `time without time zone NULL` |
| COLUMN | `booking.booking_status` | `character varying(255) NULL DEFAULT 'PENDING'::character varying` | `character varying(255) NULL` |
| COLUMN | `booking.booking_tripdate` | `date NOT NULL` | `date NULL` |
| COLUMN | `booking.booking_triptype` | `character varying(255) NULL` | `character varying(30) NULL` |
| COLUMN | `booking.cust_id` | `bigint NOT NULL` | `bigint NULL` |
| COLUMN | `booking_request_idempotency.id` | `bigint NOT NULL DEFAULT nextval('booking_request_idempotency_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `customer.approval_status` | `character varying(20) NULL` | — |
| COLUMN | `customer.cust_createdat` | `timestamp without time zone NULL DEFAULT CURRENT_TIMESTAMP` | — |
| COLUMN | `customer.cust_email` | `character varying(255) NOT NULL` | `character varying(255) NULL` |
| COLUMN | `customer.cust_name` | `character varying(255) NOT NULL` | `character varying(255) NULL` |
| COLUMN | `customer.cust_password` | `character varying(255) NOT NULL` | `character varying(255) NULL` |
| COLUMN | `customer.cust_status` | `character varying(255) NULL DEFAULT 'ACTIVE'::character varying` | `character varying(255) NULL` |
| COLUMN | `customer.cust_type` | `character varying(255) NOT NULL` | `character varying(255) NULL` |
| COLUMN | `customer.cust_updatedat` | `timestamp without time zone NULL` | — |
| COLUMN | `customer.rejection_reason` | `text NULL` | `character varying(500) NULL` |
| COLUMN | `documents.created_at` | `timestamp without time zone NULL DEFAULT CURRENT_TIMESTAMP` | `timestamp without time zone NULL` |
| COLUMN | `documents.document_id` | `bigint NOT NULL DEFAULT nextval('documents_document_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `driver.driver_createdat` | `timestamp without time zone NULL DEFAULT CURRENT_TIMESTAMP` | — |
| COLUMN | `driver.driver_email` | `character varying(255) NOT NULL` | `character varying(255) NULL` |
| COLUMN | `driver.driver_id` | `bigint NOT NULL DEFAULT nextval('driver_driver_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `driver.driver_name` | `character varying(255) NOT NULL` | `character varying(255) NULL` |
| COLUMN | `driver.driver_password` | `character varying(255) NOT NULL` | `character varying(255) NULL` |
| COLUMN | `driver.driver_status` | `character varying(255) NULL DEFAULT 'ACTIVE'::character varying` | `character varying(255) NULL` |
| COLUMN | `driver.driver_updatedat` | `timestamp without time zone NULL` | — |
| COLUMN | `driver_assignment.assignment_date` | `timestamp without time zone NULL DEFAULT CURRENT_TIMESTAMP` | `timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| COLUMN | `driver_assignment.assignment_id` | `bigint NOT NULL DEFAULT nextval('driver_assignment_assignment_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `driver_assignment.assignment_status` | `character varying(24) NULL DEFAULT 'ASSIGNED'::character varying` | `character varying(24) NOT NULL DEFAULT 'ASSIGNED'::character varying` |
| COLUMN | `fleet_document.fleet_document_id` | `bigint NOT NULL DEFAULT nextval('fleet_document_fleet_document_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `fleet_document_audit.fleet_document_audit_id` | `bigint NOT NULL DEFAULT nextval('fleet_document_audit_fleet_document_audit_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `fleet_expiry_reminder.fleet_expiry_reminder_id` | `bigint NOT NULL DEFAULT nextval('fleet_expiry_reminder_fleet_expiry_reminder_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `invoice.booking_id` | `integer NOT NULL` | — |
| COLUMN | `invoice.invoice_amount` | `numeric(10,2) NULL` | — |
| COLUMN | `invoice.invoice_date` | `date NULL` | — |
| COLUMN | `invoice.invoice_id` | `integer NOT NULL DEFAULT nextval('invoice_invoice_id_seq'::regclass)` | — |
| COLUMN | `invoice.invoice_number` | `character varying(50) NULL` | — |
| COLUMN | `invoice.invoice_status` | `character varying(30) NULL DEFAULT 'UNPAID'::character varying` | — |
| COLUMN | `notification.notification_createdat` | `timestamp without time zone NULL DEFAULT CURRENT_TIMESTAMP` | `timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| COLUMN | `notification.notification_id` | `bigint NOT NULL DEFAULT nextval('notification_notification_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `notification.notification_status` | `character varying(24) NULL DEFAULT 'UNREAD'::character varying` | `character varying(24) NOT NULL DEFAULT 'UNREAD'::character varying` |
| COLUMN | `payment.legacy_payment_date` | `timestamp without time zone NULL` | — |
| COLUMN | `payment.legacy_payment_method` | `character varying(50) NULL` | — |
| COLUMN | `payment.legacy_payment_status` | `character varying(30) NULL` | — |
| COLUMN | `payment.payment_id` | `bigint NOT NULL DEFAULT nextval('payment_payment_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `payment.payment_status` | `character varying(24) NOT NULL DEFAULT 'PENDING'::character varying` | `character varying(24) NOT NULL DEFAULT 'PENDING_VERIFICATION'::character varying` |
| COLUMN | `payment.submitted_at` | `timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP` | `timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| COLUMN | `purchase_order.po_date` | `date NULL` | — |
| COLUMN | `purchase_order.po_id` | `integer NOT NULL DEFAULT nextval('purchase_order_po_id_seq'::regclass)` | — |
| COLUMN | `purchase_order.po_number` | `character varying(50) NULL` | — |
| COLUMN | `purchase_order.po_status` | `character varying(30) NULL DEFAULT 'PENDING'::character varying` | — |
| COLUMN | `purchase_order.quotation_id` | `integer NOT NULL` | — |
| COLUMN | `quotation.cust_id` | `integer NOT NULL` | — |
| COLUMN | `quotation.quotation_amount` | `numeric(10,2) NULL` | — |
| COLUMN | `quotation.quotation_date` | `date NULL` | — |
| COLUMN | `quotation.quotation_id` | `integer NOT NULL DEFAULT nextval('quotation_quotation_id_seq'::regclass)` | — |
| COLUMN | `quotation.quotation_number` | `character varying(50) NULL` | — |
| COLUMN | `quotation.quotation_status` | `character varying(30) NULL DEFAULT 'PENDING'::character varying` | — |
| COLUMN | `receipt.payment_id` | `integer NOT NULL` | — |
| COLUMN | `receipt.receipt_amount` | `numeric(10,2) NULL` | — |
| COLUMN | `receipt.receipt_date` | `date NULL` | — |
| COLUMN | `receipt.receipt_id` | `integer NOT NULL DEFAULT nextval('receipt_receipt_id_seq'::regclass)` | — |
| COLUMN | `receipt.receipt_number` | `character varying(50) NULL` | — |
| COLUMN | `trip_status.booking_id` | `integer NOT NULL` | — |
| COLUMN | `trip_status.remarks` | `text NULL` | — |
| COLUMN | `trip_status.status_id` | `integer NOT NULL DEFAULT nextval('trip_status_status_id_seq'::regclass)` | — |
| COLUMN | `trip_status.status_time` | `timestamp without time zone NULL DEFAULT CURRENT_TIMESTAMP` | — |
| COLUMN | `trip_status.trip_status` | `character varying(30) NOT NULL` | — |
| COLUMN | `vehicle.vehicle_capacity` | `integer NOT NULL` | `integer NULL` |
| COLUMN | `vehicle.vehicle_id` | `bigint NOT NULL DEFAULT nextval('vehicle_vehicle_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `vehicle.vehicle_model` | `character varying(255) NOT NULL` | `character varying(255) NULL` |
| COLUMN | `vehicle.vehicle_plateno` | `character varying(255) NOT NULL` | `character varying(255) NULL` |
| COLUMN | `vehicle.vehicle_status` | `character varying(255) NULL DEFAULT 'AVAILABLE'::character varying` | `character varying(255) NULL` |
| COLUMN | `vehicle.vehicle_type` | `character varying(255) NOT NULL` | `character varying(255) NULL` |
| COLUMN | `vehicle_category.vehicle_category_id` | `bigint NOT NULL DEFAULT nextval('vehicle_category_vehicle_category_id_seq'::regclass)` | `bigint NOT NULL IDENTITY d` |
| COLUMN | `verification_photo.booking_id` | `integer NOT NULL` | — |
| COLUMN | `verification_photo.photo_id` | `integer NOT NULL DEFAULT nextval('verification_photo_photo_id_seq'::regclass)` | — |
| COLUMN | `verification_photo.photo_path` | `character varying(255) NULL` | — |
| COLUMN | `verification_photo.status_id` | `integer NULL` | — |
| COLUMN | `verification_photo.uploaded_at` | `timestamp without time zone NULL DEFAULT CURRENT_TIMESTAMP` | — |
| CONSTRAINT | `admin.admin_admin_createdat_not_null` | — | `NOT NULL admin_createdat` |
| CONSTRAINT | `admin.admin_admin_role_not_null` | — | `NOT NULL admin_role` |
| CONSTRAINT | `admin.admin_admin_status_not_null` | — | `NOT NULL admin_status` |
| CONSTRAINT | `admin.admin_admin_updatedat_not_null` | — | `NOT NULL admin_updatedat` |
| CONSTRAINT | `assignment.assignment_assignment_id_not_null` | `NOT NULL assignment_id` | — |
| CONSTRAINT | `assignment.assignment_booking_id_fkey` | `FOREIGN KEY (booking_id) REFERENCES booking(booking_id)` | — |
| CONSTRAINT | `assignment.assignment_booking_id_not_null` | `NOT NULL booking_id` | — |
| CONSTRAINT | `assignment.assignment_driver_id_fkey` | `FOREIGN KEY (driver_id) REFERENCES driver(driver_id)` | — |
| CONSTRAINT | `assignment.assignment_driver_id_not_null` | `NOT NULL driver_id` | — |
| CONSTRAINT | `assignment.assignment_pkey` | `PRIMARY KEY (assignment_id)` | — |
| CONSTRAINT | `assignment.assignment_vehicle_id_fkey` | `FOREIGN KEY (vehicle_id) REFERENCES vehicle(vehicle_id)` | — |
| CONSTRAINT | `assignment.assignment_vehicle_id_not_null` | `NOT NULL vehicle_id` | — |
| CONSTRAINT | `booking.booking_booking_destinationaddress_not_null` | `NOT NULL booking_destinationaddress` | — |
| CONSTRAINT | `booking.booking_booking_passengercount_not_null` | `NOT NULL booking_passengercount` | — |
| CONSTRAINT | `booking.booking_booking_pickupaddress_not_null` | `NOT NULL booking_pickupaddress` | — |
| CONSTRAINT | `booking.booking_booking_pickuptime_not_null` | `NOT NULL booking_pickuptime` | — |
| CONSTRAINT | `booking.booking_booking_tripdate_not_null` | `NOT NULL booking_tripdate` | — |
| CONSTRAINT | `booking.booking_cust_id_fkey` | — | `FOREIGN KEY (cust_id) REFERENCES customer(cust_id)` |
| CONSTRAINT | `booking.booking_cust_id_not_null` | `NOT NULL cust_id` | — |
| CONSTRAINT | `booking.booking_driver_id_fkey` | — | `FOREIGN KEY (driver_id) REFERENCES driver(driver_id)` |
| CONSTRAINT | `booking.booking_vehicle_id_fkey` | — | `FOREIGN KEY (vehicle_id) REFERENCES vehicle(vehicle_id)` |
| CONSTRAINT | `booking.fk_booking_customer` | `FOREIGN KEY (cust_id) REFERENCES customer(cust_id)` | — |
| CONSTRAINT | `booking.fk_booking_driver` | `FOREIGN KEY (driver_id) REFERENCES driver(driver_id)` | — |
| CONSTRAINT | `booking.fk_booking_vehicle` | `FOREIGN KEY (vehicle_id) REFERENCES vehicle(vehicle_id)` | — |
| CONSTRAINT | `booking_fare_revision.fk_booking_fare_revision_proposed_by` | `FOREIGN KEY (proposed_by) REFERENCES admin(admin_id) ON DELETE SET NULL` | — |
| CONSTRAINT | `customer.customer_cust_email_key` | `UNIQUE (cust_email)` | — |
| CONSTRAINT | `customer.customer_cust_email_not_null` | `NOT NULL cust_email` | — |
| CONSTRAINT | `customer.customer_cust_name_not_null` | `NOT NULL cust_name` | — |
| CONSTRAINT | `customer.customer_cust_password_not_null` | `NOT NULL cust_password` | — |
| CONSTRAINT | `customer.customer_cust_type_not_null` | `NOT NULL cust_type` | — |
| CONSTRAINT | `documents.documents_booking_id_fkey` | — | `FOREIGN KEY (booking_id) REFERENCES booking(booking_id)` |
| CONSTRAINT | `documents.documents_cust_id_fkey` | — | `FOREIGN KEY (cust_id) REFERENCES customer(cust_id)` |
| CONSTRAINT | `documents.documents_payment_id_fkey` | — | `FOREIGN KEY (payment_id) REFERENCES payment(payment_id) ON DELETE RESTRICT` |
| CONSTRAINT | `documents.documents_source_document_id_fkey` | — | `FOREIGN KEY (source_document_id) REFERENCES documents(document_id) ON DELETE RESTRICT` |
| CONSTRAINT | `documents.fk_document_booking` | `FOREIGN KEY (booking_id) REFERENCES booking(booking_id)` | — |
| CONSTRAINT | `documents.fk_document_customer` | `FOREIGN KEY (cust_id) REFERENCES customer(cust_id)` | — |
| CONSTRAINT | `documents.fk_document_payment_current` | `FOREIGN KEY (payment_id) REFERENCES payment(payment_id) ON DELETE RESTRICT` | — |
| CONSTRAINT | `driver.driver_driver_email_key` | `UNIQUE (driver_email)` | — |
| CONSTRAINT | `driver.driver_driver_email_not_null` | `NOT NULL driver_email` | — |
| CONSTRAINT | `driver.driver_driver_name_not_null` | `NOT NULL driver_name` | — |
| CONSTRAINT | `driver.driver_driver_password_not_null` | `NOT NULL driver_password` | — |
| CONSTRAINT | `driver_assignment.driver_assignment_assignment_date_not_null` | — | `NOT NULL assignment_date` |
| CONSTRAINT | `driver_assignment.driver_assignment_assignment_status_not_null` | — | `NOT NULL assignment_status` |
| CONSTRAINT | `driver_assignment.driver_assignment_booking_id_fkey` | — | `FOREIGN KEY (booking_id) REFERENCES booking(booking_id)` |
| CONSTRAINT | `driver_assignment.driver_assignment_driver_id_fkey` | — | `FOREIGN KEY (driver_id) REFERENCES driver(driver_id)` |
| CONSTRAINT | `driver_assignment.driver_assignment_vehicle_id_fkey` | — | `FOREIGN KEY (vehicle_id) REFERENCES vehicle(vehicle_id)` |
| CONSTRAINT | `driver_assignment.fk_assignment_booking` | `FOREIGN KEY (booking_id) REFERENCES booking(booking_id)` | — |
| CONSTRAINT | `driver_assignment.fk_assignment_driver` | `FOREIGN KEY (driver_id) REFERENCES driver(driver_id)` | — |
| CONSTRAINT | `driver_assignment.fk_assignment_vehicle` | `FOREIGN KEY (vehicle_id) REFERENCES vehicle(vehicle_id)` | — |
| CONSTRAINT | `fleet_document.ck_fleet_document_owner_type` | `CHECK (((((document_type)::text = ANY ((ARRAY['DRIVER_IC'::character varying, 'DRIVER_LICENSE'::character varying])::text[])) AND (driver_id IS NOT NULL)) OR (((document_type)::text = 'VEHICLE_ROAD_TAX'::text) AND (vehicle_id IS NOT NULL))))` | — |
| CONSTRAINT | `fleet_document.ck_fleet_document_single_owner` | `CHECK ((((driver_id IS NOT NULL) AND (vehicle_id IS NULL)) OR ((driver_id IS NULL) AND (vehicle_id IS NOT NULL))))` | — |
| CONSTRAINT | `fleet_document.fleet_document_check` | — | `CHECK ((((driver_id IS NOT NULL) AND (vehicle_id IS NULL)) OR ((driver_id IS NULL) AND (vehicle_id IS NOT NULL))))` |
| CONSTRAINT | `fleet_document.fleet_document_check1` | — | `CHECK (((((document_type)::text = ANY ((ARRAY['DRIVER_IC'::character varying, 'DRIVER_LICENSE'::character varying])::text[])) AND (driver_id IS NOT NULL)) OR (((document_type)::text = 'VEHICLE_ROAD_TAX'::text) AND (vehicle_id IS NOT NULL))))` |
| CONSTRAINT | `invoice.fk_invoice_booking` | `FOREIGN KEY (booking_id) REFERENCES booking(booking_id)` | — |
| CONSTRAINT | `invoice.invoice_booking_id_not_null` | `NOT NULL booking_id` | — |
| CONSTRAINT | `invoice.invoice_invoice_id_not_null` | `NOT NULL invoice_id` | — |
| CONSTRAINT | `invoice.invoice_invoice_number_key` | `UNIQUE (invoice_number)` | — |
| CONSTRAINT | `invoice.invoice_pkey` | `PRIMARY KEY (invoice_id)` | — |
| CONSTRAINT | `notification.fkn2xfgfgrnl177v2p9ca2odk2o` | `FOREIGN KEY (driver_id) REFERENCES driver(driver_id)` | — |
| CONSTRAINT | `notification.notification_driver_id_fkey` | — | `FOREIGN KEY (driver_id) REFERENCES driver(driver_id)` |
| CONSTRAINT | `notification.notification_notification_createdat_not_null` | — | `NOT NULL notification_createdat` |
| CONSTRAINT | `notification.notification_notification_status_not_null` | — | `NOT NULL notification_status` |
| CONSTRAINT | `payment.fk_payment_booking_current` | `FOREIGN KEY (booking_id) REFERENCES booking(booking_id) ON DELETE RESTRICT` | — |
| CONSTRAINT | `payment.fk_payment_recorded_admin_current` | `FOREIGN KEY (recorded_by_admin_id) REFERENCES admin(admin_id) ON DELETE SET NULL` | — |
| CONSTRAINT | `payment.fk_payment_recorded_driver_current` | `FOREIGN KEY (recorded_by_driver_id) REFERENCES driver(driver_id) ON DELETE SET NULL` | — |
| CONSTRAINT | `payment.fk_payment_verified_by_current` | `FOREIGN KEY (verified_by) REFERENCES admin(admin_id) ON DELETE SET NULL` | — |
| CONSTRAINT | `payment.payment_amount_check` | — | `CHECK ((amount > (0)::numeric))` |
| CONSTRAINT | `payment.payment_amount_not_null` | — | `NOT NULL amount` |
| CONSTRAINT | `payment.payment_booking_id_fkey` | — | `FOREIGN KEY (booking_id) REFERENCES booking(booking_id) ON DELETE RESTRICT` |
| CONSTRAINT | `payment.payment_payment_amount_not_null` | `NOT NULL amount` | — |
| CONSTRAINT | `payment.payment_payment_method_check` | — | `CHECK (((payment_method)::text = ANY ((ARRAY['BANK_TRANSFER'::character varying, 'CASH'::character varying, 'CARD'::character varying])::text[])))` |
| CONSTRAINT | `payment.payment_payment_status_check` | — | `CHECK (((payment_status)::text = ANY ((ARRAY['PENDING_VERIFICATION'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying])::text[])))` |
| CONSTRAINT | `payment.payment_recorded_by_admin_id_fkey` | — | `FOREIGN KEY (recorded_by_admin_id) REFERENCES admin(admin_id) ON DELETE SET NULL` |
| CONSTRAINT | `payment.payment_recorded_by_driver_id_fkey` | — | `FOREIGN KEY (recorded_by_driver_id) REFERENCES driver(driver_id) ON DELETE SET NULL` |
| CONSTRAINT | `payment.payment_surcharge_amount_check` | — | `CHECK ((surcharge_amount >= (0)::numeric))` |
| CONSTRAINT | `payment.payment_verified_by_fkey` | — | `FOREIGN KEY (verified_by) REFERENCES admin(admin_id) ON DELETE SET NULL` |
| CONSTRAINT | `purchase_order.fk_po_quotation` | `FOREIGN KEY (quotation_id) REFERENCES quotation(quotation_id)` | — |
| CONSTRAINT | `purchase_order.purchase_order_pkey` | `PRIMARY KEY (po_id)` | — |
| CONSTRAINT | `purchase_order.purchase_order_po_id_not_null` | `NOT NULL po_id` | — |
| CONSTRAINT | `purchase_order.purchase_order_po_number_key` | `UNIQUE (po_number)` | — |
| CONSTRAINT | `purchase_order.purchase_order_quotation_id_not_null` | `NOT NULL quotation_id` | — |
| CONSTRAINT | `quotation.fk_quotation_customer` | `FOREIGN KEY (cust_id) REFERENCES customer(cust_id)` | — |
| CONSTRAINT | `quotation.quotation_cust_id_not_null` | `NOT NULL cust_id` | — |
| CONSTRAINT | `quotation.quotation_pkey` | `PRIMARY KEY (quotation_id)` | — |
| CONSTRAINT | `quotation.quotation_quotation_id_not_null` | `NOT NULL quotation_id` | — |
| CONSTRAINT | `quotation.quotation_quotation_number_key` | `UNIQUE (quotation_number)` | — |
| CONSTRAINT | `receipt.fk_receipt_payment` | `FOREIGN KEY (payment_id) REFERENCES payment(payment_id)` | — |
| CONSTRAINT | `receipt.receipt_payment_id_not_null` | `NOT NULL payment_id` | — |
| CONSTRAINT | `receipt.receipt_pkey` | `PRIMARY KEY (receipt_id)` | — |
| CONSTRAINT | `receipt.receipt_receipt_id_not_null` | `NOT NULL receipt_id` | — |
| CONSTRAINT | `receipt.receipt_receipt_number_key` | `UNIQUE (receipt_number)` | — |
| CONSTRAINT | `trip_status.fk_tripstatus_booking` | `FOREIGN KEY (booking_id) REFERENCES booking(booking_id)` | — |
| CONSTRAINT | `trip_status.trip_status_booking_id_not_null` | `NOT NULL booking_id` | — |
| CONSTRAINT | `trip_status.trip_status_pkey` | `PRIMARY KEY (status_id)` | — |
| CONSTRAINT | `trip_status.trip_status_status_id_not_null` | `NOT NULL status_id` | — |
| CONSTRAINT | `trip_status.trip_status_trip_status_not_null` | `NOT NULL trip_status` | — |
| CONSTRAINT | `vehicle.fk_vehicle_driver` | `FOREIGN KEY (driver_id) REFERENCES driver(driver_id)` | — |
| CONSTRAINT | `vehicle.vehicle_driver_id_fkey` | — | `FOREIGN KEY (driver_id) REFERENCES driver(driver_id)` |
| CONSTRAINT | `vehicle.vehicle_vehicle_capacity_not_null` | `NOT NULL vehicle_capacity` | — |
| CONSTRAINT | `vehicle.vehicle_vehicle_model_not_null` | `NOT NULL vehicle_model` | — |
| CONSTRAINT | `vehicle.vehicle_vehicle_plateno_key` | `UNIQUE (vehicle_plateno)` | — |
| CONSTRAINT | `vehicle.vehicle_vehicle_plateno_not_null` | `NOT NULL vehicle_plateno` | — |
| CONSTRAINT | `vehicle.vehicle_vehicle_type_not_null` | `NOT NULL vehicle_type` | — |
| CONSTRAINT | `vehicle_category.vehicle_category_boot_capacity_check` | `CHECK ((boot_capacity_units >= 0))` | — |
| CONSTRAINT | `vehicle_category.vehicle_category_boot_capacity_units_check` | — | `CHECK ((boot_capacity_units >= 0))` |
| CONSTRAINT | `verification_photo.fk_photo_booking` | `FOREIGN KEY (booking_id) REFERENCES booking(booking_id)` | — |
| CONSTRAINT | `verification_photo.fk_photo_status` | `FOREIGN KEY (status_id) REFERENCES trip_status(status_id)` | — |
| CONSTRAINT | `verification_photo.verification_photo_booking_id_not_null` | `NOT NULL booking_id` | — |
| CONSTRAINT | `verification_photo.verification_photo_photo_id_not_null` | `NOT NULL photo_id` | — |
| CONSTRAINT | `verification_photo.verification_photo_pkey` | `PRIMARY KEY (photo_id)` | — |
| INDEX | `admin.uq_admin_email_normalized` | — | `CREATE UNIQUE INDEX uq_admin_email_normalized ON admin USING btree (lower(btrim((admin_email)::text))) WHERE ((admin_email IS NOT NULL) AND (btrim((admin_email)::text) <> ''::text))` |
| INDEX | `assignment.assignment_pkey` | `CREATE UNIQUE INDEX assignment_pkey ON assignment USING btree (assignment_id)` | — |
| INDEX | `assignment_verification_photo.idx_assignment_photo_assignment` | — | `CREATE INDEX idx_assignment_photo_assignment ON assignment_verification_photo USING btree (assignment_id, uploaded_at DESC)` |
| INDEX | `assignment_verification_photo.idx_assignment_verification_photo_assignment` | `CREATE INDEX idx_assignment_verification_photo_assignment ON assignment_verification_photo USING btree (assignment_id, uploaded_at DESC)` | — |
| INDEX | `booking.idx_booking_customer_created` | — | `CREATE INDEX idx_booking_customer_created ON booking USING btree (cust_id, booking_createdat DESC)` |
| INDEX | `booking.idx_booking_customer_created_at` | `CREATE INDEX idx_booking_customer_created_at ON booking USING btree (cust_id, booking_createdat DESC)` | — |
| INDEX | `booking.idx_booking_status` | — | `CREATE INDEX idx_booking_status ON booking USING btree (booking_status)` |
| INDEX | `customer.customer_cust_email_key` | `CREATE UNIQUE INDEX customer_cust_email_key ON customer USING btree (cust_email)` | — |
| INDEX | `documents.idx_documents_booking` | — | `CREATE INDEX idx_documents_booking ON documents USING btree (booking_id)` |
| INDEX | `documents.idx_documents_customer` | — | `CREATE INDEX idx_documents_customer ON documents USING btree (cust_id)` |
| INDEX | `documents.idx_documents_source` | — | `CREATE INDEX idx_documents_source ON documents USING btree (source_document_id)` |
| INDEX | `documents.idx_documents_type_created` | — | `CREATE INDEX idx_documents_type_created ON documents USING btree (document_type, created_at DESC)` |
| INDEX | `documents.uq_documents_type_source` | — | `CREATE UNIQUE INDEX uq_documents_type_source ON documents USING btree (document_type, source_document_id) WHERE (source_document_id IS NOT NULL)` |
| INDEX | `driver.driver_driver_email_key` | `CREATE UNIQUE INDEX driver_driver_email_key ON driver USING btree (driver_email)` | — |
| INDEX | `driver.uq_driver_email_normalized` | `CREATE UNIQUE INDEX uq_driver_email_normalized ON driver USING btree (lower(btrim((driver_email)::text)))` | `CREATE UNIQUE INDEX uq_driver_email_normalized ON driver USING btree (lower(btrim((driver_email)::text))) WHERE ((driver_email IS NOT NULL) AND (btrim((driver_email)::text) <> ''::text))` |
| INDEX | `driver_assignment.idx_assignment_booking` | — | `CREATE INDEX idx_assignment_booking ON driver_assignment USING btree (booking_id)` |
| INDEX | `driver_assignment.idx_assignment_status` | — | `CREATE INDEX idx_assignment_status ON driver_assignment USING btree (assignment_status)` |
| INDEX | `driver_assignment.idx_assignment_vehicle` | — | `CREATE INDEX idx_assignment_vehicle ON driver_assignment USING btree (vehicle_id)` |
| INDEX | `invoice.invoice_invoice_number_key` | `CREATE UNIQUE INDEX invoice_invoice_number_key ON invoice USING btree (invoice_number)` | — |
| INDEX | `invoice.invoice_pkey` | `CREATE UNIQUE INDEX invoice_pkey ON invoice USING btree (invoice_id)` | — |
| INDEX | `notification.idx_notification_driver_created` | — | `CREATE INDEX idx_notification_driver_created ON notification USING btree (driver_id, notification_createdat DESC)` |
| INDEX | `purchase_order.purchase_order_pkey` | `CREATE UNIQUE INDEX purchase_order_pkey ON purchase_order USING btree (po_id)` | — |
| INDEX | `purchase_order.purchase_order_po_number_key` | `CREATE UNIQUE INDEX purchase_order_po_number_key ON purchase_order USING btree (po_number)` | — |
| INDEX | `quotation.quotation_pkey` | `CREATE UNIQUE INDEX quotation_pkey ON quotation USING btree (quotation_id)` | — |
| INDEX | `quotation.quotation_quotation_number_key` | `CREATE UNIQUE INDEX quotation_quotation_number_key ON quotation USING btree (quotation_number)` | — |
| INDEX | `receipt.receipt_pkey` | `CREATE UNIQUE INDEX receipt_pkey ON receipt USING btree (receipt_id)` | — |
| INDEX | `receipt.receipt_receipt_number_key` | `CREATE UNIQUE INDEX receipt_receipt_number_key ON receipt USING btree (receipt_number)` | — |
| INDEX | `trip_status.trip_status_pkey` | `CREATE UNIQUE INDEX trip_status_pkey ON trip_status USING btree (status_id)` | — |
| INDEX | `vehicle.idx_vehicle_driver` | — | `CREATE INDEX idx_vehicle_driver ON vehicle USING btree (driver_id)` |
| INDEX | `vehicle.vehicle_vehicle_plateno_key` | `CREATE UNIQUE INDEX vehicle_vehicle_plateno_key ON vehicle USING btree (vehicle_plateno)` | — |
| INDEX | `verification_photo.verification_photo_pkey` | `CREATE UNIQUE INDEX verification_photo_pkey ON verification_photo USING btree (photo_id)` | — |
| TABLE | `assignment` | `r / p` | — |
| TABLE | `invoice` | `r / p` | — |
| TABLE | `purchase_order` | `r / p` | — |
| TABLE | `quotation` | `r / p` | — |
| TABLE | `receipt` | `r / p` | — |
| TABLE | `trip_status` | `r / p` | — |
| TABLE | `verification_photo` | `r / p` | — |
