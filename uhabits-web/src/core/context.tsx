import { createContext, useContext } from "react";
import type { AppServices } from "./init";

const AppContext = createContext<AppServices | null>(null);

export function AppProvider({
  services,
  children,
}: {
  services: AppServices;
  children: React.ReactNode;
}) {
  return <AppContext.Provider value={services}>{children}</AppContext.Provider>;
}

export function useApp(): AppServices {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used within AppProvider");
  return ctx;
}
