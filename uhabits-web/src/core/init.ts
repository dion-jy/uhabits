import type { Database as SqlJsDb } from "sql.js";
import {
  JsDatabase,
  SQLModelFactory,
  CommandRunner,
  createTaskRunner,
  setToday,
  LocalDate,
} from "./bridge";
import type { HabitList, ModelFactory } from "./bridge";
import { createSqlJsDatabase } from "./database";
import { loadFromIndexedDB, saveToIndexedDB } from "./persistence";

export interface AppServices {
  habitList: HabitList;
  modelFactory: ModelFactory;
  commandRunner: CommandRunner;
  sqlDb: SqlJsDb;
}

function updateToday() {
  const now = new Date();
  setToday(
    LocalDate.fromYearMonthDay(
      now.getFullYear(),
      now.getMonth() + 1,
      now.getDate(),
    ),
  );
}

export async function initApp(): Promise<AppServices> {
  // 1. Set today's date for the core library
  updateToday();

  // 2. Try loading saved database from IndexedDB
  const saved = await loadFromIndexedDB();

  // 3. Initialize sql.js + run schema if needed
  const sqlDb = await createSqlJsDatabase(saved ?? undefined);

  // 4. Wrap in Kotlin/JS JsDatabase
  const database = new JsDatabase(sqlDb);

  // 5. Create core services
  const modelFactory = new SQLModelFactory(database);
  const habitList = modelFactory.buildHabitList();
  const taskRunner = createTaskRunner();
  const commandRunner = new CommandRunner(taskRunner);

  // 6. Recompute all habits (mirrors HabitsApplication.kt startup)
  for (const habit of habitList.toArray()) {
    habit.recompute();
  }

  // 7. Wire up auto-save after every command
  commandRunner.addListener({
    onCommandFinished: () => saveToIndexedDB(sqlDb),
  } as any);

  return { habitList, modelFactory, commandRunner, sqlDb };
}
