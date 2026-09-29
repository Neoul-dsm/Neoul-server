-- MySQL 8: remove only the three indexes formerly declared by the entities.
-- Run after schema alignment against the selected application database.
-- No table data, primary keys, unique constraints or foreign keys are removed.
-- An unnamed single-column index is added only when needed to support an
-- existing ship foreign key before removing its old composite index.
-- https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html

SET @custom_index_exists = EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'human_log'
      AND index_name = 'idx_person_log_ship_id' AND non_unique = 1
);
SET @ship_fk_needs_index = EXISTS (
    SELECT 1 FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE() AND table_name = 'human_log'
      AND column_name = 'ship_id' AND referenced_table_name IS NOT NULL
) AND NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'human_log'
      AND index_name <> 'idx_person_log_ship_id'
      AND seq_in_index = 1 AND column_name = 'ship_id' AND is_visible = 'YES'
);
SET @remove_index_sql = IF(@custom_index_exists,
    CONCAT('ALTER TABLE human_log ',
        IF(@ship_fk_needs_index, 'ADD INDEX (ship_id), ', ''),
        'DROP INDEX idx_person_log_ship_id'),
    'SELECT 1');
PREPARE remove_index_statement FROM @remove_index_sql;
EXECUTE remove_index_statement;
DEALLOCATE PREPARE remove_index_statement;

SET @custom_index_exists = EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'marine_life_log'
      AND index_name = 'idx_marine_log_ship_id' AND non_unique = 1
);
SET @ship_fk_needs_index = EXISTS (
    SELECT 1 FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE() AND table_name = 'marine_life_log'
      AND column_name = 'ship_id' AND referenced_table_name IS NOT NULL
) AND NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'marine_life_log'
      AND index_name <> 'idx_marine_log_ship_id'
      AND seq_in_index = 1 AND column_name = 'ship_id' AND is_visible = 'YES'
);
SET @remove_index_sql = IF(@custom_index_exists,
    CONCAT('ALTER TABLE marine_life_log ',
        IF(@ship_fk_needs_index, 'ADD INDEX (ship_id), ', ''),
        'DROP INDEX idx_marine_log_ship_id'),
    'SELECT 1');
PREPARE remove_index_statement FROM @remove_index_sql;
EXECUTE remove_index_statement;
DEALLOCATE PREPARE remove_index_statement;

SET @remove_index_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'revoked_access_tokens'
      AND index_name = 'idx_revoked_token_expiry' AND non_unique = 1
), 'ALTER TABLE revoked_access_tokens DROP INDEX idx_revoked_token_expiry', 'SELECT 1');
PREPARE remove_index_statement FROM @remove_index_sql;
EXECUTE remove_index_statement;
DEALLOCATE PREPARE remove_index_statement;

