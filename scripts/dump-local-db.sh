#!/bin/bash

# Script to dump local database data
# This will create a SQL dump file with all data from your local database

set -e

# Local database configuration
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="eme_cache"
DB_USER="eme_user"
DB_PASSWORD="eme_password"

# Output file
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_FILE="./backups/eme_local_dump_${TIMESTAMP}.sql"

# Create backups directory if it doesn't exist
mkdir -p ./backups

echo "Dumping local database to ${OUTPUT_FILE}..."

# Use pg_dump to export data
# --data-only: dump only data, not schema (since Flyway will handle schema)
# --column-inserts: use column names in INSERT commands for better compatibility
# --disable-triggers: disable triggers during restore
PGPASSWORD="${DB_PASSWORD}" pg_dump \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --data-only \
  --column-inserts \
  --disable-triggers \
  -f "${OUTPUT_FILE}"

echo "Database dump completed successfully!"
echo "File saved to: ${OUTPUT_FILE}"
echo ""
echo "Next steps:"
echo "1. Set up your remote database credentials in application-remote.properties"
echo "2. Run Flyway migrations on remote: ./gradlew flywayMigrate -Dspring.profiles.active=remote"
echo "3. Import data to remote: ./scripts/restore-to-remote.sh ${OUTPUT_FILE}"
