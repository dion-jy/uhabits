import { JsView } from "../../../common/views/JsView";
import { useTheme, useThemeColor } from "../../../../theme/ThemeProvider";
import {
  getToday,
  CheckmarkButton,
  CheckmarkButtonState,
  NumberButton,
  NumberButtonState,
  HabitType,
  Ring,
} from "uhabits-core";
import type { Habit } from "uhabits-core";
import styles from "./HabitCard.module.css";

interface HabitCardProps {
  habit: Habit;
  checkmarkCount: number;
  onToggle: (dateOffset: number) => void;
  onEdit: () => void;
  onDelete: () => void;
}

const BUTTON_SIZE = 48;
const RING_SIZE = 20;

export function HabitCard({
  habit,
  checkmarkCount,
  onToggle,
  onEdit,
  onDelete,
}: HabitCardProps) {
  const { theme } = useTheme();
  const colorCss = useThemeColor(habit.color.paletteIndex);
  const color = theme.color(habit.color.paletteIndex);
  const today = getToday();
  const score = habit.scores.get(today).value;

  const dateFrom = today.minus(checkmarkCount - 1);
  const entries = habit.computedEntries
    .getByInterval(dateFrom, today)
    .asJsReadonlyArrayView();

  return (
    <div className={styles.cardWrapper}>
      <div className={styles.card} onDoubleClick={onEdit}>
        <JsView
          className={styles.ring}
          view={new Ring(color, score, 3.5, 7, theme, false)}
          width={RING_SIZE}
          height={RING_SIZE}
        />
        <span className={styles.name} style={{ color: colorCss }}>
          {habit.name}
        </span>
        <div className={styles.checkmarks}>
          {entries.map((entry, i) => {
            const isNumerical = habit.type === HabitType.NUMERICAL;
            const view = isNumerical
              ? new NumberButton(
                  new NumberButtonState(
                    entry.value / 1000.0,
                    color,
                    habit.targetValue,
                    habit.targetType.value,
                    habit.unit,
                    theme,
                    false,
                    entry.notes,
                  ),
                )
              : new CheckmarkButton(
                  new CheckmarkButtonState(entry.value, color, theme, false, entry.notes),
                );
            return (
              <JsView
                key={i}
                view={view}
                width={BUTTON_SIZE}
                height={BUTTON_SIZE}
                onClick={() => onToggle(i)}
              />
            );
          })}
        </div>
      </div>
    </div>
  );
}
