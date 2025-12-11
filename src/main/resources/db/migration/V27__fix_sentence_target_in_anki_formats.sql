-- Replace SENTENCE_TARGET with TARGET_TEXT in anki_formats JSON data
-- SENTENCE_TARGET is not a valid CardType enum value

UPDATE anki_formats
SET format_json = REPLACE(format_json, '"SENTENCE_TARGET"', '"TARGET_TEXT"')
WHERE format_json LIKE '%SENTENCE_TARGET%';
