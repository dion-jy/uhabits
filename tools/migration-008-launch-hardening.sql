-- migration-008-launch-hardening.sql
--
-- Security hardening before a PUBLIC multi-user launch (strangers install the
-- app, sign in with Google, and hand write-capable secrets to third-party AI
-- agents). This is the last security gate before the Play closed test.
--
-- What it does:
--   1. Removes the anonymous x-device-id write path entirely (no more
--      unauthenticated INSERTs; sync is sign-in-gated client-side).
--   2. Hashes agent secrets at rest (sha256); drops the plaintext column.
--   3. Replaces the FOR ALL agent policies with least-privilege per-verb
--      policies so a leaked/injected agent secret can at worst write fake
--      check-ins + coaching, never DELETE or wipe an account.
--   4. Locks down the device-link RPCs (anon can no longer spam them).
--   5. Switches user_id FKs to ON DELETE CASCADE and adds a delete_account()
--      RPC (Google Play account-deletion requirement).
--
-- ─────────────────────────────────────────────────────────────────────────
-- ORDERING — READ BEFORE APPLYING
-- ─────────────────────────────────────────────────────────────────────────
-- This migration ADDs agent_secret_hash, DROPs the plaintext agent_secret
-- column, and removes the anonymous x-device-id path. Consequences:
--   • The OLD v2.3.5 build writes plaintext agent_secret on Link Agent → that
--     stops working after this runs. (Normal signed-in sync over the JWT path
--     is unaffected.) v2.3.5 is being retired by the Looply rebrand, so fine.
--   • The NEW Looply build writes agent_secret_hash on Link Agent and that
--     column must already exist → its Link Agent only works AFTER this runs.
--   • Already-linked agents send the PLAINTEXT secret in the x-agent-secret
--     header; the backfill below hashes their existing secret so they keep
--     working with NO re-link. The dev CLI (tools/habits, service_role) and the
--     public skill (anon + secret) are unaffected.
--
-- Safe sequence for the solo dev:
--   1. Build + install the Looply app (hash stamping + sign-in-gated sync).
--   2. Apply this migration.
--   3. (Re)generate the agent code in Looply; re-link the skill if you want.
--      Do NOT generate a code in Looply before step 2 — the column won't exist.
--
-- ─────────────────────────────────────────────────────────────────────────
-- PREFLIGHT — run these SELECTs first and eyeball the live state:
-- ─────────────────────────────────────────────────────────────────────────
--   -- current policies on the five tables:
--   SELECT schemaname, tablename, policyname, cmd
--   FROM pg_policies
--   WHERE tablename IN ('habits','entries','coaching','device_heartbeat','device_links')
--   ORDER BY tablename, policyname;
--   -- pgcrypto present, and in which schema (digest/gen_random_bytes live here):
--   SELECT n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
--   WHERE e.extname = 'pgcrypto';
--   -- FK delete behavior (expect 'a' = NO ACTION/SET NULL on user_id fkeys):
--   SELECT conname, confdeltype FROM pg_constraint
--   WHERE conname LIKE '%user_id_fkey%';
-- ─────────────────────────────────────────────────────────────────────────

BEGIN;

-- =====================================================================
-- 0. Make pgcrypto reachable. On modern Supabase it lives in `extensions`;
--    older projects have it in `public`. Adding both to search_path makes
--    digest()/gen_random_bytes() resolve either way.
-- =====================================================================
-- (search_path is set per-function below via SET search_path = public, extensions)

-- =====================================================================
-- 1. KILL the anonymous device path (closes cross-tenant injection + spam).
--    The app no longer uses x-device-id (sign-in-gated sync); after this,
--    the anon key can only hit auth endpoints + empty SELECTs.
-- =====================================================================
DROP POLICY IF EXISTS "habits_device_legacy"    ON habits;
DROP POLICY IF EXISTS "entries_device_legacy"   ON entries;
DROP POLICY IF EXISTS "coaching_device_legacy"  ON coaching;
DROP POLICY IF EXISTS "heartbeat_device_legacy" ON device_heartbeat;

-- =====================================================================
-- 2. Lock down the device-link RPCs (were EXECUTE-to-PUBLIC by default,
--    so anyone with the anon key could spam device_links / DoS pairings).
-- =====================================================================
REVOKE ALL ON FUNCTION create_device_link(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION claim_device_link(TEXT)  FROM PUBLIC;
REVOKE ALL ON FUNCTION cleanup_expired_device_links() FROM PUBLIC;
-- The dev CLI (tools/habits link) creates tokens via service_role:
GRANT EXECUTE ON FUNCTION create_device_link(TEXT) TO service_role;
-- The app claims tokens while authenticated (rondo flow, if still used):
GRANT EXECUTE ON FUNCTION claim_device_link(TEXT) TO authenticated;
-- backfill_user_id already granted to authenticated only (migration-006) — leave it.

-- =====================================================================
-- 3. Hash agent secrets at rest. Existing links keep working: the agent
--    still sends the plaintext secret in the header; the DB compares its
--    sha256. Plaintext column (and its index) are removed entirely.
-- =====================================================================
ALTER TABLE device_links ADD COLUMN IF NOT EXISTS agent_secret_hash TEXT;

UPDATE device_links
   SET agent_secret_hash = encode(digest(agent_secret, 'sha256'), 'hex')
 WHERE agent_secret IS NOT NULL
   AND agent_secret_hash IS NULL;

DROP INDEX IF EXISTS idx_device_links_agent_secret;          -- indexed plaintext
ALTER TABLE device_links DROP COLUMN IF EXISTS agent_secret; -- remove plaintext

CREATE INDEX IF NOT EXISTS idx_device_links_secret_hash
    ON device_links (agent_secret_hash)
    WHERE agent_secret_hash IS NOT NULL AND used = TRUE;

-- Canonical resolver: which user does this request's x-agent-secret belong to?
-- SECURITY DEFINER so it can read device_links regardless of the caller's RLS;
-- STABLE so the planner can hoist it; used as (SELECT agent_user_id()) in
-- policies to force a single InitPlan evaluation per statement.
CREATE OR REPLACE FUNCTION agent_user_id()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions
AS $$
    SELECT dl.user_id
    FROM device_links dl
    WHERE dl.used = TRUE
      AND dl.agent_secret_hash IS NOT NULL
      AND dl.agent_secret_hash = encode(digest(
            coalesce(current_setting('request.headers', true)::json->>'x-agent-secret', ''),
            'sha256'), 'hex')
    LIMIT 1
$$;

REVOKE ALL ON FUNCTION agent_user_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION agent_user_id() TO anon, authenticated, service_role;

-- =====================================================================
-- 4. Replace the FOR ALL agent policies with least-privilege per-verb
--    policies. Blast radius of a leaked/prompt-injected secret drops from
--    "DELETE everything" to "fake check-ins + nudge spam on own account".
--
--    Verified against the public skill (~/habits-skill/habits):
--      • habits     — GET only            -> SELECT
--      • entries    — GET + upsert POST   -> SELECT, INSERT, UPDATE (no DELETE)
--      • coaching   — POST (+ GET)        -> SELECT, INSERT
--      • heartbeat  — GET (status cmd)    -> SELECT
--      • device_links — GET (whoami/link) -> SELECT
-- =====================================================================
DROP POLICY IF EXISTS "habits_agent_access"     ON habits;
DROP POLICY IF EXISTS "entries_agent_access"    ON entries;
DROP POLICY IF EXISTS "coaching_agent_access"   ON coaching;
DROP POLICY IF EXISTS "heartbeat_agent_access"  ON device_heartbeat;
DROP POLICY IF EXISTS "device_links_agent_read" ON device_links;

-- habits: read-only for agents (habit CRUD happens in-app via the JWT path)
CREATE POLICY "habits_agent_select" ON habits
    FOR SELECT USING (user_id = (SELECT agent_user_id()));

-- entries: read + upsert (no delete). user_id is the cross-tenant boundary.
CREATE POLICY "entries_agent_select" ON entries
    FOR SELECT USING (user_id = (SELECT agent_user_id()));
CREATE POLICY "entries_agent_insert" ON entries
    FOR INSERT WITH CHECK (user_id = (SELECT agent_user_id()));
CREATE POLICY "entries_agent_update" ON entries
    FOR UPDATE USING (user_id = (SELECT agent_user_id()))
            WITH CHECK (user_id = (SELECT agent_user_id()));

-- coaching: agent writes messages to the user; may read them back.
CREATE POLICY "coaching_agent_select" ON coaching
    FOR SELECT USING (user_id = (SELECT agent_user_id()));
CREATE POLICY "coaching_agent_insert" ON coaching
    FOR INSERT WITH CHECK (user_id = (SELECT agent_user_id()));

-- heartbeat: read-only (the status command compares app vs cloud).
CREATE POLICY "heartbeat_agent_select" ON device_heartbeat
    FOR SELECT USING (user_id = (SELECT agent_user_id()));

-- device_links: the agent may read its own link row (whoami).
CREATE POLICY "device_links_agent_read" ON device_links
    FOR SELECT USING (
        used = TRUE
        AND agent_secret_hash IS NOT NULL
        AND user_id = (SELECT agent_user_id())
    );

-- =====================================================================
-- 5. Account deletion (Google Play requirement) + FK CASCADE.
--    user_id FKs were ON DELETE SET NULL (migration-002); switch to CASCADE
--    so deleting the auth user fully removes their rows and delete_account()
--    is robust under every deletion path (in-app, dashboard, Admin API).
-- =====================================================================
ALTER TABLE habits   DROP CONSTRAINT IF EXISTS habits_user_id_fkey;
ALTER TABLE entries  DROP CONSTRAINT IF EXISTS entries_user_id_fkey;
ALTER TABLE coaching DROP CONSTRAINT IF EXISTS coaching_user_id_fkey;

ALTER TABLE habits   ADD CONSTRAINT habits_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE entries  ADD CONSTRAINT entries_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE coaching ADD CONSTRAINT coaching_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
-- device_links + device_heartbeat are already ON DELETE CASCADE (migrations 002/003).

-- Self-service account+data deletion for the signed-in user. Explicit table
-- deletes run first (so habit data is purged even if the auth.users delete is
-- restricted under the definer role); the auth.users delete then removes the
-- identity (and cascades as a backstop).
CREATE OR REPLACE FUNCTION delete_account()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE v_uid UUID := auth.uid();
BEGIN
    IF v_uid IS NULL THEN
        RETURN jsonb_build_object('ok', false, 'error', 'not_authenticated');
    END IF;

    DELETE FROM entries          WHERE user_id = v_uid;
    DELETE FROM habits           WHERE user_id = v_uid;
    DELETE FROM coaching         WHERE user_id = v_uid;
    DELETE FROM device_links     WHERE user_id = v_uid;
    DELETE FROM device_heartbeat WHERE user_id = v_uid;
    DELETE FROM auth.users       WHERE id = v_uid;

    RETURN jsonb_build_object('ok', true);
END;
$$;

REVOKE ALL ON FUNCTION delete_account() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION delete_account() TO authenticated;

-- =====================================================================
-- 6. Minor: the unread-coaching index keyed on device_id, but the app reads
--    coaching by read_at under the JWT (user_id) path. Re-key it to user_id.
-- =====================================================================
DROP INDEX IF EXISTS idx_coaching_unread;
CREATE INDEX IF NOT EXISTS idx_coaching_unread
    ON coaching (user_id, read_at) WHERE read_at IS NULL;

COMMIT;

-- ─────────────────────────────────────────────────────────────────────────
-- NOT included (deliberately):
--   • CHECK (user_id IS NOT NULL): redundant once the anon device path is
--     gone — no unauthenticated role can insert a NULL-owner row, and the JWT
--     and agent WITH CHECKs both bind a non-null user_id. Skipped to avoid any
--     interaction with pre-existing legacy NULL-owner orphan rows.
--   • Per-user habit-cap trigger: a BEFORE INSERT count(*) would fire on every
--     habit upsert during normal sync. The Supabase spend cap + db-max-rows is
--     the right backstop for a free launch. Revisit if abuse appears.
--   • Secret expiry (expires_at): would silently break linked agents with no
--     notification channel. Revocation = regenerate (nulls old hash) + the new
--     in-app "Unlink agent" button. Revisit post-launch with last_used_at.
-- ─────────────────────────────────────────────────────────────────────────
