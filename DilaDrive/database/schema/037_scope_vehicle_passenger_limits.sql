-- Update only the five categories named in the master scope.
BEGIN;

UPDATE vehicle_category AS category
SET max_passenger_capacity = scope.passengers
FROM (VALUES ('SEDAN', 3), ('MPV', 5), ('VAN_10_SEATER', 8),
             ('VAN_14_SEATER', 11), ('VAN_18_SEATER', 15)) AS scope(key, passengers)
WHERE category.category_key = scope.key
  AND category.max_passenger_capacity IS DISTINCT FROM scope.passengers;

COMMIT;
