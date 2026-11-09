# DilaDrive Spring Boot API

Run with Java 17+, Maven and PostgreSQL configured in
`src/main/resources/application.properties`:
```bash
mvn spring-boot:run
```

The mobile app authenticates existing customer and driver accounts with:
```http
POST http://localhost:8080/api/auth/mobile/login
Content-Type: application/json

{"email":"user@example.com","password":"...","role":"CUSTOMER"}
```

Use `"DRIVER"` for driver accounts. Passwords created through the customer and
driver APIs are stored as BCrypt hashes; legacy plain-text passwords are upgraded
to BCrypt after a successful login.

Customer bookings are created as `PENDING_VERIFICATION` and are not assigned a
driver or vehicle until an admin verifies them:
```http
POST http://localhost:8080/api/bookings
Content-Type: application/json

{
  "custId": 1,
  "bookingTripdate": "2026-11-20",
  "bookingPickuptime": "09:30:00",
  "bookingPickupaddress": "Pickup address",
  "bookingDestinationaddress": "Destination address",
  "bookingType": "Sedan",
  "bookingTripfare": 80.00
}
```

Check driver and vehicle availability with
`GET /api/bookings/availability?vehicleType=Sedan&tripDate=2026-11-20&pickupTime=09:30:00`.
Admin verification uses `PUT /api/bookings/{id}/verify`; it assigns an available
matching vehicle and driver and changes the status to `CONFIRMED` only when both
are available. Driver and vehicle availability is evaluated for the requested
pickup date and time. Passenger count is optional because the customer booking
form currently does not collect it; when present, it is checked against vehicle
capacity.
