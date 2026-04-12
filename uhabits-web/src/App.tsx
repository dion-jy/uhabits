import { useState, useEffect } from "react";
import { initApp, type AppServices } from "./core/init";
import { AppProvider } from "./core/context";
import * as bridge from "./core/bridge";

export function App() {
  const [services, setServices] = useState<AppServices | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    initApp()
      .then((s) => {
        // Expose on window for console smoke-testing
        (window as any).app = s;
        (window as any).core = bridge;
        setServices(s);
      })
      .catch((e) => setError(e.message));
  }, []);

  if (error) return <div className="error">Failed to load: {error}</div>;
  if (!services) return <div className="loading">Loading...</div>;

  return (
    <AppProvider services={services}>
      <div style={{ padding: 24, fontFamily: "sans-serif" }}>
        <h1>Loop Habit Tracker (Web)</h1>
        <p>
          Core services loaded. Open the browser console and use{" "}
          <code>window.app</code> to interact.
        </p>
      </div>
    </AppProvider>
  );
}
