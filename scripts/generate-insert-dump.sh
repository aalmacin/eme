#!/bin/bash

# Script to generate a SQL dump with INSERT statements
# This creates a human-readable SQL file with all your current data

set -e

# Local database configuration
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="eme_cache"
DB_USER="eme_user"
DB_PASSWORD="eme_password"

# Output file
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_FILE="./backups/eme_inserts_${TIMESTAMP}.sql"

# Create backups directory if it doesn't exist
mkdir -p ./backups

echo "Generating SQL dump with INSERT statements..."
echo "Output file: ${OUTPUT_FILE}"

# Use pg_dump to export data with INSERT statements
# --data-only: dump only data, not schema
# --column-inserts: use INSERT INTO table (col1, col2) VALUES (val1, val2) format
# --rows-per-insert=1: one row per INSERT statement (more readable)

# Use Docker with PostgreSQL 15 to match server version
docker run --rm \
  -e PGPASSWORD="${DB_PASSWORD}" \
  --network host \
  postgres:15 \
  pg_dump \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --data-only \
  --column-inserts \
  --rows-per-insert=1 \
  > "${OUTPUT_FILE}"

echo ""
echo "✓ SQL dump generated successfully!"
echo "✓ File: ${OUTPUT_FILE}"
echo ""
echo "The file contains INSERT statements for all records in your database."
echo "You can:"
echo "  - Review it in a text editor"
echo "  - Use it to restore data: psql -h HOST -U USER -d DATABASE -f ${OUTPUT_FILE}"
echo "  - Commit specific inserts to version control if needed"
