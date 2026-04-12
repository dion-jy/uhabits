import { useState, useEffect, useCallback } from "react";
import { useAppContainer } from "../core/context";
import type { Habit } from "../core/bridge";

export function useHabitList(): Habit[] {
  const { habitList, commandRunner } = useAppContainer();
  const [habits, setHabits] = useState<Habit[]>([]);

  const refresh = useCallback(() => {
    const list: Habit[] = [];
    for (const habit of habitList.toArray()) {
      if (!habit.isArchived) list.push(habit);
    }
    setHabits(list);
  }, [habitList]);

  useEffect(() => {
    refresh();
    const listener = { onCommandFinished: () => refresh() };
    commandRunner.addListener(listener as any);
    return () => commandRunner.removeListener(listener as any);
  }, [commandRunner, refresh]);

  return habits;
}
