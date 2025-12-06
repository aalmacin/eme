# Database Migration Guide

This guide explains how to migrate your local PostgreSQL database to a remote database.

## Prerequisites

- PostgreSQL client tools installed (`pg_dump`, `psql`)
- Access to remote database with credentials
- Remote database should be empty or freshly created

## Migration Steps

### 1. Prepare Remote Database

Ensure your remote PostgreSQL database is created and accessible:

```bash
# Test connection to remote database
psql -h YOUR_REMOTE_HOST -U YOUR_REMOTE_USER -d YOUR_REMOTE_DB -c "SELECT version();"
```

### 2. Configure Remote Database Credentials

Create a `.env.remote` file from the example:

```bash
cp .env.remote.example .env.remote
```

Edit `.env.remote` with your actual remote database credentials:

```bash
export REMOTE_DB_HOST=your-remote-host.example.com
export REMOTE_DB_PORT=5432
export REMOTE_DB_NAME=eme_cache
export REMOTE_DB_USER=your_remote_user
export REMOTE_DB_PASSWORD=your_remote_password
```

### 3. Run Flyway Migrations on Remote Database

First, load your remote environment variables and run migrations:

```bash
# Load remote database environment
source .env.remote

# Run Flyway migrations on remote database
SPRING_PROFILES_ACTIVE=remote ./gradlew flywayMigrate
```

This will create all tables and schema in your remote database.

### 4. Dump Local Database

Export all data from your local database:

```bash
chmod +x ./scripts/dump-local-db.sh
./scripts/dump-local-db.sh
```

This creates a timestamped SQL file in `./backups/` directory.

### 5. Restore to Remote Database

Import the data to your remote database:

```bash
# Make script executable
chmod +x ./scripts/restore-to-remote.sh

# Load remote database environment
source .env.remote

# Restore data (replace with your actual dump file name)
./scripts/restore-to-remote.sh ./backups/eme_local_dump_YYYYMMDD_HHMMSS.sql
```

### 6. Verify Migration

Connect to your remote database and verify the data:

```bash
source .env.remote

psql -h $REMOTE_DB_HOST -U $REMOTE_DB_USER -d $REMOTE_DB_NAME -c "
SELECT
  'sessions' as table_name, COUNT(*) as count FROM sessions
UNION ALL
SELECT 'words', COUNT(*) FROM words
UNION ALL
SELECT 'sentences', COUNT(*) FROM sentences
UNION ALL
SELECT 'character_guide', COUNT(*) FROM character_guide
UNION ALL
SELECT 'generation_presets', COUNT(*) FROM generation_presets
UNION ALL
SELECT 'anki_formats', COUNT(*) FROM anki_formats;
"
```

Compare these counts with your local database to ensure all data was migrated.

### 7. Run Application with Remote Database

To run your application with the remote database:

```bash
source .env.remote
./gradlew bootRun -Dspring.profiles.active=remote
```

Or set the environment variables in your deployment platform.

## Troubleshooting

### Connection Issues

- Verify firewall rules allow connection to remote database
- Check that your IP is whitelisted (for cloud databases)
- Verify SSL/TLS requirements for your remote database

### Permission Issues

- Ensure remote user has CREATE, INSERT, SELECT, UPDATE, DELETE permissions
- For Flyway: user needs access to `flyway_schema_history` table

### Data Conflicts

If you get duplicate key errors, the remote database may not be empty:

```bash
# Clear all data from remote (CAUTION: destructive)
source .env.remote
psql -h $REMOTE_DB_HOST -U $REMOTE_DB_USER -d $REMOTE_DB_NAME -c "
TRUNCATE sessions, words, sentences, character_guide, generation_presets, anki_formats CASCADE;
"
```

## Alternative: Using pg_dump for Full Database Copy

If you want to copy the entire database including schema (without using Flyway):

```bash
# Dump entire local database
PGPASSWORD=eme_password pg_dump -h localhost -U eme_user eme_cache > full_backup.sql

# Restore to remote
source .env.remote
PGPASSWORD=$REMOTE_DB_PASSWORD psql -h $REMOTE_DB_HOST -U $REMOTE_DB_USER $REMOTE_DB_NAME < full_backup.sql
```

Note: This approach may conflict with Flyway migrations, so use the data-only approach recommended above.
