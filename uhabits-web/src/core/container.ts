import type { Database as SqlJsDb } from "sql.js";
import {
  Habit,
  JsDatabase,
  SQLModelFactory,
  CommandRunner,
  CreateHabitCommand,
  Frequency,
  PaletteColor,
  HabitType,
  Entry,
  createTaskRunner,
  createPreferences,
  createListHabitsBehavior,
  setToday,
  getToday,
  LocalDate,
} from "./bridge";
import type { HabitList, ModelFactory } from "./bridge";
import type { ListHabitsBehavior, Preferences } from "uhabits-core";

type TaskRunner = ReturnType<typeof createTaskRunner>;
import { createSqlJsDatabase } from "./database";

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

function nextGaussian(): number {
  const u1 = Math.random();
  const u2 = Math.random();
  return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
}

function randomize(habit: InstanceType<typeof Habit>) {
  const YES_MANUAL = 2;
  habit.originalEntries.clear();
  let strength = 50.0;
  const today = getToday();
  for (let i = 0; i < 365 * 5; i++) {
    if (i % 7 === 0) strength = Math.max(0, Math.min(100, strength + 10 * nextGaussian()));
    if (Math.random() * 100 > strength) continue;
    let value = YES_MANUAL;
    if (habit.isNumerical) {
      value = Math.round(1000 + 250 * nextGaussian() * strength / 100) * 1000;
    }
    habit.originalEntries.add(new Entry(today.minus(i), value));
  }
}

export async function createAppContainer(): Promise<AppContainer> {
  updateToday();
  await loadFonts();

  const sqlDb = await createSqlJsDatabase();
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
    for (const habit of container.habitList.toArray()) {
      randomize(habit);
    }
  }

  // Recompute all habits (mirrors HabitsApplication.kt startup)
  for (const habit of container.habitList.toArray()) {
    habit.recompute();
  }

  return container;
}
