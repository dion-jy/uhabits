import { useState } from "react";
import { useAuth } from "../hooks/useAuth";
import { supabase } from "../lib/supabase";

const styles = {
  container: {
    maxWidth: 520,
    margin: "60px auto",
    padding: "0 20px",
    fontFamily: "system-ui, sans-serif",
  },
  card: {
    background: "#fff",
    borderRadius: 12,
    padding: "32px",
    boxShadow: "0 2px 12px rgba(0,0,0,0.08)",
    marginBottom: 24,
  },
  h2: { margin: "0 0 16px", fontSize: 20, fontWeight: 600 },
  step: { margin: "0 0 8px", color: "#444", fontSize: 14, lineHeight: 1.6 },
  code: {
    display: "block",
    background: "#f4f4f4",
    padding: "12px 16px",
    borderRadius: 8,
    fontSize: 13,
    fontFamily: "monospace",
    overflowX: "auto" as const,
    margin: "12px 0",
    wordBreak: "break-all" as const,
  },
  input: {
    padding: "10px 14px",
    fontSize: 14,
    border: "1px solid #ddd",
    borderRadius: 8,
    width: "100%",
    boxSizing: "border-box" as const,
    marginBottom: 12,
  },
  button: {
    padding: "10px 20px",
    fontSize: 14,
    fontWeight: 500,
    border: "none",
    borderRadius: 8,
    background: "#4285F4",
    color: "#fff",
    cursor: "pointer",
    marginRight: 8,
  },
  secondaryButton: {
    padding: "10px 20px",
    fontSize: 14,
    fontWeight: 500,
    border: "1px solid #ddd",
    borderRadius: 8,
    background: "#fff",
    cursor: "pointer",
  },
  status: { marginTop: 12, fontSize: 13, color: "#666" },
};

interface Props {
  onComplete: () => void;
}

export default function SetupGuide({ onComplete }: Props) {
  const { user } = useAuth();
  const [token, setToken] = useState("");
  const [claiming, setClaiming] = useState(false);
  const [result, setResult] = useState<string | null>(null);

  const claimToken = async () => {
    if (!token.trim()) return;
    setClaiming(true);
    setResult(null);
    try {
      const { data, error } = await supabase.rpc("claim_device_link", {
        p_token: token.trim(),
      });
      if (error) {
        setResult(`Error: ${error.message}`);
      } else if (data?.ok) {
        setResult(
          `Device linked successfully! Instance: ${data.instance_id}`
        );
      } else {
        setResult(`Failed: ${data?.error ?? "unknown error"}`);
      }
    } catch (e: unknown) {
      setResult(`Error: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setClaiming(false);
    }
  };

  return (
    <div style={styles.container}>
      <h1 style={{ fontSize: 24, fontWeight: 600, marginBottom: 8 }}>
        Link Your Device
      </h1>
      <p style={{ color: "#666", fontSize: 14, marginBottom: 24 }}>
        Welcome, {user?.email}. Connect your phone to see habits here.
      </p>

      <div style={styles.card}>
        <h2 style={styles.h2}>Step 1: Get a link token</h2>
        <p style={styles.step}>
          Ask your AI agent to run:
        </p>
        <code style={styles.code}>habits link</code>
        <p style={styles.step}>
          The agent will give you a token to paste below.
        </p>
      </div>

      <div style={styles.card}>
        <h2 style={styles.h2}>Step 2: Enter the token</h2>
        <p style={styles.step}>Paste the token you received:</p>
        <input
          style={styles.input}
          type="text"
          placeholder="Paste device link token here..."
          value={token}
          onChange={(e) => setToken(e.target.value)}
        />
        <div>
          <button
            style={styles.button}
            onClick={claimToken}
            disabled={claiming || !token.trim()}
          >
            {claiming ? "Linking..." : "Link Device"}
          </button>
          <button style={styles.secondaryButton} onClick={onComplete}>
            Skip for now
          </button>
        </div>
        {result && <p style={styles.status}>{result}</p>}
      </div>

      <div style={styles.card}>
        <h2 style={styles.h2}>Already linked?</h2>
        <p style={styles.step}>
          If you have already linked a device, go straight to your dashboard.
        </p>
        <button style={styles.button} onClick={onComplete}>
          Go to Dashboard
        </button>
      </div>
    </div>
  );
}
