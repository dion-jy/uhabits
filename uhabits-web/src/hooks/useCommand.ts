import { useCallback } from "react";
import { useAppContainer } from "../core/context";
import type { Command } from "../core/bridge";

export function useCommand() {
  const { commandRunner } = useAppContainer();
  return useCallback(
    (command: Command) => commandRunner.run(command),
    [commandRunner],
  );
}
