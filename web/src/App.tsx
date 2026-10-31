import { useEffect, useMemo, useRef, useState } from 'react';
import { catalog, arabicDigits, durationLabel, episodesFor, getTitle, kindLabel, seasonsFor, titlesById, type Episode, type MashhadTitle, type MediaType } from './catalog';
import { discoverTitles, fetchRecommendations, fetchUpcoming, loadGenres, fetchSeasonEpisodes, fetchSeasonList, fetchTmdbTitles, fetchTitleById, fetchWatchProviders, hasTmdbKey, searchTmdbTitles, type SeasonInfo, type WatchProvider } from './tmdb';
import { Icon } from './Icon';
import { PosterArt } from './PosterArt';
import { RouteLoader, Spinner, Splash, TAGLINE } from './Splash';
import { isFinished, readWatchProgress, removeWatchProgress, resumeSeconds, saveWatchProgress, type WatchEntry } from './watchProgress';

type View = 'home' | 'browse' | 'list' | 'profile' | 'details';
type NavView = Exclude<View, 'details'>;
type PlayerState = { titleId: string; episodeId?: string; seconds: number; playing: boolean; captions: 'off' | 'ar' | 'en' };

const NAV: { id: NavView; label: string; icon: 'home' | 'search' | 'bookmark' }[] = [
  { id: 'home', label: 'الرئيسية', icon: 'home' },
  { id: 'browse', label: 'اكتشف', icon: 'search' },
  { id: 'list', label: 'قائمتي', icon: 'bookmark' },
];

function formatTime(seconds: number): string {
  const minutes = Math.floor(Math.max(0, seconds) / 60);
  const remainder = Math.floor(Math.max(0, seconds) % 60);
  return `${arabicDigits(minutes)}:${arabicDigits(String(remainder).padStart(2, '0'))}`;
}

// Arabic number agreement: موسم واحد، موسمان، ٣–١٠ مواسم، ١١+ موسمًا.
function seasonsLabel(count: number): string {
  if (count === 1) return 'موسم واحد';
  if (count === 2) return 'موسمان';
  return `${arabicDigits(count)} ${count <= 10 ? 'مواسم' : 'موسمًا'}`;
}

// Arabic number agreement: حلقة واحدة، حلقتان، ٣–١٠ حلقات، ١١+ حلقة.
function episodesLabel(count: number): string {
  if (count === 1) return 'حلقة واحدة';
  if (count === 2) return 'حلقتان';
  return `${arabicDigits(count)} ${count <= 10 ? 'حلقات' : 'حلقة'}`;
}

function savedLabel(count: number): string {
  if (count === 0) return 'لم تحفظ أي عنوان بعد';
  if (count === 1) return 'عنوان واحد محفوظ';
  if (count === 2) return 'عنوانان محفوظان';
  return `${arabicDigits(count)} ${count <= 10 ? 'عناوين محفوظة' : 'عنوانًا محفوظًا'}`;
}

/** Season and episode numbers carried in an episode id such as `tmdb-tv-1399-s3-e5`. */
function episodeRef(episodeId?: string): { season: number; number: number } | null {
  const match = episodeId?.match(/-s(\d+)-e(\d+)$/);
  return match ? { season: Number(match[1]), number: Number(match[2]) } : null;
}

function Mark() {
  return <span className="wordmark" aria-label="مَشهد"><span>مشهد</span><i /></span>;
}

function DemoNote({ children = 'بيانات من TMDB' }: { children?: string }) {
  return <span className="demo-note"><Icon name="sparkle" size={15} />{children}</span>;
}

function NavButtons({ mode, active, onNavigate, savedCount }: { mode: 'side' | 'bottom'; active: NavView; onNavigate: (view: NavView) => void; savedCount: number }) {
  if (mode === 'side') return <nav className="side-nav" aria-label="التنقّل الرئيسي">
    {NAV.map((item) => <button key={item.id} className={`nav-item ${active === item.id ? 'active' : ''}`} onClick={() => onNavigate(item.id)} aria-current={active === item.id ? 'page' : undefined}>
      <span className="nav-icon"><Icon name={item.icon} /></span><span>{item.label}</span>
      {item.id === 'list' && savedCount > 0 ? <span className="nav-count">{arabicDigits(savedCount)}</span> : null}
    </button>)}
  </nav>;
  return <nav className="bottom-nav" aria-label="التنقّل الرئيسي">
    {NAV.map((item) => <button key={item.id} className={`bottom-item ${active === item.id ? 'active' : ''}`} onClick={() => onNavigate(item.id)} aria-current={active === item.id ? 'page' : undefined}>
      <span className="bottom-icon"><Icon name={item.icon} size={22} />{item.id === 'list' && savedCount > 0 ? <i className="bottom-count">{arabicDigits(savedCount)}</i> : null}</span><span>{item.label}</span>
    </button>)}
  </nav>;
}

function Header({ onNavigate, onSearch }: { onNavigate: (view: NavView) => void; onSearch: () => void }) {
  return <header className="page-header">
    <button className="header-brand" onClick={() => onNavigate('home')} aria-label="العودة إلى الرئيسية"><Mark /></button>
    <div className="header-actions">
      <button className="icon-button" onClick={onSearch} aria-label="بحث"><Icon name="search" /></button>
    </div>
  </header>;
}

// One random seed per page load keeps the hero order stable while browsing but new after each refresh.
const HERO_SEED = Math.floor(Math.random() * 2 ** 31);
function shuffleRank(id: string): number {
  let hash = HERO_SEED;
  for (let index = 0; index < id.length; index++) hash = Math.imul(hash ^ id.charCodeAt(index), 2654435761);
  return (hash ^ (hash >>> 16)) >>> 0;
}

function SectionHeading({ overline, title, action, onAction }: { overline?: string; title: string; action?: string; onAction?: () => void }) {
  return <div className="section-heading">
    <div>{overline ? <p className="eyebrow-small">{overline}</p> : null}<h2>{title}</h2></div>
    {action && onAction ? <button className="text-action" onClick={onAction}>{action}<Icon name="chevron" size={17} /></button> : null}
  </div>;
}

function Rating({ title }: { title: MashhadTitle }) {
  const duration = title.type === 'film' && title.durationMinutes ? durationLabel(title.durationMinutes) : seasonsLabel(title.seasonCount ?? 1);
  return <div className="meta-line"><span>{arabicDigits(title.year)}</span><i />{title.genres.slice(0, 2).map((genre) => <span key={genre}>{genre}</span>)}<i /><span>{duration}</span><b className="age-rating">{title.rating}</b></div>;
}

function PosterCard({ title, saved, onOpen, rank }: { title: MashhadTitle; saved?: boolean; onOpen: (id: string) => void; rank?: number }) {
  return <button className={`poster-card ${rank ? 'ranked-card' : ''}`} onClick={() => onOpen(title.id)} aria-label={rank ? `المرتبة ${arabicDigits(rank)}: ${title.title}` : `تفاصيل ${title.title}`}>
    {rank ? <span className="poster-rank" aria-hidden="true">{arabicDigits(rank)}</span> : null}
    <span className="poster-visual"><PosterArt title={title} />
      <span className="poster-bottom-shade" />
      {title.tag ? <span className="poster-tag">{title.tag}</span> : null}
      {saved ? <span className="poster-saved"><Icon name="bookmark" size={17} /></span> : null}
      <span className="poster-title">{title.title}</span>
    </span>
    <span className="poster-meta">{kindLabel(title.type)}، {arabicDigits(title.year)}</span>
  </button>;
}

function PosterRow({ titleIds, saved, onOpen, ranked = false }: { titleIds: string[]; saved: string[]; onOpen: (id: string) => void; ranked?: boolean }) {
  const items = [...new Set(titleIds)].map((id) => titlesById.get(id)).filter((title): title is MashhadTitle => Boolean(title));
  return <div className={`poster-row ${ranked ? 'ranked-row' : ''}`}>
    {items.map((title, index) => <PosterCard key={title.id} title={title} saved={saved.includes(title.id)} onOpen={onOpen} rank={ranked ? index + 1 : undefined} />)}
  </div>;
}

function HomePage({ saved, watchHistory, onForget, homeType, setHomeType, onNavigate, onOpen, onSave, onPlay, onSearch }: {
  saved: string[]; watchHistory: WatchEntry[]; onForget: (titleId: string) => void; homeType: 'all' | MediaType; setHomeType: (type: 'all' | MediaType) => void;
  onNavigate: (view: NavView) => void; onOpen: (id: string) => void; onSave: (id: string) => void; onPlay: (id: string, episodeId?: string) => void; onSearch: () => void;
}) {
  const [featuredIndex, setFeaturedIndex] = useState(0);
  const featuredTitles = catalog.featuredIds.map((id) => titlesById.get(id)).filter((title): title is MashhadTitle => Boolean(title));
  const matchesType = (title: MashhadTitle) => homeType === 'all' || title.type === homeType;
  // Featured picks of the chosen type first, topped up with the most popular titles of that type, five slides in all.
  // Five picks drawn from the 20 most popular titles of the chosen type, reshuffled on every page load.
  const visibleFeatured = catalog.titles
    .filter((title, index, all) => matchesType(title) && Boolean(title.backdropPath ?? title.posterPath) && all.findIndex((other) => other.id === title.id) === index)
    .slice(0, 20)
    .map((title) => ({ title, order: shuffleRank(title.id) }))
    .sort((a, b) => a.order - b.order)
    .slice(0, 5)
    .map(({ title }) => title);
  const visibleFeaturedIndex = Math.min(featuredIndex, Math.max(visibleFeatured.length - 1, 0));
  const featured = visibleFeatured[visibleFeaturedIndex] ?? featuredTitles[0] ?? catalog.titles[0];
  const slideCount = visibleFeatured.length;
  const step = (delta: number) => { if (slideCount > 1) setFeaturedIndex((visibleFeaturedIndex + delta + slideCount) % slideCount); };
  const drag = useRef<{ x: number; y: number; id: number } | null>(null);
  const dragged = useRef(false);
  const [dragX, setDragX] = useState(0);
  function onPointerDown(event: React.PointerEvent) {
    if (event.button !== 0 || slideCount < 2) return;
    drag.current = { x: event.clientX, y: event.clientY, id: event.pointerId };
    dragged.current = false;
  }
  function onPointerMove(event: React.PointerEvent) {
    const start = drag.current;
    if (!start || start.id !== event.pointerId) return;
    const dx = event.clientX - start.x;
    if (!dragged.current) {
      // Only take over horizontal gestures; vertical ones keep scrolling the page.
      if (Math.abs(dx) < 8 || Math.abs(dx) < Math.abs(event.clientY - start.y)) return;
      dragged.current = true;
      try { event.currentTarget.setPointerCapture(event.pointerId); } catch { /* pointer already released */ }
    }
    setDragX(dx);
  }
  function onPointerEnd(event: React.PointerEvent) {
    const start = drag.current;
    if (!start || start.id !== event.pointerId) return;
    drag.current = null;
    // RTL: the next slide sits to the left, so dragging the current one to the right brings it in.
    if (dragged.current && Math.abs(dragX) > 60) step(dragX > 0 ? 1 : -1);
    setDragX(0);
  }
  // Real history from this browser, unfinished titles only, newest first.
  const continueEntries = watchHistory.filter((entry) => !isFinished(entry) && titlesById.has(entry.titleId) && matchesType(titlesById.get(entry.titleId)!)).slice(0, 8);
  const topIds = catalog.topTenIds.filter((id) => homeType === 'all' || getTitle(id).type === homeType);
  const libraryPool = catalog.titles.filter((title, index, all) => matchesType(title) && all.findIndex((other) => other.id === title.id) === index);
  const libraryIds = (libraryPool.some((title) => !topIds.includes(title.id)) ? libraryPool.filter((title) => !topIds.includes(title.id)) : libraryPool).map((title) => title.id);
  const firstEpisode = featured.episodes?.[0];
  const editorPick = catalog.titles.find((t) => homeType === 'film' ? t.type === 'film' : t.type === 'series') ?? catalog.titles[0];

  return <div className="home-page">
    <section className={`hero ${dragX ? 'dragging' : ''}`} aria-labelledby="hero-title" aria-roledescription="عرض متتابع"
      onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerEnd} onPointerCancel={onPointerEnd}
      onClickCapture={(event) => { if (dragged.current) { event.preventDefault(); event.stopPropagation(); dragged.current = false; } }}
      onKeyDown={(event) => { if (event.key === 'ArrowLeft') step(1); if (event.key === 'ArrowRight') step(-1); }}
      onDragStart={(event) => event.preventDefault()}>
      <PosterArt key={`art-${featured.id}`} title={featured} className="hero-art" backdrop style={{ transform: dragX ? `translateX(${dragX * .18}px)` : undefined }} />
      <div className="hero-vignette" />
      <div className="hero-topline">
        <button className="hero-wordmark" onClick={() => onNavigate('home')} aria-label="مَشهد، الرئيسية"><Mark /></button>
        <div className="hero-actions">
          <button className="icon-button on-image" onClick={onSearch} aria-label="بحث"><Icon name="search" /></button>
        </div>
      </div>
      <div className="hero-type-switch" role="group" aria-label="تصفية الصفحة الرئيسية">
        {([['all', 'الكل'], ['series', 'مسلسلات'], ['film', 'أفلام']] as const).map(([value, label]) => <button key={value} className={homeType === value ? 'selected' : ''} onClick={() => { setHomeType(value); setFeaturedIndex(0); }} aria-pressed={homeType === value}>{label}</button>)}
      </div>
      <div className="hero-copy" key={`copy-${featured.id}`} style={dragX ? { transform: `translateX(${dragX * .5}px)`, opacity: Math.max(.35, 1 - Math.abs(dragX) / 420) } : undefined}>
        <p className="hero-kicker"><span />مختارات مَشهد</p>
        <h1 id="hero-title">{featured.title}</h1>
        <p className="hero-logline">{featured.logline}</p>
        <Rating title={featured} />
        <div className="hero-cta">
          <button className="button-primary" onClick={() => onPlay(featured.id, firstEpisode?.id)}><Icon name="play" size={18} />ابدأ المشاهدة</button>
          <button className={`button-secondary ${saved.includes(featured.id) ? 'is-saved' : ''}`} onClick={() => onSave(featured.id)} aria-pressed={saved.includes(featured.id)}><Icon name={saved.includes(featured.id) ? 'check' : 'plus'} size={19} />قائمتي</button>
          <button className="icon-button on-image" onClick={() => onOpen(featured.id)} aria-label="التفاصيل"><Icon name="info" /></button>
        </div>
        <div className="hero-pager" aria-label="العناوين المميزة">
          {visibleFeatured.map((title, index) => <button key={title.id} className={index === visibleFeaturedIndex ? 'current' : ''} onClick={() => setFeaturedIndex(index)} aria-label={`العنوان ${arabicDigits(index + 1)}: ${title.title}`} aria-current={index === visibleFeaturedIndex ? 'true' : undefined}><i /></button>)}
        </div>
      </div>
      <DemoNote />
    </section>

    <div className="home-content content-width">
      <section className="content-section">
        <SectionHeading overline="تصفّح من هنا" title="من مكتبة مَشهد" action="كل العناوين" onAction={() => onNavigate('browse')} />
        <PosterRow titleIds={libraryIds} saved={saved} onOpen={onOpen} />
      </section>

      {continueEntries.length ? <section className="content-section continue-section">
        <SectionHeading overline="تابِع من حيث توقّفت" title="واصل المشاهدة" />
        <div className="continue-row">
          {continueEntries.map((entry) => {
            const title = titlesById.get(entry.titleId)!;
            const episode = title.episodes?.find((candidate) => candidate.id === entry.episodeId);
            const where = entry.season ? `م${arabicDigits(entry.season)} ح${arabicDigits(entry.number ?? 1)}${episode ? `: ${episode.title}` : ''}` : 'فيلم';
            const left = entry.seconds === null ? null : Math.max(1, Math.round((entry.duration - entry.seconds) / 60));
            return <div className="continue-card" key={entry.titleId}>
              <button className="continue-open" onClick={() => onPlay(entry.titleId, entry.episodeId)} aria-label={`تابِع ${title.title}`}>
                <span className="continue-art"><PosterArt title={title} backdrop /><span className="continue-play"><Icon name="play" /></span>{entry.seconds !== null ? <i style={{ width: `${Math.min(100, entry.seconds / entry.duration * 100)}%` }} /> : null}</span>
                <span className="continue-copy"><b dir="auto">{title.title}</b><small dir="auto">{where}</small><small>{left === null ? 'آخر مشاهدة: ' + new Date(entry.updatedAt).toLocaleDateString('ar-EG', { day: 'numeric', month: 'long' }) : `بقيت ${arabicDigits(left)} دقيقة`}</small></span>
              </button>
              <button className="continue-forget" onClick={() => onForget(entry.titleId)} aria-label={`إزالة ${title.title} من «واصل المشاهدة»`}><Icon name="close" size={16} /></button>
            </div>;
          })}
        </div>
      </section> : null}

      <section className="content-section top-ten-section">
        <SectionHeading overline="الأكثر تداولًا في عالم مَشهد" title="اختيارات الأسبوع" action="اكتشف المزيد" onAction={() => onNavigate('browse')} />
        <PosterRow titleIds={topIds} saved={saved} onOpen={onOpen} ranked />
      </section>

      <section className="editorial-card content-section">
        <PosterArt title={editorPick} className="editorial-art" />
        <div className="editorial-shade" />
        <div className="editorial-copy"><p className="eyebrow-small">اختيار المحرّر، {editorPick.genres[0]}</p><h2>لماذا نشاهد «{editorPick.title}»؟</h2>{editorPick.editorQuote ? <><blockquote>«{editorPick.editorQuote.text}»</blockquote><p className="editorial-by">{editorPick.editorQuote.by}</p></> : <p className="editorial-synopsis">{editorPick.synopsis}</p>}<button className="button-secondary" onClick={() => onOpen(editorPick.id)}>اكتشف الحكاية<Icon name="arrow" size={18} /></button></div>
      </section>

      {catalog.upcoming.length > 0 ? <section className="content-section">
        <SectionHeading overline="مواعيد العرض" title="قريبًا في عالم مَشهد" />
        <div className="upcoming-row">
          {catalog.upcoming.map((item) => {
            const title = getTitle(item.titleId);
            return <article className="upcoming-card" key={`${item.titleId}-${item.day}`}>
              <div className="upcoming-date"><b>{arabicDigits(item.day)}</b><span>{item.month}</span></div>
              <div className="upcoming-info"><span>{item.label}</span><b>{title.title}</b><button onClick={() => onOpen(title.id)}>تفاصيل العنوان<Icon name="chevron" size={15} /></button></div>
            </article>;
          })}
        </div>
      </section> : null}
    </div>
  </div>;
}

function PageTitle({ kicker, title, description, action }: { kicker?: string; title: string; description?: string; action?: React.ReactNode }) {
  return <div className="page-title-row"><div>{kicker ? <p className="eyebrow-small">{kicker}</p> : null}<h1>{title}</h1>{description ? <p className="page-description">{description}</p> : null}</div>{action}</div>;
}

function BrowsePage({ query, setQuery, typeFilter, setTypeFilter, genreFilter, setGenreFilter, saved, onOpen }: {
  query: string; setQuery: (query: string) => void; typeFilter: 'all' | MediaType; setTypeFilter: (type: 'all' | MediaType) => void;
  genreFilter: string; setGenreFilter: (genre: string) => void; saved: string[]; onOpen: (id: string) => void;
}) {
  const [searchResults, setSearchResults] = useState<MashhadTitle[] | null>(null);
  const [searchingQuery, setSearching] = useState(false);
  useEffect(() => {
    if (!query.trim()) { setSearchResults(null); setSearching(false); return; }
    let cancelled = false;
    setSearching(true);
    const timer = window.setTimeout(() => {
      searchTmdbTitles(query).then((r) => {
        if (cancelled) return;
        setSearchResults(r);
        setSearching(false);
        for (const t of r) if (!titlesById.has(t.id)) titlesById.set(t.id, t);
      });
    }, 400);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [query]);
  const [recent, setRecent] = useState<string[]>(readRecent);
  const searchMode = Boolean(query.trim());

  // Genre chips: TMDB's official names for the chosen type.
  const [genreLists, setGenreLists] = useState<{ movie: string[]; tv: string[] } | null>(null);
  useEffect(() => {
    let cancelled = false;
    loadGenres().then((lists) => { if (!cancelled) setGenreLists({ movie: [...lists.movie.keys()], tv: [...lists.tv.keys()] }); });
    return () => { cancelled = true; };
  }, []);
  const genres = !genreLists ? catalog.genres
    : typeFilter === 'film' ? genreLists.movie : typeFilter === 'series' ? genreLists.tv : [...new Set([...genreLists.movie, ...genreLists.tv])];
  // A genre that does not exist for the newly chosen type falls back to «الكل».
  useEffect(() => { if (genreLists && genreFilter !== 'الكل' && !genres.includes(genreFilter)) setGenreFilter('الكل'); }, [genreLists, typeFilter]); // eslint-disable-line react-hooks/exhaustive-deps

  // Catalog view: pages come straight from TMDB for the selected type and genre.
  const [feed, setFeed] = useState<{ key: string; titles: MashhadTitle[]; page: number; done: boolean; loading: boolean }>({ key: '', titles: [], page: 0, done: false, loading: false });
  const feedKey = `${typeFilter}|${genreFilter}`;
  const feedRef = useRef(feed);
  feedRef.current = feed;
  async function loadPage(key: string, page: number) {
    setFeed((current) => ({ ...(current.key === key ? current : { key, titles: [], page: 0, done: false }), loading: true }));
    const [type, genre] = key.split('|') as ['all' | MediaType, string];
    const { titles, done } = await discoverTitles(type, genre, page).catch(() => ({ titles: [] as MashhadTitle[], done: true }));
    for (const title of titles) if (!titlesById.has(title.id)) titlesById.set(title.id, title);
    setFeed((current) => current.key !== key ? current : {
      key, page, done, loading: false,
      titles: [...current.titles, ...titles.filter((title) => !current.titles.some((existing) => existing.id === title.id))],
    });
  }
  useEffect(() => { if (!searchMode && hasTmdbKey()) loadPage(feedKey, 1); }, [feedKey, searchMode]); // eslint-disable-line react-hooks/exhaustive-deps
  function loadMore() {
    const current = feedRef.current;
    if (current.loading || current.done || current.key !== feedKey) return;
    loadPage(feedKey, current.page + 1);
  }

  const results = searchMode
    ? (searchResults ?? []).filter((title) => (typeFilter === 'all' || title.type === typeFilter) && (genreFilter === 'الكل' || title.genres.includes(genreFilter)))
    : feed.key === feedKey ? feed.titles : [];
  // Until the first answer for this text arrives, show the loading state rather than «لم نعثر على نتائج».
  const searching = searchMode ? searchingQuery || searchResults === null : feed.loading && results.length === 0;
  const filtered = searchMode || genreFilter !== 'الكل' || typeFilter !== 'all';
  const canLoadMore = !searchMode && hasTmdbKey() && feed.key === feedKey && !feed.done;
  const resetAll = () => { setQuery(''); setTypeFilter('all'); setGenreFilter('الكل'); };

  // While more can load, show whole rows only; the leftover titles appear once the next page fills their row.
  const gridRef = useRef<HTMLDivElement>(null);
  const [columns, setColumns] = useState(0);
  useEffect(() => {
    const grid = gridRef.current;
    if (!grid) return;
    const measure = () => setColumns(getComputedStyle(grid).gridTemplateColumns.split(' ').filter(Boolean).length);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(grid);
    return () => observer.disconnect();
  }, [results.length > 0]);
  const visible = canLoadMore && columns > 0 && results.length > columns ? results.slice(0, results.length - (results.length % columns)) : results;

  // Load the next page automatically as the end of the grid comes near; the button stays as a fallback.
  const sentinelRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !canLoadMore) return;
    const check = () => { if (sentinel.getBoundingClientRect().top < window.innerHeight + 600) loadMore(); };
    window.addEventListener('scroll', check, { passive: true });
    window.addEventListener('resize', check);
    return () => { window.removeEventListener('scroll', check); window.removeEventListener('resize', check); };
  }, [canLoadMore, visible.length]); // eslint-disable-line react-hooks/exhaustive-deps

  function open(id: string) {
    const trimmed = query.trim();
    if (trimmed) {
      const next = [trimmed, ...recent.filter((item) => item !== trimmed)].slice(0, 6);
      setRecent(next);
      try { window.localStorage.setItem(RECENT_KEY, JSON.stringify(next)); } catch { /* recent searches are optional */ }
    }
    onOpen(id);
  }

  return <div className="page-content content-width browse-page">
    <PageTitle kicker="من أين نبدأ؟" title="اكتشِف" description="ابحث في آلاف الأفلام والمسلسلات، أو تصفّح حسب النوع." />
    <label className="search-field browse-search">
      <span className="search-field-icon">{searching ? <Spinner size={22} glyph={false} /> : <Icon name="search" size={21} />}</span>
      <input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="ابحث عن فيلم أو مسلسل أو ممثّل…" aria-label="ابحث عن فيلم أو مسلسل أو ممثّل"
        onKeyDown={(event) => { if (event.key === 'Enter' && query.trim() && results[0]) open(results[0].id); }} enterKeyHint="search" autoComplete="off" />
      {query || filtered ? <button className="search-field-clear" onClick={resetAll} aria-label="مسح البحث والتصفية والعودة إلى الكل"><Icon name="close" size={18} /></button> : null}
    </label>

    <div className="search-filters browse-filters" role="group" aria-label="نوع العنوان">
      {([['all', 'الكل'], ['film', 'أفلام'], ['series', 'مسلسلات']] as const).map(([value, label]) => <button key={value} className={typeFilter === value ? 'selected' : ''} aria-pressed={typeFilter === value} onClick={() => setTypeFilter(value)}>{label}</button>)}
    </div>
    <div className="genre-chips" role="group" aria-label="التصنيف حسب النوع">
      {['الكل', ...genres].map((genre) => <button key={genre} className={genreFilter === genre ? 'selected' : ''} onClick={() => setGenreFilter(genre)} aria-pressed={genreFilter === genre}>{genre}</button>)}
    </div>

    {!query && recent.length ? <section className="search-section browse-recent"><div className="search-section-head"><h3>عمليات بحث سابقة</h3><button onClick={() => { setRecent([]); try { window.localStorage.removeItem(RECENT_KEY); } catch { /* optional */ } }}>مسح</button></div>
      <div className="search-recent">{recent.map((item) => <button key={item} onClick={() => setQuery(item)} dir="auto"><Icon name="clock" size={15} />{item}</button>)}</div></section> : null}

    <p className="search-status" role="status" aria-live="polite">
      {searchMode
        ? (searching ? `جارٍ البحث عن «${query.trim()}»…` : results.length ? `نتائج «${query.trim()}»؛ اضغط Enter لفتح الأولى` : '')
        : feed.loading && !results.length ? 'جارٍ التحميل…'
        : `${typeFilter === 'film' ? 'أفلام' : typeFilter === 'series' ? 'مسلسلات' : 'أفلام ومسلسلات'}${genreFilter !== 'الكل' ? `، ${genreFilter}` : ''}، الأكثر رواجًا أوّلًا`}
    </p>

    {searching ? <div className="search-grid browse-grid" aria-hidden="true">{Array.from({ length: 12 }, (_, index) => <span className="search-card skeleton" key={index}><span className="search-card-poster" /><span className="skeleton-line" /><span className="skeleton-line short" /></span>)}</div>
      : results.length ? <div ref={gridRef} className="search-grid browse-grid">{visible.map((title) => <SearchResultCard key={title.id} title={title} onOpen={open} saved={saved.includes(title.id)} />)}</div>
      : <div className="search-empty"><Icon name="search" size={30} /><b>{searchMode ? `لم نعثر على نتائج لـ «${query.trim()}»` : 'لا توجد عناوين بهذه التصفية'}</b><span>{searchMode ? 'تحقّق من الإملاء، أو جرّب الاسم الأصلي للعنوان بالإنجليزية.' : 'جرّب نوعًا آخر، أو عُد إلى «الكل».'}</span>
        {filtered ? <button className="button-secondary" onClick={resetAll}>العودة إلى الكل</button> : null}</div>}

    {canLoadMore && results.length ? <div className="load-more" ref={sentinelRef}><button className="button-primary" onClick={loadMore} disabled={feed.loading}>{feed.loading ? <><Spinner size={22} glyph={false} />جارٍ التحميل…</> : 'تحميل المزيد'}</button></div> : null}
  </div>;
}

function DetailPage({ title, saved, watchEntry, watchProviders, onBack, onOpen, onSave, onPlay, showNotice }: {
  title: MashhadTitle; saved: boolean; watchEntry?: WatchEntry; watchProviders: { country: string; providers: WatchProvider[]; link: string | null } | null; onBack: () => void; onOpen: (id: string) => void; onSave: (id: string) => void;
  onPlay: (id: string, episodeId?: string) => void; showNotice: (message: string) => void;
}) {
  const isTmdbSeries = title.type === 'series' && Boolean(title.tmdbId);
  const [seasonList, setSeasonList] = useState<SeasonInfo[] | null>(null);
  const [seasonEpisodes, setSeasonEpisodes] = useState<Record<number, Episode[]>>({});
  const [season, setSeason] = useState(seasonsFor(title)[0] ?? 1);
  const seasons = seasonList?.map((info) => info.number) ?? seasonsFor(title);
  const episodes = isTmdbSeries ? seasonEpisodes[season] ?? [] : episodesFor(title, season);
  const loadingEpisodes = isTmdbSeries && !seasonEpisodes[season];

  // TMDB series: load the real season list, then each season's episodes the first time it is opened.
  useEffect(() => {
    if (!isTmdbSeries) return;
    let cancelled = false;
    setSeasonList(null);
    setSeasonEpisodes({});
    fetchSeasonList(title.tmdbId!).then((list) => {
      if (cancelled) return;
      setSeasonList(list.length ? list : [{ number: 1, episodeCount: 0 }]);
      setSeason(list[0]?.number ?? 1);
    });
    return () => { cancelled = true; };
  }, [title.id, isTmdbSeries, title.tmdbId]);

  useEffect(() => {
    if (!isTmdbSeries || seasonEpisodes[season]) return;
    let cancelled = false;
    fetchSeasonEpisodes(title.tmdbId!, season).then((list) => {
      if (cancelled) return;
      // Keep them on the title too, so the player can find the episode it is asked to open.
      title.episodes = [...(title.episodes ?? []).filter((episode) => episode.season !== season), ...list];
      setSeasonEpisodes((current) => ({ ...current, [season]: list }));
    });
    return () => { cancelled = true; };
  }, [title, isTmdbSeries, season, seasonEpisodes]);
  // Real recommendations from TMDB; sample-catalog titles fall back to titles that share a genre.
  const [recommended, setRecommended] = useState<MashhadTitle[] | null>(null);
  useEffect(() => {
    if (!title.tmdbId) { setRecommended(null); return; }
    let cancelled = false;
    setRecommended(null);
    fetchRecommendations(title.tmdbId, title.type).then((list) => {
      if (cancelled) return;
      // Register them so opening one works like any other title, keeping richer copies already loaded.
      for (const item of list) if (!titlesById.has(item.id)) titlesById.set(item.id, item);
      setRecommended(list.map((item) => titlesById.get(item.id) ?? item));
    });
    return () => { cancelled = true; };
  }, [title.id, title.tmdbId, title.type]);
  const related = title.tmdbId
    ? recommended ?? []
    : catalog.titles.filter((other) => other.id !== title.id && !other.tmdbId && other.genres.some((genre) => title.genres.includes(genre))).slice(0, 8);
  const loadingRelated = Boolean(title.tmdbId) && recommended === null;
  const resumeAt = watchEntry && !isFinished(watchEntry) ? watchEntry : undefined;
  const resumeLabel = resumeAt
    ? `تابِع${resumeAt.season ? ` م${arabicDigits(resumeAt.season)} ح${arabicDigits(resumeAt.number ?? 1)}` : ''}${resumeAt.seconds ? ` من ${formatTime(resumeAt.seconds)}` : ''}`
    : 'ابدأ المشاهدة';
  const firstEpisode = episodes[0];

  return <div className="detail-page">
    <section className="detail-hero">
      <PosterArt title={title} className="detail-art" />
      <div className="detail-shade" />
      <button className="detail-back icon-button on-image" onClick={onBack} aria-label="رجوع"><Icon name="back" /></button>
      <div className="detail-hero-copy">
        <p className="hero-kicker"><span />{kindLabel(title.type)}، {title.genres.join('، ')}</p>
        <h1>{title.title}</h1>
        <Rating title={title} />
        <div className="detail-hero-actions">
          <button className="button-primary" onClick={() => onPlay(title.id, resumeAt?.episodeId ?? firstEpisode?.id)}><Icon name="play" size={18} />{resumeLabel}</button>
          <button className={`button-secondary ${saved ? 'is-saved' : ''}`} onClick={() => onSave(title.id)} aria-pressed={saved}><Icon name={saved ? 'check' : 'plus'} size={19} />{saved ? 'في قائمتي' : 'أضف إلى قائمتي'}</button>
        </div>
      </div>
      <DemoNote />
    </section>

    <div className="detail-body content-width">
      <div className={`detail-overview ${title.editorQuote ? '' : 'single'}`}>
        <section className="synopsis-block"><p className="eyebrow-small">عن الحكاية</p>{title.logline ? <h2>{title.logline}</h2> : null}<p>{title.synopsis}</p><div className="credits"><span>بطولة: <b>{title.cast?.join('، ') ?? 'غير متوفّر'}</b></span><span>إخراج: <b>{title.director ?? 'غير متوفّر'}</b></span></div></section>
        {title.editorQuote ? <aside className="quote-card"><Icon name="sparkle" size={19} /><blockquote>«{title.editorQuote.text}»</blockquote><span>{title.editorQuote.by}</span></aside> : null}
      </div>

      {title.type === 'series' ? <section className="episodes-section">
        <div className="section-heading"><div><p className="eyebrow-small">دليل الحلقات</p><h2>الحلقات</h2></div>{seasons.length ? <span className="season-count">{seasonsLabel(seasons.length)}</span> : null}</div>
        {seasons.length > 1 ? <div className="season-tabs" role="tablist" aria-label="المواسم">
          {seasons.map((number) => {
            const count = seasonList?.find((info) => info.number === number)?.episodeCount ?? episodesFor(title, number).length;
            return <button key={number} role="tab" aria-selected={season === number} className={season === number ? 'selected' : ''} onClick={() => setSeason(number)}>
              <b>الموسم {arabicDigits(number)}</b>{count ? <small>{episodesLabel(count)}</small> : null}
            </button>;
          })}
        </div> : null}
        {loadingEpisodes ? <div className="episodes-loading" aria-busy="true"><Spinner size={34} glyph={false} /><span>جارٍ تحميل حلقات الموسم {arabicDigits(season)}…</span></div> : null}
        {!loadingEpisodes && episodes.length === 0 ? <p className="page-description">لا تتوفّر حلقات لهذا الموسم بعد.</p> : null}
        <div className="episode-list">{episodes.map((episode) => <article className="episode-card" key={episode.id}>
          <button className="episode-art-button" onClick={() => { if (watchProviders?.link) window.open(watchProviders.link, '_blank', 'noopener'); else onPlay(title.id, episode.id); }} aria-label={`شاهِد الحلقة ${arabicDigits(episode.number)}: ${episode.title}`}><PosterArt title={title} className="episode-art" /><span className="episode-play"><Icon name="play" size={19} /></span></button>
          <div className="episode-info"><span className="episode-overline">م{arabicDigits(episode.season)} ح{arabicDigits(episode.number)}، {arabicDigits(episode.durationMinutes)} دقيقة</span><h3>{episode.title}</h3><p>{episode.overview ?? (title.tmdbId ? 'لا يتوفّر وصف لهذه الحلقة بعد.' : 'حلقة من عيّنة الكتالوج، متاحة لمعاينة الواجهة فقط.')}</p></div>
          <div className="episode-actions"><button className="icon-button" onClick={() => onPlay(title.id, episode.id)} aria-label={`شاهِد الحلقة ${arabicDigits(episode.number)}`}><Icon name="play" size={19} /></button></div>
        </article>)}</div>
      </section> : <section className="film-demo-card"><span className="film-demo-icon"><Icon name="film" /></span><div><b>معلومات الفيلم</b><span>مدّة {durationLabel(title.durationMinutes ?? 100)}.</span></div></section>}

      {loadingRelated || related.length ? <section className="content-section"><SectionHeading overline={title.tmdbId ? 'ترشيحات بناءً على هذا العنوان' : 'من النوع نفسه'} title="قد يعجبك أيضًا" />
        {loadingRelated ? <div className="poster-row" aria-busy="true">{Array.from({ length: 6 }, (_, index) => <span className="search-card skeleton related-skeleton" key={index}><span className="search-card-poster" /><span className="skeleton-line" /></span>)}</div>
          : <div className="poster-row">{related.map((other) => <PosterCard key={other.id} title={other} saved={false} onOpen={onOpen} />)}</div>}
      </section> : null}
      {watchProviders && watchProviders.providers.length > 0 ? <section className="content-section"><SectionHeading overline={`أين تُشاهَد، ${watchProviders.country}`} title="خدمات البثّ" /><div className="provider-row">{watchProviders.providers.map((p) => <a key={p.id} className="provider-chip" href={watchProviders.link ?? '#'} target="_blank" rel="noopener noreferrer"><span>{p.name}</span></a>)}</div></section> : null}
      {watchProviders && watchProviders.providers.length === 0 && title.tmdbId ? <section className="content-section"><SectionHeading overline="أين تُشاهَد" title="لا توجد خدمات بثّ مسجّلة" /><p className="page-description">لم نجد خدمة بثّ لهذا العنوان في منطقتك (أو أن المفتاح غير مفعّل).</p></section> : null}
      <div className="demo-boundary"><Icon name="info" size={18} /><span>مَشهد كتالوج واكتشاف. التشغيل يتم في OM Player أو خدمة بثّ.</span><button onClick={() => showNotice('مَشهد لا يشغّل فيديو بنفسه؛ يوجّهك إلى خدمة بثّ أو OM Player.')}>ما معنى ذلك؟</button></div>
    </div>
  </div>;
}

function ListPage({ saved, onNavigate, onOpen, onSave }: { saved: string[]; onNavigate: (view: NavView) => void; onOpen: (id: string) => void; onSave: (id: string) => void }) {
  const [sort, setSort] = useState<'recent' | 'az'>('recent');
  const items = saved.map((id) => titlesById.get(id)).filter((title): title is MashhadTitle => Boolean(title));
  if (sort === 'az') items.sort((a, b) => a.title.localeCompare(b.title, 'ar'));
  return <div className="page-content content-width">
    <PageTitle kicker="محفوظ محليًا في هذا المتصفح" title="قائمتي" description={savedLabel(items.length)} />
    {items.length ? <><div className="sort-tabs" role="group" aria-label="ترتيب قائمتي"><button className={sort === 'recent' ? 'selected' : ''} onClick={() => setSort('recent')}>أُضيف مؤخرًا</button><button className={sort === 'az' ? 'selected' : ''} onClick={() => setSort('az')}>أبجديًا</button></div><div className="saved-list">{items.map((title) => <article className="saved-row" key={title.id}>
      <button className="saved-art" onClick={() => onOpen(title.id)} aria-label={`تفاصيل ${title.title}`}><PosterArt title={title} /></button>
      <button className="saved-copy" onClick={() => onOpen(title.id)}><b>{title.title}</b><span>{kindLabel(title.type)}، {title.genres[0]}، {arabicDigits(title.year)}</span><small>{title.logline}</small></button>
      <div className="saved-actions"><button className="icon-button" onClick={() => onOpen(title.id)} aria-label="التفاصيل"><Icon name="info" /></button><button className="icon-button remove-button" onClick={() => onSave(title.id)} aria-label={`إزالة ${title.title} من قائمتي`}><Icon name="trash" /></button></div>
    </article>)}</div></> : <div className="empty-state"><span className="empty-icon"><Icon name="bookmark" size={28} /></span><h2>قائمتك تبدأ من هنا</h2><p>احفظ عنوانًا من صفحة التفاصيل ليظهر في هذه القائمة. الحفظ محلي لهذا المتصفح.</p><button className="button-primary" onClick={() => onNavigate('browse')}>استكشف العناوين</button></div>}
    <div className="demo-boundary"><Icon name="info" size={18} /><span>قائمتك محفوظة في هذا المتصفح، ولا تحتاج إلى حساب.</span></div>
  </div>;
}

function ProfilePage({ settings, setSettings, showNotice }: {
  settings: Record<string, boolean>; setSettings: (settings: Record<string, boolean>) => void; showNotice: (message: string) => void;
}) {
  const rows: { key: string; icon: 'play' | 'wifi' | 'captions' | 'globe' | 'settings'; title: string; detail: string }[] = [
    { key: 'autoplay', icon: 'play', title: 'تشغيل الحلقة التالية تلقائيًا', detail: 'ينتقل إلى الحلقة التالية عند انتهاء الحالية' },
    { key: 'captions', icon: 'captions', title: 'الترجمة الافتراضية', detail: 'العربية' },
    { key: 'dataSaver', icon: 'wifi', title: 'توفير البيانات', detail: 'إعداد محلي للواجهة فقط' },
    { key: 'notifications', icon: 'globe', title: 'تذكيرات العرض', detail: 'لا تُرسل إشعارات حاليًا' },
  ];
  return <div className="page-content content-width">
    <PageTitle kicker="الإعدادات" title="الإعدادات" description="تفضيلات محلية." />
    <section className="settings-section"><h2>تفضيلات العرض</h2>{rows.map((row) => <button className="setting-row" key={row.key} onClick={() => {
      if (row.key === 'captions') { showNotice('الترجمة في مشغّل العرض اختيارية.'); return; }
      setSettings({ ...settings, [row.key]: !settings[row.key] });
    }}><span className="setting-icon"><Icon name={row.icon} /></span><span className="setting-copy"><b>{row.title}</b><small>{row.detail}</small></span><span className={`toggle-switch ${settings[row.key] ? 'on' : ''}`} aria-hidden="true" /></button>)}</section>
    <div className="demo-boundary"><Icon name="info" size={18} /><span>مَشهد، كتالوج واكتشاف. التشغيل في OM Player أو خدمة بثّ.</span></div>
  </div>;
}

/**
 * Season and episode picker shown over the player. It only changes the app's player state
 * (episode, history, URL), so it works the same with any player behind it.
 */
function EpisodePicker({ title, currentEpisodeId, onPick, onClose }: { title: MashhadTitle; currentEpisodeId?: string; onPick: (episodeId: string) => void; onClose: () => void }) {
  const current = episodeRef(currentEpisodeId);
  const [seasons, setSeasons] = useState<SeasonInfo[] | null>(title.tmdbId ? null : seasonsFor(title).map((number) => ({ number, episodeCount: episodesFor(title, number).length })));
  const [season, setSeason] = useState(current?.season ?? seasonsFor(title)[0] ?? 1);
  const [bySeason, setBySeason] = useState<Record<number, Episode[]>>({});
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!title.tmdbId) return;
    let cancelled = false;
    fetchSeasonList(title.tmdbId).then((list) => { if (!cancelled) setSeasons(list.length ? list : [{ number: 1, episodeCount: 0 }]); });
    return () => { cancelled = true; };
  }, [title.id, title.tmdbId]);

  const loaded = episodesFor(title, season);
  const episodes = bySeason[season] ?? (title.tmdbId ? [] : loaded);
  const loading = Boolean(title.tmdbId) && !bySeason[season];
  useEffect(() => {
    if (!title.tmdbId || bySeason[season]) return;
    let cancelled = false;
    fetchSeasonEpisodes(title.tmdbId, season).then((list) => {
      if (cancelled) return;
      // Keep them on the title so the player and history can resolve the episode.
      title.episodes = [...(title.episodes ?? []).filter((episode) => episode.season !== season), ...list];
      setBySeason((all) => ({ ...all, [season]: list }));
    });
    return () => { cancelled = true; };
  }, [title, season, bySeason]);

  // Focus the panel once on open and close on Escape; the ref keeps the latest handler without re-running this.
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    panelRef.current?.focus();
    function onKey(event: KeyboardEvent) { if (event.key === 'Escape') { event.stopPropagation(); closeRef.current(); } }
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, []);
  useEffect(() => { panelRef.current?.querySelector('.picker-episode.playing')?.scrollIntoView({ block: 'center' }); }, [episodes.length]);

  return <div className="picker-scrim" onClick={onClose}>
    <aside className="episode-picker" role="dialog" aria-modal="true" aria-label={`حلقات ${title.title}`} tabIndex={-1} ref={panelRef} onClick={(event) => event.stopPropagation()}>
      <header className="picker-head">
        <div><p className="eyebrow-small">اختر الحلقة</p><h2 dir="auto">{title.title}</h2></div>
        <button className="icon-button" onClick={onClose} aria-label="إغلاق قائمة الحلقات"><Icon name="close" /></button>
      </header>
      {seasons && seasons.length > 1 ? <div className="season-tabs picker-seasons" role="tablist" aria-label="المواسم">
        {seasons.map((info) => <button key={info.number} role="tab" aria-selected={season === info.number} className={season === info.number ? 'selected' : ''} onClick={() => setSeason(info.number)}>
          <b>الموسم {arabicDigits(info.number)}</b>{info.episodeCount ? <small>{episodesLabel(info.episodeCount)}</small> : null}
        </button>)}
      </div> : null}
      <div className="picker-list">
        {loading || !seasons ? <div className="episodes-loading" aria-busy="true"><Spinner size={34} glyph={false} /><span>جارٍ تحميل الحلقات…</span></div>
          : episodes.length ? episodes.map((episode) => {
            const playing = episode.id === currentEpisodeId || (current?.season === episode.season && current?.number === episode.number);
            return <button key={episode.id} className={`picker-episode ${playing ? 'playing' : ''}`} onClick={() => onPick(episode.id)} aria-current={playing ? 'true' : undefined}>
              <span className="picker-number">{arabicDigits(episode.number)}</span>
              <span className="picker-copy"><b dir="auto">{episode.title}</b><small>{playing ? 'تُعرض الآن' : `${arabicDigits(episode.durationMinutes)} دقيقة`}</small></span>
              <Icon name={playing ? 'pause' : 'play'} size={18} />
            </button>;
          }) : <p className="page-description">لا تتوفّر حلقات لهذا الموسم بعد.</p>}
      </div>
    </aside>
  </div>;
}

/** The episode after the current one: next in its season, else the first of the next season when that is loaded. */
function nextEpisodeOf(title: MashhadTitle, episodeId?: string): Episode | undefined {
  const list = [...(title.episodes ?? [])].sort((a, b) => a.season - b.season || a.number - b.number);
  const ref = episodeRef(episodeId);
  if (!ref) return undefined;
  return list.find((episode) => episode.season === ref.season && episode.number === ref.number + 1)
    ?? list.find((episode) => episode.season === ref.season + 1 && episode.number === 1);
}

function PlayerOverlay({ state, setState, onClose }: { state: PlayerState; setState: (state: PlayerState | null) => void; onClose: () => void }) {
  const title = getTitle(state.titleId);
  const loadedEpisode = title.episodes?.find((candidate) => candidate.id === state.episodeId);
  // Before its season is loaded (after a reload), the season and number still come from the episode id,
  // so the player opens on the right episode instead of falling back to the first one.
  const idRef = episodeRef(state.episodeId);
  const episode: Episode | undefined = loadedEpisode ?? (idRef && state.episodeId ? { id: state.episodeId, season: idRef.season, number: idRef.number, title: '', durationMinutes: title.durationMinutes ?? 45 } : undefined);
  const duration = (episode?.durationMinutes ?? title.durationMinutes ?? 42) * 60;
  const [pickerOpen, setPickerOpen] = useState(false);
  const isSeries = title.type === 'series';
  const ref = episodeRef(state.episodeId);
  // Opened on an episode whose season isn't loaded yet (a reload or a shared #/watch link): load it,
  // so the episode name and «الحلقة التالية» are available.
  const [, setSeasonLoaded] = useState(0);
  useEffect(() => {
    if (!isSeries || !title.tmdbId || !ref || loadedEpisode) return;
    let cancelled = false;
    fetchSeasonEpisodes(title.tmdbId, ref.season).then((list) => {
      if (cancelled || !list.length) return;
      title.episodes = [...(title.episodes ?? []).filter((item) => item.season !== ref.season), ...list];
      setSeasonLoaded((count) => count + 1);
    });
    return () => { cancelled = true; };
  }, [title, isSeries, ref?.season, Boolean(loadedEpisode)]); // eslint-disable-line react-hooks/exhaustive-deps
  const next = isSeries ? nextEpisodeOf(title, state.episodeId) : undefined;
  const pick = (episodeId: string) => { setPickerOpen(false); setState({ ...state, episodeId, seconds: 0, playing: state.playing }); };
  const episodeLabel = episode ? `م${arabicDigits(episode.season)} ح${arabicDigits(episode.number)}${episode.title ? `: ${episode.title}` : ''}` : 'فيلم';
  const seriesControls = isSeries ? <div className="player-episode-actions">
    {next ? <button className="player-chip" onClick={() => pick(next.id)}><Icon name="skipForward" size={17} />الحلقة التالية</button> : null}
    <button className="player-chip" onClick={() => setPickerOpen(true)} aria-haspopup="dialog" aria-expanded={pickerOpen}><Icon name="list" size={17} />الحلقات</button>
  </div> : null;
  const picker = pickerOpen ? <EpisodePicker title={title} currentEpisodeId={state.episodeId} onPick={pick} onClose={() => setPickerOpen(false)} /> : null;

  // In-app playback via embed for TMDB-backed titles (has ads; OM Player stays ad-free for local files).
  if (title.tmdbId) {
    const tmdbId = title.tmdbId;
    let embedUrl = `https://hnembed.cc/embed/movie/${tmdbId}`;
    if (title.type === 'series') {
      const season = episode?.season ?? 1;
      const number = episode?.number ?? 1;
      embedUrl = `https://hnembed.cc/embed/tv/${tmdbId}/${season}/${number}`;
    }
    return (
      <div className="player-overlay" role="dialog" aria-modal="true" aria-label="مشغّل">
        <div className="player-top" style={{ position: 'relative', zIndex: 2 }}>
          <button className="icon-button on-image" onClick={onClose} aria-label="إغلاق المشغّل"><Icon name="close" /></button>
          <div><b>{title.title}</b><span dir="auto">{episodeLabel}</span></div>
          {seriesControls}
          <DemoNote>بثّ تجريبي</DemoNote>
        </div>
        {picker}
        <iframe
          src={embedUrl}
          title={title.title}
          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', border: 0, zIndex: 1 }}
          allow="autoplay; fullscreen; picture-in-picture"
          allowFullScreen
        />
      </div>
    );
  }
  const percentage = Math.min(100, (state.seconds / duration) * 100);
  const captionLines = ['لم يكن في الحكاية ما توقّعناه.', 'لكن الطريق يعرف أسماءنا.', 'سنلتقي قبل انطفاء الضوء.'];
  return <div className="player-overlay" role="dialog" aria-modal="true" aria-label="مشغّل تجريبي">
    <PosterArt title={title} className="player-art" />
    <div className="player-scrim" />
    <div className="player-top"><button className="icon-button on-image" onClick={onClose} aria-label="إغلاق المشغّل"><Icon name="close" /></button><div><b>{title.title}</b><span dir="auto">{episodeLabel}</span></div>{seriesControls}<DemoNote>مشغّل تجريبي</DemoNote></div>
    {picker}
    <div className="player-message"><span className="playback-watermark">معاينة واجهة</span>{state.captions !== 'off' && state.seconds > 4 ? <p className={state.captions === 'en' ? 'caption english' : 'caption'}>{state.captions === 'en' ? 'We will meet before the light goes out.' : captionLines[Math.floor(state.seconds / 6) % captionLines.length]}</p> : null}</div>
    <div className="player-controls">
      <div className="player-title"><p className="eyebrow-small">لا يوجد ملف فيديو أو بثّ</p><h2>{episode?.title ?? title.title}</h2><span>الأزرار تحاكي شكل المشغّل فقط.</span></div>
      <div className="player-buttons"><button className="skip-button" onClick={() => setState({ ...state, seconds: Math.max(0, state.seconds - 10) })} aria-label="رجوع عشر ثوانٍ"><Icon name="skipBack" /><small>١٠ ثوانٍ</small></button><button className="big-play" onClick={() => setState({ ...state, playing: !state.playing })} aria-label={state.playing ? 'إيقاف المحاكاة مؤقتًا' : 'بدء المحاكاة'}><Icon name={state.playing ? 'pause' : 'play'} size={30} /></button><button className="skip-button" onClick={() => setState({ ...state, seconds: Math.min(duration, state.seconds + 10) })} aria-label="تقديم عشر ثوانٍ"><Icon name="skipForward" /><small>١٠ ثوانٍ</small></button></div>
      <div className="timeline" dir="ltr"><span>{formatTime(state.seconds)}</span><input aria-label="موضع التشغيل التجريبي" type="range" min="0" max={duration} value={state.seconds} style={{ '--progress': `${percentage}%` } as React.CSSProperties} onChange={(event) => setState({ ...state, seconds: Number(event.target.value) })} /><span>−{formatTime(duration - state.seconds)}</span></div>
      <label className="caption-select"><Icon name="captions" size={18} /><span>الترجمة في المعاينة</span><select value={state.captions} onChange={(event) => setState({ ...state, captions: event.target.value as PlayerState['captions'] })}><option value="off">إيقاف</option><option value="ar">العربية</option><option value="en">English</option></select></label>
    </div>
  </div>;
}

const RECENT_KEY = 'mashhad.recentSearches';
function readRecent(): string[] {
  try { const value = JSON.parse(window.localStorage.getItem(RECENT_KEY) ?? '[]'); return Array.isArray(value) ? value.filter((item) => typeof item === 'string').slice(0, 6) : []; } catch { return []; }
}

function SearchResultCard({ title, onOpen, saved = false }: { title: MashhadTitle; onOpen: (id: string) => void; saved?: boolean }) {
  return <button className="search-card" onClick={() => onOpen(title.id)} aria-label={`${kindLabel(title.type)}: ${title.title}، ${arabicDigits(title.year)}`}>
    <span className="search-card-poster"><PosterArt title={title} /><span className={`search-card-type ${title.type}`}>{kindLabel(title.type)}</span>{saved ? <span className="search-card-saved" aria-label="في قائمتي"><Icon name="bookmark" size={15} /></span> : null}</span>
    {/* dir="auto" keeps English titles readable from their own start instead of being clipped from the wrong side. */}
    <span className="search-card-title" dir="auto">{title.title}</span>
    <span className="search-card-meta">{arabicDigits(title.year)}{title.genres[0] ? `، ${title.genres[0]}` : ''}</span>
  </button>;
}

function SearchModalInner({ onOpen, onClose }: { onOpen: (id: string) => void; onClose: () => void }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<MashhadTitle[]>([]);
  const [loading, setLoading] = useState(false);
  const [kind, setKind] = useState<'all' | MediaType>('all');
  const [recent, setRecent] = useState<string[]>(readRecent);
  const inputRef = useRef<HTMLInputElement>(null);
  const trimmed = query.trim();

  useEffect(() => {
    inputRef.current?.focus();
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    function onKey(event: KeyboardEvent) { if (event.key === 'Escape') onClose(); }
    window.addEventListener('keydown', onKey);
    return () => { document.body.style.overflow = previous; window.removeEventListener('keydown', onKey); };
  }, [onClose]);

  useEffect(() => {
    if (!trimmed) { setResults([]); setLoading(false); return; }
    let cancelled = false;
    setLoading(true);
    const timer = window.setTimeout(() => {
      searchTmdbTitles(trimmed).then((found) => {
        if (cancelled) return;
        // Openable, but kept out of the catalog so searches never leak into home rows or «اكتشف».
        for (const title of found) if (!titlesById.has(title.id)) titlesById.set(title.id, title);
        setResults(found.map((title) => titlesById.get(title.id) ?? title));
        setLoading(false);
      });
    }, 350);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [trimmed]);

  function open(id: string) {
    if (trimmed) {
      const next = [trimmed, ...recent.filter((item) => item !== trimmed)].slice(0, 6);
      setRecent(next);
      try { window.localStorage.setItem(RECENT_KEY, JSON.stringify(next)); } catch { /* recent searches are optional */ }
    }
    onOpen(id);
  }

  const shown = kind === 'all' ? results : results.filter((title) => title.type === kind);
  const trending = [...new Set(catalog.topTenIds)].map((id) => titlesById.get(id)).filter((title): title is MashhadTitle => Boolean(title)).slice(0, 10);

  return <>
    <div className="search-head">
      <label className="search-field">
        <span className="search-field-icon">{loading ? <Spinner size={22} glyph={false} /> : <Icon name="search" size={21} />}</span>
        <input ref={inputRef} value={query} onChange={(event) => setQuery(event.target.value)} placeholder="ابحث عن فيلم أو مسلسل…" aria-label="ابحث عن فيلم أو مسلسل"
          onKeyDown={(event) => { if (event.key === 'Enter' && shown[0]) open(shown[0].id); }} enterKeyHint="search" autoComplete="off" />
        {query ? <button className="search-field-clear" onClick={() => { setQuery(''); inputRef.current?.focus(); }} aria-label="مسح البحث"><Icon name="close" size={18} /></button> : null}
      </label>
      <button className="search-close" onClick={onClose}>إغلاق<kbd>Esc</kbd></button>
    </div>

    {trimmed && results.length ? <div className="search-filters" role="group" aria-label="نوع العنوان">
      {([['all', 'الكل'], ['film', 'أفلام'], ['series', 'مسلسلات']] as const).map(([value, label]) => <button key={value} className={kind === value ? 'selected' : ''} aria-pressed={kind === value} onClick={() => setKind(value)}>{label}</button>)}
    </div> : null}

    <div className="search-body">
      <p className="search-status" role="status" aria-live="polite">
        {!trimmed ? '' : loading ? `جارٍ البحث عن «${trimmed}»…` : shown.length ? `${arabicDigits(shown.length)} ${shown.length <= 10 && shown.length > 2 ? 'نتائج' : 'نتيجة'} لـ «${trimmed}»؛ اضغط Enter لفتح الأولى` : ''}
      </p>

      {!trimmed ? <>
        {recent.length ? <section className="search-section"><div className="search-section-head"><h3>عمليات بحث سابقة</h3><button onClick={() => { setRecent([]); try { window.localStorage.removeItem(RECENT_KEY); } catch { /* optional */ } }}>مسح</button></div>
          <div className="search-recent">{recent.map((item) => <button key={item} onClick={() => setQuery(item)} dir="auto"><Icon name="clock" size={15} />{item}</button>)}</div></section> : null}
        {trending.length ? <section className="search-section"><div className="search-section-head"><h3>الأكثر رواجًا الآن</h3></div>
          <div className="search-grid">{trending.map((title) => <SearchResultCard key={title.id} title={title} onOpen={open} />)}</div></section> : null}
      </> : null}

      {trimmed && loading && !results.length ? <div className="search-grid" aria-hidden="true">{Array.from({ length: 10 }, (_, index) => <span className="search-card skeleton" key={index}><span className="search-card-poster" /><span className="skeleton-line" /><span className="skeleton-line short" /></span>)}</div> : null}

      {trimmed && !loading && !results.length ? <div className="search-empty"><Icon name="search" size={30} /><b>لم نعثر على نتائج لـ «{trimmed}»</b><span>تحقّق من الإملاء، أو جرّب الاسم الأصلي للعنوان بالإنجليزية.</span></div> : null}

      {trimmed && shown.length ? <div className={`search-grid ${loading ? 'refreshing' : ''}`}>{shown.map((title) => <SearchResultCard key={title.id} title={title} onOpen={open} />)}</div> : null}
    </div>
  </>;
}

export default function App() {
  function parseHash(): { view: View; selectedId: string; from: NavView; watch?: { titleId: string; episodeId?: string } } {
    const h = window.location.hash.replace(/^#\/?/, '');
    const [path, qs] = h.split('?');
    const parts = path.split('/');
    const params = new URLSearchParams(qs || '');
    const from = (params.get('from') as NavView) || 'home';
    if (parts[0] === 'details' && parts[1]) return { view: 'details', selectedId: decodeURIComponent(parts[1]), from };
    if (parts[0] === 'watch' && parts[1]) {
      const titleId = decodeURIComponent(parts[1]);
      return { view: 'details', selectedId: titleId, from, watch: { titleId, episodeId: params.get('ep') ?? undefined } };
    }
    if (parts[0] === 'browse') return { view: 'browse', selectedId: '', from: 'home' };
    if (parts[0] === 'list') return { view: 'list', selectedId: '', from: 'home' };
    if (parts[0] === 'profile') return { view: 'profile', selectedId: '', from: 'home' };
    return { view: 'home', selectedId: '', from: 'home' };
  }
  const initial = parseHash();
  // A #/watch link opens the player once its title has loaded.
  const pendingWatch = useRef(initial.watch ?? null);
  const [view, setView] = useState<View>(initial.view);
  const [previousView, setPreviousView] = useState<NavView>(initial.from);
  const [selectedId, setSelectedId] = useState<string>(initial.selectedId);
  function readSavedTitles(): string[] {
    try {
      const saved = JSON.parse(window.localStorage.getItem('mashhad.savedTitles') ?? '[]') as unknown;
      if (!Array.isArray(saved)) return [];
      return saved.filter((id): id is string => typeof id === 'string');
    } catch {
      return [];
    }
  }
  const [saved, setSaved] = useState<string[]>(readSavedTitles);
  const [query, setQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<'all' | MediaType>('all');
  const [genreFilter, setGenreFilter] = useState('الكل');
  const [homeType, setHomeType] = useState<'all' | MediaType>('all');
  const [settings, setSettings] = useState<Record<string, boolean>>({ autoplay: false, dataSaver: false, notifications: true });
  const [player, setPlayer] = useState<PlayerState | null>(null);
  const [watchHistory, setWatchHistory] = useState<WatchEntry[]>(readWatchProgress);
  const [notice, setNotice] = useState('');
  const [catalogVersion, setCatalogVersion] = useState(0);
  const [watchProviders, setWatchProviders] = useState<{ country: string; providers: WatchProvider[]; link: string | null } | null>(null);
  const [loadingCatalog, setLoadingCatalog] = useState(true);
  const [boot, setBoot] = useState({ progress: 0, label: 'جارٍ تحميل الخطوط' });
  const [splash, setSplash] = useState<'on' | 'leaving' | 'off'>('on');
  const [routeLoading, setRouteLoading] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [missingId, setMissingId] = useState('');

  useEffect(() => {
    try { window.localStorage.setItem('mashhad.savedTitles', JSON.stringify(saved)); } catch { /* local saving is optional in restricted browsers */ }
  }, [saved]);

  useEffect(() => {
    if (!player?.playing) return;
    const timer = window.setInterval(() => setPlayer((current) => current ? { ...current, seconds: current.seconds + 1 } : null), 1000);
    return () => window.clearInterval(timer);
  }, [player?.playing]);

  const lastSaved = useRef(0);
  useEffect(() => {
    if (!player) return;
    const seconds = Math.round(player.seconds);
    if (!player.playing || Math.abs(seconds - lastSaved.current) >= 5) {
      lastSaved.current = seconds;
      recordProgress(player);
    }
    // recordProgress only reads the catalog; re-running on its identity would save on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [player?.titleId, player?.episodeId, player?.playing, player && Math.floor(player.seconds / 5)]);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(''), 2600);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const lastPlayerTitle = useRef<string | null>(null);
  // Sync URL hash with view/selectedId so refresh and back/forward work.
  useEffect(() => {
    // A #/watch link is still waiting for its title: leave the address alone so a refresh keeps the episode.
    if (!player && pendingWatch.current) return;
    let hash = '';
    if (player) hash = `#/watch/${encodeURIComponent(player.titleId)}?${player.episodeId ? `ep=${encodeURIComponent(player.episodeId)}&` : ''}from=${previousView}`;
    else if (view === 'details' && selectedId) hash = `#/details/${encodeURIComponent(selectedId)}?from=${previousView}`;
    else if (view === 'browse') hash = '#/browse';
    else if (view === 'list') hash = '#/list';
    else if (view === 'profile') hash = '#/profile';
    else hash = '#/';
    if (window.location.hash !== hash) {
      // Switching episodes inside an open player replaces the entry, so Back closes the player instead of stepping through episodes.
      if (player && lastPlayerTitle.current === player.titleId) window.history.replaceState(null, '', hash);
      else window.location.hash = hash;
    }
    lastPlayerTitle.current = player?.titleId ?? null;
  }, [view, selectedId, previousView, player?.titleId, player?.episodeId]);

  // Respond to browser back/forward.
  useEffect(() => {
    function onHashChange() {
      const p = parseHash();
      setView(p.view);
      if (p.selectedId) setSelectedId(p.selectedId);
      setPreviousView(p.from);
      // Back/forward into or out of #/watch opens or closes the player.
      const current = playerRef.current;
      if (p.watch && (current?.titleId !== p.watch.titleId || current?.episodeId !== p.watch.episodeId)) {
        if (titlesById.has(p.watch.titleId)) openPlayerRef.current(p.watch.titleId, p.watch.episodeId);
        else pendingWatch.current = p.watch;
      } else if (!p.watch && current) {
        closePlayerRef.current();
      }
    }
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  useEffect(() => {
    const title = selectedId ? titlesById.get(selectedId) : null;
    if (!title || !title.tmdbId) { setWatchProviders(null); setRouteLoading(false); return; }
    let cancelled = false;
    fetchWatchProviders(title.tmdbId, title.type).then((r) => {
      if (cancelled) return;
      setWatchProviders(r);
    }).finally(() => { if (!cancelled) setRouteLoading(false); });
    return () => { cancelled = true; };
  }, [selectedId]);

  // Ensure a title referenced by the URL (e.g. after refresh) is loaded even if not in the initial batch.
  useEffect(() => {
    if (!selectedId || titlesById.has(selectedId) || !hasTmdbKey()) return;
    let cancelled = false;
    setMissingId('');
    fetchTitleById(selectedId).then((t) => {
      if (cancelled) return;
      if (!t) { setMissingId(selectedId); return; }
      if (!catalog.titles.some((e) => e.id === t.id)) catalog.titles.push(t);
      titlesById.set(t.id, t);
      setCatalogVersion((v) => v + 1);
    });
    return () => { cancelled = true; };
  }, [selectedId]);

  // Launch: the splash stays up until fonts, the TMDB catalog and the first posters are loaded.
  useEffect(() => {
    let cancelled = false;
    const started = performance.now();
    const sleep = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));
    const preload = (src: string) => new Promise<void>((resolve) => {
      const image = new Image();
      const timer = window.setTimeout(resolve, 4000);
      image.onload = image.onerror = () => { window.clearTimeout(timer); resolve(); };
      image.src = src;
    });
    (async () => {
      await document.fonts?.ready;
      if (cancelled) return;
      setBoot({ progress: .1, label: 'جارٍ جلب العناوين' });
      if (hasTmdbKey()) {
        await loadGenres().catch(() => null);   // official Arabic genre names before any title is mapped
        const titles = await fetchTmdbTitles(40, 1, (done, total) => { if (!cancelled) setBoot({ progress: .1 + .7 * done / total, label: 'جارٍ جلب العناوين' }); });
        if (cancelled) return;
        for (const t of titles) {
          if (!catalog.titles.some((e) => e.id === t.id)) catalog.titles.push(t);
          titlesById.set(t.id, t);
        }
        if (titles.length > 0) {
          catalog.featuredIds = titles.filter((t) => t.featured).map((t) => t.id);
          catalog.topTenIds = titles.map((t) => t.id).slice(0, 10);
          // Genre chips come from the titles actually loaded, most common first.
          const genreCounts = new Map<string, number>();
          for (const t of titles) for (const genre of t.genres) genreCounts.set(genre, (genreCounts.get(genre) ?? 0) + 1);
          catalog.genres = [...genreCounts.entries()].sort((a, b) => b[1] - a[1]).map(([genre]) => genre);
          setCatalogVersion((v) => v + 1);
          setSelectedId((prev) => prev || titles[0].id);
          setNotice(`تم تحميل ${titles.length} عنوانًا من TMDB.`);
        }
        // Real release dates for «قريبًا»; failure just leaves the section hidden.
        const upcoming = await fetchUpcoming().catch(() => []);
        if (cancelled) return;
        for (const { title } of upcoming) if (!titlesById.has(title.id)) titlesById.set(title.id, title);
        catalog.upcoming = upcoming.map(({ title, date }) => ({
          titleId: title.id,
          label: 'عرض أوّل في السينما',
          day: date.getDate(),
          month: date.toLocaleDateString('ar-EG', { month: 'long' }),
        }));
      }
      // Saved and recently watched titles may come from search or recommendations, not today's popular list:
      // fetch any that are missing so «قائمتي» and «واصل المشاهدة» stay complete after a refresh.
      if (hasTmdbKey()) {
        const wanted = [...new Set([...readSavedTitles(), ...readWatchProgress().map((entry) => entry.titleId)])].filter((id) => !titlesById.has(id));
        const found = await Promise.all(wanted.slice(0, 40).map((id) => fetchTitleById(id).catch(() => null)));
        if (cancelled) return;
        for (const title of found) if (title && !titlesById.has(title.id)) titlesById.set(title.id, title);
      }
      const posters = [
        ...catalog.featuredIds.map((id) => titlesById.get(id)?.backdropPath).filter((path): path is string => Boolean(path)).map((path) => `w1280${path}`),
        ...[...new Set([...catalog.featuredIds, ...catalog.topTenIds])].map((id) => titlesById.get(id)?.posterPath).filter((path): path is string => Boolean(path)).map((path) => `w500${path}`),
      ];
      let ready = 0;
      setBoot({ progress: .8, label: 'جارٍ تجهيز الملصقات' });
      await Promise.all(posters.map((path) => preload(`https://image.tmdb.org/t/p/${path}`).then(() => {
        if (!cancelled) setBoot({ progress: .8 + .2 * ++ready / posters.length, label: 'جارٍ تجهيز الملصقات' });
      })));
      if (cancelled) return;
      setBoot({ progress: 1, label: 'جاهز' });
      setLoadingCatalog(false);
      await sleep(Math.max(250, 2700 - (performance.now() - started)));   // let the logo sequence finish
      if (cancelled) return;
      setSplash('leaving');
      await sleep(760);
      if (!cancelled) setSplash('off');
    })();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, [view, selectedId]);

  const activeNav: NavView = view === 'details' ? previousView : view;
  const selectedTitle = useMemo(() => (selectedId ? titlesById.get(selectedId) ?? null : null), [selectedId, catalogVersion]);

  function navigate(next: NavView) {
    setView(next);
    if (next === 'browse' && view !== 'details') setPreviousView('browse');
  }

  function openDetails(id: string) {
    setPreviousView(view === 'details' ? 'browse' : view);
    if (id !== selectedId && titlesById.get(id)?.tmdbId) setRouteLoading(true);
    setSelectedId(id);
    setView('details');
  }

  function toggleSaved(id: string) {
    const alreadySaved = saved.includes(id);
    setSaved((current) => alreadySaved ? current.filter((item) => item !== id) : [id, ...current]);
    setNotice(alreadySaved ? 'أُزيل العنوان من قائمتك المحلية.' : 'أُضيف العنوان إلى قائمتك المحلية.');
  }

  function durationOf(titleId: string, episodeId?: string): number {
    const title = titlesById.get(titleId);
    const episode = title?.episodes?.find((candidate) => candidate.id === episodeId);
    return (episode?.durationMinutes ?? title?.durationMinutes ?? 42) * 60;
  }

  function recordProgress(state: PlayerState) {
    const title = titlesById.get(state.titleId);
    if (!title) return;
    const episode = title.episodes?.find((candidate) => candidate.id === state.episodeId);
    const ref = episodeRef(state.episodeId);
    // TMDB titles play in an embedded third-party player whose position the page can't read, so only "opened" is kept.
    setWatchHistory(saveWatchProgress({
      titleId: title.id, episodeId: state.episodeId, season: episode?.season ?? ref?.season, number: episode?.number ?? ref?.number,
      seconds: title.tmdbId ? null : Math.round(state.seconds), duration: durationOf(title.id, episode?.id),
    }));
  }

  function openPlayer(titleId: string, episodeId?: string) {
    // Without an explicit episode, a series resumes the episode it stopped at.
    const last = watchHistory.find((entry) => entry.titleId === titleId);
    const episode = episodeId ?? (last && !isFinished(last) ? last.episodeId : undefined) ?? titlesById.get(titleId)?.episodes?.[0]?.id;
    const next: PlayerState = { titleId, episodeId: episode, seconds: resumeSeconds(watchHistory, titleId, episode), playing: false, captions: 'off' };
    setPlayer(next);
    recordProgress(next);
  }

  function closePlayer() {
    if (player) recordProgress(player);
    setPlayer(null);
  }
  // The hashchange listener is registered once, so it reaches the latest state through refs.
  const playerRef = useRef(player);
  playerRef.current = player;
  const openPlayerRef = useRef(openPlayer);
  openPlayerRef.current = openPlayer;
  const closePlayerRef = useRef(closePlayer);
  closePlayerRef.current = closePlayer;
  useEffect(() => {
    const pending = pendingWatch.current;
    if (!pending || loadingCatalog || !titlesById.has(pending.titleId)) return;
    pendingWatch.current = null;
    openPlayer(pending.titleId, pending.episodeId);
  }, [loadingCatalog, catalogVersion]); // eslint-disable-line react-hooks/exhaustive-deps

  const selectedIsSaved = saved.includes(selectedId);

  const splashLayer = splash !== 'off' ? <Splash key="splash" progress={boot.progress} label={boot.label} leaving={splash === 'leaving'} fontsReady={boot.progress > 0} /> : null;
  if (loadingCatalog) {
    return <div className="application" dir="rtl">{splashLayer}</div>;
  }
  if (catalog.titles.length === 0) {
    return <div className="application" dir="rtl"><main className="main-panel" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 12 }}><p style={{ color: '#EDF4F2', fontSize: 20 }}>لا توجد بيانات</p><p style={{ color: '#A7B7C3' }}>أضف مفتاح TMDB في web/.env (VITE_TMDB_API_KEY).</p></main></div>;
  }
  // The splash stays the first child in both branches so React keeps the same element and its animation doesn't restart.
  return <div className={`application ${splash === 'leaving' ? 'entering' : ''}`} dir="rtl">
    {splashLayer}
    <aside className="sidebar"><button className="sidebar-brand" onClick={() => navigate('home')}><Mark /></button><NavButtons mode="side" active={activeNav} onNavigate={navigate} savedCount={saved.length} /><div className="sidebar-foot"><p className="sidebar-tagline">{TAGLINE}</p><DemoNote /><span>الإصدار ٠٫١</span></div></aside>
    <main className={`main-panel ${view === 'home' ? 'home-view' : ''}`}>
      {view !== 'home' ? <Header onNavigate={navigate} onSearch={() => setSearchOpen(true)} /> : null}
      {view === 'home' ? <HomePage key={catalogVersion} saved={saved} watchHistory={watchHistory} onForget={(id) => setWatchHistory(removeWatchProgress(id))} homeType={homeType} setHomeType={setHomeType} onNavigate={navigate} onOpen={openDetails} onSave={toggleSaved} onPlay={openPlayer} onSearch={() => setSearchOpen(true)} /> : null}
      {view === 'browse' ? <BrowsePage key={catalogVersion} query={query} setQuery={setQuery} typeFilter={typeFilter} setTypeFilter={setTypeFilter} genreFilter={genreFilter} setGenreFilter={setGenreFilter} saved={saved} onOpen={openDetails} /> : null}
      {view === 'details' && !selectedTitle ? (missingId === selectedId || !hasTmdbKey()
        ? <div className="page-content content-width"><div className="empty-state"><span className="empty-icon"><Icon name="film" size={28} /></span><h2>لم نعثر على هذا العنوان</h2><p>ربما حُذف من TMDB أو أن الرابط غير صحيح.</p><button className="button-primary" onClick={() => navigate('home')}>العودة إلى الرئيسية</button></div></div>
        : <div className="page-content content-width episodes-loading" aria-busy="true"><Spinner size={44} /><span>جارٍ تحميل العنوان…</span></div>) : null}
      {view === 'details' && selectedTitle ? <DetailPage title={selectedTitle!} saved={selectedIsSaved} watchEntry={watchHistory.find((entry) => entry.titleId === selectedTitle!.id)} watchProviders={watchProviders} onBack={() => setView(previousView)} onOpen={openDetails} onSave={toggleSaved} onPlay={openPlayer} showNotice={setNotice} /> : null}
      {view === 'list' ? <ListPage saved={saved} onNavigate={navigate} onOpen={openDetails} onSave={toggleSaved} /> : null}

      {view === 'profile' ? <ProfilePage settings={settings} setSettings={setSettings} showNotice={setNotice} /> : null}
    </main>
    <NavButtons mode="bottom" active={activeNav} onNavigate={navigate} savedCount={saved.length} />
    {player ? <PlayerOverlay state={player} setState={(next) => setPlayer(next)} onClose={closePlayer} /> : null}
    <div className={`toast ${notice ? 'visible' : ''}`} role="status" aria-live="polite">{notice}</div>
    <RouteLoader active={routeLoading && view === 'details'} />
    {searchOpen ? (
      <div className="search-modal-backdrop" onClick={() => setSearchOpen(false)}>
        <div className="search-modal" role="dialog" aria-modal="true" aria-label="البحث" onClick={(e) => e.stopPropagation()}>
          <SearchModalInner onOpen={(id) => { setSearchOpen(false); openDetails(id); }} onClose={() => setSearchOpen(false)} />
        </div>
      </div>
    ) : null}
  </div>;
}
