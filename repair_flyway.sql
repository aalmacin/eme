-- Repair Flyway Schema History
-- This script removes the failed V5 migration record so we can retry it

-- Connect to your database and run this:
-- psql -h localhost -p 5432 -U eme_user -d eme_cache -f repair_flyway.sql
-- Or use any PostgreSQL client (pgAdmin, DBeaver, etc.)

DELETE FROM flyway_schema_history
WHERE version = '5' AND success = false;

-- Verify the deletion
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
