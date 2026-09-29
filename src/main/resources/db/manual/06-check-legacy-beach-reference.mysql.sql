-- Read-only checks for the legacy users.beach_id reference.
-- Run against the application database and review the results before cleanup.
-- Do not run 01-erd-alignment.sql when beach and beaches already coexist.

-- 1. Confirm the remaining foreign key and whether the old column is nullable.
SELECT
    k.CONSTRAINT_NAME,
    k.COLUMN_NAME,
    k.REFERENCED_TABLE_NAME,
    k.REFERENCED_COLUMN_NAME,
    c.IS_NULLABLE
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA = k.TABLE_SCHEMA
 AND c.TABLE_NAME = k.TABLE_NAME
 AND c.COLUMN_NAME = k.COLUMN_NAME
WHERE k.TABLE_SCHEMA = DATABASE()
  AND k.TABLE_NAME = 'users'
  AND k.COLUMN_NAME = 'beach_id';

-- 2. Compare each old assignment with the user's current registrations.
-- Names alone do not prove that two beach records represent the same place.
SELECT
    u.id AS user_id,
    u.beach_id AS old_beach_id,
    old_beach.name AS old_beach_name,
    sb.beach_id AS registered_beach_id,
    current_beach.name AS registered_beach_name
FROM users u
LEFT JOIN beach old_beach ON old_beach.id = u.beach_id
LEFT JOIN star_beaches sb ON sb.user_id = u.id
LEFT JOIN beaches current_beach ON current_beach.id = sb.beach_id
WHERE u.beach_id IS NOT NULL
ORDER BY u.id, sb.beach_id;

-- 3. Find any other tables that still reference the old beach table.
SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_NAME = 'beach';

-- After the old assignments have been verified or migrated, the reported
-- constraint can be removed separately. Keep this disabled until then.
-- ALTER TABLE users DROP FOREIGN KEY FKqyy65blh72dts4p3pqovdxgo1;
-- Dropping the foreign key does not migrate registrations or remove beach_id.
