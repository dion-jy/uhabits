// Loop's habit color palette, indexed by the habits.color int.
// Copied verbatim from uhabits-core Themes.kt (LightTheme.color(index)).
const PALETTE: string[] = [
  "#D32F2F", "#E64A19", "#F57C00", "#FF8F00", "#F9A825",
  "#AFB42B", "#7CB342", "#388E3C", "#00897B", "#00ACC1",
  "#039BE5", "#1976D2", "#303F9F", "#5E35B1", "#8E24AA",
  "#D81B60", "#5D4037", "#424242", "#757575", "#9E9E9E",
];

export function habitColor(index: number): string {
  if (index >= 0 && index < PALETTE.length) return PALETTE[index];
  return "#000000";
}
