INSERT INTO character_attrs (character_id, attr, value_json)
VALUES (?, ?, ?)
ON CONFLICT (character_id, attr) DO UPDATE SET value_json = EXCLUDED.value_json
