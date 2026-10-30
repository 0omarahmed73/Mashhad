// Watch history kept in this browser's localStorage: what was watched and where it stopped.
// One entry per title (the latest episode for a series), newest first, capped at MAX_ENTRIES.

export type WatchEntry = {
  titleId: string;
  episodeId?: string;
  season?: number;
  number?: number;
  /** Seconds watched; null when the player can't report a position (an embedded third-party player). */
  seconds: number | null;
  duration: number;
  updatedAt: number;
};

const KEY = 'mashhad.watchProgress';
const MAX_ENTRIES = 40;
/** Past this share of the runtime the title counts as finished and drops out of "continue watching". */
export const FINISHED_AT = .95;

export function readWatchProgress(): WatchEntry[] {
  try {
    const value = JSON.parse(window.localStorage.getItem(KEY) ?? '[]') as unknown;
    if (!Array.isArray(value)) return [];
    return value.filter((entry): entry is WatchEntry => Boolean(entry) && typeof entry.titleId === 'string' && typeof entry.duration === 'number')
      .sort((a, b) => b.updatedAt - a.updatedAt);
  } catch {
    return [];
  }
}

export function saveWatchProgress(entry: Omit<WatchEntry, 'updatedAt'>): WatchEntry[] {
  const next = [{ ...entry, updatedAt: Date.now() }, ...readWatchProgress().filter((item) => item.titleId !== entry.titleId)].slice(0, MAX_ENTRIES);
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* storage full or blocked: progress just isn't kept */ }
  return next;
}

export function removeWatchProgress(titleId: string): WatchEntry[] {
  const next = readWatchProgress().filter((item) => item.titleId !== titleId);
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* optional */ }
  return next;
}

export function isFinished(entry: WatchEntry): boolean {
  return entry.seconds !== null && entry.seconds >= entry.duration * FINISHED_AT;
}

/** Where to start a title or episode: the saved second if it is the same episode and not finished, else 0. */
export function resumeSeconds(entries: WatchEntry[], titleId: string, episodeId?: string): number {
  const entry = entries.find((item) => item.titleId === titleId);
  if (!entry || entry.seconds === null || isFinished(entry)) return 0;
  if (episodeId && entry.episodeId && entry.episodeId !== episodeId) return 0;
  return entry.seconds;
}
