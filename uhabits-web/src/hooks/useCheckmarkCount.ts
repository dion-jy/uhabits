import { useState, useEffect, useRef } from "react";

const LABEL_WIDTH = 200;
const BUTTON_WIDTH = 48;
const MAX_CHECKMARKS = 60;

function computeCheckmarkCount(containerWidth: number): number {
  const available = containerWidth - LABEL_WIDTH;
  const count = Math.floor(available / BUTTON_WIDTH);
  return Math.min(Math.max(count, 3), MAX_CHECKMARKS);
}

export function useCheckmarkCount(): [
  React.RefObject<HTMLDivElement | null>,
  number,
  number,
] {
  const ref = useRef<HTMLDivElement>(null);
  const [count, setCount] = useState(5);
  const [width, setWidth] = useState(0);

  useEffect(() => {
    if (!ref.current) return;
    const observer = new ResizeObserver(([entry]) => {
      const w = entry.contentRect.width;
      setWidth(w);
      setCount(computeCheckmarkCount(w));
    });
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, []);

  return [ref, count, width];
}
