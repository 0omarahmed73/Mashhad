import type { SVGProps } from 'react';

type IconName = 'home' | 'search' | 'bookmark' | 'download' | 'user' | 'play' | 'pause' | 'arrow' | 'plus' | 'check' | 'sparkle' | 'close' | 'back' | 'info' | 'clock' | 'film' | 'menu' | 'trash' | 'settings' | 'chevron' | 'filter' | 'calendar' | 'list' | 'globe' | 'wifi' | 'captions' | 'skipBack' | 'skipForward';

const paths: Record<IconName, React.ReactNode> = {
  home: <><path d="m3 10 9-7 9 7"/><path d="M5 9v11h14V9M9 20v-6h6v6"/></>,
  search: <><circle cx="10.8" cy="10.8" r="6.6"/><path d="m16 16 4.3 4.3"/></>,
  bookmark: <path d="M6 4.5A1.5 1.5 0 0 1 7.5 3h9A1.5 1.5 0 0 1 18 4.5V21l-6-3.8L6 21z"/>,
  download: <><path d="M12 3v12m-5-5 5 5 5-5"/><path d="M5 16v4h14v-4"/></>,
  user: <><circle cx="12" cy="8" r="3.5"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/></>,
  play: <path d="m8 5 11 7-11 7z" fill="currentColor" stroke="none"/>,
  pause: <><path d="M8 5h3v14H8z" fill="currentColor" stroke="none"/><path d="M15 5h3v14h-3z" fill="currentColor" stroke="none"/></>,
  arrow: <><path d="M5 12h14"/><path d="m13 6 6 6-6 6"/></>,
  plus: <><path d="M12 5v14M5 12h14"/></>,
  check: <path d="m5 12 4.5 4.5L19 7"/>,
  sparkle: <><path d="m12 3 1.8 6.2L20 11l-6.2 1.8L12 19l-1.8-6.2L4 11l6.2-1.8z"/><path d="m19 16 .8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z"/></>,
  close: <><path d="m6 6 12 12M18 6 6 18"/></>,
  back: <><path d="M19 12H5"/><path d="m11 18-6-6 6-6"/></>,
  info: <><circle cx="12" cy="12" r="9"/><path d="M12 11v5m0-8h.01"/></>,
  clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
  film: <><rect x="3.5" y="4" width="17" height="16" rx="2"/><path d="M7 4v16M17 4v16M3.5 9h3.5m-3.5 6h3.5m10-6h3.5m-3.5 6h3.5"/></>,
  menu: <><path d="M4 7h16M4 12h16M4 17h16"/></>,
  trash: <><path d="M4 7h16M10 11v6m4-6v6M6.5 7l1 14h9l1-14M9 7V4h6v3"/></>,
  settings: <><circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1a1.7 1.7 0 0 1-2.4 2.4l-.1-.1a1.7 1.7 0 0 0-2.9 1.2v.2a1.7 1.7 0 0 1-3.4 0v-.2a1.7 1.7 0 0 0-2.9-1.2l-.1.1a1.7 1.7 0 0 1-2.4-2.4l.1-.1a1.7 1.7 0 0 0-1.2-2.9H4a1.7 1.7 0 0 1 0-3.4h.2A1.7 1.7 0 0 0 5.4 5.8l-.1-.1a1.7 1.7 0 0 1 2.4-2.4l.1.1a1.7 1.7 0 0 0 2.9-1.2V2a1.7 1.7 0 0 1 3.4 0v.2a1.7 1.7 0 0 0 2.9 1.2l.1-.1a1.7 1.7 0 0 1 2.4 2.4l-.1.1a1.7 1.7 0 0 0 1.2 2.9h.2a1.7 1.7 0 0 1 0 3.4h-.2a1.7 1.7 0 0 0-1.2 2.9z" transform="translate(0 1) scale(.92)"/></>,
  chevron: <path d="m9 18 6-6-6-6"/>,
  filter: <><path d="M4 6h16M7 12h10m-7 6h4"/><circle cx="8" cy="6" r="1" fill="currentColor"/><circle cx="15" cy="12" r="1" fill="currentColor"/><circle cx="11" cy="18" r="1" fill="currentColor"/></>,
  calendar: <><rect x="3.5" y="5" width="17" height="16" rx="2"/><path d="M7 3v4m10-4v4M4 10h16"/></>,
  list: <><path d="M9 6h11M9 12h11M9 18h11"/><circle cx="4.5" cy="6" r=".7" fill="currentColor"/><circle cx="4.5" cy="12" r=".7" fill="currentColor"/><circle cx="4.5" cy="18" r=".7" fill="currentColor"/></>,
  globe: <><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"/></>,
  wifi: <><path d="M3 9.5a14 14 0 0 1 18 0M6.5 13a9 9 0 0 1 11 0M10 16.5a3.4 3.4 0 0 1 4 0"/><circle cx="12" cy="20" r=".8" fill="currentColor"/></>,
  captions: <><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M7 11h4m-4 3h3m4-3h3m-3 3h2"/></>,
  skipBack: <><path d="M5 5v14m14-14-9 7 9 7z"/><path d="M2 9v6"/></>,
  skipForward: <><path d="M19 5v14M5 5l9 7-9 7z"/><path d="M22 9v6"/></>,
};

export function Icon({ name, size = 22, className, ...props }: SVGProps<SVGSVGElement> & { name: IconName; size?: number }) {
  return <svg aria-hidden="true" viewBox="0 0 24 24" width={size} height={size} className={className} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" {...props}>{paths[name]}</svg>;
}
