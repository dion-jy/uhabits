import { useAuth } from "../hooks/useAuth";

const styles = {
  container: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    justifyContent: "center",
    minHeight: "100vh",
    fontFamily: "system-ui, sans-serif",
    background: "#fafafa",
  },
  card: {
    background: "#fff",
    borderRadius: 12,
    padding: "48px 40px",
    boxShadow: "0 2px 12px rgba(0,0,0,0.08)",
    textAlign: "center" as const,
    maxWidth: 380,
  },
  title: { margin: "0 0 8px", fontSize: 24, fontWeight: 600 },
  subtitle: { margin: "0 0 32px", color: "#666", fontSize: 14 },
  button: {
    display: "inline-flex",
    alignItems: "center",
    gap: 8,
    padding: "12px 24px",
    fontSize: 15,
    fontWeight: 500,
    border: "1px solid #ddd",
    borderRadius: 8,
    background: "#fff",
    cursor: "pointer",
  },
};

export default function Login() {
  const { signInWithGoogle } = useAuth();

  return (
    <div style={styles.container}>
      <div style={styles.card}>
        <h1 style={styles.title}>HabitLoop</h1>
        <p style={styles.subtitle}>
          Sign in to view your habits and streaks
        </p>
        <button style={styles.button} onClick={signInWithGoogle}>
          <svg width="18" height="18" viewBox="0 0 48 48">
            <path
              fill="#4285F4"
              d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
            />
            <path
              fill="#34A853"
              d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
            />
            <path
              fill="#FBBC05"
              d="M10.53 28.59a14.5 14.5 0 010-9.18l-7.98-6.19a24.1 24.1 0 000 21.56l7.98-6.19z"
            />
            <path
              fill="#EA4335"
              d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
            />
          </svg>
          Continue with Google
        </button>
      </div>
    </div>
  );
}
