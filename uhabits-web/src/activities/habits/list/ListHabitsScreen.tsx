import { useState, useCallback, useMemo } from "react";
import {
  useAppContainer,
  useListHabitsContainer,
  ListHabitsProvider,
} from "../../../core/context";
import { ListHabitsContainer } from "../../../core/container";
import { useHabitList } from "../../../hooks/useHabitList";
import { useCheckmarkCount } from "../../../hooks/useCheckmarkCount";
import { useTheme } from "../../../theme/ThemeProvider";
import { Header } from "./views/Header";
import { HabitCard } from "./views/HabitCard";
import { EmptyState } from "./views/EmptyState";
import { JsView } from "../../common/views/JsView";
import {
  getToday,
  deleteHabitCommand,
  CreateHabitCommand,
  Frequency,
  PaletteColor,
  HabitType,
  HabitListHeader,
  JsLocalDateFormatter,
} from "uhabits-core";
import styles from "./ListHabitsScreen.module.css";

const fmt = new JsLocalDateFormatter();
const HEADER_HEIGHT = 48;

export function ListHabitsScreen() {
  const app = useAppContainer();
  const screenContainer = useMemo(
    () => new ListHabitsContainer(app),
    [app],
  );

  return (
    <ListHabitsProvider container={screenContainer}>
      <ListHabitsScreenContent />
    </ListHabitsProvider>
  );
}

function ListHabitsScreenContent() {
  const { commandRunner, modelFactory, habitList } = useAppContainer();
  const { behavior } = useListHabitsContainer();
  const habits = useHabitList();
  const [listRef, checkmarkCount, listWidth] = useCheckmarkCount();
  const { theme } = useTheme();
  const [isCreating, setIsCreating] = useState(false);
  const [editName, setEditName] = useState("");

  const handleToggle = useCallback(
    (habitId: bigint, dateOffset: number) => {
      const habit = habitList.getById(habitId);
      if (!habit) return;
      const today = getToday();
      const date = today.minus(dateOffset);
      behavior.onToggleComputed(habit, date);
    },
    [habitList, behavior],
  );

  const handleDelete = useCallback(
    (habitId: bigint) => {
      const habit = habitList.getById(habitId);
      if (!habit) return;
      if (!confirm(`Delete "${habit.name}"?`)) return;
      commandRunner.run(deleteHabitCommand(habitList, habit));
    },
    [habitList, commandRunner],
  );

  const handleCreate = useCallback(() => {
    if (!editName.trim()) return;
    const habit = modelFactory.buildHabit();
    habit.name = editName.trim();
    habit.frequency = new Frequency(1, 1);
    habit.color = new PaletteColor(11);
    habit.type = HabitType.YES_NO;
    commandRunner.run(new CreateHabitCommand(modelFactory, habitList, habit));
    setEditName("");
    setIsCreating(false);
  }, [editName, modelFactory, habitList, commandRunner]);

  return (
    <div className={styles.screen} ref={listRef}>
      <Header onAddClick={() => setIsCreating(true)} />

      {habits.length === 0 && !isCreating ? (
        <EmptyState onCreateClick={() => setIsCreating(true)} />
      ) : (
        <>
          {listWidth > 0 && (
            <JsView
              className={styles.listHeader}
              view={new HabitListHeader(getToday(), checkmarkCount, theme, fmt)}
              width={listWidth}
              height={HEADER_HEIGHT}
            />
          )}
          <div className={styles.habitList}>
            {habits.map((habit) => (
              <HabitCard
                key={Number(habit.id)}
                habit={habit}
                checkmarkCount={checkmarkCount}
                onToggle={(offset) => handleToggle(habit.id!, offset)}
                onEdit={() => {}}
                onDelete={() => handleDelete(habit.id!)}
              />
            ))}
          </div>
        </>
      )}

      {isCreating && (
        <div className={styles.overlay} onClick={() => setIsCreating(false)}>
          <div className={styles.dialog} onClick={(e) => e.stopPropagation()}>
            <h2 className={styles.dialogTitle}>New Habit</h2>
            <input
              className={styles.input}
              type="text"
              placeholder="Habit name"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleCreate()}
              autoFocus
            />
            <div className={styles.dialogActions}>
              <button
                className={styles.cancelBtn}
                onClick={() => setIsCreating(false)}
              >
                Cancel
              </button>
              <button className={styles.saveBtn} onClick={handleCreate}>
                Save
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

