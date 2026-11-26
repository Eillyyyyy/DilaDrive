\set ON_ERROR_STOP on
\getenv seed_hash DILADRIVE_DEV_SEED_HASH
SELECT current_database() = 'DilaDrive'
       AND inet_server_addr() IN ('127.0.0.1'::inet, '::1'::inet)
       AND inet_server_port() = 5432
       AND :'seed_hash' ~ '^\$2[aby]\$1[0-9]\$[./A-Za-z0-9]{53}$' AS verified_dev \gset
\if :verified_dev
BEGIN;
INSERT INTO admin (admin_name, admin_email, admin_password, admin_role, admin_status)
SELECT 'DEV-SEED Admin','hardening-admin@example.invalid',:'seed_hash','ADMIN','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM admin WHERE admin_email='hardening-admin@example.invalid');
UPDATE admin SET admin_password=:'seed_hash'
WHERE admin_email='hardening-admin@example.invalid' AND admin_name='DEV-SEED Admin';

INSERT INTO customer (cust_type,cust_name,cust_email,cust_password,cust_phoneno,cust_status,
                      cust_companyname,cust_companyaddress,cust_contactperson,company_registration_number)
VALUES ('NORMAL','DEV-SEED Customer','hardening-normal@example.invalid',:'seed_hash','0123456789','ACTIVE',NULL,NULL,NULL,NULL),
       ('COMPANY','DEV-SEED Company','hardening-company@example.invalid',:'seed_hash','0123456789','ACTIVE',
        'DEV-SEED Company','Fixture Address','Fixture Contact','FIXTURE-ONLY'),
       ('NORMAL','DEV-SEED Pending','hardening-pending@example.invalid',:'seed_hash','0123456789','PENDING',NULL,NULL,NULL,NULL)
ON CONFLICT (cust_email) DO UPDATE SET cust_password=EXCLUDED.cust_password WHERE customer.cust_name=EXCLUDED.cust_name;

INSERT INTO driver (driver_name,driver_email,driver_password,driver_status,driver_availability)
VALUES ('DEV-SEED Driver One','hardening-driver1@example.invalid',:'seed_hash','ACTIVE','AVAILABLE'),
       ('DEV-SEED Driver Two','hardening-driver2@example.invalid',:'seed_hash','ACTIVE','AVAILABLE')
ON CONFLICT (driver_email) DO UPDATE SET driver_password=EXCLUDED.driver_password WHERE driver.driver_name=EXCLUDED.driver_name;

INSERT INTO vehicle (driver_id,vehicle_category_id,vehicle_ownership,vehicle_model,vehicle_plateno,
                     vehicle_type,vehicle_capacity,vehicle_status,vehicle_luggagecapacity)
SELECT d.driver_id,c.vehicle_category_id,'COMPANY_OWNED','DEV-SEED Sedan',v.plate,'Sedan',4,'AVAILABLE',4
FROM (VALUES ('hardening-driver1@example.invalid','FIXTURE1'),('hardening-driver2@example.invalid','FIXTURE2')) v(email,plate)
JOIN driver d ON d.driver_email=v.email
JOIN vehicle_category c ON c.category_key='SEDAN'
WHERE NOT EXISTS (SELECT 1 FROM vehicle x WHERE x.driver_id=d.driver_id);
WITH fixtures AS (
    SELECT c.cust_id,d.driver_id,v.vehicle_id,t.label,t.offset_days
    FROM (VALUES ('normal',1,'DEV-SEED Pickup A',20),('company',2,'DEV-SEED Pickup B',21)) t(customer,driver,label,offset_days)
    JOIN customer c ON c.cust_email='hardening-'||t.customer||'@example.invalid'
    JOIN driver d ON d.driver_email='hardening-driver'||t.driver||'@example.invalid'
    JOIN vehicle v ON v.driver_id=d.driver_id
), created AS (
    INSERT INTO booking (cust_id,driver_id,vehicle_id,booking_status,booking_type,booking_triptype,
                         booking_pickupaddress,booking_destinationaddress,booking_tripdate,booking_pickuptime,
                         booking_passengercount,booking_tripfare)
    SELECT cust_id,driver_id,vehicle_id,'CONFIRMED','Sedan','NORMAL_TRANSFER',
           label,'DEV-SEED Destination',CURRENT_DATE+offset_days,TIME '12:00',1,100
    FROM fixtures
    WHERE NOT EXISTS (SELECT 1 FROM booking b WHERE b.cust_id=fixtures.cust_id AND b.booking_pickupaddress=fixtures.label)
    RETURNING booking_id,driver_id,vehicle_id,booking_tripdate,booking_pickuptime
)
INSERT INTO driver_assignment(booking_id,driver_id,vehicle_id,assignment_status,assignment_date,scheduled_end_at)
SELECT booking_id,driver_id,vehicle_id,'ASSIGNED',LOCALTIMESTAMP,booking_tripdate+booking_pickuptime+interval '2 hours' FROM created;
INSERT INTO booking(cust_id,booking_status,booking_type,booking_triptype,booking_pickupaddress,
                    booking_destinationaddress,booking_tripdate,booking_pickuptime,booking_passengercount,booking_tripfare)
SELECT c.cust_id,'PENDING','Sedan','NORMAL_TRANSFER','DEV-SEED Pending Pickup','DEV-SEED Destination',
       CURRENT_DATE+22,TIME '12:00',1,100
FROM customer c WHERE c.cust_email='hardening-normal@example.invalid'
AND NOT EXISTS (SELECT 1 FROM booking b WHERE b.cust_id=c.cust_id AND b.booking_pickupaddress='DEV-SEED Pending Pickup');
COMMIT;
\else
\echo 'Dev fixture refused.'
SELECT 1/0;
\endif
