import { useEffect, useState } from 'react';
import { arabicDigits } from './catalog';

export const TAGLINE = 'عينك على المَشهد';

export function Spinner({ size = 56, glyph = true, label }: { size?: number; glyph?: boolean; label?: string }) {
  return <span className="spinner" style={{ '--sz': `${size}px` } as React.CSSProperties} role={label ? 'status' : undefined} aria-label={label} aria-hidden={label ? undefined : true}>
    <svg viewBox="0 0 64 64"><circle cx="32" cy="32" r="27" className="spinner-track" /><circle cx="32" cy="32" r="27" className="spinner-arc" /></svg>
    {glyph ? <span className="spinner-glyph">م<i /></span> : null}
  </span>;
}

// Shows `active` only once it has lasted `delay` ms, then keeps it visible for at least `min` ms so it never flashes.
export function useDelayedFlag(active: boolean, delay = 160, min = 420): boolean {
  const [visible, setVisible] = useState(false);
  const [shownAt, setShownAt] = useState(0);
  useEffect(() => {
    if (active && !visible) {
      const timer = window.setTimeout(() => { setVisible(true); setShownAt(performance.now()); }, delay);
      return () => window.clearTimeout(timer);
    }
    if (!active && visible) {
      const timer = window.setTimeout(() => setVisible(false), Math.max(0, min - (performance.now() - shownAt)));
      return () => window.clearTimeout(timer);
    }
  }, [active, visible, delay, min, shownAt]);
  return visible;
}

export function RouteLoader({ active }: { active: boolean }) {
  const visible = useDelayedFlag(active);
  return <div className={`route-loader ${visible ? 'on' : ''}`} aria-hidden={!visible}>
    {visible ? <Spinner size={60} label="جارٍ التحميل" /> : null}
    <span>لحظة، نجهّز المشهد…</span>
  </div>;
}

export function Splash({ progress, label, leaving, fontsReady }: { progress: number; label: string; leaving: boolean; fontsReady: boolean }) {
  return <div className={`splash ${leaving ? 'leaving' : ''}`} role="status" aria-live="polite" aria-busy={!leaving} aria-label="جارٍ تحميل مَشهد">
    <div className="splash-grain" />
    <div className="splash-stage">
      <div className="splash-crop" />
      {/* Mount the lettering only once Aref Ruqaa is ready, so it never shows in a fallback face first. */}
      {fontsReady ? <><div className="splash-cone" /><div className="splash-word"><span className="wordmark"><span>مشهد</span><i /></span></div></> : null}
    </div>
    {fontsReady ? <p className="splash-tagline">{TAGLINE}</p> : null}
    <div className="splash-foot">
      <div className="splash-track"><i style={{ transform: `scaleX(${Math.max(0, Math.min(1, progress))})` }} /></div>
      <span>{progress >= 1 ? 'جاهز' : `${label} ${arabicDigits(Math.round(progress * 100))}٪`}</span>
    </div>
  </div>;
}
