-- migration-003-device-heartbeat.sql
-- App reports its local state on every sync.
-- Agent compares this against Supabase data to detect stale/out-of-sync situations.

CREATE TABLE device_heartbeat (
    device_id       TEXT        PRIMARY KEY,
    user_id         UUID        REFERENCES auth.users(id) ON DELETE CASCADE,
    habit_count     INTEGER     NOT NULL DEFAULT 0,
    entry_count     INTEGER     NOT NULL DEFAULT 0,
    last_entry_ms   BIGINT      DEFAULT 0,
    app_version     TEXT        DEFAULT '',
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE device_heartbeat ENABLE ROW LEVEL SECURITY;

CREATE POLICY "heartbeat_user_access" ON device_heartbeat
    FOR ALL USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "heartbeat_device_legacy" ON device_heartbeat
    FOR ALL USING (
        user_id IS NULL
        AND device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id', ''
        )
    )
    WITH CHECK (
        user_id IS NULL
        AND device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id', ''
        )
    );
