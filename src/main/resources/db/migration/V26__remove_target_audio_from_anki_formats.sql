-- Remove TARGET_AUDIO card type from anki_formats JSON data
-- This card type is no longer supported

UPDATE anki_formats
SET format_json = REGEXP_REPLACE(
    format_json,
    '\{"cardType":"TARGET_AUDIO"[^}]*\},?',
    '',
    'g'
)
WHERE format_json LIKE '%TARGET_AUDIO%';

-- Clean up any trailing commas that may have been left behind
UPDATE anki_formats
SET format_json = REGEXP_REPLACE(format_json, ',\s*\]', ']', 'g')
WHERE format_json LIKE '%,%]%';
