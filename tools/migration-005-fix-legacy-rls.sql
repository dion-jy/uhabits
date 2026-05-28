-- migration-005-fix-legacy-rls.sql
-- Fix: legacy device_id policies blocked access after user_id backfill.
-- Old policy required user_id IS NULL, but claim_device_link sets user_id
-- on all existing data. Now allow device_id access regardless of user_id.

DROP POLICY IF EXISTS "habits_device_legacy" ON habits;
DROP POLICY IF EXISTS "entries_device_legacy" ON entries;
DROP POLICY IF EXISTS "coaching_device_legacy" ON coaching;

CREATE POLICY "habits_device_legacy" ON habits
    FOR ALL USING (
        device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id', ''
        )
    )
    WITH CHECK (
        device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id', ''
        )
    );

CREATE POLICY "entries_device_legacy" ON entries
    FOR ALL USING (
        device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id', ''
        )
    )
    WITH CHECK (
        device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id', ''
        )
    );

CREATE POLICY "coaching_device_legacy" ON coaching
    FOR ALL USING (
        device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id', ''
        )
    )
    WITH CHECK (
        device_id = coalesce(
            current_setting('request.headers', true)::json->>'x-device-id', ''
        )
    );
