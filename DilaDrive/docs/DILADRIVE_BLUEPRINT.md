# DilaDrive – Pre-Development Blueprint
**Integrated Transport Management System for Fadilah Transportation**
Version 0.1 · 17 Oct 2026 · Source: Thesis Ch.1–3, Use Case Diagram, ERD

---

## 1. Overview & Goals

### 1.1 What is DilaDrive
A centralized platform that replaces WhatsApp + Google Calendar + Word/Excel/receipt-book with one system for booking management, driver scheduling, milestone tracking and automatic document generation for Fadilah Transportation (private chauffeur, Klang Valley).

### 1.2 Problems → Solutions (from Ch.1)
| # | Problem | Solution in system |
|---|---------|--------------------|
| a | Manual documentation (repeat data entry, static templates, local files) | Auto-generate Quotation / Invoice / Receipt from booking data, stored in DB |
| b | No centralized booking & customer records | Admin dashboard + calendar view of drivers/vehicles + booking DB |
| c | No real-time task monitoring of associate drivers | Driver app milestone updates with timestamps, shown on admin dashboard |

### 1.3 Goals
- G1. One source of truth for customers, bookings, trips, drivers, vehicles, payments, documents.
- G2. Zero double-booking: driver AND vehicle conflict detection before assignment.
- G3. Documents generated in < 5 seconds from booking data, consistent format, retrievable any time.
- G4. Owner sees trip progress without calling the driver.
- G5. Customer can book, track status and download documents anytime.

### 1.4 In scope / Out of scope
**In scope:** auth + roles, account mgmt, vehicle mgmt, booking lifecycle, fare calculation, availability check, driver/vehicle assignment & reassignment, milestone tracking, payment receipt upload + verification, document generation/download, notifications, dashboard + sales summary.
**Out of scope (per thesis scope):** online payment gateway, third-party GPS tracking apps, iOS build (Android only).

### 1.5 Success criteria (testable)
- Admin can complete full flow: create booking → verify → assign → trip completed → payment verified → invoice/receipt generated, with no manual re-typing.
- Assigning an overlapping driver/vehicle is blocked with a clear message.
- Driver milestone update appears on admin dashboard within ~5s (polling/refresh) .
- Customer can download own documents only.

---

## 2. Current System Analysis (As-Is)

### 2.1 As-Is flow
1. Customer finds Facebook / TripAdvisor → contacts owner (WhatsApp / email / call).
2. Owner replies with price, vehicle, availability → gives quotation.
3. Customer confirms → owner records details (name, phone, date, pickup time, pickup/destination, pax, luggage, flight no.).
4. Owner adds trip to Google Calendar.
5. Owner available? Yes → owner drives. No → assign trusted associate driver via WhatsApp.
6. Trip completed.
7. Corporate booking? Yes → Quotation (Word) → PO from client → Invoice (Excel) → Receipt. No → handwritten receipt book.
8. Payment received (cash / online transfer / card +6%).

### 2.2 Pain points → requirements
| Pain point | Requirement |
|------------|-------------|
| Same data typed 3–4 times | Single booking record feeds all documents |
| Data across WhatsApp / Calendar / laptop | Central DB + admin dashboard |
| Cannot see idle slots | Calendar with driver/vehicle availability |
| No buffer/reminder | Conflict detection with buffer time + notifications |
| Owner not told driver status | Milestone updates with timestamp |
| No driver performance/commission record | Trip history + status history per driver |
| Old documents hard to find | Document table, searchable by booking/customer |

### 2.3 Business data observed in real documents (must be supported)
- **Booking form fields:** name, phone, date, pickup time / time landed, pickup address, destination, no. of persons, no. of luggage, flight number.
- **Payment:** cash / online transfer / credit card (+6% surcharge) — settled after job.
- **Corporate:** Quotation no. (QCA0020), PO number (01413), Invoice no. (0017), supplier no., attention-to person, multi-trip & multi-day invoice, "Pager" / extra charge lines, SST field.
- **Receipt book:** receipt no. (1563), from/to, type (1 way / 2 way / limo / taxi / van), payment mode, driver name & ID.

> ⚠ Corporate invoices have **multiple line items per booking/trip**. Current ERD has no line-item structure → see §3.6 gaps.

---

## 3. Tech Stack & Architecture

### 3.1 Stack
| Layer | Technology |
|-------|-----------|
| IDE | VS Code (Java, Spring Boot, Flutter/Dart, ESLint extensions) |
| Backend | Java 21 (17 min) + Spring Boot 3.x, Maven, REST API |
| Admin web | React + Vite (TypeScript recommended) |
| Mobile | Flutter (Dart) – Android, customer + driver |
| Database | PostgreSQL 16 + pgAdmin |
| Diagrams | Draw.io |

### 3.2 Recommended libraries
**Backend:** Spring Web, Spring Data JPA, Spring Security + JWT (jjwt), Validation, Lombok, MapStruct, Flyway (DB migration), springdoc-openapi (Swagger UI), PostgreSQL driver, OpenPDF *or* Thymeleaf + openhtmltopdf (PDF from HTML template), Spring Mail (optional), Firebase Admin SDK (optional push).
**Admin web:** React Router, TanStack Query, Axios, React Hook Form + Zod, MUI (theme-able to maroon), FullCalendar (schedule view), Recharts (sales summary).
**Flutter:** Riverpod (state), dio (HTTP), go_router, flutter_secure_storage (JWT), freezed/json_serializable, image_picker + file_picker (receipt upload), url_launcher / open_filex (documents), firebase_messaging (optional push).

### 3.3 Architecture
```
 ┌──────────────────┐   ┌──────────────────────┐
 │ React Admin Web  │   │ Flutter App (Android) │
 │ (Admin)          │   │ Customer · Driver     │
 └────────┬─────────┘   └──────────┬───────────┘
          │  HTTPS / JSON (REST) + JWT Bearer
          ▼                        ▼
 ┌────────────────────────────────────────────┐
 │ Spring Boot API  /api/v1/...               │
 │  Controller → Service → Repository (JPA)   │
 │  Security (role-based) · Validation · PDF  │
 │  Scheduler (reminders) · File storage      │
 └───────────────┬────────────────────────────┘
                 ▼
     PostgreSQL (pgAdmin)      /uploads (receipts, PDFs)
```

### 3.4 Backend package structure (feature-based)
```
com.diladrive
 ├─ config/        security, cors, openapi
 ├─ common/        exceptions, response wrapper, utils
 ├─ auth/          login, register, jwt
 ├─ user/          user, customer, driver
 ├─ vehicle/
 ├─ booking/       booking, fare, availability
 ├─ trip/          trip, trip_status_history, assignment
 ├─ payment/
 ├─ document/      template, generator, storage
 ├─ notification/
 └─ dashboard/     stats, sales summary
```
Each feature: `controller / service / repository / entity / dto / mapper`.
Rule: controllers never touch repositories; entities never returned directly (use DTO).

### 3.5 Cross-cutting decisions
- **Auth:** JWT access token (short) + refresh token. Passwords hashed with BCrypt. Role in token.
- **API response:** `{ success, message, data, errors }`; errors via global `@ControllerAdvice`.
- **Time:** store UTC (`timestamptz`), display `Asia/Kuala_Lumpur`.
- **IDs:** use `bigint` identity (or UUID for public-facing booking ref + human booking code e.g. `DD-2610-0001`).
- **Real-time:** v1 = polling / pull-to-refresh (every 10–15s on active screens). Upgrade path: WebSocket/SSE or FCM push.
- **Files:** save to server folder, store relative path in DB (`receipt_file`, `document_file`); serve via authenticated endpoint only.
- **Environments:** `dev` (local PG) → `prod`. Config via `application-dev.yml` + env vars (never commit secrets).
- **Android emulator → local API:** use `http://10.0.2.2:8080`.

### 3.6 ERD review – gaps to fix BEFORE creating tables
Current ERD (USER, CUSTOMER, DRIVER, VEHICLE, BOOKING, TRIP, TRIP_STATUS_HISTORY, PAYMENT, DOCUMENT, NOTIFICATION) covers the core. Recommended additions:

| # | Gap | Suggested fix |
|---|-----|---------------|
| 1 | **No fare/amount** though use case "Calculate trip fare" exists | Add `BOOKING.total_fare`, `vehicle_type`, `trip_type` (1-way/2-way), `surcharge` ; add `FARE_RULE` table (route/zone/vehicle type → price) |
| 2 | Corporate invoice has many lines | Add `BOOKING_ITEM` (booking_id, description, amount) *or* allow multiple bookings per document via `DOCUMENT_BOOKING` |
| 3 | PO number / company info | Add to CUSTOMER: `company_name`, `supplier_no`, `contact_person`; add `BOOKING.po_number` |
| 4 | Document numbering (QCA0020, Invoice 0017, Receipt 1563) | Add `document_no` (unique, sequence per type) to DOCUMENT |
| 5 | Booking ↔ Trip shown as 1:1 | Keep 1:1 for v1; if 2-way/multi-leg later → 1:N |
| 6 | `vehicle_id` used in trip but vehicle type for booking | `vehicle_type` on booking is requested; actual `vehicle_id` assigned on trip |
| 7 | Payment only 0..1 per booking | OK for v1; add `amount`, `payment_method` (cash/transfer/card) to PAYMENT |
| 8 | Status fields are free `string` | Use PostgreSQL enum or `varchar` + Java enum + CHECK constraint |
| 9 | Notifications lack read state / link | Add `is_read`, `booking_id` (nullable) |
| 10 | Driver "associate" vs owner | Add `driver_type` (owner / associate) to DRIVER for commission reports |
| 11 | Reminder / buffer | Add `BOOKING.estimated_duration_min`; system setting `buffer_minutes` |
| 12 | Customer has `customer_type` | Define values: `INDIVIDUAL`, `CORPORATE` |

### 3.7 Core enums
```
Role:            ADMIN, DRIVER, CUSTOMER
AccountStatus:   PENDING, ACTIVE, SUSPENDED
CustomerType:    INDIVIDUAL, CORPORATE
VerifyStatus:    UNVERIFIED, VERIFIED, REJECTED
VehicleStatus:   AVAILABLE, IN_USE, MAINTENANCE, INACTIVE
DriverAvail:     AVAILABLE, ON_TRIP, OFF_DUTY
BookingStatus:   PENDING → CONFIRMED → ASSIGNED → IN_PROGRESS → COMPLETED
                 (+ REJECTED, CANCELLED)
TripStatus:      ASSIGNED → ARRIVED_PICKUP → PASSENGER_ONBOARD → EN_ROUTE → ARRIVED_DROPOFF → COMPLETED
                 (+ CANCELLED)
PaymentStatus:   UNPAID → RECEIPT_UPLOADED → VERIFIED (or REJECTED)
DocumentType:    QUOTATION, INVOICE, RECEIPT
NotificationType:BOOKING_VERIFIED, DRIVER_ASSIGNED, TRIP_UPDATE, PAYMENT_VERIFIED, DOCUMENT_READY, REMINDER
```
Milestones match the driver storyboard: *Arrived Pick-up → Passenger Onboard → En Route → Arrived Drop-off*.

---

## 4. Design System

### 4.1 Recommendation: **Maroon + Cream + White cards** (hybrid)
Your storyboard already uses a dark maroon sidebar with white content. Pure white feels sterile; pure cream reduces contrast for tables. Hybrid = brand identity + readability:
- Sidebar / app bars / primary buttons → **Maroon**
- Page background → **Warm cream**
- Cards, tables, forms → **White**

### 4.2 Color tokens
| Token | Hex | Use |
|-------|-----|-----|
| `primary-900` | `#3D0606` | Sidebar bg, pressed state |
| `primary-700` | `#5B0A0A` | Primary buttons, app bar, headings accent |
| `primary-500` | `#8B1A1A` | Hover, links, icons |
| `primary-100` | `#F3E3E3` | Selected row, subtle badge bg |
| `bg-cream` | `#FAF6F0` | Page background |
| `surface` | `#FFFFFF` | Cards, modals, inputs |
| `border` | `#E8E0D6` | Dividers, input borders |
| `accent-gold` | `#C9A24B` | Highlights, premium/chauffeur touch (sparingly) |
| `text-primary` | `#2B1B1B` | Body text |
| `text-muted` | `#7A6A6A` | Secondary text |
| `success` | `#2E7D32` | Completed, verified |
| `warning` | `#ED8B00` | Pending, receipt uploaded |
| `danger` | `#C62828` | Cancelled, rejected, conflict |
| `info` | `#1565C0` | In progress / assigned |

Contrast: white text on `primary-700` ≥ 9:1 ✔; body text on cream ≥ 12:1 ✔.

### 4.3 Typography & shape
- Font: **Poppins** (headings) + **Inter** (body/tables). Flutter: `google_fonts`.
- Scale: 12 / 14 (body) / 16 / 20 / 24 / 32.
- Radius: 8px inputs/buttons, 12px cards. Shadow: soft (`0 1px 3px rgba(61,6,6,.08)`).
- Spacing grid: 4 / 8 / 16 / 24 / 32.

### 4.4 Components (build once, reuse)
Button (primary/secondary/ghost/danger) · TextField · Select · DatePicker/TimePicker · StatusBadge (colour per enum) · DataTable (search, filter, pagination) · Card · Modal/Dialog · Stepper (milestones) · Calendar · Toast · EmptyState · Skeleton loader · FileUploader.

### 4.5 Status badge colours
`PENDING` warning · `CONFIRMED` info · `ASSIGNED` info · `IN_PROGRESS` primary-500 · `COMPLETED` success · `CANCELLED/REJECTED` danger · `RECEIPT_UPLOADED` warning · `VERIFIED` success.

### 4.6 Layout
- **Admin web:** fixed maroon sidebar (240px, collapsible) + top bar + cream content; responsive down to tablet.
- **Mobile:** bottom navigation (3–4 tabs), maroon app bar, large tap targets (≥ 48dp), driver screens optimized for one-hand use (big milestone button).
- Implement tokens once: MUI `createTheme` (web) and `ThemeData`/`ColorScheme` (Flutter).

---

## 5. Roles, Sitemap & Access Matrix

### 5.1 Roles
| Role | Platform | Who |
|------|----------|-----|
| **Admin** | React web | Owner (Mrs. Fadilah) / staff |
| **Driver** | Flutter Android | Associate drivers (and owner when driving) |
| **Customer** | Flutter Android | Individual & corporate clients |

Notes: Customer self-registers → account `PENDING` → Admin verifies (use case *Verify Customer Account*). Driver accounts are created by Admin only.

### 5.2 Sitemap

**Admin Web (matches storyboard sidebar)**
```
/login
/dashboard                      KPIs, today's trips, active fleet status, recent bookings
/bookings                       list, filter by status/date
   /bookings/new                create booking (fare + availability auto-check)
   /bookings/:id                detail, verify, update, cancel, assign, payment, docs, status timeline
/coordination                   Driver Coordination
   /coordination/calendar       driver & vehicle schedule (FullCalendar)
   /coordination/assign         assign / reassign driver + vehicle
   /coordination/live           live trip progress (milestones)
/documents                      Automated Document
   list · generate · view · update · download (quotation/invoice/receipt)
/customers                      Customer Record (list, verify, view/update, booking history)
/drivers                        driver accounts + license expiry + availability
/vehicles                       vehicle CRUD + road tax/service expiry alerts
/payments                       verify uploaded receipts
/reports                        sales summary, driver trip/commission summary
/settings                       fare rules, buffer time, company profile (document header), numbering
/profile
```

**Driver App**
```
Login → Dashboard (total trips, today's active trip, earnings*)
      → Schedule (view driver schedule – calendar/list)
      → Current Trip (client details + milestone stepper + update button)
      → Trip History
      → Profile
```
*Earnings optional – depends on commission rule (open question).

**Customer App**
```
Login / Register → Home (upcoming booking, quick book)
  → New Booking (form: pickup, destination, datetime, pax, luggage, flight no. → fare preview)
  → My Bookings (status, detail, update, cancel, trip progress tracker)
  → Payments (upload receipt, status)
  → Documents (download quotation / invoice / receipt)
  → Notifications
  → Profile
```

### 5.3 Access matrix (from use case diagram)
✔ = allowed · ◐ = own records only · ✖ = no

| Function | Admin | Driver | Customer |
|----------|:----:|:-----:|:-------:|
| Create account | ✔ | ✖ | ✔ (self-register) |
| View account | ✔ | ◐ | ◐ |
| Update account | ✔ | ◐ | ◐ |
| Verify customer account | ✔ | ✖ | ✖ |
| Create / view / update vehicle | ✔ | ✖ | ✖ |
| Create booking | ✔ | ✖ | ✔ |
| View booking | ✔ | ◐ (assigned) | ◐ |
| Update booking | ✔ | ✖ | ◐ (before confirmed) |
| Cancel booking | ✔ | ✖ | ◐ |
| Verify booking (→ send notification) | ✔ | ✖ | ✖ |
| Assign driver & vehicle | ✔ | ✖ | ✖ |
| Reassign trip | ✔ | ✖ | ✖ |
| View driver schedule | ✔ | ◐ | ✖ |
| View assigned trip | ✔ | ◐ | ✖ |
| Update trip status / milestones | ✔ (override) | ◐ | ✖ |
| View trip progress | ✔ | ◐ | ◐ (read-only) |
| Upload payment receipt | ✖ | ✖ | ◐ |
| Verify payment status | ✔ | ✖ | ✖ |
| Generate document | ✔ | ✖ | ✖ |
| View document | ✔ | ✖ | ◐ |
| Update document | ✔ | ✖ | ✖ |
| Download document | ✔ | ✖ | ◐ |
| Dashboard / reports | ✔ | ✖ | ✖ |

> Interpretation notes: Use case diagram shows Driver linked to *View Account*; Customer to *Create/View/Update Account*. Admin override on milestones and customer progress view are my additions to match thesis scope ("customers can track trip details") — confirm.

### 5.4 Enforcement
- Backend: `@PreAuthorize("hasRole('ADMIN')")` on every endpoint + ownership check in service (`booking.customerId == currentUser.customerId`).
- Frontend: route guards + hide UI actions — **UI hiding is convenience only, backend is the real gate.**

---

## 6. Core Workflows

### 6.1 Account onboarding
1. Customer registers (app) → `account_status = PENDING`.
2. Admin reviews → *Verify Customer Account* → `ACTIVE`, `verification_status = VERIFIED`.
3. Admin creates driver accounts (user + driver profile with licence info).

### 6.2 Booking → Completion (main flow)
```
Customer/Admin creates booking
   ├─ include: Calculate trip fare (fare rule + vehicle type + surcharge)
   └─ include: Check resource & capacity availability (pax ≤ seats, luggage ≤ capacity, ≥1 free vehicle & driver)
        ↓ status = PENDING
Admin verifies booking  ──► REJECTED (reason) → notify customer
        ↓ CONFIRMED  (include: Send notification)
Admin generates QUOTATION (corporate) → customer PO (record PO no.)
        ↓
Admin assigns driver + vehicle
   └─ include: Check driver availability (no overlap incl. buffer, licence valid, vehicle not in maintenance)
        ↓ booking ASSIGNED, trip created (ASSIGNED) → notify driver + customer
Day before: reminder to driver & customer (scheduled job)
        ↓
Driver updates milestones (each writes TRIP_STATUS_HISTORY + timestamp):
   ARRIVED_PICKUP → PASSENGER_ONBOARD → EN_ROUTE → ARRIVED_DROPOFF → COMPLETED
        ↓ booking COMPLETED, driver/vehicle freed
Payment (see 6.4) → Invoice / Receipt generated → customer downloads
```

### 6.3 Conflict detection & reassignment
```
isDriverFree(driver, start, end) =
   NOT EXISTS trip t of driver, t.status ∉ {CANCELLED, COMPLETED},
       t.start − buffer < end  AND  t.end + buffer > start
```
Same check for vehicle. `end = pickup_datetime + estimated_duration`.
- Driver emergency / unavailable → Admin **Reassign Trip**: pick another driver/vehicle (re-run checks), log previous assignment, notify old driver, new driver, customer.
- Vehicle MAINTENANCE or expired road tax/service → excluded from assignment list.
- Use a DB transaction + row lock on assignment to prevent two admins double-assigning.

### 6.4 Payment (no online gateway)
1. Customer pays by bank transfer / cash / card (+6%) outside system.
2. Customer uploads receipt image/PDF → `PAYMENT = RECEIPT_UPLOADED`.
3. Admin *Verify Payment Status* → `VERIFIED` (or `REJECTED` + reason → customer re-uploads).
4. On verified → generate **Official Receipt**; notify customer.
Cash on completion: admin/driver marks received → admin verifies → receipt.

### 6.5 Document generation (static template)
- Types: **Quotation, Invoice, Receipt** (PO is customer-issued; store PO number/file).
- Flow: Admin clicks *Generate* on booking (or multi-booking for corporate) → backend loads booking + customer + fare lines → fills HTML template (company header, supplier no., numbering) → renders PDF → saves file + DOCUMENT row → returns link.
- *Update Document* = edit data (e.g., extra charge line) and regenerate; keep old version or overwrite (decision).
- Customer sees only own documents; download endpoint checks ownership.
- Numbering: separate sequence per type, e.g. `QCA0021`, `INV-0018`, `RCP-1564`.

### 6.6 Cancellation
- Customer may cancel before trip start (policy: cut-off hours — open question). Admin can cancel anytime.
- Cancelling releases driver/vehicle, closes trip as CANCELLED, notifies all parties.

### 6.7 Notifications (v1)
In-app list + (optional) FCM push. Triggers: booking verified/rejected, driver assigned/reassigned, each milestone (to customer), payment verified/rejected, document ready, day-before reminder.

---

## 7. Open Questions (need your decision)
1. **Fare calculation** – fixed price table per route/zone/vehicle type set by owner (recommended, no GPS needed), or distance-based? What are the owner's real price rules?
2. **Customer self-booking** – does a customer booking become binding on submit, or always `PENDING` until admin confirms? (Recommended: always pending.)
3. **Driver earnings/commission** – show on driver dashboard? What formula?
4. **One Flutter app (role-based login) vs two separate apps?** Recommended: **one app**, role decides home screen (less work, shared auth/theme).
5. **Multi-leg / 2-way trips & multi-day corporate invoices** – model as multiple bookings merged into one invoice (recommended) or multi-leg trip?
6. **Cancellation policy / cut-off time** and buffer minutes between jobs (default suggestion: 30 min).
7. **Push notifications (FCM)** in scope for v1, or in-app only?
8. **Language** of UI: English only, or English + Bahasa Melayu?
9. **Document branding** – confirm company registration no. (TR0242559-U), bank details, logo to place in templates.
10. **Hosting** for demo/deployment (local only for FYP, or cloud VPS)?

---

## 8. Suggested Build Order (after blueprint approved)
1. Repo setup (monorepo: `/backend`, `/admin-web`, `/mobile`, `/docs`) + git.
2. DB: finalize ERD → Flyway migrations → seed (admin, sample driver/vehicle/fare rules).
3. Backend: auth + roles → user/vehicle → booking (+fare, availability) → assignment/conflict → trip milestones → payment → documents → notifications → dashboard.
4. Admin web: theme + layout + login → bookings → coordination/calendar → documents → customers/drivers/vehicles → dashboard.
5. Flutter: theme + auth → driver flow (trip + milestones) → customer flow (booking, payment, documents).
6. Integration testing with real sample data (Cambridge booking example), UAT with owner, fix, document (SRS/SDD alignment).
