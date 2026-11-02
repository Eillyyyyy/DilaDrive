-- Reference data. Fare prices are PLACEHOLDERS until Mrs. Fadilah provides the real price list.

INSERT INTO system_settings (setting_key, setting_value, description) VALUES
 ('buffer_minutes',         '30',          'Minimum gap between two jobs for the same driver/vehicle'),
 ('cancel_cutoff_hours',    '24',          'Customer can self-cancel up to this many hours before pickup'),
 ('card_surcharge_percent', '6',           'Surcharge added when paying by credit card'),
 ('two_way_multiplier',     '2',           'Fare multiplier for two-way trips'),
 ('sst_percent',            '0',           'SST percentage applied on documents'),
 ('company_name',           'Fadilah Transportion', 'Company name printed on documents'),
 ('company_reg_no',         'TR0242559-U', 'Company registration number'),
 ('company_address',        '122, Lorong Balai Raya, Batu 4 Jalan Gombak, Wilayah Persekutuan 53000', 'Address printed on documents'),
 ('company_phone',          '0196668073',  'Contact phone printed on documents'),
 ('company_email',          'dila3602@gmail.com', 'Contact email printed on documents'),
 ('bank_account',           'TO BE CONFIRMED', 'Bank details printed on invoices');

INSERT INTO zones (name) VALUES
 ('KLIA 1'), ('KLIA 2'), ('KL Sentral'), ('Kuala Lumpur City'), ('Bandar Utama'),
 ('Bandar Sri Damansara'), ('Gombak'), ('Putrajaya'), ('Cyberjaya'), ('Genting Highlands'), ('Other');

INSERT INTO document_sequences (document_type, prefix, last_number) VALUES
 ('QUOTATION', 'QTN-', 0),
 ('INVOICE',   'INV-', 0),
 ('RECEIPT',   'RCP-', 0);

-- Placeholder per-km rules (one per vehicle type)
INSERT INTO fare_rules (rule_type, vehicle_type, base_fare, rate_per_km, min_fare) VALUES
 ('PER_KM', 'SEDAN', 20.00, 2.00,  50.00),
 ('PER_KM', 'MPV',   25.00, 2.50,  70.00),
 ('PER_KM', 'VAN',   30.00, 3.00,  90.00),
 ('PER_KM', 'LIMO',  40.00, 4.00, 120.00);

-- Placeholder fixed routes (values taken from the sample quotation: 130 / 180 / 200)
INSERT INTO fare_rules (rule_type, vehicle_type, origin_zone_id, destination_zone_id, fixed_price)
SELECT 'FIXED_ROUTE', 'SEDAN', o.zone_id, d.zone_id, 180.00
FROM zones o, zones d WHERE o.name = 'KLIA 2' AND d.name = 'Bandar Sri Damansara';
INSERT INTO fare_rules (rule_type, vehicle_type, origin_zone_id, destination_zone_id, fixed_price)
SELECT 'FIXED_ROUTE', 'SEDAN', o.zone_id, d.zone_id, 130.00
FROM zones o, zones d WHERE o.name = 'Bandar Utama' AND d.name = 'Bandar Sri Damansara';
