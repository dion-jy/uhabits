# Looply Dashboard (web v1)

A minimal, Toggl-style web dashboard for the **Looply** habit tracker. A
signed-in user can view their habits and check in from the browser. It talks
**straight to Supabase** (no backend, no service_role key).

## Run

```bash
cd web/dashboard
cp .env.example .env      # public anon values; safe in client code
npm i
npm run dev               # http://localhost:5173
```

Build / preview:

```bash
npm run build
npm run preview
```

### `.env.example`

```
VITE_SUPABASE_URL=https://vzhkkxqwqtqajnutpjmt.supabase.co
VITE_SUPABASE_ANON_KEY=<public anon key>
```

Only the **anon** key belongs here. **Never** put a `service_role` key in this
app — it ships to the browser.

## What it does (v1 scope)

- **Google sign-in** via `supabase.auth.signInWithOAuth({ provider: 'google' })`.
  Session is persisted by supabase-js (localStorage) and auto-refreshed.
- **Habit list** with a last-14-days cell grid per habit, colored with the
  habit's Loop palette color.
- **Today is interactive** for boolean habits: click to toggle **YES (2) ⇄ NO
  (0)** (two-state in v1; SKIP/clear are out of scope). Numeric habits are
  **read-only** (shown as `value / 1000` + unit).
- **7-day / 30-day done counts** per habit (entries with value in `(1, 2)`).
- **Optimistic UI** on toggle; **refetch on `visibilitychange`**.
- Persistent note: *"Check-ins appear on your phone the next time you open the
  app."* (the app has no background pull — cloud→phone happens on app open.)

### Deliberately NOT in v1

Habit create/edit (needs an app-side auto-pull), realtime, charts/score, notes
editing, SKIP, numeric writes.

## Data contract (verified, do not re-derive)

- **habits**: PK `uuid` (TEXT). Read `archived = 0`, order by `position` asc.
- **entries**: UNIQUE `(habit_uuid, timestamp)`. `device_id` and `habit_id` are
  NOT NULL — copied from the habit row on write.
- **Entry values** (boolean, type 0): `YES_MANUAL = 2`, `NO = 0`. `YES_AUTO = 1`
  is computed by the app and never written here.
- **Numeric** (type 1): stored ×1000 — display `value / 1000`.
- **timestamp** = UTC-midnight epoch ms of the user's **local** calendar date:
  `Date.UTC(y, m, d)` from the local Y/M/D.
- **Check-in write** is a PostgREST upsert on `(habit_uuid, timestamp)` with
  `Prefer: resolution=merge-duplicates`. Payload:
  `{ device_id, habit_id, habit_uuid, timestamp, value, source: "agent", user_id }`.
  `notes` is **omitted** (merge-duplicates would clobber app-typed notes).
  `user_id` MUST equal `session.user.id` (RLS WITH CHECK), and `source` MUST be
  `"agent"` — that is what the Android app's sync pulls.

---

## Supabase / Google console setup the developer must do

The Android app signs in with a **native Google id_token** flow, so a *web*
OAuth client may not exist yet. This dashboard uses the browser redirect flow,
which needs the steps below. One-time setup:

### 1. Supabase Auth — URL configuration

In **Supabase → Authentication → URL Configuration**:

- **Site URL**: set to where the dashboard runs (e.g. `http://localhost:5173`
  for dev, or the deployed Pages/static-host origin).
- **Redirect URLs (allowlist)**: add every origin the app redirects back to —
  e.g. `http://localhost:5173` and your production origin. The app passes
  `redirectTo: window.location.origin`, so the exact origin must be allowlisted
  or the OAuth redirect is rejected.

### 2. Supabase Google provider — needs a **web** OAuth client

In **Supabase → Authentication → Providers → Google**, enable Google and supply
a **Web** OAuth **client id + secret** (the native/Android client used by the
app does not have a client secret and cannot be used for the web flow).

Create the web client in **Google Cloud Console → APIs & Services →
Credentials → Create Credentials → OAuth client ID → Web application**.

### 3. GCP web client — authorized redirect URI

In that GCP **web** OAuth client, add Supabase's callback to **Authorized
redirect URIs**:

```
https://vzhkkxqwqtqajnutpjmt.supabase.co/auth/v1/callback
```

(Replace the host if the project ref differs:
`https://<project-ref>.supabase.co/auth/v1/callback`.)

### 4. RLS must be current

Make sure **migration-006 Part B** and **migration-008** have been applied so
RLS is correct: authenticated users access only rows where `user_id =
auth.uid()`, and the anonymous device path is closed. The dashboard relies on
the `*_user_access` policies (`user_id = auth.uid()`) for both reads and the
check-in upsert; without them reads return empty and writes are rejected.
