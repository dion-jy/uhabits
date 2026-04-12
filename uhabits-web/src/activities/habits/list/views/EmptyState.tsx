import styles from "./EmptyState.module.css";

interface EmptyStateProps {
  onCreateClick: () => void;
}

export function EmptyState({ onCreateClick }: EmptyStateProps) {
  return (
    <div className={styles.container}>
      <p className={styles.message}>
        You don't have any habits yet.
      </p>
      <button className={styles.button} onClick={onCreateClick}>
        Create your first habit
      </button>
    </div>
  );
}
