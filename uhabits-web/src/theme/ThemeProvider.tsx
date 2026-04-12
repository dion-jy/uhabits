import { createContext, useContext, useState, useEffect, useMemo } from "react";
import { LightTheme, DarkTheme } from "../core/bridge";
import type { Color } from "uhabits-core";

type ThemeMode = "light" | "dark";
type Theme = InstanceType<typeof LightTheme> | InstanceType<typeof DarkTheme>;

function colorToCss(c: Color): string {
  const r = Math.round(c.red * 255);
  const g = Math.round(c.green * 255);
  const b = Math.round(c.blue * 255);
  return `rgba(${r},${g},${b},${c.alpha})`;
}

interface ThemeContextValue {
  mode: ThemeMode;
  theme: Theme;
  toggle: () => void;
}

const ThemeContext = createContext<ThemeContextValue>(null!);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(() => {
    return (localStorage.getItem("theme") as ThemeMode) ?? "light";
  });

  const toggle = () => {
    setMode((prev) => {
      const next = prev === "light" ? "dark" : "light";
      localStorage.setItem("theme", next);
      return next;
    });
  };

  const theme: Theme = useMemo(
    () => (mode === "light" ? new LightTheme() : new DarkTheme()),
    [mode],
  );

  useEffect(() => {
    const root = document.documentElement;
    root.style.setProperty("--app-bg", colorToCss(theme.appBackgroundColor));
    root.style.setProperty("--card-bg", colorToCss(theme.cardBackgroundColor));
    root.style.setProperty("--header-bg", colorToCss(theme.headerBackgroundColor));
    root.style.setProperty("--high-contrast", colorToCss(theme.highContrastTextColor));
    root.style.setProperty("--medium-contrast", colorToCss(theme.mediumContrastTextColor));
    root.style.setProperty("--low-contrast", colorToCss(theme.lowContrastTextColor));
  }, [mode]);

  return (
    <ThemeContext.Provider value={{ mode, toggle, theme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  return useContext(ThemeContext);
}

export function useThemeColor(paletteIndex: number): string {
  const { theme } = useTheme();
  return colorToCss(theme.color(paletteIndex));
}
