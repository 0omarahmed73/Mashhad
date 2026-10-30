import { useId, useState } from 'react';
import type { MashhadTitle } from './catalog';
import { Spinner } from './Splash';

// `backdrop` uses TMDB's wide still (for heroes) and falls back to the poster.
export function PosterArt({ title, className = '', backdrop = false, style }: { title: MashhadTitle; className?: string; backdrop?: boolean; style?: React.CSSProperties }) {
  const rawId = useId().replaceAll(':', '');
  const skyId = `sky-${rawId}`;
  const glowId = `glow-${rawId}`;
  const grainId = `grain-${rawId}`;
  const sky = title.artwork.sky;
  const accent = title.artwork.accent;
  const horizon = title.artwork.horizon;
  const posterUrl = backdrop && title.backdropPath ? `https://image.tmdb.org/t/p/w1280${title.backdropPath}`
    : title.posterPath ? `https://image.tmdb.org/t/p/w500${title.posterPath}` : '';
  // Posters preloaded by the launch splash are already cached, so they skip the spinner.
  const [loaded, setLoaded] = useState(() => {
    if (!posterUrl) return false;
    const probe = new Image();
    probe.src = posterUrl;
    return probe.complete;
  });

  if (posterUrl) {
    return (
      <div className={`poster-art ${className}`} style={style} aria-hidden="true">
        {!loaded ? <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#1B2A3A', zIndex: 2 }}><Spinner size={30} glyph={false} /></div> : null}
        <img src={posterUrl} alt="" draggable={false} loading={backdrop ? "eager" : "lazy"} onLoad={() => setLoaded(true)} style={{ width: '100%', height: '100%', objectFit: 'cover', opacity: loaded ? 1 : 0, transition: 'opacity .2s' }} />
      </div>
    );
  }

  return (
    <div className={`poster-art ${className}`} style={style} aria-hidden="true">
      <svg viewBox="0 0 400 650" preserveAspectRatio="xMidYMid slice">
        <defs>
          <linearGradient id={skyId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor={sky[0]} />
            <stop offset=".55" stopColor={sky[1]} />
            <stop offset="1" stopColor={sky[2]} />
          </linearGradient>
          <radialGradient id={glowId}>
            <stop offset="0" stopColor={accent} stopOpacity=".66" />
            <stop offset="1" stopColor={accent} stopOpacity="0" />
          </radialGradient>
          <filter id={grainId} x="0" y="0" width="100%" height="100%">
            <feTurbulence type="fractalNoise" baseFrequency=".8" numOctaves="2" stitchTiles="stitch" />
            <feColorMatrix values="1 0 0 0 0 0 1 0 0 0 0 0 1 0 0 0 0 0 .09 0" />
          </filter>
        </defs>
        <rect width="400" height="650" fill={`url(#${skyId})`} />
        <ellipse cx="260" cy="258" rx="165" ry="165" fill={`url(#${glowId})`} />
        {title.artStyle === 'space' ? <SpaceScene accent={accent} horizon={horizon} /> : null}
        {title.artStyle === 'desert' || title.artStyle === 'canyon' ? <DesertScene accent={accent} horizon={horizon} canyon={title.artStyle === 'canyon'} /> : null}
        {title.artStyle === 'sea' ? <SeaScene accent={accent} horizon={horizon} /> : null}
        {title.artStyle === 'alley' || title.artStyle === 'hospital' || title.artStyle === 'cafe' || title.artStyle === 'city' ? <CityScene accent={accent} horizon={horizon} scene={title.artStyle} /> : null}
        {title.artStyle === 'train' ? <TrainScene accent={accent} horizon={horizon} /> : null}
        {title.artStyle === 'palms' ? <PalmScene accent={accent} horizon={horizon} /> : null}
        {title.artStyle === 'salt' ? <SaltScene accent={accent} horizon={horizon} /> : null}
        <rect width="400" height="650" filter={`url(#${grainId})`} opacity=".22" />
      </svg>
    </div>
  );
}

function SpaceScene({ accent, horizon }: { accent: string; horizon: string }) {
  return <g>
    <circle cx="265" cy="247" r="99" fill={accent} opacity=".32" />
    <circle cx="263" cy="248" r="71" fill="#C47E70" />
    <path d="M195 250c32-37 99-60 151-20" fill="none" stroke="#E9D5C4" strokeWidth="5" opacity=".7" />
    <ellipse cx="260" cy="254" rx="122" ry="34" fill="none" stroke="#D7D8E9" strokeWidth="4" opacity=".75" transform="rotate(-18 260 254)" />
    <path d="M0 476c87-44 160-38 229-16 67 21 126 24 171-1v191H0z" fill={horizon} />
    <circle cx="56" cy="160" r="2" fill="#E7EDF6"/><circle cx="92" cy="93" r="1.5" fill="#E7EDF6"/><circle cx="341" cy="111" r="2" fill="#E7EDF6"/><circle cx="326" cy="388" r="1.6" fill="#E7EDF6"/>
  </g>;
}

function DesertScene({ accent, horizon, canyon }: { accent: string; horizon: string; canyon: boolean }) {
  return <g>
    <circle cx="267" cy="268" r="53" fill={accent} opacity=".82" />
    {canyon ? <><path d="M0 210 83 300 100 487 0 551z" fill="#793F34" opacity=".8"/><path d="m400 162-94 118-18 220 112 50z" fill="#763A31"/><path d="m0 266 83 79v142l-83 42zm400-41-87 93-8 147 95 41z" fill={horizon}/></> : <><path d="M0 401 77 355l71 30 99-95 153 98v262H0z" fill={horizon}/><path d="M0 478c76-58 129-43 193-16s136 47 207-5v193H0z" fill="#40292B"/><path d="M0 538c77-25 126-18 198 7s132 34 202 5v100H0z" fill="#211B24"/></>}
    <path d="M40 528c26-35 55-49 87-52m-68 57c6-23 5-37-1-55m27 44c16-25 33-39 51-45" fill="none" stroke="#1C1B21" strokeWidth="5" strokeLinecap="round" opacity=".8"/>
  </g>;
}

function SeaScene({ accent, horizon }: { accent: string; horizon: string }) {
  return <g>
    <circle cx="255" cy="279" r="54" fill={accent} opacity=".86" />
    <path d="M0 363c61-20 117-20 173 0s126 19 227-1v288H0z" fill={horizon}/>
    <path d="M0 404c45-17 79-17 122 0s76 17 120 0 78-17 158 1M0 453c54-16 93-16 142 1s95 17 147 0 74-15 111-4M0 507c48-15 85-13 128 2s89 18 132 1 87-18 140 1" fill="none" stroke="#BAC9D5" strokeOpacity=".44" strokeWidth="4"/>
    <path d="M0 570c76-18 145-18 203 0s125 18 197-1v81H0z" fill="#101829" opacity=".85"/>
    <path d="M52 364h28v-39h40v39h28v-58h27v58h35v-76h24v76h28v-47h35v47h29v-32h34v32h27" fill="#1C2130" opacity=".55"/>
  </g>;
}

function CityScene({ accent, horizon, scene }: { accent: string; horizon: string; scene: string }) {
  const blocks = scene === 'alley'
    ? <><path d="M0 171h122v479H0z" fill="#24212E"/><path d="M278 125h122v525H278z" fill="#191C2A"/><path d="M30 208h41v59H30zm62 0h20v59H92zm-62 95h41v50H30zm62 0h20v50H92zm211-119h48v68h-48zm0 109h48v53h-48zm63 12h35v68h-35z" fill={accent} opacity=".58"/><path d="M0 470 131 309l105 88 164-180v433H0z" fill={horizon}/></>
    : scene === 'hospital'
      ? <><path d="M65 169h270v481H65z" fill="#182633"/><path d="M95 205h74v64H95zm133 0h74v64h-74zM95 320h74v65H95zm133 0h74v65h-74zM95 433h74v65H95zm133 0h74v65h-74z" fill={accent} opacity=".35"/><path d="M184 506h31v144h-31z" fill="#0D1922"/><path d="M194 83h12v48h-12zm-18 18h48v12h-48z" fill={accent}/><path d="M0 572h400v78H0z" fill={horizon}/></>
      : scene === 'cafe'
        ? <><path d="M0 186h400v464H0z" fill="#24170F"/><path d="M38 234h122v206H38zm202 0h122v206H240z" fill={accent} opacity=".28" stroke="#E7B98F" strokeOpacity=".45" strokeWidth="8"/><path d="M0 464h400v186H0z" fill={horizon}/><path d="M114 462v89m170-89v89m-213 0h255" stroke="#D2945A" strokeWidth="12"/><path d="M184 390h39v57h-39z" fill={accent} opacity=".8"/></>
        : <><path d="M0 315 53 213l42 102v335H0zm95 0 61-167 62 167v335H95zm132 0 50-91 52 91v335H227zm100 0 42-155 56 155v335H327z" fill={horizon}/><path d="M24 347h14m38 0h14m34 0h14m39 0h14m39 0h14m34 0h14m41 0h14" stroke={accent} strokeWidth="5" opacity=".55"/><path d="M0 560c100-22 195-23 400 0v90H0z" fill="#111821"/></>;
  return <g>
    <circle cx="284" cy="245" r="43" fill={accent} opacity=".55" />
    {blocks}
    {scene === 'city' ? <path d="M40 160 66 234m57-116 25 80m68-104 21 74m79-82 20 73m40-42 14 53" stroke="#BDD2DE" strokeOpacity=".35" strokeWidth="3"/> : null}
    <path d="M14 650V565m17 85v-34m331 34v-76m19 76v-38" stroke="#10151D" strokeWidth="10" opacity=".8"/>
  </g>;
}

function TrainScene({ accent, horizon }: { accent: string; horizon: string }) {
  return <g>
    <circle cx="242" cy="260" r="47" fill={accent} opacity=".84" />
    <path d="M0 374h400v276H0z" fill={horizon}/>
    <path d="M104 650 177 372h19L155 650m140 0-54-278h20l82 278" fill="none" stroke="#C39770" strokeWidth="8" opacity=".55"/>
    <path d="M83 650 175 428h50l91 222" fill="none" stroke="#D8B48A" strokeWidth="3" opacity=".45"/>
    <path d="M0 456 100 414v236H0m400-252-90 31v221h90" fill="#241C24"/>
    <rect x="130" y="423" width="126" height="88" rx="12" fill="#17202A" stroke={accent} strokeOpacity=".55" strokeWidth="4"/>
    <path d="M148 444h39v40h-39zm52 0h38v40h-38z" fill={accent} opacity=".55"/>
  </g>;
}

function PalmScene({ accent, horizon }: { accent: string; horizon: string }) {
  return <g>
    <circle cx="291" cy="268" r="52" fill={accent} opacity=".82" />
    <path d="M0 436c88-70 168-53 229-15 61 38 112 43 171 9v220H0z" fill={horizon}/>
    <path d="M96 495c9-120 25-210 43-277" fill="none" stroke="#39251D" strokeWidth="18"/>
    <path d="M139 226c-58-50-91-43-119-40 43 28 72 51 116 61m7-17c-21-67-8-100 2-130 18 47 20 79 9 130m3 7c44-62 78-71 112-81-27 43-57 70-101 94m-30 9c-62-18-99-5-130 8 51 11 88 11 135 2m5-8c58-31 93-28 125-21-44 29-78 42-126 37" fill="#1E3027" stroke="#263B2F" strokeWidth="4"/>
    <path d="M0 557c100-33 181-23 264 5s106 31 136 22v66H0z" fill="#151B1D"/>
  </g>;
}

function SaltScene({ accent, horizon }: { accent: string; horizon: string }) {
  return <g>
    <circle cx="299" cy="263" r="43" fill={accent} opacity=".7" />
    <path d="M0 381c79-20 139-18 207 3s116 25 193-2v268H0z" fill={horizon}/>
    <path d="M0 452 102 421l103 33 111-25 84 20v201H0z" fill="#E0DED5" opacity=".72"/>
    <path d="M0 493c82-22 139-12 205 13s124 32 195 4M0 568c98-17 151-3 220 19s107 22 180 0" fill="none" stroke="#F7F1E8" strokeWidth="7" opacity=".52"/>
    <circle cx="160" cy="453" r="10" fill="#1E2830"/><path d="m150 466-14 65h49l-14-65zm-12 21-18 20m52-20 18 20" fill="#1E2830"/>
  </g>;
}
