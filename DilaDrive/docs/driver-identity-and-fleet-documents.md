# Driver identity and fleet documents

## Data protection and access

- Driver IC numbers are normalized, encrypted with AES-GCM, and searchable only
  through a keyed HMAC lookup value. They are masked in admin responses.
- Set `DRIVER_DATA_MASTER_KEY` to a Base64-encoded 32-byte secret shared by the
  API replicas. Existing nonblank plaintext IC values require this key for the
  startup migration; do not rotate it without a planned re-encryption.
- Driver IC, licence and vehicle road-tax uploads accept validated JPG, PNG or
  PDF files up to 5 MB. Files are encrypted outside static resources. Preview
  requests require an authenticated Admin session and are audited.
- Replacing a document retains its metadata and audit history, marks it
  superseded, and removes the old encrypted file after the replacement
  transaction commits.
- Existing driver and vehicle records remain accessible and editable. The
  migration does not deactivate accounts or invent missing expiry dates.

## Fleet rules

Vehicle category limits are stored in `vehicle_category` and cap the recorded
passenger capacity: Sedan 4, MPV 5, 10-Seater Van 8, 14-Seater Van 11, and
18-Seater Van 15.

Customer trip availability/quotes and assignment previews require driver
licence and vehicle road-tax dates to cover the complete scheduled trip,
through its scheduled end. All final assignment, confirmation and reassignment
operations repeat the check. The legacy address-only vehicle recommendation
can screen against the pickup date because it has no route duration; final
assignment still checks the scheduled end. Missing legacy expiry dates do not
change account status; records should be completed by an Admin. The service
expiry date is used for reminders and does not by itself block a booking.

## Expiry reminders

The daily scheduler runs at 08:00 Asia/Kuala_Lumpur by default and creates
notifications for active Admin accounts:

- Driver licence and vehicle road tax: 30, 14 and 7 days before expiry.
- Vehicle service: 14 and 7 days before the recorded service date.

Override the thresholds with `DRIVER_DOCUMENT_EXPIRY_REMINDER_DAYS` and
`VEHICLE_SERVICE_REMINDER_DAYS`; override the schedule with
`DRIVER_DOCUMENT_REMINDER_CRON`. The `fleet_expiry_reminder` key includes owner,
reminder type, due date and threshold so a scheduler retry cannot duplicate the
same alert.

## Schema and ERD

For existing databases, apply `033_driver_identity_documents.sql`, then
`034_fleet_expiry_reminders.sql`, before deploying the API. New databases use
`BASELINE_CURRENT.sql`. The current relationships are shown in
`documentation/DilaDrive_ERD.drawio` and the domain view in
`docs/DilaDrive-Domain-Class-Diagram.drawio`.
