import { useState, useEffect } from "react";
import { createAppContainer, type AppContainer } from "./core/container";
import { AppProvider } from "./core/context";
import { ThemeProvider } from "./theme/ThemeProvider";
import { ListHabitsScreen } from "./activities/habits/list/ListHabitsScreen";
import { ErrorBoundary } from "./ErrorBoundary";

export function App() {
  const [container, setContainer] = useState<AppContainer | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    createAppContainer()
      .then(setContainer)
      .catch((e) => setError(e.message));
  }, []);

  if (error) return <div className="error">Failed to load: {error}</div>;
  if (!container) return <div className="loading">Loading...</div>;

  return (
    <ErrorBoundary>
      <AppProvider container={container}>
        <ThemeProvider>
          <ListHabitsScreen />
        </ThemeProvider>
      </AppProvider>
    </ErrorBoundary>
  );
}
