# DilaDrive refinement test report

## Executed checks

| Check | Result |
| --- | --- |
| Maven compilation and test suites | Passed. The combined run reported 284 tests, 280 passed and four opt-in cases skipped. The final focused PostgreSQL, document and driver rerun passed all 28 tests. |
| PostgreSQL booking integration | Passed. 55 cases in an isolated schema, including the combined milestone, incident, ownership, commission and dashboard endpoint test. |
| Real HTTP pricing and read-only live fleet | Passed. Eight real HTTP pricing tests and one read-only live fleet check. Spring started against PostgreSQL. The route used the existing road provider and Windows HTTPS trust. The quote example returned 139.6706 km and RM 172.42 with the test category rate. The live fleet audit returned HTTP 200 and MPV. |
| React | Passed. Production build and 36 project tests. |
| Flutter dependencies and analysis | Passed. Existing dependencies resolved and the analyzer found no issues. |
| Standard Windows Flutter test | Blocked before tests by the PDFium native build hook because no Windows host C/C++ compiler was configured. |
| Flutter temporary test copy | Passed. 191 tests. The Windows PDF native hooks were omitted in this copy. |
| Android | Passed. Debug APK built using the project dependencies and Android SDK. |
| Local migration 032 | Applied. Audited booking, assignment, document and payment counts remained 10, 3, 6 and 2. Local PostgreSQL now contains 32 tables. |
| Draw.io artifacts | Both parsed as XML and every connector resolved. The ERD contains 32 tables and 53 foreign-key connectors. The UCD contains 33 use cases. |

## Scenario coverage

| Acceptance area | Executed evidence or remaining coverage |
| --- | --- |
| Registration, account verification, unverified access and Admin driver creation | Account HTTP/session, customer approval and driver creation tests |
| Capacity, overlap for each resource, same-time different pairs | PostgreSQL recommendation, interval and multiple-pair cases |
| Admin confirmation, concurrent confirmation and reassignment history | PostgreSQL confirmation, atomic reservation, cancellation and assignment-history cases |
| Driver access to assigned trips | Driver session tests and the new endpoint test reject access from another driver |
| Transition validation, retries and timestamps | The new endpoint test rejects an invalid transition, accepts and replays OTW, verifies two saved events and fetches the same timeline as the booking owner |
| Incident and notification inbox | The new endpoint test verifies creation by the assigned driver and read access by Admin. Existing inbox tests cover ownership |
| Quotation PDF, finalized invoice, receipt and commission | Document and PDF tests, real HTTP pricing/document checks, and completion/commission tests. The PO test verifies the approved quotation, recorded client reference and source link |
| Calendar and mobile status | React production build, Flutter widget tests, and PostgreSQL confirmation, cancellation and reassignment cases. Direct browser interaction across all calendar modes remains a manual check. |
| Live Firebase delivery and full workflow on a physical device | Not executed. Requires target Firebase configuration and a registered device. |

## Repeat locally

- Backend suites use the installed Maven because the project has no checked-in wrapper. From the backend directory run `mvn test`. Set `RUN_CUSTOMER_TRIP_IT=true` and run `mvn "-Dtest=*Test,CustomerTripPostgresIT" test` for the isolated PostgreSQL cases. Enable `RUN_PRICING_IT=true` with `PricingPostgresIT` for real road-provider and HTTP checks. Set up the local disposable-schema permission before running opt-in integration tests.
- From Admin Web run `npm run build` and `node --test tests/*.test.cjs`.
- From Flutter run `flutter pub get`, `flutter analyze`, `flutter test`, and `flutter build apk --debug`. Standard Windows tests require the native PDF build toolchain. The Android build artifact is `mobile-app/build/app/outputs/flutter-apk/app-debug.apk`.

For a demonstration, submit a booking as a verified customer, confirm it in Admin, check the calendar, advance a milestone as the assigned driver, check both timelines, submit an incident, and open it on the Admin dashboard. Verify the received-PO reference and the PDF/payment lifecycle using suitable demonstration records.
