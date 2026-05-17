#!/usr/bin/env python3
"""
P18 HabitLoop — Agent client for reading habits/entries and writing coaching.

Uses service_role key (bypasses RLS) so agent can see all devices.
Intended to run from claude-workspace, not on the Android device.

Usage:
    python agent_client.py habits                   # list all habits
    python agent_client.py entries [--days 7]       # recent entries
    python agent_client.py coach <device_id> <msg>  # send coaching
    python agent_client.py summary                  # per-habit streak summary
"""

import argparse
import json
import os
import sys
from datetime import datetime, timedelta, timezone
from urllib.request import Request, urlopen
from urllib.error import HTTPError

SUPABASE_URL = os.environ.get(
    "SUPABASE_URL",
    "https://vzhkkxqwqtqajnutpjmt.supabase.co",
)
SUPABASE_KEY = os.environ.get(
    "SUPABASE_SERVICE_ROLE_KEY",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ6aGtreHF3cXRxYWpudXRwam10Iiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3ODg1MjYyNSwiZXhwIjoyMDk0NDI4NjI1fQ.S09LTGtR9DQV7sJZg9jhDb5zwTRBNts9xpv6olF1bwY",
)
REST = f"{SUPABASE_URL}/rest/v1"


def _headers(extra=None):
    h = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
    }
    if extra:
        h.update(extra)
    return h


def _get(path):
    req = Request(f"{REST}/{path}", headers=_headers())
    with urlopen(req) as resp:
        return json.loads(resp.read())


def _post(path, data, extra_headers=None):
    body = json.dumps(data).encode()
    req = Request(f"{REST}/{path}", data=body, headers=_headers(extra_headers))
    with urlopen(req) as resp:
        return json.loads(resp.read())



def cmd_habits(args):
    params = "select=*&order=position.asc"
    habits = _get(f"habits?{params}")
    if not habits:
        print("No habits synced yet.")
        return
    for h in habits:
        status = "archived" if h["archived"] else "active"
        freq = f"{h['freq_num']}/{h['freq_den']}"
        kind = "numerical" if h["type"] == 1 else "boolean"
        print(f"  [{h['device_id'][:8]}] id={h['id']} {h['name']} ({kind}, {freq}, {status})")


def cmd_entries(args):
    days = args.days
    cutoff_ms = int((datetime.now(timezone.utc) - timedelta(days=days)).timestamp() * 1000)
    params = f"select=*,habits!inner(name)&timestamp=gte.{cutoff_ms}&order=timestamp.desc"
    try:
        entries = _get(f"entries?{params}")
    except HTTPError:
        # fallback without join if foreign key not set up
        entries = _get(f"entries?select=*&timestamp=gte.{cutoff_ms}&order=timestamp.desc")
    if not entries:
        print(f"No entries in last {days} days.")
        return
    for e in entries:
        dt = datetime.fromtimestamp(e["timestamp"] / 1000, tz=timezone.utc)
        habit_name = e.get("habits", {}).get("name", f"habit_id={e['habit_id']}")
        val_map = {2: "YES", 1: "AUTO", 0: "NO", 3: "SKIP"}
        val = val_map.get(e["value"], str(e["value"]))
        notes = f" — {e['notes']}" if e.get("notes") else ""
        print(f"  {dt:%Y-%m-%d} {habit_name}: {val}{notes}")


def cmd_coach(args):
    data = {
        "device_id": args.device_id,
        "message": args.message,
        "type": args.type,
        "habit_uuid": args.habit_uuid,
    }
    if args.metadata:
        data["metadata"] = json.loads(args.metadata)
    result = _post("coaching", data, {"Prefer": "return=representation"})
    print(f"Coaching sent: id={result[0]['id']}")


def cmd_summary(args):
    habits = _get("habits?select=*&order=position.asc")
    if not habits:
        print("No habits synced yet.")
        return
    cutoff_ms = int((datetime.now(timezone.utc) - timedelta(days=90)).timestamp() * 1000)
    entries = _get(f"entries?select=habit_id,timestamp,value&timestamp=gte.{cutoff_ms}&order=timestamp.desc")

    by_habit = {}
    for e in entries:
        by_habit.setdefault(e["habit_id"], []).append(e)

    print(f"{'Habit':<30} {'Last 7d':>7} {'Last 30d':>8} {'Streak':>7}")
    print("-" * 55)

    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    day_ms = 86400 * 1000

    for h in habits:
        if h["archived"]:
            continue
        hid = h["id"]
        device = h["device_id"]
        es = sorted(by_habit.get(hid, []), key=lambda x: x["timestamp"], reverse=True)
        completed = [e for e in es if e["value"] in (1, 2)]

        last_7 = sum(1 for e in completed if now_ms - e["timestamp"] < 7 * day_ms)
        last_30 = sum(1 for e in completed if now_ms - e["timestamp"] < 30 * day_ms)

        # streak: consecutive days from today
        streak = 0
        if completed:
            day_set = set()
            for e in completed:
                day_set.add(e["timestamp"] // day_ms)
            today_day = now_ms // day_ms
            for i in range(90):
                if (today_day - i) in day_set:
                    streak += 1
                else:
                    break

        print(f"  {h['name']:<28} {last_7:>7} {last_30:>8} {streak:>7}")


def cmd_check(args):
    """Write an entry for a habit (agent -> app via Supabase)."""
    device_id = args.device_id
    habit_name = args.habit
    value_str = args.value.upper()
    date_str = args.date or datetime.now(timezone.utc).strftime("%Y-%m-%d")

    val_map = {"YES": 2, "NO": 0, "SKIP": 3}
    if value_str in val_map:
        value = val_map[value_str]
    else:
        try:
            value = int(value_str)
        except ValueError:
            print(f"Invalid value: {value_str}. Use YES/NO/SKIP or integer.")
            return

    # Resolve habit name to id
    habits = _get(f"habits?device_id=eq.{device_id}&name=eq.{habit_name}&select=id")
    if not habits:
        print(f"Habit '{habit_name}' not found for device {device_id}")
        return
    habit_id = habits[0]["id"]

    # Convert date to unix millis
    dt = datetime.strptime(date_str, "%Y-%m-%d").replace(tzinfo=timezone.utc)
    timestamp_ms = int(dt.timestamp() * 1000)

    data = {
        "device_id": device_id,
        "habit_id": habit_id,
        "timestamp": timestamp_ms,
        "value": value,
        "notes": args.notes or "",
        "source": "agent",
    }
    _post(
        "entries?on_conflict=device_id,habit_id,timestamp",
        data,
        {"Prefer": "return=representation,resolution=merge-duplicates"},
    )
    print(f"Checked '{habit_name}' = {value_str} on {date_str} (source=agent)")


def main():
    parser = argparse.ArgumentParser(description="P18 HabitLoop agent client")
    sub = parser.add_subparsers(dest="command")

    sub.add_parser("habits", help="List all habits")

    p_entries = sub.add_parser("entries", help="List recent entries")
    p_entries.add_argument("--days", type=int, default=7)

    p_coach = sub.add_parser("coach", help="Send coaching message")
    p_coach.add_argument("device_id")
    p_coach.add_argument("message")
    p_coach.add_argument("--type", default="nudge", choices=["nudge", "insight", "celebration"])
    p_coach.add_argument("--habit-uuid", default=None)
    p_coach.add_argument("--metadata", default=None, help="JSON string")

    p_check = sub.add_parser("check", help="Check/uncheck a habit (agent -> app)")
    p_check.add_argument("device_id")
    p_check.add_argument("habit", help="Habit name")
    p_check.add_argument("value", help="YES/NO/SKIP or integer for numerical")
    p_check.add_argument("--date", default=None, help="YYYY-MM-DD (default: today)")
    p_check.add_argument("--notes", default=None)

    sub.add_parser("summary", help="Per-habit streak summary")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        return

    {"habits": cmd_habits, "entries": cmd_entries, "coach": cmd_coach, "summary": cmd_summary, "check": cmd_check}[
        args.command
    ](args)


if __name__ == "__main__":
    main()
