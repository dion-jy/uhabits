import { useEffect, useState } from "react";
import { useAuth } from "../hooks/useAuth";
import { supabase } from "../lib/supabase";

// ---- Types ----

interface Habit {
  id: number;
  device_id: string;
  name: string;
  description: string;
  color: number;
  archived: number;
  type: number;
  target_value: number;
  unit: string;
  freq_num: number;
  freq_den: number;
}

interface Entry {
  habit_id: number;
  device_id: string;
  timestamp: number;
  value: number;
}

const ENTRY_YES_MANUAL = 2;

// ---- Helpers ----

/** Start of today as Unix ms */
function todayMs(): number {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function daysAgoMs(n: number): number {
  return todayMs() - n * 86400000;
}

/** Compute current streak (consecutive days with value >= 2 ending at today/yesterday) */
function computeStreak(entries: Entry[]): number {
  if (entries.length === 0) return 0;
  const daySet = new Set(
    entries
      .filter((e) => e.value >= ENTRY_YES_MANUAL)
      .map((e) => Math.floor(e.timestamp / 86400000))
  );
  const todayDay = Math.floor(todayMs() / 86400000);
  let streak = 0;
  // start from today, allow gap of today (check yesterday first)
  let start = daySet.has(todayDay) ? todayDay : todayDay - 1;
  if (!daySet.has(start)) return 0;
  for (let d = start; daySet.has(d); d--) {
    streak++;
  }
  return streak;
}

/** Get last 7 days completion flags */
function last7Days(entries: Entry[]): boolean[] {
  const result: boolean[] = [];
  for (let i = 6; i >= 0; i--) {
    const dayStart = daysAgoMs(i);
    const dayEnd = dayStart + 86400000;
    const done = entries.some(
      (e) => e.value >= ENTRY_YES_MANUAL && e.timestamp >= dayStart && e.timestamp < dayEnd
    );
    result.push(done);
  }
  return result;
}

const DAY_LABELS = ["M", "T", "W", "T", "F", "S", "S"];

// ---- Styles ----

const s = {
  container: {
    maxWidth: 640,
    margin: "0 auto",
    padding: "24px 20px",
    fontFamily: "system-ui, sans-serif",
  },
  header: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 24,
  },
  title: { fontSize: 22, fontWeight: 600, margin: 0 },
  signOutBtn: {
    padding: "6px 14px",
    fontSize: 13,
    border: "1px solid #ddd",
    borderRadius: 6,
    background: "#fff",
    cursor: "pointer",
  },
  statsRow: {
    display: "flex",
    gap: 16,
    marginBottom: 24,
  },
  statCard: {
    flex: 1,
    background: "#fff",
    borderRadius: 10,
    padding: "16px 20px",
    boxShadow: "0 1px 6px rgba(0,0,0,0.06)",
    textAlign: "center" as const,
  },
  statValue: { fontSize: 28, fontWeight: 700, margin: 0 },
  statLabel: { fontSize: 12, color: "#888", marginTop: 4 },
  habitCard: {
    background: "#fff",
    borderRadius: 10,
    padding: "16px 20px",
    marginBottom: 12,
    boxShadow: "0 1px 6px rgba(0,0,0,0.06)",
  },
  habitName: { fontSize: 16, fontWeight: 600, margin: "0 0 8px" },
  weekRow: { display: "flex", gap: 6 },
  dayDot: (done: boolean) => ({
    width: 28,
    height: 28,
    borderRadius: "50%",
    background: done ? "#4CAF50" : "#eee",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: 11,
    color: done ? "#fff" : "#aaa",
    fontWeight: 500,
  }),
  streakBadge: {
    fontSize: 13,
    color: "#F57C00",
    fontWeight: 600,
    marginLeft: "auto",
  },
  habitHeader: {
    display: "flex",
    alignItems: "center",
    marginBottom: 8,
  },
  empty: { textAlign: "center" as const, color: "#888", padding: 40 },
  loading: { textAlign: "center" as const, color: "#888", padding: 60 },
  linkBtn: {
    padding: "6px 14px",
    fontSize: 13,
    border: "1px solid #ddd",
    borderRadius: 6,
    background: "#fff",
    cursor: "pointer",
    marginLeft: 8,
  },
};

// ---- Component ----

interface Props {
  onSetup: () => void;
}

export default function Dashboard({ onSetup }: Props) {
  const { signOut } = useAuth();
  const [habits, setHabits] = useState<Habit[]>([]);
  const [entries, setEntries] = useState<Entry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    const thirtyDaysAgo = daysAgoMs(30);

    const [habitsRes, entriesRes] = await Promise.all([
      supabase
        .from("habits")
        .select("id, device_id, name, description, color, archived, type, target_value, unit, freq_num, freq_den")
        .eq("archived", 0)
        .order("position"),
      supabase
        .from("entries")
        .select("habit_id, device_id, timestamp, value")
        .gte("timestamp", thirtyDaysAgo),
    ]);

    if (habitsRes.error) console.error("Failed to load habits:", habitsRes.error);
    if (entriesRes.error) console.error("Failed to load entries:", entriesRes.error);
    setHabits(habitsRes.data ?? []);
    setEntries(entriesRes.data ?? []);
    setLoading(false);
  };

  if (loading) {
    return <div style={s.loading}>Loading habits...</div>;
  }

  // Per-habit entries and streaks
  const habitData = habits.map((h) => {
    const hEntries = entries.filter(
      (e) => e.habit_id === h.id && e.device_id === h.device_id
    );
    return {
      habit: h,
      streak: computeStreak(hEntries),
      week: last7Days(hEntries),
    };
  });

  // Summary stats
  const totalHabits = habits.length;
  const completedToday = habitData.filter((d) => d.week[6]).length;
  const longestStreak = habitData.reduce(
    (max, d) => Math.max(max, d.streak),
    0
  );

  // Day labels for the last 7 days, starting from 6 days ago
  const dayLabels: string[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(daysAgoMs(i));
    dayLabels.push(DAY_LABELS[d.getDay() === 0 ? 6 : d.getDay() - 1]);
  }

  return (
    <div style={s.container}>
      <div style={s.header}>
        <h1 style={s.title}>HabitLoop</h1>
        <div>
          <button style={s.linkBtn} onClick={onSetup}>
            Link Device
          </button>
          <button style={s.signOutBtn} onClick={signOut}>
            Sign Out
          </button>
        </div>
      </div>

      <div style={s.statsRow}>
        <div style={s.statCard}>
          <p style={s.statValue}>{completedToday}</p>
          <p style={s.statLabel}>Done today</p>
        </div>
        <div style={s.statCard}>
          <p style={s.statValue}>{totalHabits}</p>
          <p style={s.statLabel}>Active habits</p>
        </div>
        <div style={s.statCard}>
          <p style={s.statValue}>{longestStreak}</p>
          <p style={s.statLabel}>Best streak</p>
        </div>
      </div>

      {habitData.length === 0 ? (
        <div style={s.empty}>
          <p>No habits found.</p>
          <p>Link a device to see your habits here.</p>
        </div>
      ) : (
        habitData.map(({ habit, streak, week }) => (
          <div key={`${habit.device_id}-${habit.id}`} style={s.habitCard}>
            <div style={s.habitHeader}>
              <p style={s.habitName}>{habit.name}</p>
              {streak > 0 && (
                <span style={s.streakBadge}>{streak}d streak</span>
              )}
            </div>
            <div style={s.weekRow}>
              {week.map((done, i) => (
                <div key={i} style={s.dayDot(done)}>
                  {dayLabels[i]}
                </div>
              ))}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
