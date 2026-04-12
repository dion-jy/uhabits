// Re-export core classes for convenient access throughout the web app.
// The generated .d.mts from Kotlin/JS provides type information.
export {
  Habit,
  Entry,
  EntryList,
  Frequency,
  PaletteColor,
  HabitType,
  HabitList,
  SQLModelFactory,
  CommandRunner,
  CreateHabitCommand,
  EditHabitCommand,
  CreateRepetitionCommand,
  DeleteHabitsCommand,
  JsDatabase,
  JsDatabaseOpener,
  migrateDatabase,
  LightTheme,
  DarkTheme,
  createTaskRunner,
  Score,
  ScoreList,
  LocalDate,
  getToday,
  setToday,
  nextToggleValueJs,
} from "uhabits-core";

export type {
  Command,
  Database,
  ModelObservable,
  ModelFactory,
  KtList,
} from "uhabits-core";

// Constants re-exported for convenience (mirrors Entry companion object)
export const EntryValue = {
  YES_MANUAL: 2,
  YES_AUTO: 1,
  NO: 0,
  SKIP: 3,
  UNKNOWN: -1,
} as const;
