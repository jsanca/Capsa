/**
 * Lightweight pub-sub for changes to the active `AuthProvider`.
 *
 * Decouples the holder from React so non-component code can notify without
 * importing React.
 */
type Listener = () => void;
const listeners = new Set<Listener>();

export function subscribeAuthChange(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function notifyAuthChange(): void {
  for (const listener of Array.from(listeners)) {
    listener();
  }
}