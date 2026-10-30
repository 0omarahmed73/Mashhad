# Mashhad project handoff

**Updated:** 2026-10-31\
**Workspace:** `/home/omarahmed/Downloads/OmarFelix`\
**Purpose:** Self-contained starting point for a new agent continuing the DramaWorld-to-Mashhad project without needing this chat.

## Start here

The user wants **Mashhad**, an Arabic-first web application and a standalone native Android app for films and series, inspired by the provided DramaWorld APK but built from scratch. The user liked Claude's design in [`mashhad/index.html`](mashhad/index.html); it remains the visual reference.

The **functional local-sample MVP is implemented** in [`web/`](web/), [`android/`](android/), and [`shared/catalog/catalog.json`](shared/catalog/catalog.json). The user explicitly asked to build the sample MVP and stop. There is no production backend, authorized catalog feed, or real video playback in this MVP.

Read this file first, then use [Project files](#project-files). The earlier [`prototype/index.html`](prototype/index.html) is not the chosen design; the user disliked its layout.

## User goal and decisions

- Product: an original responsive web app and native Android app with their own screens and touch interactions.
- Build strategy: clean-room implementation informed by the APK's observable product behavior. The JADX export is evidence, not a buildable source project.
- Language and layout: Arabic-first UI with correct RTL behavior. Media timelines and playback time remain left-to-right.
- Visual direction: the user approved the Mashhad concept and its dark navy, blue, and mint theme. The recommended home direction in the prototype is **The Poster**.
- Working brand: **مَشهد (Mashhad)** appears in the accepted concept. The user liked the design; they have not separately confirmed that the name is final for package/store branding.

## Accepted design system

The exact palette in `mashhad/index.html` is:

| Role | Color |
| --- | --- |
| Main background | `#121D2B` |
| Surface | `#1B2A3A` |
| Raised surface | `#26384B` |
| Border | `#354A5D` |
| Mint accent | `#92DECC` |
| Main text | `#EDF4F2` |
| Secondary text | `#A7B7C3` |
| Destructive state | `#D88F9C` |

The design file presents three home-screen compositions: **The Poster** (recommended), **The Reel**, and **The Magazine**. The connected sample prototype is The Poster, sized at 393 × 852 dp. Its typography reference is Aref Ruqaa for titles/quotes, IBM Plex Sans Arabic for interface text, and Reem Kufi for numerals/kickers. The web apps load these from Google Fonts. The Android app bundles Aref Ruqaa and IBM Plex Sans Arabic (OFL) in `android/app/src/main/res/font/`; Reem Kufi is not bundled (only a variable font is published).

The branding concept uses the Arabic wordmark مَشهد with a mint fatha over the meem as a projector-beam motif, plus a viewfinder-style app icon. The approved tagline is **«عينك على المَشهد»**.

### Launch splash and loaders (all three clients)

- Animated splash: mint viewfinder corners close in, the wordmark is revealed right to left, the mint fatha slides in and casts a flickering projector beam over film grain, then the tagline rises. Exit: corners open outward and the content fades in.
- The progress bar tracks real work (fonts → catalog/TMDB requests → first posters), with a minimum of about 2.7 s so the sequence finishes, and a timeout fallback.
- Shared spinner: mint arc turning counter-clockwise around a meem with a pulsing fatha. Loaders appear only after 160 ms and stay at least 420 ms so they never flash.
- `mashhad/index.html`: full-page splash on load and in-phone replay ("شاشة البداية").
- `web/`: `src/Splash.tsx` (Splash, Spinner, RouteLoader, `useDelayedFlag`); wired in `App.tsx`; `tmdb.ts` `fetchTmdbTitles` takes an optional progress callback. The splash must stay the first child of `.application` in both render branches or React remounts it and the animation replays.
- `android/`: `Splash.kt` (fonts, `Wordmark`, `MashhadSpinner`, `RouteLoader`, `MashhadSplash`); system SplashScreen API hands over to it via `Theme.Mashhad.Starting`. The HTML also explains rejected naming options **سِتارة** and **عَرض**; do not treat those as selected names.

## Current MVP behavior

Both clients load the same eight fictional Arabic titles from `shared/catalog/catalog.json`. It includes five fictional series with 64 flattened episode rows and contains no external art or video URLs.

### Web app

- Responsive Arabic RTL screens: Home, Explore/search/type-and-genre filters, title details/seasons/episodes, My List, demo Downloads, Profile, and a simulated player.
- Poster artwork is generated locally with SVG. My List uses browser `localStorage`; download requests and playback state are demo-only.
- Real titles from TMDB (popular movies/shows in Arabic) are fetched at startup when `VITE_TMDB_API_KEY` is set in `web/.env`. Fictional titles remain as fallback.
- Detail page shows a "أين تُشاهَد" section with streaming services from TMDB's free watch-providers endpoint.
- **Discrepancy (2026-10-31):** this file previously said scraping/iframe playback was removed, but `web/src/App.tsx` `PlayerOverlay` still embeds `vidsrc.to` for TMDB titles. That service streams copyrighted titles without a licence; remove it before any release and keep playback to official trailers, watch-provider links, or user-owned media.
- Production build passed with `npm run build` (`tsc --noEmit` and Vite 8.3.4). A browser walkthrough confirmed search, series details/episodes, and saving to My List. No test suite was run.
- Run locally: `cd web && npm install && npm run dev`.

### Native Android app

- Kotlin + Jetpack Compose app in `android/`, package `app.mashhad.android`, label `مَشهد`.
- Screens: Home, Explore/search/type-and-genre filters, title details and episodes, My List, demo Downloads, Profile, and a simulated player. The app loads the shared JSON as an Android asset. Artwork is drawn locally with Compose Canvas.
- The hero and detail-page `معاينة تجريبية` buttons use one-line text at the connected phone's target width.
- Launcher icon: adaptive vector (`res/drawable/ic_launcher_foreground.xml`), the meem outline generated from Aref Ruqaa Bold plus mint fatha and viewfinder corners, with a monochrome layer and an Android 6–7 fallback.
- TMDB loading runs on `Dispatchers.IO` and in parallel (it previously ran on the main thread, which threw `NetworkOnMainThreadException` and silently returned no titles). Watch providers and search also run on IO.
- The TMDB key is read from `android/local.properties` (`tmdb.apiKey=…`) into `BuildConfig.TMDB_API_KEY`; it is no longer in source. `local.properties` is listed in `android/.gitignore`.
- My List and download status are in-memory only. Player controls and progress are simulated; there is no video/audio source. The manifest requests Internet permission.
- Real titles from TMDB (popular movies/shows in Arabic) are fetched at startup when `tmdb.apiKey` is set in `android/local.properties`. Fictional titles remain as fallback.
- Detail screen shows a "أين تُشاهَد" section with streaming services from TMDB's free watch-providers endpoint.
- "فتح في OM Player" button on Home and Detail launches a file picker and hands the selected video to OM Player (package `com.omplayer`) for ad-free playback. Falls back to a chooser if OM Player is not installed.
- `:app:assembleDebug` passed with Gradle 8.14.3, AGP 8.13.0, Kotlin 2.2.20, and Compose. The updated APK installed on the user's USB-connected phone, `MainActivity` launched successfully, and a screenshot confirmed the CTA remains on one line.
- Rebuild: `cd android && ./gradlew assembleDebug`. APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Android Studio can open the `android/` directory.

### Design reference

`mashhad/index.html` remains the design presentation and connected prototype, not the app source. To serve it locally, run `python3 -m http.server 8766 --bind 127.0.0.1` from `mashhad/` and open `http://127.0.0.1:8766/`.

## Original APK recovery findings

Input APK: `/home/omarahmed/Downloads/DramaWorld_V4.2a.apk`\
SHA-256: `0e13b9a116f4f8e14f11b75333c8cb5eb69b10c9209784ce99790dc99f597a84`

- Manifest package/application ID: `com.alam.aldrama3`; version `V4.2` / code `35`; min SDK 21; target SDK 35. The filename suffix `4.2a` is not the manifest version.
- APK contains four primary DEX files, a separate audience-network DEX asset, compiled resources, and native libraries for multiple ABIs.
- JADX 1.5.6 exported a Gradle-shaped project under `recovered/`, but reported 37 decompilation errors. The export contains about 21,689 Java files including libraries/generated classes; 67 top-level files retain the app namespace. It has 111 methods containing explicit “Method not decompiled” exception stubs. Decompiled output is not proof that those stubs exist in the shipped bytecode.
- APK metadata records AGP 8.7.0, data binding 8.7.0, Room 2.6.1, AppCompat 1.6.1, Material 1.11.0, and Kotlin coroutines 1.7.1. These bundled versions do not reconstruct the full original build configuration.
- Observed app flow: `MyApplication` initializes shared state/services; `SplashActivity` obtains Remote Config and validates app identity before routing to intro/login/home; `HomeActivity` uses a drawer and five-fragment ViewPager; movie/series/episode screens request data/source through `api/apiRest.java`; playback/download flows rely on external integrations. See `analysis/HOW_IT_IS_BUILT.md` for evidence and limits.
- Other useful source landmarks in the export: `api/apiRest.java` (Retrofit declarations), `Utils/MyDatabase.java` (Room cache/resume state), and `Provider/DownloadForegroundService.java` (download notifications). Original playback also queries an external player package. These describe the old client and are not requirements for the new app.
- The original backend implementation, database, admin panel, signing private key, and full original source project are not in the APK.

## API and backend status

`analysis/API_INVENTORY.md` and `analysis/api-inventory.json` contain **58 recovered Retrofit declarations** from the client. They are static evidence only: no server was contacted, and the response Java types are not verified backend schemas.

The APK's startup validation sends signing-certificate-derived data and receives encrypted configuration, including the content API base URL. That base URL is unknown offline. A new signing key may not be authorized by the original service. No validation was bypassed. Extracted endpoint declarations do not grant API access or permission to use a service.

The recovered declarations cover home data and search; movie/series/channel discovery and filters; actors, categories, genres, and countries; title details, seasons, episodes, sources, and subtitles; My List; user registration/login/profile/password flows; comments, ratings, share/view/download counters; support; and subscription/payment methods. These are **observed capabilities of the original client**, not a confirmed scope for Mashhad. Exact paths and parameters remain in the inventory files.

Do not hardcode guessed endpoints, secrets, credentials, or keys.

## Recovered build status

The JADX export is **not currently buildable as recovered**:

- Its generated `recovered/app/build.gradle` has an empty `dependencies` block and does not reproduce original build logic.
- The attempted offline build changed only `recovered/build.gradle` to use locally cached AGP 8.13.0; the original export was preserved as `recovered/build.gradle.jadx-export`.
- Gradle reached `:app:mergeDebugResources` and failed because JADX exported AndroidX nine-patch resources as 1×1 PNGs; AAPT2 requires at least 3×3 source nine-patch images. Java compilation was not reached.
- The build therefore does not establish whether the decompiled Java compiles. The 37 JADX errors, missing dependency declarations, and runtime validation are separate blockers.
- A rebuild would have a different signature. Without the original signing key and service-side authorization, it cannot be assumed to work as an update or pass startup validation.
- The original APK was not installed or launched in the recorded analysis. Login, streaming, and downloads were not runtime-verified.

Environment notes from the recovery work: Java 21 is installed at `/usr/lib/jvm/java-21-openjdk-amd64`; Android SDK is at `/home/omarahmed/Android/Sdk`; local Gradle 8.14.3 and Android SDK platforms through 36 are available. Cached AGP 8.13.0 was used for the failed offline check. Re-check the environment before choosing versions for the new project.

Do not try to repair this export into Mashhad. Preserve it as a reference; the new Android app is in `android/`.

## Current stopping point

**2026-10-31 (test pass and fixes):**
- Web «اكتشف» pages TMDB `/discover` by type and genre (`discoverTitles` in `tmdb.ts`) with official Arabic genre names (`loadGenres`); search and recommendation results register in `titlesById` only, never in `catalog.titles`. The ✕ resets query, type and genre.
- Player routing: `#/watch/<titleId>?ep=<episodeId>`; episode switches inside the player use `history.replaceState`, so Back closes the player. Season/episode are parsed from episode ids (`episodeRef`), so resume works before a season is loaded.
- Season/episode picker overlay in the web player (`EpisodePicker`, «الحلقات» and «الحلقة التالية» chips). It only changes app state, so it works with any player. The user plans to replace the `vidsrc.to` embed with a licensed player.
- Security: `proxy/server.js` is an open proxy (fetches any URL incl. internal addresses, forwards cookies, permissive CORS); not used by either app. Recommended deletion along with `tools/` scrapers. `web/.env` is now git-ignored; the TMDB key is visible in the client bundle and the APK.
- Accessibility pass: 44 px minimum targets, 12 px minimum text, broken detail links show «لم نعثر على هذا العنوان», saved/watched titles missing from the first catalog page are fetched at startup.
- Android: activity-compose 1.13.0, material3 1.4.0, Compose 1.11.4, compile/target SDK 36, Gradle 8.14.6, backup rules for Android 12+. Compose 1.12.x needs AGP 9.1+, Gradle 9 and SDK 37 — a separate migration. Lint: 0 errors.

**2026-10-31 (copy pass):** «حسابي» removed from the web and Android navigation; the profile opens from the header avatar (Android: new avatar button in the top bar). Dialectal strings rewritten in فصحى (e.g. «لم نعثر على نتائج», «قائمتك فارغة حتى الآن», «لا توجد تنزيلات»), season counts use Arabic number agreement (موسم واحد، موسمان، ٣ مواسم), «·» separators next to Arabic-Indic digits replaced with «،» (the dot reads as ٠), and copy that called every title "fictional" now fits real TMDB titles. The half-finished removal of the web Downloads page was completed (page, route and state removed; download buttons now say downloads are available in the Android app).

**2026-10-31:** Splash/spinner/tagline added to `mashhad/index.html`, `web/`, and `android/`; Android fonts, icon, IO-thread fix, and key relocation done. The user asked not to run the Android app on a device yet; they will run it after they finish fixing other issues. The Android splash, icon and wordmark fatha position are therefore not yet visually verified on a device.


The sample-data MVP is built and installed on the user's phone. Since then, Mashhad has been extended toward a real catalog + discovery app:
- Web: TMDB-backed real titles, watch-providers availability, no in-app scraping.
- Android: TMDB-backed real titles, watch-providers availability, OM Player handoff for local files.
- OM Player manifest updated to accept http/https handoff.
- Scraping/proxy code remains in `tools/` and `proxy/` but is no longer used by the apps.

Real ad-free playback requires licensed or self-hosted media; OM Player handles playback of files/streams the user controls.

## Project files

| Path | Purpose / status |
| --- | --- |
| `HANDOFF.md` | This continuation brief. |
| `mashhad/index.html` | Current user-approved visual concept and connected web prototype. |
| `web/` | Responsive React + TypeScript + Vite web MVP and production output. Run `npm install` then `npm run dev`. |
| `android/` | Native Kotlin + Jetpack Compose app with Gradle wrapper. Run `./gradlew assembleDebug`. |
| `shared/catalog/catalog.json` | Shared fictional Arabic sample catalog loaded by both apps. |
| `prototype/index.html` | Earlier concept; user disliked its layout. Keep only as history/reference. |
| `BUILD_STATUS.md` | Detailed APK recovery and failed recovered-build record. |
| `analysis/HOW_IT_IS_BUILT.md` | Static architecture, recovered build metadata, execution paths, and caveats. |
| `analysis/API_INVENTORY.md` | Human-readable summary of 58 client API declarations. |
| `analysis/api-inventory.json` | Structured endpoint/method/parameter inventory. |
| `analysis/jadx-recovery.log` | Original JADX recovery log. |
| `analysis/graphify-out/` | Static graph for 67 app-namespace Java files only (2,938 nodes, 8,106 edges); obfuscated helpers and runtime/reflection links are outside its coverage. |
| `recovered/` | JADX-generated resources, manifest, Java, and failed Gradle export; reference only. |
| `tools/jadx-1.5.6/` | Local JADX 1.5.6 distribution used for recovery. |
| `tools/extract_api.py` | Re-extracts/checks Retrofit declarations from recovered source; it does not validate a server. |

There is no usable Git repository at the workspace root. The new Android app is separate from `recovered/`; do not try to convert the JADX export into Mashhad.
