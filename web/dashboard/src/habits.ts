// Data contract — verified against tools/supabase-schema.sql,
// tools/migration-007-uuid-identity.sql, and Entry.kt. Do not re-derive.

import { supabase } from "./supabase";

// --- Entry value constants (boolean habits, type 0) -------------------------
// YES_AUTO (1) is computed by the app — never written from here.
export const YES_MANUAL = 2;
export const NO = 0;
// SKIP (3) / UNKNOWN (-1) are out of scope for v1.

// "done" for the per-habit counts = value in (1, 2).
export const DONE_VALUES = new Set([1, 2]);

export const TYPE_BOOLEAN = 0;
export const TYPE_NUMERIC = 1;

export interface Habit {
  uuid: string;
  id: number;
  device_id: string;
  name: string;
  question: string;
  color: number;
  position: number;
  type: number;
  target_value: number;
  unit: string;
  archived: number;
}

export interface Entry {
  habit_uuid: string;
  timestamp: number;
  value: number;
}

// timestamp = UTC-midnight epoch ms of the user's LOCAL calendar date.
// Take local Y/M/D, then Date.UTC(y, m, d). (Do NOT use startOfDay in UTC.)
export function dayTimestamp(d: Date): number {
  return Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
}

export function todayTimestamp(): number {
  return dayTimestamp(new Date());
}

// Newest-first list of the last `n` day-timestamps (index 0 = today).
export function lastNDayTimestamps(n: number): number[] {
  const out: number[] = [];
  const now = new Date();
  for (let i = 0; i < n; i++) {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
    out.push(dayTimestamp(d));
  }
  return out;
}

// --- Loads ------------------------------------------------------------------

export async function fetchHabits(): Promise<Habit[]> {
  const { data, error } = await supabase
    .from("habits")
    .select(
      "uuid,id,device_id,name,question,color,position,type,target_value,unit,archived"
    )
    .eq("archived", 0)
    .order("position", { ascending: true });
  if (error) throw error;
  return (data ?? []) as Habit[];
}

// One query for all entries since today-(days). Grouped client-side by uuid.
export async function fetchRecentEntries(days: number): Promise<Entry[]> {
  const since = dayTimestamp(
    new Date(
      new Date().getFullYear(),
      new Date().getMonth(),
      new Date().getDate() - days
    )
  );
  const { data, error } = await supabase
    .from("entries")
    .select("habit_uuid,timestamp,value")
    .gte("timestamp", since);
  if (error) throw error;
  return (data ?? []) as Entry[];
}

export type EntryMap = Map<string, Map<number, number>>;

export function groupEntries(entries: Entry[]): EntryMap {
  const map: EntryMap = new Map();
  for (const e of entries) {
    if (!e.habit_uuid) continue;
    let byTs = map.get(e.habit_uuid);
    if (!byTs) {
      byTs = new Map();
      map.set(e.habit_uuid, byTs);
    }
    byTs.set(e.timestamp, e.value);
  }
  return map;
}

// --- Write a check-in (PostgREST upsert on (habit_uuid, timestamp)) ---------
// NOTE: `notes` is deliberately OMITTED — merge-duplicates SETs every supplied
// column, so sending notes would clobber notes the user typed in the app.
export async function writeCheckin(
  habit: Habit,
  timestamp: number,
  value: number,
  userId: string
): Promise<void> {
  const { error } = await supabase
    .from("entries")
    .upsert(
      {
        device_id: habit.device_id,
        habit_id: habit.id,
        habit_uuid: habit.uuid,
        timestamp,
        value,
        source: "agent", // what the Android app's sync pulls
        user_id: userId, // MUST equal session.user.id or RLS rejects it
      },
      { onConflict: "habit_uuid,timestamp" }
    );
  if (error) throw error;
}
