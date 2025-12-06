#!/bin/bash

# Script to restore database dump to remote database
# Usage: ./scripts/restore-to-remote.sh <dump-file>

set -e

if [ $# -eq 0 ]; then
    echo "Usage: $0 <dump-file>"
    echo "Example: $0 ./backups/eme_local_dump_20231201_120000.sql"
    exit 1
fi

DUMP_FILE="$1"

if [ ! -f "$DUMP_FILE" ]; then
    echo "Error: Dump file not found: $DUMP_FILE"
    exit 1
fi

# Remote database configuration from environment variables
DB_HOST="${REMOTE_DB_HOST}"
DB_PORT="${REMOTE_DB_PORT:-5432}"
DB_NAME="${REMOTE_DB_NAME}"
DB_USER="${REMOTE_DB_USER}"
DB_PASSWORD="${REMOTE_DB_PASSWORD}"

# Validate required environment variables
if [ -z "$DB_HOST" ] || [ -z "$DB_NAME" ] || [ -z "$DB_USER" ] || [ -z "$DB_PASSWORD" ]; then
    echo "Error: Missing required environment variables"
    echo "Please set the following variables:"
    echo "  REMOTE_DB_HOST"
    echo "  REMOTE_DB_NAME"
    echo "  REMOTE_DB_USER"
    echo "  REMOTE_DB_PASSWORD"
    echo "  REMOTE_DB_PORT (optional, defaults to 5432)"
    exit 1
fi

echo "Restoring database from ${DUMP_FILE} to remote database..."
echo "Remote Host: ${DB_HOST}"
echo "Remote Database: ${DB_NAME}"
echo ""
read -p "Continue with restore? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Restore cancelled"
    exit 0
fi

# Restore data to remote database
PGPASSWORD="${DB_PASSWORD}" psql \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  -f "${DUMP_FILE}"

echo "Database restore completed successfully!"
