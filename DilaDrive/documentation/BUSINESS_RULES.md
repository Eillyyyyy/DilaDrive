# DilaDrive business rules

## Report context

ReportElla(1).pdf is a reference for requirements and stakeholder evidence. Chapter 1 describes scattered bookings, repetitive document entry and manual driver follow-up. Chapter 2 describes static templates, interval conflict detection and milestone-based tracking. Chapter 3 describes requirements, design and implementation deliverables. The pasted refinement request governs this implementation. The PDF describes a client-provided PO in Chapter 1, alongside different statements about when invoices are issued in later material.

## Accounts, booking and schedule

- Super Admin creates Admin accounts, Admin registers drivers and linked vehicles, and customers register independently. Only verified customers can submit bookings. Normal and corporate accounts use existing roles and registration fields. The Driver editor no longer requests IC information, and driver create/update no longer persist incoming IC values. Historical fields remain stored.
- The customer chooses a category after passenger and bag sizing. Existing fleet capacity, document expiry and routing checks apply. The signed estimated fare is validated before submission.
- Submission creates a pending booking and a temporary hold. Admin confirms or rejects and can select an eligible replacement. Existing locks serialize concurrent resource changes. The Malaysia wall-clock overlap predicate is requestedStart < existingEnd and requestedEnd > existingStart. The existing 30-minute buffer is configurable and remains an initial policy. Emergency reassignment before pickup remains available under existing authorization.
- The Admin calendar reads existing bookings and assignments. Account and vehicle badges indicate operational state. Period management remains a separate gap. Availability reads stored maintenance and driver blackout periods.
- Booking edits through the customer flow remain limited to pending requests. Changes to confirmed trip details need a stakeholder-approved amendment workflow with availability, document and pricing checks.

## Trip and incidents

- The driver advances one step at a time through Assigned, OTW, Waiting, Pickup, Drop and Completed. Admin completion is available at Drop and uses the same transition validation. Payment and payment verification are independent of trip completion. Each real transition writes an event in the assignment transaction. A retry of the current milestone reuses its timestamp. Reassignment and cancellation are recorded. Older assignments have no fabricated timeline entries.
- The assigned driver can submit an incident description against their allocation, including after completion. An Admin inbox notification is persisted with the report. Admin reads recent incidents and the original assigned driver remains traceable through the assignment. The driver cannot report for another driver's assignment. Incident attachments and resolution states are outside this delivery.

## Documents and finance

- Quotation, invoice and receipt PDFs use static templates and recorded customer, booking and payment data. Existing prices are preserved.
- Admin can record a received client PO reference from an approved quotation. The reference is retained in the document record. The app does not perform an upload in this flow. The existing API compatibility operation is retained. PO timing enforcement and a single invoice timing rule require stakeholder approval because the report and current behavior differ.
- The operational dashboard shows verified payment totals and outstanding balances on issued invoices. Payments keep their historical values. Payments are verified manually.
- Firebase pushes use existing post-commit delivery. Delivery to a physical device requires project credentials and device registration. The persisted inbox remains the record of notifications.

The [temporary Customer workflow](CUSTOMER_BOOKING_WORKFLOW.md) excludes payment and receipt screens while keeping Corporate quotations and invoices accessible.
