-- migration-007-uuid-identity.sql
--
-- Switch the cloud identity of habits/entries from device-scoped keys to the
-- portable uuid, so a reinstall (which rotates device_id and the local id)
-- UPDATES the same cloud row instead of inserting a duplicate set.
--
--   habits:  identity (device_id, uuid)            -> uuid
--   entries: identity (device_id, habit_id, ts)    -> (habit_uuid, timestamp)
--
-- No real data is destroyed: the only DELETEs collapse duplicate copies of the
-- SAME habit/entry that accumulated one-per-device_id. One canonical row per
-- uuid (resp. per habit_uuid+timestamp) is kept.
--
-- Apply Part A any time (purely additive). REVIEW the Part B preview counts,
-- then apply Part B (dedup + constraint swap). The new app build (2.3.5+) and
-- the updated CLI/skill send habit_uuid and upsert on the new keys.

-- =====================================================================
-- Part A — additive: give entries a portable habit_uuid + backfill
-- =====================================================================
ALTER TABLE entries ADD COLUMN IF NOT EXISTS habit_uuid TEXT;

-- Backfill habit_uuid from the habit each entry points at, via its
-- (device_id, habit_id). Done before any dedup so every entry keeps its link.
UPDATE entries e
SET habit_uuid = h.uuid
FROM habits h
WHERE e.habit_uuid IS NULL
  AND e.device_id = h.device_id
  AND e.habit_id = h.id;

CREATE INDEX IF NOT EXISTS idx_entries_habit_uuid_ts
    ON entries (habit_uuid, timestamp);

-- =====================================================================
-- Before Part B — verify the old constraint names match (they are the
-- standard inline-CREATE TABLE names; confirm in case they were recreated):
--   SELECT conname FROM pg_constraint
--   WHERE conrelid IN ('habits'::regclass,'entries'::regclass) AND contype IN ('u','p');
-- Expect: habits_pkey, habits_device_id_uuid_key,
--         entries_pkey, entries_device_id_habit_id_timestamp_key
-- =====================================================================

-- =====================================================================
-- Part B preview — run this SELECT first to see what Part B will delete
-- =====================================================================
-- SELECT
--   (SELECT count(*) - count(DISTINCT uuid) FROM habits)                       AS habit_dupes_to_delete,
--   (SELECT count(*) FROM (
--       SELECT 1 FROM entries WHERE habit_uuid IS NOT NULL
--       GROUP BY habit_uuid, timestamp HAVING count(*) > 1
--    ) x)                                                                       AS entry_dup_groups,
--   (SELECT count(*) FROM entries WHERE habit_uuid IS NULL)                     AS orphan_entries_kept;

-- =====================================================================
-- Part B — dedup, then swap to uuid-based unique constraints
-- =====================================================================
BEGIN;

-- Collapse habits to one row per uuid. Keep the best: owned over unowned,
-- then non-archived, then most recently synced.
DELETE FROM habits
WHERE ctid IN (
    SELECT ctid FROM (
        SELECT ctid, ROW_NUMBER() OVER (
            PARTITION BY uuid
            ORDER BY (user_id IS NOT NULL) DESC, archived ASC, synced_at DESC NULLS LAST
        ) AS rn
        FROM habits
    ) t WHERE rn > 1
);

-- Collapse entries to one row per (habit_uuid, timestamp). Keep newest synced.
-- (Orphan entries with NULL habit_uuid are left untouched.)
DELETE FROM entries
WHERE habit_uuid IS NOT NULL AND ctid IN (
    SELECT ctid FROM (
        SELECT ctid, ROW_NUMBER() OVER (
            PARTITION BY habit_uuid, timestamp
            ORDER BY synced_at DESC NULLS LAST
        ) AS rn
        FROM entries WHERE habit_uuid IS NOT NULL
    ) t WHERE rn > 1
);

-- Swap habits identity to uuid. Also REPLACE the old composite primary key
-- (device_id, id) with PRIMARY KEY (uuid): otherwise an on_conflict=uuid upsert
-- from a reinstalled device rewrites the row's device_id/id and could collide
-- with that device's (device_id, id) PK (23505). uuid is NOT NULL and now
-- unique post-dedup, so it is a valid PK and removes the collision entirely.
ALTER TABLE habits DROP CONSTRAINT IF EXISTS habits_device_id_uuid_key;
ALTER TABLE habits DROP CONSTRAINT IF EXISTS habits_pkey;
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'habits_pkey' AND conrelid = 'habits'::regclass
    ) THEN
        ALTER TABLE habits ADD CONSTRAINT habits_pkey PRIMARY KEY (uuid);
    END IF;
END $$;

-- Swap entries identity: drop (device_id, habit_id, timestamp), add
-- (habit_uuid, timestamp). entries keeps its own serial PK (id).
ALTER TABLE entries DROP CONSTRAINT IF EXISTS entries_device_id_habit_id_timestamp_key;
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'entries_habit_uuid_ts_key' AND conrelid = 'entries'::regclass
    ) THEN
        ALTER TABLE entries ADD CONSTRAINT entries_habit_uuid_ts_key UNIQUE (habit_uuid, timestamp);
    END IF;
END $$;

COMMIT;
