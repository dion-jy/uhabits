import type { Database as SqlJsDb } from "sql.js";
import {
  JsDatabase,
  SQLModelFactory,
  CommandRunner,
  CreateHabitCommand,
  Frequency,
  PaletteColor,
  HabitType,
  createTaskRunner,
  createPreferences,
  createListHabitsBehavior,
  setToday,
  LocalDate,
} from "./bridge";
import type { HabitList, ModelFactory } from "./bridge";
import type { ListHabitsBehavior, Preferences } from "uhabits-core";

type TaskRunner = ReturnType<typeof createTaskRunner>;
import { createSqlJsDatabase } from "./database";
import { loadFromIndexedDB, saveToIndexedDB } from "./persistence";

/**
 * App-scoped container — mirrors HabitsApplicationComponent on Android.
 * Created once at startup, lives for the lifetime of the app.
 */
export class AppContainer {
  readonly database: JsDatabase;
  readonly modelFactory: ModelFactory;
  readonly habitList: HabitList;
  readonly taskRunner: TaskRunner;
  readonly commandRunner: CommandRunner;
  readonly preferences: Preferences;

  constructor(readonly sqlDb: SqlJsDb) {
    this.database = new JsDatabase(sqlDb);
    this.modelFactory = new SQLModelFactory(this.database);
    this.habitList = this.modelFactory.buildHabitList();
    this.taskRunner = createTaskRunner();
    this.commandRunner = new CommandRunner(this.taskRunner);
    this.preferences = createPreferences();
  }
}

/**
 * Screen-scoped container for ListHabits — mirrors HabitsActivityComponent
 * bindings for the list screen. Created when the screen mounts, disposed
 * when it unmounts.
 */
export class ListHabitsContainer {
  readonly behavior: ListHabitsBehavior;

  constructor(private app: AppContainer) {
    this.behavior = createListHabitsBehavior(
      app.habitList,
      app.commandRunner,
      app.preferences,
      app.taskRunner,
    );
  }
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

async function loadFonts() {
  await Promise.all([
    document.fonts.load("16px NotoSans"),
    document.fonts.load("bold 16px NotoSansBold"),
    document.fonts.load("16px FontAwesome"),
  ]);
}

export async function createAppContainer(): Promise<AppContainer> {
  updateToday();
  await loadFonts();

  const saved = await loadFromIndexedDB();
  const sqlDb = await createSqlJsDatabase(saved ?? undefined);
  const container = new AppContainer(sqlDb);

  // Seed default habits if database is empty
  if (container.habitList.size() === 0) {
    const names = [
      "Wake up early", "Meditate", "Read books", "Exercise",
      "Cook healthy dinner", "Write journal", "Learn French",
      "Practice guitar", "Play chess", "Call a friend",
    ];
    for (const name of names) {
      const h = container.modelFactory.buildHabit();
      h.name = name;
      h.frequency = new Frequency(1, 1);
      h.color = new PaletteColor(names.indexOf(name) % 20);
      h.type = HabitType.YES_NO;
      new CreateHabitCommand(container.modelFactory, container.habitList, h).run();
    }
  }

  // Recompute all habits (mirrors HabitsApplication.kt startup)
  for (const habit of container.habitList.toArray()) {
    habit.recompute();
  }

  // Auto-save after every command
  container.commandRunner.addListener({
    onCommandFinished: () => {
      saveToIndexedDB(sqlDb).catch((err) => {
        console.error("Failed to persist database to IndexedDB:", err);
      });
    },
  } as any);

  return container;
}
