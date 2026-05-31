-- migration-006-user-id-ownership.sql
--
-- Makes user_id the real ownership boundary for multi-user.
--
-- Background: the app never stamped user_id on rows; it only wrote device_id.
-- user_id was only ever set by the (unused) claim_device_link backfill, so
-- after Google sign-in a user's own rows stayed user_id IS NULL and were
-- unreachable through user_access / agent_access — requiring manual PATCH
-- backfills. The new app build (a) stamps user_id on every row while signed
-- in, and (b) calls backfill_user_id() once right after sign-in.
--
-- Apply Part A any time (safe, additive).
-- Apply Part B AFTER installing the new app build and signing in again on
-- each active device — it re-closes the cross-tenant hole that migration-005
-- opened (device_id-only access with no user_id check). Until a device is on
-- the new build + re-logged-in, Part B would make its already-owned rows
-- unreachable via the anonymous device path.

-- =====================================================================
-- Part A — backfill RPC: adopt a device's unowned rows into the caller
-- =====================================================================
-- Sets user_id = auth.uid() on this device's rows that are not yet owned.
-- The `user_id IS NULL` guard means it can never steal rows already owned by
-- another user; the worst case requires knowing a victim's random device UUID
-- and racing before they sign in (same exposure as claim_device_link).
CREATE OR REPLACE FUNCTION backfill_user_id(p_device_id TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_h   INT;
    v_e   INT;
    v_c   INT;
BEGIN
    IF v_uid IS NULL THEN
        RETURN jsonb_build_object('ok', false, 'error', 'not_authenticated');
    END IF;

    UPDATE habits   SET user_id = v_uid
        WHERE device_id = p_device_id AND user_id IS NULL;
    GET DIAGNOSTICS v_h = ROW_COUNT;

    UPDATE entries  SET user_id = v_uid
        WHERE device_id = p_device_id AND user_id IS NULL;
    GET DIAGNOSTICS v_e = ROW_COUNT;

    UPDATE coaching SET user_id = v_uid
        WHERE device_id = p_device_id AND user_id IS NULL;
    GET DIAGNOSTICS v_c = ROW_COUNT;

    UPDATE device_heartbeat SET user_id = v_uid
        WHERE device_id = p_device_id AND user_id IS NULL;

    RETURN jsonb_build_object(
        'ok', true, 'habits', v_h, 'entries', v_e, 'coaching', v_c
    );
END;
$$;

REVOKE ALL ON FUNCTION backfill_user_id(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION backfill_user_id(TEXT) TO authenticated;

-- =====================================================================
-- Part B — re-close device_legacy: only UNOWNED rows are device-readable
-- =====================================================================
-- (Apply after the new app build is installed + re-logged-in everywhere.)
-- Restores the `user_id IS NULL` guard that migration-005 removed, so a known
-- device_id can no longer read/write another user's owned data via the anon
-- key. Signed-in devices use the Bearer-JWT user_access path and never need
-- the legacy policy for their owned rows.
DROP POLICY IF EXISTS "habits_device_legacy" ON habits;
DROP POLICY IF EXISTS "entries_device_legacy" ON entries;
DROP POLICY IF EXISTS "coaching_device_legacy" ON coaching;

CREATE POLICY "habits_device_legacy" ON habits
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

CREATE POLICY "entries_device_legacy" ON entries
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

CREATE POLICY "coaching_device_legacy" ON coaching
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
