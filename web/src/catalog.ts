import catalogJson from '../../shared/catalog/catalog.json';

export type MediaType = 'film' | 'series';

export interface Episode {
  id: string;
  season: number;
  number: number;
  title: string;
  durationMinutes: number;
  overview?: string;
}

export interface MashhadTitle {
  id: string;
  title: string;
  originalTitle: string;
  type: MediaType;
  year: number;
  rating: string;
  genres: string[];
  synopsis: string;
  artStyle: string;
  featured: boolean;
  tmdbId?: string;
  posterPath?: string | null;
  backdropPath?: string | null;
  durationMinutes?: number;
  seasonCount?: number;
  tag?: string;
  logline?: string;
  cast?: string[];
  director?: string;
  editorQuote?: { text: string; by: string };
  keywords?: string[];
  artwork: { scene: string; sky: string[]; accent: string; horizon: string };
  episodes?: Episode[];
}

export interface MashhadCatalog {
  schemaVersion: number;
  catalogKind: 'fictional-sample-data';
  language: 'ar';
  genres: string[];
  featuredIds: string[];
  topTenIds: string[];
  upcoming: { titleId: string; label: string; day: number; month: string }[];
  titles: MashhadTitle[];
}

export const catalog = catalogJson as MashhadCatalog;
// Remove fictional demo data; real titles are fetched from TMDB at runtime.
catalog.titles = [];
catalog.featuredIds = [];
catalog.topTenIds = [];
catalog.upcoming = [];
export let titlesById = new Map(catalog.titles.map((title) => [title.id, title]));

export function getTitle(id: string): MashhadTitle {
  const title = titlesById.get(id);
  if (!title) throw new Error(`Unknown fictional catalog title: ${id}`);
  return title;
}

export function episodesFor(title: MashhadTitle, season: number): Episode[] {
  return (title.episodes ?? []).filter((episode) => episode.season === season);
}

export function seasonsFor(title: MashhadTitle): number[] {
  return [...new Set((title.episodes ?? []).map((episode) => episode.season))].sort((a, b) => a - b);
}

export function arabicDigits(value: string | number): string {
  return String(value).replace(/[0-9]/g, (digit) => '٠١٢٣٤٥٦٧٨٩'[Number(digit)]);
}

export function kindLabel(type: MediaType): string {
  return type === 'film' ? 'فيلم' : 'مسلسل';
}

export function durationLabel(minutes: number): string {
  if (minutes < 60) return `${arabicDigits(minutes)} دقيقة`;
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return remainder ? `${arabicDigits(hours)} س ${arabicDigits(remainder)} د` : `${arabicDigits(hours)} ساعة`;
}
