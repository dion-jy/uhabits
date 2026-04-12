import { createContext, useContext } from "react";
import type { AppContainer, ListHabitsContainer } from "./container";

// App-scoped context — available everywhere
const AppContext = createContext<AppContainer | null>(null);

export function AppProvider({
  container,
  children,
}: {
  container: AppContainer;
  children: React.ReactNode;
}) {
  return <AppContext.Provider value={container}>{children}</AppContext.Provider>;
}

export function useAppContainer(): AppContainer {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useAppContainer must be used within AppProvider");
  return ctx;
}

// Screen-scoped context — available within a screen
const ListHabitsContext = createContext<ListHabitsContainer | null>(null);

export function ListHabitsProvider({
  container,
  children,
}: {
  container: ListHabitsContainer;
  children: React.ReactNode;
}) {
  return (
    <ListHabitsContext.Provider value={container}>
      {children}
    </ListHabitsContext.Provider>
  );
}

export function useListHabitsContainer(): ListHabitsContainer {
  const ctx = useContext(ListHabitsContext);
  if (!ctx)
    throw new Error(
      "useListHabitsContainer must be used within ListHabitsProvider",
    );
  return ctx;
}
