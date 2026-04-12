import initSqlJs, { type Database as SqlJsDb } from "sql.js";
import sqlWasmUrl from "sql.js/dist/sql-wasm.wasm?url";

// Fresh schema equivalent to migrations 09 through 25 on an empty database.
// The Checkmarks, Streak, and Score tables (dropped in migration 20) are
// omitted — all computed data lives in memory.
const FRESH_SCHEMA = `
CREATE TABLE IF NOT EXISTS Habits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    archived INTEGER DEFAULT 0,
    color INTEGER DEFAULT 8,
    description TEXT DEFAULT '',
    freq_den INTEGER DEFAULT 1,
    freq_num INTEGER DEFAULT 1,
    highlight INTEGER DEFAULT 0,
    name TEXT DEFAULT '',
    position INTEGER DEFAULT 0,
    reminder_hour INTEGER,
    reminder_min INTEGER,
    reminder_days INTEGER DEFAULT 127,
    type INTEGER DEFAULT 0,
    target_type INTEGER DEFAULT 0,
    target_value REAL DEFAULT 0,
    unit TEXT DEFAULT '',
    question TEXT DEFAULT '',
    uuid TEXT
);

CREATE TABLE IF NOT EXISTS Repetitions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    habit INTEGER NOT NULL REFERENCES Habits(id),
    timestamp INTEGER NOT NULL,
    value INTEGER NOT NULL DEFAULT 2,
    notes TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_repetitions_habit_timestamp
    ON Repetitions(habit, timestamp);

CREATE TABLE IF NOT EXISTS Events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp INTEGER,
    message TEXT,
    server_id INTEGER
);

PRAGMA user_version = 25;
`;

export async function createSqlJsDatabase(
  existingData?: Uint8Array,
): Promise<SqlJsDb> {
  const SQL = await initSqlJs({
    locateFile: () => sqlWasmUrl,
  });

  const db = existingData ? new SQL.Database(existingData) : new SQL.Database();

  // Check current schema version
  const result = db.exec("PRAGMA user_version");
  const currentVersion =
    (result[0]?.values[0]?.[0] as number | undefined) ?? 0;

  if (currentVersion < 25) {
    // Run the fresh schema (idempotent with IF NOT EXISTS)
    for (const stmt of FRESH_SCHEMA.split(";").filter((s) => s.trim())) {
      db.run(stmt);
    }
  }

  return db;
}
