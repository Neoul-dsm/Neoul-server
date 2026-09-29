-- MySQL 8: remove the previous unique name index without assuming its generated name.
-- Different beaches may have the same name. Keep other unique indexes intact.
SET @beach_index_drops = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', REPLACE(index_name, '`', '``'), '`') SEPARATOR ', ')
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'beaches'
          AND non_unique = 0 AND index_name <> 'PRIMARY'
        GROUP BY index_name
        HAVING COUNT(*) = 1 AND MAX(column_name) = 'name'
    ) AS indexes_to_drop
);
SET @beach_index_sql = IF(@beach_index_drops IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE beaches ', @beach_index_drops));
PREPARE beach_index_statement FROM @beach_index_sql;
EXECUTE beach_index_statement;
DEALLOCATE PREPARE beach_index_statement;
