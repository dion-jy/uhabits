import { useCallback, useEffect, useMemo, useState } from "react";
import type { Session } from "@supabase/supabase-js";
import { supabase } from "./supabase";
import {
  DONE_VALUES,
  Habit,
  NO,
  TYPE_BOOLEAN,
  YES_MANUAL,
  EntryMap,
  fetchHabits,
  fetchRecentEntries,
  groupEntries,
  lastNDayTimestamps,
  todayTimestamp,
  writeCheckin,
} from "./habits";
import { habitColor } from "./colors";

const GRID_DAYS = 14;

export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [authReady, setAuthReady] = useState(false);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session);
      setAuthReady(true);
    });
    const { data: sub } = supabase.auth.onAuthStateChange((_e, s) => {
      setSession(s);
    });
    return () => sub.subscription.unsubscribe();
  }, []);

  if (!authReady) return <div className="center muted">Loading…</div>;
  if (!session) return <Landing />;
  return <Dashboard session={session} />;
}

function Landing() {
  const signIn = () =>
    supabase.auth.signInWithOAuth({
      provider: "google",
      options: { redirectTo: window.location.origin },
    });

  return (
    <div className="center landing">
      <h1>Looply</h1>
      <p className="muted">View your habits and check in from the web.</p>
      <button className="primary" onClick={signIn}>
        Sign in with Google
      </button>
    </div>
  );
}

function Dashboard({ session }: { session: Session }) {
  const userId = session.user.id;
  const [habits, setHabits] = useState<Habit[]>([]);
  const [entries, setEntries] = useState<EntryMap>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const days = useMemo(() => lastNDayTimestamps(GRID_DAYS), []);
  const today = useMemo(() => todayTimestamp(), []);

  const load = useCallback(async () => {
    try {
      setError(null);
      const [h, e] = await Promise.all([
        fetchHabits(),
        fetchRecentEntries(GRID_DAYS),
      ]);
      setHabits(h);
      setEntries(groupEntries(e));
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // Refetch when the tab becomes visible again.
  useEffect(() => {
    const onVis = () => {
      if (document.visibilityState === "visible") load();
    };
    document.addEventListener("visibilitychange", onVis);
    return () => document.removeEventListener("visibilitychange", onVis);
  }, [load]);

  const setLocalValue = useCallback(
    (uuid: string, ts: number, value: number) => {
      setEntries((prev) => {
        const next = new Map(prev);
        const byTs = new Map(next.get(uuid) ?? new Map<number, number>());
        byTs.set(ts, value);
        next.set(uuid, byTs);
        return next;
      });
    },
    []
  );

  const toggleToday = useCallback(
    async (habit: Habit) => {
      if (habit.type !== TYPE_BOOLEAN) return;
      const current = entries.get(habit.uuid)?.get(today);
      // Two-state in v1: YES (2) <-> NO (0). Treat YES_AUTO(1) as "done" too.
      const isDone = current === YES_MANUAL || current === 1;
      const next = isDone ? NO : YES_MANUAL;
      const prev = current;

      setLocalValue(habit.uuid, today, next); // optimistic
      try {
        await writeCheckin(habit, today, next, userId);
      } catch (err) {
        setLocalValue(habit.uuid, today, prev ?? -1); // rollback
        setError(err instanceof Error ? err.message : String(err));
      }
    },
    [entries, today, userId, setLocalValue]
  );

  return (
    <div className="app">
      <header className="topbar">
        <h1>Looply</h1>
        <div className="topbar-right">
          <span className="muted small">{session.user.email}</span>
          <button className="ghost" onClick={() => supabase.auth.signOut()}>
            Sign out
          </button>
        </div>
      </header>

      <p className="note">
        Check-ins appear on your phone the next time you open the app.
      </p>

      {error && <p className="error">{error}</p>}
      {loading ? (
        <p className="muted">Loading habits…</p>
      ) : habits.length === 0 ? (
        <p className="muted">No habits yet.</p>
      ) : (
        <div className="habits">
          {habits.map((h) => (
            <HabitRow
              key={h.uuid}
              habit={h}
              days={days}
              today={today}
              byTs={entries.get(h.uuid)}
              onToggleToday={() => toggleToday(h)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function HabitRow({
  habit,
  days,
  today,
  byTs,
  onToggleToday,
}: {
  habit: Habit;
  days: number[];
  today: number;
  byTs: Map<number, number> | undefined;
  onToggleToday: () => void;
}) {
  const color = habitColor(habit.color);
  const isBoolean = habit.type === TYPE_BOOLEAN;

  const counts = useMemo(() => {
    const count = (n: number) => {
      const since = today - (n - 1) * 86400000;
      let c = 0;
      if (byTs) {
        for (const [ts, v] of byTs) {
          if (ts >= since && DONE_VALUES.has(v)) c++;
        }
      }
      return c;
    };
    return { d7: count(7), d30: count(30) };
  }, [byTs, today]);

  // Grid is newest-first; render oldest-first (left → right ending at today).
  const cells = [...days].reverse();

  return (
    <div className="habit">
      <div className="habit-head">
        <span className="dot" style={{ background: color }} />
        <div className="habit-name">
          <strong>{habit.name}</strong>
          {habit.question && <span className="muted small q">{habit.question}</span>}
        </div>
        <span className="counts muted small">
          7d {counts.d7} · 30d {counts.d30}
        </span>
      </div>

      <div className="grid">
        {cells.map((ts) => {
          const v = byTs?.get(ts);
          const done = v === YES_MANUAL || v === 1;
          const isToday = ts === today;
          const filled = done;
          const numericVal =
            !isBoolean && v !== undefined ? v / 1000 : undefined;

          const cellStyle: React.CSSProperties = filled
            ? { background: color, borderColor: color }
            : {};

          if (isBoolean && isToday) {
            return (
              <button
                key={ts}
                className={`cell today ${filled ? "filled" : ""}`}
                style={cellStyle}
                onClick={onToggleToday}
                title="Toggle today (YES / NO)"
                aria-label="Toggle today"
              />
            );
          }
          return (
            <div
              key={ts}
              className={`cell ${filled ? "filled" : ""} ${
                isToday ? "today-ro" : ""
              }`}
              style={cellStyle}
              title={
                numericVal !== undefined
                  ? `${numericVal}${habit.unit ? " " + habit.unit : ""}`
                  : undefined
              }
            >
              {numericVal !== undefined && numericVal !== 0 ? (
                <span className="numlabel">{numericVal}</span>
              ) : null}
            </div>
          );
        })}
      </div>

      {!isBoolean && (
        <span className="muted small ro-note">
          Numeric habit — read-only here{habit.unit ? ` (${habit.unit})` : ""}
        </span>
      )}
    </div>
  );
}
