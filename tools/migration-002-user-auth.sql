-- migration-002-user-auth.sql
-- P18 HabitLoop: User authentication and device linking
--
-- Purpose:
--   Migrate from insecure x-device-id header RLS to Supabase Auth (auth.uid())
--   with Google SSO. Devices link to user accounts via one-time tokens
--   (rondo plugin pattern). Data is tied to user accounts, surviving reinstalls.
--
-- Prerequisites:
--   - Supabase Auth enabled with Google OAuth provider
--   - Existing schema from supabase-schema.sql (habits, entries, coaching)
--
-- Run this in Supabase SQL Editor.

BEGIN;

-- ============================================================================
-- STEP 1: Add user_id column to existing tables
-- ============================================================================
-- user_id references Supabase Auth users. Nullable so existing rows (created
-- before auth was added) remain valid. New rows from authenticated clients
-- will populate this column.

ALTER TABLE habits
    ADD COLUMN user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;

ALTER TABLE entries
    ADD COLUMN user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;

ALTER TABLE coaching
    ADD COLUMN user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;

-- Indexes for the new column (RLS policies will filter on user_id)
CREATE INDEX idx_habits_user_id ON habits (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_entries_user_id ON entries (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_coaching_user_id ON coaching (user_id) WHERE user_id IS NOT NULL;

-- ============================================================================
-- STEP 2: Create device_links table (rondo plugin pattern)
-- ============================================================================
-- Flow:
--   1. Agent (service_role) creates a row with a random token + instance_id
--   2. User opens link containing the token in the app
--   3. App (authenticated via Google SSO) calls an RPC to claim the token
--   4. RPC sets user_id on the device_links row and marks it used
--   5. All data for that device_id/instance_id gets backfilled with user_id
--
-- instance_id: opaque string identifying the app installation (e.g. device_id).
-- token: short-lived one-time code the user enters or scans.

CREATE TABLE device_links (
    id         BIGSERIAL   PRIMARY KEY,
    token      TEXT        NOT NULL UNIQUE,
    instance_id TEXT       NOT NULL,
    user_id    UUID        REFERENCES auth.users(id) ON DELETE CASCADE,
    used       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at TIMESTAMPTZ
);

-- Only one active (unclaimed) link per instance at a time
CREATE UNIQUE INDEX idx_device_links_active
    ON device_links (instance_id) WHERE used = FALSE;

-- Lookup by token must be fast (claim flow)
CREATE INDEX idx_device_links_token ON device_links (token) WHERE used = FALSE;

ALTER TABLE device_links ENABLE ROW LEVEL SECURITY;

-- Authenticated users can read their own links
CREATE POLICY "device_links_user_read" ON device_links
    FOR SELECT USING (auth.uid() = user_id);

-- Service role bypasses RLS (creates tokens, manages links)
-- No INSERT/UPDATE policy for anon/authenticated; only service_role inserts.

-- ============================================================================
-- STEP 3: RPC to claim a device link token
-- ============================================================================
-- Called by the authenticated app after Google SSO sign-in.
-- Validates the token, binds it to auth.uid(), and backfills user_id on
-- all existing data for that device/instance.

CREATE OR REPLACE FUNCTION claim_device_link(p_token TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER          -- runs as DB owner to bypass RLS for backfill
SET search_path = public  -- prevent search_path injection
AS $$
DECLARE
    v_link   device_links%ROWTYPE;
    v_uid    UUID := auth.uid();
    v_now    TIMESTAMPTZ := now();
BEGIN
    -- Must be called by an authenticated user
    IF v_uid IS NULL THEN
        RETURN jsonb_build_object('ok', false, 'error', 'not_authenticated');
    END IF;

    -- Find and lock the token row
    SELECT * INTO v_link
    FROM device_links
    WHERE token = p_token AND used = FALSE
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('ok', false, 'error', 'invalid_or_expired_token');
    END IF;

    -- Expire tokens older than 15 minutes
    IF v_link.created_at < v_now - INTERVAL '15 minutes' THEN
        UPDATE device_links SET used = TRUE WHERE id = v_link.id;
        RETURN jsonb_build_object('ok', false, 'error', 'token_expired');
    END IF;

    -- Claim the link
    UPDATE device_links
    SET user_id = v_uid,
        used = TRUE,
        claimed_at = v_now
    WHERE id = v_link.id;

    -- Backfill user_id on all existing data for this device/instance
    UPDATE habits  SET user_id = v_uid WHERE device_id = v_link.instance_id AND user_id IS NULL;
    UPDATE entries SET user_id = v_uid WHERE device_id = v_link.instance_id AND user_id IS NULL;
    UPDATE coaching SET user_id = v_uid WHERE device_id = v_link.instance_id AND user_id IS NULL;

    RETURN jsonb_build_object(
        'ok', true,
        'instance_id', v_link.instance_id,
        'user_id', v_uid::text
    );
END;
$$;

-- ============================================================================
-- STEP 4: Update RLS policies
-- ============================================================================
-- New strategy (layered):
--   a) Authenticated users see rows where user_id = auth.uid()
--   b) Legacy device-header access preserved for transition period
--      (rows with null user_id can still be read via x-device-id header)
--   c) Service role bypasses RLS entirely (agent access, unchanged)
--
-- We drop the old policies and create new ones.

-- 4a. Drop old device-only policies
DROP POLICY IF EXISTS "habits_device_access"  ON habits;
DROP POLICY IF EXISTS "entries_device_access"  ON entries;
DROP POLICY IF EXISTS "coaching_device_access" ON coaching;

-- 4b. Habits policies

-- Authenticated user: full access to own rows
CREATE POLICY "habits_user_access" ON habits
    FOR ALL USING (
        user_id = auth.uid()
    )
    WITH CHECK (
        user_id = auth.uid()
    );

-- Legacy fallback: device-header access for rows not yet linked to a user
-- This lets the app keep working before the user signs in.
CREATE POLICY "habits_device_legacy" ON habits
    FOR ALL USING (
        user_id IS NULL
        AND device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id',
            ''
        )
    )
    WITH CHECK (
        -- New rows via legacy path get null user_id
        user_id IS NULL
        AND device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id',
            ''
        )
    );

-- 4c. Entries policies

CREATE POLICY "entries_user_access" ON entries
    FOR ALL USING (
        user_id = auth.uid()
    )
    WITH CHECK (
        user_id = auth.uid()
    );

CREATE POLICY "entries_device_legacy" ON entries
    FOR ALL USING (
        user_id IS NULL
        AND device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id',
            ''
        )
    )
    WITH CHECK (
        user_id IS NULL
        AND device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id',
            ''
        )
    );

-- 4d. Coaching policies

CREATE POLICY "coaching_user_access" ON coaching
    FOR ALL USING (
        user_id = auth.uid()
    )
    WITH CHECK (
        user_id = auth.uid()
    );

CREATE POLICY "coaching_device_legacy" ON coaching
    FOR ALL USING (
        user_id IS NULL
        AND device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id',
            ''
        )
    )
    WITH CHECK (
        user_id IS NULL
        AND device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id',
            ''
        )
    );

-- ============================================================================
-- STEP 5: Helper function to generate a device link token (agent use)
-- ============================================================================
-- Called by the agent (via service_role) to create a pairing token for a device.
-- Returns the token string. The agent gives this to the user (e.g. QR code, link).

CREATE OR REPLACE FUNCTION create_device_link(p_instance_id TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_token TEXT;
BEGIN
    -- Generate a URL-safe random token (22 chars, ~132 bits of entropy)
    v_token := encode(gen_random_bytes(16), 'base64');
    -- Make URL-safe: replace +/ with -_, strip trailing =
    v_token := replace(replace(rtrim(v_token, '='), '+', '-'), '/', '_');

    -- Expire any previous unclaimed token for this instance
    UPDATE device_links SET used = TRUE
    WHERE instance_id = p_instance_id AND used = FALSE;

    -- Insert new token
    INSERT INTO device_links (token, instance_id)
    VALUES (v_token, p_instance_id);

    RETURN v_token;
END;
$$;

-- ============================================================================
-- STEP 6: Cleanup helper (optional, run periodically or via pg_cron)
-- ============================================================================
-- Marks expired unclaimed tokens as used so they don't accumulate.

CREATE OR REPLACE FUNCTION cleanup_expired_device_links()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_count INTEGER;
BEGIN
    UPDATE device_links
    SET used = TRUE
    WHERE used = FALSE
      AND created_at < now() - INTERVAL '15 minutes';

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$;

COMMIT;

-- ============================================================================
-- NOTES
-- ============================================================================
--
-- Service role bypass:
--   Supabase service_role key automatically bypasses RLS. The agent continues
--   to use service_role for all reads/writes, so no policy is needed for it.
--
-- Migration path:
--   1. Run this migration
--   2. Enable Google OAuth in Supabase Auth dashboard
--   3. App adds Google SSO sign-in flow
--   4. On first sign-in, app calls create_device_link (via agent) then
--      claim_device_link (via authenticated client) to bind device to user
--   5. After binding, app sends auth token (JWT) instead of x-device-id header
--   6. Once all devices are migrated, drop the *_device_legacy policies
--
-- Rondo pattern summary:
--   The "rondo" pattern is a secure device-to-account linking flow:
--   - The server (agent) generates a short-lived token tied to a device instance
--   - The token is presented to the user out-of-band (QR code, deep link, etc.)
--   - The user, already authenticated via SSO, submits the token to claim it
--   - This binds the device to the authenticated user without the device ever
--     handling credentials directly
--   - Named after the musical rondo form (A-B-A): server creates, user bridges,
--     server finalizes
