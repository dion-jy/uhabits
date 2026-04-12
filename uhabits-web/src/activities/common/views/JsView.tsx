import { useRef, useEffect } from "react";
import { JsCanvas } from "../../../core/bridge";
import type { View } from "uhabits-core";

interface CoreViewProps {
  view: View;
  width: number;
  height: number;
  onClick?: () => void;
  className?: string;
}

const PIXEL_SCALE = 2;

export function JsView({ view, width, height, onClick, className }: CoreViewProps) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const htmlCanvas = document.createElement("canvas");
    htmlCanvas.width = width * PIXEL_SCALE;
    htmlCanvas.height = height * PIXEL_SCALE;
    htmlCanvas.style.width = `${width}px`;
    htmlCanvas.style.height = `${height}px`;
    const canvas = new JsCanvas(htmlCanvas, PIXEL_SCALE);
    view.draw(canvas);
    el.replaceChildren(htmlCanvas);
  }, [view, width, height]);

  return <div ref={ref} onClick={onClick} className={className} />;
}
