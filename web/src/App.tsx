import { useEffect, useState } from "react";
import { AuthProvider, useAuth } from "./hooks/useAuth";
import { supabase } from "./lib/supabase";
import Login from "./components/Login";
import SetupGuide from "./components/SetupGuide";
import Dashboard from "./components/Dashboard";

type View = "setup" | "dashboard" | "loading";

function AppContent() {
  const { user, loading } = useAuth();
  const [view, setView] = useState<View>("loading");

  useEffect(() => {
    if (!user) return;
    supabase
      .from("device_links")
      .select("id")
      .eq("user_id", user.id)
      .eq("used", true)
      .limit(1)
      .then(({ data }) => {
        setView(data && data.length > 0 ? "dashboard" : "setup");
      });
  }, [user]);

  if (loading || (user && view === "loading")) {
    return (
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          minHeight: "100vh",
          fontFamily: "system-ui, sans-serif",
          color: "#888",
        }}
      >
        Loading...
      </div>
    );
  }

  if (!user) return <Login />;
  if (view === "setup") return <SetupGuide onComplete={() => setView("dashboard")} />;
  return <Dashboard onSetup={() => setView("setup")} />;
}

export default function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}
