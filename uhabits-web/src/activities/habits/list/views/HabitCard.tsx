import { JsView } from "../../../common/views/JsView";
import { useTheme, useThemeColor } from "../../../../theme/ThemeProvider";
import {
  getToday,
  CheckmarkButton,
  Ring,
} from "../../../../core/bridge";
import type { Habit } from "../../../../core/bridge";
import styles from "./HabitCard.module.css";

interface HabitCardProps {
  habit: Habit;
  checkmarkCount: number;
  onToggle: (dateOffset: number) => void;
  onEdit: () => void;
  onDelete: () => void;
}

const BUTTON_SIZE = 48;
const RING_SIZE = 30;

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
          view={new Ring(color, score, 3, 12, theme, false)}
          width={RING_SIZE}
          height={RING_SIZE}
        />
        <span className={styles.name} style={{ color: colorCss }}>
          {habit.name}
        </span>
        <div className={styles.checkmarks}>
          {entries.map((entry, i) => (
            <JsView
              key={i}
              view={new CheckmarkButton(entry.value, color, theme)}
              width={BUTTON_SIZE}
              height={BUTTON_SIZE}
              onClick={() => onToggle(i)}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
