-- P18 HabitLoop: Supabase schema for agent-readable habit substrate
-- Run this in Supabase SQL Editor after creating the project.

-- habits table (mirrors uhabits SQLite Habits table)
CREATE TABLE habits (
    id BIGINT NOT NULL,
    uuid TEXT NOT NULL,
    device_id TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    question TEXT DEFAULT '',
    freq_num INTEGER DEFAULT 1,
    freq_den INTEGER DEFAULT 1,
    color INTEGER DEFAULT 0,
    position INTEGER DEFAULT 0,
    type INTEGER DEFAULT 0,
    target_value REAL DEFAULT 0,
    target_type INTEGER DEFAULT 0,
    unit TEXT DEFAULT '',
    archived INTEGER DEFAULT 0,
    synced_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (device_id, id),
    UNIQUE (device_id, uuid)
);

-- entries table (mirrors uhabits SQLite Repetitions table)
CREATE TABLE entries (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    habit_id BIGINT NOT NULL,
    timestamp BIGINT NOT NULL,
    value INTEGER NOT NULL,
    notes TEXT DEFAULT '',
    source TEXT DEFAULT 'app',
    synced_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (device_id, habit_id, timestamp)
);

-- coaching table (agent -> app communication)
CREATE TABLE coaching (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL,
    habit_uuid TEXT,
    message TEXT NOT NULL,
    type TEXT DEFAULT 'nudge',
    created_at TIMESTAMPTZ DEFAULT now(),
    read_at TIMESTAMPTZ,
    metadata JSONB DEFAULT '{}'
);

-- indexes
CREATE INDEX idx_entries_device_habit ON entries (device_id, habit_id);
CREATE INDEX idx_entries_timestamp ON entries (timestamp DESC);
CREATE INDEX idx_entries_agent ON entries (device_id, source) WHERE source = 'agent';
CREATE INDEX idx_coaching_unread ON coaching (device_id, read_at) WHERE read_at IS NULL;

-- Row Level Security
ALTER TABLE habits ENABLE ROW LEVEL SECURITY;
ALTER TABLE entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE coaching ENABLE ROW LEVEL SECURITY;

-- Policies: use x-device-id header for access control
-- For anon access, the device_id is passed as a custom header
CREATE POLICY "habits_device_access" ON habits
    FOR ALL USING (
        device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id',
            ''
        )
    );

CREATE POLICY "entries_device_access" ON entries
    FOR ALL USING (
        device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id',
            ''
        )
    );

CREATE POLICY "coaching_device_access" ON coaching
    FOR ALL USING (
        device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id',
            ''
        )
    );

-- Service role (for agent access) bypasses RLS.
-- Agent should use the service_role key, not anon key.
-- This is secure because the service_role key is only on the workspace server.
