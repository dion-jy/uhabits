import { useTheme } from "../../../../theme/ThemeProvider";
import styles from "./Header.module.css";

interface HeaderProps {
  onAddClick: () => void;
}

export function Header({ onAddClick }: HeaderProps) {
  const { mode, toggle } = useTheme();

  return (
    <header className={styles.header}>
      <h1 className={styles.title}><b>Loop</b> Habit Tracker</h1>
      <div className={styles.actions}>
        <button
          className={`${styles.button} ${styles.icon}`}
          onClick={onAddClick}
          title="Add habit"
        >
          {"\uF067"}
        </button>
        <button
          className={`${styles.button} ${styles.icon}`}
          onClick={toggle}
          title={`Switch to ${mode === "light" ? "dark" : "light"} mode`}
        >
          {mode === "light" ? "\uF186" : "\uF185"}
        </button>
      </div>
    </header>
  );
}
