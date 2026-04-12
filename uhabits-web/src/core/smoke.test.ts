import { describe, it, expect } from "vitest";
import {
  JsDatabase,
  SQLModelFactory,
  CommandRunner,
  CreateHabitCommand,
  CreateRepetitionCommand,
  Frequency,
  PaletteColor,
  HabitType,
  LocalDate,
  setToday,
  createTaskRunner,
} from "./bridge";
import { createSqlJsDatabase } from "./database";

describe("smoke test", () => {
  it("should create database, add habit, and persist across reload", async () => {
    setToday(LocalDate.fromYearMonthDay(2025, 1, 15));
    const today = LocalDate.fromYearMonthDay(2025, 1, 15);

    // 1. Create a fresh database using the production init path
    const sqlDb = await createSqlJsDatabase();
    const db = new JsDatabase(sqlDb);

    // 2. Build core services
    const modelFactory = new SQLModelFactory(db);
    const habitList = modelFactory.buildHabitList();
    const taskRunner = createTaskRunner();
    const commandRunner = new CommandRunner(taskRunner);

    // 3. Create a habit
    const habit = modelFactory.buildHabit();
    habit.name = "Meditate";
    habit.frequency = new Frequency(1, 1);
    habit.color = new PaletteColor(5);
    habit.type = HabitType.YES_NO;
    commandRunner.run(new CreateHabitCommand(modelFactory, habitList, habit));

    // 4. Verify the habit exists
    const habits = habitList.toArray();
    expect(habits).toHaveLength(1);
    expect(habits[0].name).toBe("Meditate");

    // 5. Toggle a checkmark
    commandRunner.run(
      new CreateRepetitionCommand(habitList, habits[0], today, 2, ""),
    );
    expect(habits[0].computedEntries.get(today).value).toBe(2);

    // 6. Export DB bytes and reload from them
    const exported = sqlDb.export();
    sqlDb.close();

    const sqlDb2 = await createSqlJsDatabase(exported);
    const db2 = new JsDatabase(sqlDb2);
    const modelFactory2 = new SQLModelFactory(db2);
    const habitList2 = modelFactory2.buildHabitList();
    for (const h of habitList2.toArray()) h.recompute();

    // 7. Verify the habit and entry survived
    const habits2 = habitList2.toArray();
    expect(habits2).toHaveLength(1);
    expect(habits2[0].name).toBe("Meditate");
    expect(habits2[0].computedEntries.get(today).value).toBe(2);
  });
});
