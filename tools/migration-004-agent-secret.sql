-- migration-004-agent-secret.sql
-- Agent access via secret key instead of service_role.
-- App generates the secret, agent stores it, RLS enforces access.

-- Step 1: Add agent_secret column
ALTER TABLE device_links ADD COLUMN agent_secret TEXT;
CREATE INDEX idx_device_links_agent_secret
    ON device_links (agent_secret) WHERE agent_secret IS NOT NULL AND used = TRUE;

-- Step 2: Allow authenticated users to insert their own links
CREATE POLICY "device_links_user_insert" ON device_links
    FOR INSERT WITH CHECK (user_id = auth.uid());

-- Step 3: Agent access policies on data tables
-- Agent sends x-agent-secret header; RLS checks it against device_links

CREATE POLICY "habits_agent_access" ON habits
    FOR ALL USING (
        user_id IN (
            SELECT dl.user_id FROM device_links dl
            WHERE dl.agent_secret = coalesce(
                current_setting('request.headers', true)::json->>'x-agent-secret', ''
            )
            AND dl.used = TRUE
            AND dl.agent_secret IS NOT NULL
        )
    );

CREATE POLICY "entries_agent_access" ON entries
    FOR ALL USING (
        user_id IN (
            SELECT dl.user_id FROM device_links dl
            WHERE dl.agent_secret = coalesce(
                current_setting('request.headers', true)::json->>'x-agent-secret', ''
            )
            AND dl.used = TRUE
            AND dl.agent_secret IS NOT NULL
        )
    );

CREATE POLICY "coaching_agent_access" ON coaching
    FOR ALL USING (
        user_id IN (
            SELECT dl.user_id FROM device_links dl
            WHERE dl.agent_secret = coalesce(
                current_setting('request.headers', true)::json->>'x-agent-secret', ''
            )
            AND dl.used = TRUE
            AND dl.agent_secret IS NOT NULL
        )
    );

CREATE POLICY "heartbeat_agent_access" ON device_heartbeat
    FOR ALL USING (
        user_id IN (
            SELECT dl.user_id FROM device_links dl
            WHERE dl.agent_secret = coalesce(
                current_setting('request.headers', true)::json->>'x-agent-secret', ''
            )
            AND dl.used = TRUE
            AND dl.agent_secret IS NOT NULL
        )
    );
