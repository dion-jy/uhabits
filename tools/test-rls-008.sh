#!/usr/bin/env bash
#
# test-rls-008.sh — adversarial RLS check for migration-008-launch-hardening.
#
# Run this against the PROD Supabase project AFTER applying migration-008 and
# BEFORE inviting closed-test testers. Every attack must FAIL (403 / empty / RLS
# error); every positive control must SUCCEED. It uses only the public anon key
# and a test agent secret / JWT — never the service_role key.
#
# Usage:
#   SUPABASE_URL=...  ANON_KEY=...  \
#   VICTIM_UID=<another user's uuid>  \
#   AGENT_SECRET=<a valid linked agent secret>  \
#   USER_JWT=<a signed-in user's access token>  \
#   ./tools/test-rls-008.sh
#
# Any credential left unset just skips the tests that need it (and says so).
# Defaults for URL/ANON are the public values (anon key is public by design).

set -uo pipefail

SUPABASE_URL="${SUPABASE_URL:-https://vzhkkxqwqtqajnutpjmt.supabase.co}"
ANON_KEY="${ANON_KEY:-eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ6aGtreHF3cXRxYWpudXRwam10Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzg4NTI2MjUsImV4cCI6MjA5NDQyODYyNX0.rRW-MGdghQk2yyPlwmBeCLvgZqIt9IApgVSg-PjSouE}"
REST="$SUPABASE_URL/rest/v1"

pass=0 fail=0 skip=0
ok()   { echo "  PASS  $1"; pass=$((pass+1)); }
bad()  { echo "  FAIL  $1"; fail=$((fail+1)); }
warn() { echo "  SKIP  $1"; skip=$((skip+1)); }

# curl helper: prints "HTTPCODE\nBODY"
req() { # method path  [extra header]...  [--data BODY]
  local method="$1" path="$2"; shift 2
  curl -s -o /tmp/rls_body.$$ -w "%{http_code}" -X "$method" "$REST/$path" \
       -H "apikey: $ANON_KEY" "$@"
}
body() { cat /tmp/rls_body.$$ 2>/dev/null; }

echo "== migration-008 adversarial RLS checks =="
echo "Target: $SUPABASE_URL"
echo

# ── 1. Anonymous coaching-injection (the headline hole 008 closes) ──────────
# Anon key + spoofed x-device-id, trying to insert a coaching row for a victim.
if [ -n "${VICTIM_UID:-}" ]; then
  code=$(req POST "coaching" \
    -H "Authorization: Bearer $ANON_KEY" \
    -H "x-device-id: attacker-device" \
    -H "Content-Type: application/json" \
    --data "{\"device_id\":\"attacker-device\",\"user_id\":\"$VICTIM_UID\",\"message\":\"pwned\",\"type\":\"nudge\"}")
  if [ "$code" = "201" ] || [ "$code" = "200" ]; then
    bad "anon coaching-injection returned $code (ROW WAS INSERTED — hole open!)"
  else
    ok "anon coaching-injection rejected ($code)"
  fi
else
  warn "coaching-injection (set VICTIM_UID to run)"
fi

# ── 2. Anonymous NULL-owner spam insert ─────────────────────────────────────
code=$(req POST "coaching" \
  -H "Authorization: Bearer $ANON_KEY" \
  -H "x-device-id: attacker-device" \
  -H "Content-Type: application/json" \
  --data "{\"device_id\":\"attacker-device\",\"message\":\"spam\",\"type\":\"nudge\"}")
if [ "$code" = "201" ] || [ "$code" = "200" ]; then
  bad "anon NULL-owner insert returned $code (legacy path still open!)"
else
  ok "anon NULL-owner insert rejected ($code)"
fi

# ── 3. Anonymous read via x-device-id must return nothing ───────────────────
code=$(req GET "habits?select=id&limit=100" \
  -H "Authorization: Bearer $ANON_KEY" \
  -H "x-device-id: any-known-or-guessed-device")
if [ "$code" = "200" ] && [ "$(body)" = "[]" ]; then
  ok "anon x-device-id read returns [] ($code)"
elif [ "$code" = "200" ]; then
  bad "anon x-device-id read returned rows: $(body | head -c 120)"
else
  ok "anon x-device-id read blocked ($code)"
fi

# ── 4. create_device_link RPC must be revoked for anon ──────────────────────
code=$(req POST "rpc/create_device_link" \
  -H "Authorization: Bearer $ANON_KEY" \
  -H "Content-Type: application/json" \
  --data "{\"p_instance_id\":\"attacker\"}")
if [ "$code" = "401" ] || [ "$code" = "403" ] || [ "$code" = "404" ]; then
  ok "create_device_link blocked for anon ($code)"
else
  bad "create_device_link callable by anon ($code)"
fi

# ── 5. Agent secret: no plaintext secret leaks, no DELETE, no cross-user ────
if [ -n "${AGENT_SECRET:-}" ]; then
  # 5a. device_links read must not expose a plaintext agent_secret column
  req GET "device_links?select=*&limit=1" \
    -H "Authorization: Bearer $ANON_KEY" \
    -H "x-agent-secret: $AGENT_SECRET" >/dev/null
  if body | grep -q '"agent_secret"[^_]'; then
    bad "device_links response still exposes plaintext agent_secret"
  else
    ok "device_links exposes no plaintext agent_secret"
  fi

  # 5b. agent DELETE on habits must be denied (no agent delete policy)
  code=$(req DELETE "habits?uuid=eq.__nonexistent__" \
    -H "Authorization: Bearer $ANON_KEY" \
    -H "x-agent-secret: $AGENT_SECRET")
  # PostgREST returns 204 even if 0 rows match when a policy allows DELETE;
  # with no agent DELETE policy it returns 403 (or 404). 204 here = policy exists.
  if [ "$code" = "403" ] || [ "$code" = "404" ]; then
    ok "agent DELETE on habits denied ($code)"
  else
    bad "agent DELETE on habits not denied ($code) — agent has delete rights"
  fi

  # 5c. agent read scoped — cross-user fetch must be empty
  if [ -n "${VICTIM_UID:-}" ]; then
    req GET "entries?user_id=eq.$VICTIM_UID&select=id&limit=5" \
      -H "Authorization: Bearer $ANON_KEY" \
      -H "x-agent-secret: $AGENT_SECRET" >/dev/null
    if [ "$(body)" = "[]" ]; then
      ok "agent cross-user entries read returns []"
    else
      bad "agent read leaked another user's rows: $(body | head -c 120)"
    fi
  else
    warn "agent cross-user read (set VICTIM_UID)"
  fi

  # 5d. positive control — agent can read its own habits
  req GET "habits?select=id&limit=1" \
    -H "Authorization: Bearer $ANON_KEY" \
    -H "x-agent-secret: $AGENT_SECRET" >/dev/null
  if body | grep -q '"id"'; then
    ok "positive: agent reads its own habits"
  else
    warn "positive: agent read returned no rows (ok if the account has 0 habits)"
  fi
else
  warn "agent-secret tests (set AGENT_SECRET to run)"
fi

# ── 6. Revoked-secret replay ────────────────────────────────────────────────
if [ -n "${REVOKED_SECRET:-}" ]; then
  req GET "habits?select=id&limit=1" \
    -H "Authorization: Bearer $ANON_KEY" \
    -H "x-agent-secret: $REVOKED_SECRET" >/dev/null
  if [ "$(body)" = "[]" ]; then
    ok "revoked secret returns [] (access gone)"
  else
    bad "revoked secret still returns data: $(body | head -c 120)"
  fi
else
  warn "revoked-secret replay (set REVOKED_SECRET — unlink in app, then pass the old one)"
fi

rm -f /tmp/rls_body.$$
echo
echo "== $pass passed, $fail failed, $skip skipped =="
[ "$fail" -eq 0 ]
