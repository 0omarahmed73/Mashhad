import type { MashhadTitle, Episode } from './catalog';
const TMDB_BASE = 'https://api.themoviedb.org/3';
const API_KEY = import.meta.env.VITE_TMDB_API_KEY as string | undefined;

export function hasTmdbKey(): boolean {
  return Boolean(API_KEY && API_KEY.trim().length > 0);
}

export type WatchProvider = { id: number; name: string; logoPath: string | null; link: string | null };

type TmdbMovie = {
  id: number;
  title?: string;
  name?: string;
  original_title?: string;
  original_name?: string;
  overview?: string;
  release_date?: string;
  first_air_date?: string;
  genre_ids?: number[];
  genres?: { id: number; name: string }[];
  adult?: boolean;
  poster_path?: string | null;
  backdrop_path?: string | null;
  runtime?: number;
  episode_run_time?: number[];
  seasons?: { season_number: number; name: string; episode_count: number }[];
  popularity?: number;
};

export type SeasonInfo = { number: number; episodeCount: number };

// Real season list for a TMDB series (specials, season 0, are left out).
export async function fetchSeasonList(tmdbId: string): Promise<SeasonInfo[]> {
  const d: TmdbMovie | null = await tmdbFetch(`/tv/${tmdbId}`);
  return (d?.seasons ?? [])
    .filter((s) => s.season_number > 0 && s.episode_count > 0)
    .map((s) => ({ number: s.season_number, episodeCount: s.episode_count }));
}

// Episodes of one season: Arabic overview when TMDB has one, English names because Arabic ones are usually just «الحلقة N».
export async function fetchSeasonEpisodes(tmdbId: string, season: number): Promise<Episode[]> {
  const [en, ar] = await Promise.all([fetchSeason(Number(tmdbId), season), tmdbFetch(`/tv/${tmdbId}/season/${season}`)]);
  const arabicById = new Map<number, any>((ar?.episodes ?? []).map((e: any) => [e.episode_number, e]));
  return (en?.episodes ?? ar?.episodes ?? []).map((e: any) => {
    const arabic = arabicById.get(e.episode_number);
    return {
      id: `tmdb-tv-${tmdbId}-s${season}-e${e.episode_number}`,
      season,
      number: e.episode_number,
      title: e.name || arabic?.name || `Episode ${e.episode_number}`,
      durationMinutes: e.runtime || arabic?.runtime || 45,
      overview: arabic?.overview || e.overview || undefined,
    };
  });
}

const GENRE_MAP: Record<number, string> = {
  28: 'أكشن', 12: 'مغامرة', 16: 'رسوم', 35: 'كوميديا', 80: 'تشويق', 99: 'وثائقي',
  18: 'دراما', 10751: 'عائلي', 14: 'خيال', 36: 'تاريخي', 27: 'رعب', 10402: 'موسيقى',
  9648: 'غموض', 10749: 'رومانسي', 878: 'خيال علمي', 10770: 'portlet', 53: 'تشويق',
  10752: 'حرب', 37: 'غربي', 10765: 'خيال علمي', 10759: 'أكشن ومغامرة', 10768: 'حرب وسياسة',
};

function mapGenres(m: TmdbMovie): string[] {
  return genreNames(m);
}

async function tmdbFetch(path: string): Promise<any> {
  try {
    const res = await fetch(`${TMDB_BASE}${path}${path.includes('?') ? '&' : '?'}api_key=${API_KEY}&language=ar-SA`);
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

async function fetchMovieDetails(id: number): Promise<TmdbMovie | null> {
  return tmdbFetch(`/movie/${id}`);
}

async function fetchTvDetails(id: number): Promise<TmdbMovie | null> {
  return tmdbFetch(`/tv/${id}`);
}

async function fetchSeason(id: number, seasonNumber: number): Promise<any> {
  try {
    const res = await fetch(`${TMDB_BASE}/tv/${id}/season/${seasonNumber}?api_key=${API_KEY}&language=en-US`);
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}


export async function searchTmdbTitles(query: string): Promise<MashhadTitle[]> {
  if (!hasTmdbKey() || !query.trim()) return [];
  const q = encodeURIComponent(query.trim());
  try {
    const [mRes, tRes] = await Promise.all([
      fetch(`${TMDB_BASE}/search/movie?api_key=${API_KEY}&language=ar-SA&query=${q}&page=1`),
      fetch(`${TMDB_BASE}/search/tv?api_key=${API_KEY}&language=ar-SA&query=${q}&page=1`),
    ]);
    const movies: TmdbMovie[] = mRes.ok ? (await mRes.json()).results ?? [] : [];
    const shows: TmdbMovie[] = tRes.ok ? (await tRes.json()).results ?? [] : [];
    const out: (MashhadTitle & { popularity: number })[] = [];
    for (const m of movies.slice(0, 20)) {
      out.push({
        popularity: m.popularity ?? 0,
        id: `tmdb-movie-${m.id}`,
        title: m.original_title || m.title || 'Untitled',
        originalTitle: m.original_title || m.title || 'Untitled',
        type: 'film',
        year: (m.release_date || '').slice(0, 4) ? Number((m.release_date || '').slice(0, 4)) : new Date().getFullYear(),
        rating: m.adult ? '+18' : '+13',
        genres: mapGenres(m),
        synopsis: m.overview || '',
        artStyle: 'city',
        artwork: { scene: 'city', sky: ['#171226', '#563552', '#B86B72'], accent: '#F5AD6C', horizon: '#1D1B2B' },
        featured: false,
        tmdbId: String(m.id),
        posterPath: m.poster_path ?? null,
        backdropPath: m.backdrop_path ?? null,
      });
    }
    for (const s of shows.slice(0, 20)) {
      out.push({
        popularity: s.popularity ?? 0,
        id: `tmdb-tv-${s.id}`,
        title: s.original_name || s.name || 'Untitled',
        originalTitle: s.original_name || s.name || 'Untitled',
        type: 'series',
        year: (s.first_air_date || '').slice(0, 4) ? Number((s.first_air_date || '').slice(0, 4)) : new Date().getFullYear(),
        rating: '+13',
        genres: mapGenres(s),
        synopsis: s.overview || '',
        artStyle: 'city',
        artwork: { scene: 'city', sky: ['#171226', '#563552', '#B86B72'], accent: '#F5AD6C', horizon: '#1D1B2B' },
        featured: false,
        tmdbId: String(s.id),
        posterPath: s.poster_path ?? null,
        backdropPath: s.backdrop_path ?? null,
        episodes: [],
      });
    }
    return out.sort((a, b) => b.popularity - a.popularity).map(({ popularity: _popularity, ...title }) => title);
  } catch {
    return [];
  }
}

export async function fetchTitleById(id: string): Promise<MashhadTitle | null> {
  const m = id.match(/^tmdb-(movie|tv)-(\d+)$/);
  if (!m) return null;
  const isMovie = m[1] === 'movie';
  const numId = Number(m[2]);
  const endpoint = isMovie ? 'movie' : 'tv';
  const d = await tmdbFetch(`/${endpoint}/${numId}`);
  if (!d) return null;
  const originalTitle = (isMovie ? d.original_title : d.original_name) || (isMovie ? d.title : d.name) || 'Untitled';
  const yearStr = isMovie ? d.release_date : d.first_air_date;
  const year = yearStr ? Number(String(yearStr).slice(0, 4)) : new Date().getFullYear();
  const genres = (d.genres ?? []).map((g: any) => g.name).slice(0, 3);
  return {
    id,
    title: originalTitle,
    originalTitle,
    type: isMovie ? 'film' : 'series',
    year: Number.isFinite(year) ? year : new Date().getFullYear(),
    rating: d.adult ? '+18' : '+13',
    genres: genres.length ? genres : ['دراما'],
    synopsis: d.overview || 'لا يوجد وصف متاح.',
    artStyle: 'city',
    artwork: { scene: 'city', sky: ['#171226', '#563552', '#B86B72'], accent: '#F5AD6C', horizon: '#1D1B2B' },
    featured: false,
    tmdbId: String(numId),
    posterPath: d.poster_path ?? null,
    backdropPath: d.backdrop_path ?? null,
    durationMinutes: d.runtime || undefined,
    seasonCount: isMovie ? undefined : (d.seasons?.filter((s: any) => s.season_number > 0).length || 1),
    episodes: [],
  };
}

export async function fetchWatchProviders(tmdbId: string, type: 'film' | 'series'): Promise<{ country: string; providers: WatchProvider[]; link: string | null }> {
  if (!hasTmdbKey()) return { country: 'US', providers: [], link: null };
  const endpoint = type === 'film' ? 'movie' : 'tv';
  try {
    const res = await fetch(`${TMDB_BASE}/${endpoint}/${tmdbId}/watch/providers?api_key=${API_KEY}`);
    if (!res.ok) return { country: 'US', providers: [], link: null };
    const data = await res.json();
    const results = data?.results ?? {};
    const country = results.US ? 'US' : (Object.keys(results)[0] ?? 'US');
    const entry = results[country] ?? {};
    const link = entry.link ?? null;
    // Combine subscription (flatrate), rent, and buy providers, deduped by id.
    const seen = new Set<number>();
    const providers: WatchProvider[] = [];
    for (const group of [entry.flatrate ?? [], entry.rent ?? [], entry.buy ?? []]) {
      for (const p of group) {
        if (seen.has(p.provider_id)) continue;
        seen.add(p.provider_id);
        providers.push({ id: p.provider_id, name: p.provider_name, logoPath: p.logo_path ?? null, link });
      }
    }
    return { country, providers, link };
  } catch {
    return { country: 'US', providers: [], link: null };
  }
}

export async function fetchTmdbTitles(limit = 40, page = 1, onProgress?: (done: number, total: number) => void): Promise<MashhadTitle[]> {
  if (!hasTmdbKey()) return [];
  const results: MashhadTitle[] = [];
  // Two list requests, then one details request per title and one season request per show.
  let done = 0;
  const total = 2 + limit + limit / 2;
  const track = <T,>(promise: Promise<T>): Promise<T> => promise.finally(() => onProgress?.(++done, total));
  try {
    async function safeFetch(url: string): Promise<Response | null> {
      try { return await fetch(url); } catch { return null; }
    }
    const moviesRes = await track(safeFetch(`${TMDB_BASE}/movie/popular?api_key=${API_KEY}&language=ar-SA&page=${page}`));
    const tvRes = await track(safeFetch(`${TMDB_BASE}/tv/popular?api_key=${API_KEY}&language=ar-SA&page=${page}`));
    const movies: TmdbMovie[] = moviesRes && moviesRes.ok ? (await moviesRes.json()).results ?? [] : [];
    const shows: TmdbMovie[] = tvRes && tvRes.ok ? (await tvRes.json()).results ?? [] : [];

    const movieSlice = movies.slice(0, limit / 2);
    const showSlice = shows.slice(0, limit / 2);
    const movieDetails = await Promise.all(movieSlice.map((m) => track(fetchMovieDetails(m.id))));
    const showDetails = await Promise.all(showSlice.map((s) => track(fetchTvDetails(s.id))));
    // Fetch season 1 for all shows in parallel
    const showSeasons = await Promise.all(showSlice.map((s) => track(fetchSeason(s.id, 1))));

    for (let i = 0; i < movieSlice.length; i++) {
      const m = movieSlice[i];
      const d = movieDetails[i];
      const merged = { ...m, ...(d ?? {}) };
      const originalTitle = merged.original_title || merged.title || 'Untitled';
      results.push({
        id: `tmdb-movie-${m.id}`,
        title: originalTitle,
        originalTitle,
        type: 'film',
        year: (merged.release_date || '').slice(0, 4) ? Number((merged.release_date || '').slice(0, 4)) : new Date().getFullYear(),
        rating: merged.adult ? '+18' : '+13',
        genres: mapGenres(merged),
        synopsis: merged.overview || 'لا يوجد وصف متاح.',
        artStyle: 'city',
        artwork: { scene: 'city', sky: ['#171226', '#563552', '#B86B72'], accent: '#F5AD6C', horizon: '#1D1B2B' },
        featured: i < 3,
        tmdbId: String(m.id),
        posterPath: merged.poster_path ?? null,
        backdropPath: merged.backdrop_path ?? null,
        durationMinutes: merged.runtime || undefined,
      });
    }

    for (let i = 0; i < showSlice.length; i++) {
      const s = showSlice[i];
      const d = showDetails[i];
      const merged = { ...s, ...(d ?? {}) };
      const originalTitle = merged.original_name || merged.name || 'Untitled';
      const sd = showSeasons[i];
      const episodes: Episode[] = [];
      const eps = sd?.episodes ?? [];
      for (const e of eps) {
        episodes.push({
          id: `tmdb-tv-${s.id}-s1-e${e.episode_number}`,
          season: 1,
          number: e.episode_number,
          title: e.name || `Episode ${e.episode_number}`,
          durationMinutes: e.runtime || merged.episode_run_time?.[0] || 45,
        });
      }
      if (episodes.length === 0) {
        for (let ei = 0; ei < 8; ei++) episodes.push({ id: `tmdb-tv-${s.id}-s1-e${ei + 1}`, season: 1, number: ei + 1, title: `Episode ${ei + 1}`, durationMinutes: 45 });
      }
      results.push({
        id: `tmdb-tv-${s.id}`,
        title: originalTitle,
        originalTitle,
        type: 'series',
        year: (merged.first_air_date || '').slice(0, 4) ? Number((merged.first_air_date || '').slice(0, 4)) : new Date().getFullYear(),
        rating: '+13',
        genres: mapGenres(merged),
        synopsis: merged.overview || 'لا يوجد وصف متاح.',
        artStyle: 'city',
        artwork: { scene: 'city', sky: ['#171226', '#563552', '#B86B72'], accent: '#F5AD6C', horizon: '#1D1B2B' },
        featured: false,
        tmdbId: String(s.id),
        posterPath: merged.poster_path ?? null,
        backdropPath: merged.backdrop_path ?? null,
        seasonCount: merged.seasons?.filter((sn: any) => sn.season_number > 0).length || 1,
        episodes,
      });
    }
  } catch (e) {
    console.warn('TMDB fetch failed', e);
  }
  results.slice(0, 3).forEach((t) => { t.featured = true; });
  return results;
}
// "You may also like": TMDB recommendations for the title, topped up with similar titles when there are few.
export async function fetchRecommendations(tmdbId: string, type: 'film' | 'series', limit = 12): Promise<MashhadTitle[]> {
  if (!hasTmdbKey()) return [];
  const kind = type === 'film' ? 'movie' : 'tv';
  const [recommended, similar] = await Promise.all([
    tmdbFetch(`/${kind}/${tmdbId}/recommendations?page=1`),
    tmdbFetch(`/${kind}/${tmdbId}/similar?page=1`),
  ]);
  const seen = new Set<number>([Number(tmdbId)]);
  const items: TmdbMovie[] = [...(recommended?.results ?? []), ...(similar?.results ?? [])].filter((item: TmdbMovie) => {
    if (seen.has(item.id) || !item.poster_path) return false;
    seen.add(item.id);
    return true;
  });
  return items.slice(0, limit).map((item) => {
    const isFilm = type === 'film';
    const name = (isFilm ? item.original_title || item.title : item.original_name || item.name) || 'Untitled';
    const date = isFilm ? item.release_date : item.first_air_date;
    return {
      id: `tmdb-${kind}-${item.id}`,
      title: name,
      originalTitle: name,
      type,
      year: date ? Number(date.slice(0, 4)) : new Date().getFullYear(),
      rating: item.adult ? '+18' : '+13',
      genres: mapGenres(item),
      synopsis: item.overview || 'لا يوجد وصف متاح.',
      artStyle: 'city',
      artwork: { scene: 'city', sky: ['#171226', '#563552', '#B86B72'], accent: '#F5AD6C', horizon: '#1D1B2B' },
      featured: false,
      tmdbId: String(item.id),
      posterPath: item.poster_path ?? null,
      backdropPath: item.backdrop_path ?? null,
      episodes: isFilm ? undefined : [],
    } as MashhadTitle;
  });
}

// "Coming soon": the most anticipated films whose first release is still ahead, soonest first.
export async function fetchUpcoming(limit = 6): Promise<{ title: MashhadTitle; date: Date }[]> {
  if (!hasTmdbKey()) return [];
  // /movie/upcoming is regional and mostly lists films already out elsewhere; discover by first release date instead.
  const today = new Date(new Date().toDateString());
  const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  const data = await tmdbFetch(`/discover/movie?sort_by=popularity.desc&primary_release_date.gte=${iso}&with_release_type=2|3&page=1`);
  return ((data?.results ?? []) as TmdbMovie[])
    .filter((item) => item.release_date && item.poster_path && new Date(item.release_date) >= today)
    .sort((a, b) => (a.release_date ?? '').localeCompare(b.release_date ?? ''))
    .slice(0, limit)
    .map((item) => {
      const name = item.original_title || item.title || 'Untitled';
      return {
        date: new Date(item.release_date!),
        title: {
          id: `tmdb-movie-${item.id}`,
          title: name,
          originalTitle: name,
          type: 'film',
          year: Number(item.release_date!.slice(0, 4)),
          rating: item.adult ? '+18' : '+13',
          genres: mapGenres(item),
          synopsis: item.overview || 'لا يوجد وصف متاح.',
          artStyle: 'city',
          artwork: { scene: 'city', sky: ['#171226', '#563552', '#B86B72'], accent: '#F5AD6C', horizon: '#1D1B2B' },
          featured: false,
          tmdbId: String(item.id),
          posterPath: item.poster_path ?? null,
          backdropPath: item.backdrop_path ?? null,
        } as MashhadTitle,
      };
    });
}

// ---------- Browse: official TMDB genres and server-side discovery ----------

type GenreLists = { movie: Map<string, number>; tv: Map<string, number>; names: string[] };
let genreLists: GenreLists | null = null;
const genreNamesById = new Map<number, string>();

/** TMDB's own Arabic genre names for films and series, loaded once. */
export async function loadGenres(): Promise<GenreLists> {
  if (genreLists) return genreLists;
  const [movie, tv] = await Promise.all([tmdbFetch('/genre/movie/list'), tmdbFetch('/genre/tv/list')]);
  const toMap = (data: any) => new Map<string, number>(((data?.genres ?? []) as { id: number; name: string }[]).map((g) => [g.name, g.id]));
  const lists = { movie: toMap(movie), tv: toMap(tv), names: [] as string[] };
  for (const [name, id] of [...lists.movie, ...lists.tv]) genreNamesById.set(id, name);
  lists.names = [...new Set([...lists.movie.keys(), ...lists.tv.keys()])];
  // Only cache a successful load, so a failed first request is retried next time.
  if (lists.names.length) genreLists = lists;
  return lists;
}

/** Genre names for a list item: TMDB's official Arabic names when loaded, else the built-in map. */
function genreNames(item: TmdbMovie): string[] {
  if (item.genres?.length) return item.genres.map((g) => g.name).slice(0, 3);
  const names = (item.genre_ids ?? []).map((id) => genreNamesById.get(id) ?? GENRE_MAP[id]).filter(Boolean);
  return names.length ? [...new Set(names)].slice(0, 3) : ['دراما'];
}

function fromListItem(item: TmdbMovie, kind: 'movie' | 'tv'): MashhadTitle {
  const isFilm = kind === 'movie';
  const name = (isFilm ? item.original_title || item.title : item.original_name || item.name) || 'Untitled';
  const date = isFilm ? item.release_date : item.first_air_date;
  return {
    id: `tmdb-${kind}-${item.id}`,
    title: name,
    originalTitle: name,
    type: isFilm ? 'film' : 'series',
    year: date ? Number(date.slice(0, 4)) : new Date().getFullYear(),
    rating: item.adult ? '+18' : '+13',
    genres: genreNames(item),
    synopsis: item.overview || 'لا يوجد وصف متاح.',
    artStyle: 'city',
    artwork: { scene: 'city', sky: ['#171226', '#563552', '#B86B72'], accent: '#F5AD6C', horizon: '#1D1B2B' },
    featured: false,
    tmdbId: String(item.id),
    posterPath: item.poster_path ?? null,
    backdropPath: item.backdrop_path ?? null,
    episodes: isFilm ? undefined : [],
  } as MashhadTitle;
}

/**
 * One page of the browse catalog straight from TMDB, filtered by type and genre on the server,
 * so every category pages through TMDB's whole catalog rather than what happens to be loaded.
 */
export async function discoverTitles(type: 'all' | 'film' | 'series', genre: string, page: number): Promise<{ titles: MashhadTitle[]; done: boolean }> {
  if (!hasTmdbKey()) return { titles: [], done: true };
  const lists = await loadGenres();
  const kinds = (type === 'film' ? ['movie'] : type === 'series' ? ['tv'] : ['movie', 'tv']) as ('movie' | 'tv')[];
  const requests = kinds.flatMap((kind) => {
    const genreId = genre === 'الكل' ? null : lists[kind].get(genre);
    if (genre !== 'الكل' && genreId === undefined) return [];
    // A minimum vote count keeps obscure, poster-less entries out of the grid.
    const params = `sort_by=popularity.desc&vote_count.gte=40&page=${page}${genreId ? `&with_genres=${genreId}` : ''}`;
    return [tmdbFetch(`/discover/${kind}?${params}`).then((data) => ({ kind, data }))];
  });
  const pages = await Promise.all(requests);
  const titles = pages
    .flatMap(({ kind, data }) => ((data?.results ?? []) as TmdbMovie[]).filter((item) => item.poster_path).map((item) => ({ title: fromListItem(item, kind), popularity: item.popularity ?? 0 })))
    .sort((a, b) => b.popularity - a.popularity)
    .map(({ title }) => title);
  const done = pages.every(({ data }) => !data || page >= (data.total_pages ?? 1));
  return { titles, done };
}
