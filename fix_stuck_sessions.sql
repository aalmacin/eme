-- Fix sessions stuck in IN_PROGRESS that actually completed
-- These are sessions that have session_data populated (image was generated) but status wasn't updated

UPDATE translation_sessions
SET
    status = 'COMPLETED',
    completed_at = COALESCE(completed_at, updated_at),
    updated_at = NOW()
WHERE
    status = 'IN_PROGRESS'
    AND session_data IS NOT NULL
    AND session_data != ''
    AND session_data != '{}'
    AND (
        session_data LIKE '%"image_file"%'
        OR session_data LIKE '%"gcs_url"%'
        OR session_data LIKE '%"local_path"%'
    );

-- Show updated sessions
SELECT
    id,
    word,
    status,
    image_generation_enabled,
    created_at,
    completed_at
FROM translation_sessions
ORDER BY created_at DESC
LIMIT 20;
