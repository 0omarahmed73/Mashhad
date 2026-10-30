package app.mashhad.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private fun openInOmPlayer(context: Context, uri: Uri): Boolean {
    val targeted = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        setPackage("com.omplayer")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(targeted)
        true
    } catch (_: ActivityNotFoundException) {
        val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "اختر مشغّلًا")
        runCatching { context.startActivity(chooser) }.isSuccess
    }
}

/** Opens a web link (a trailer, a streaming service's page) in the matching app or the browser. */
private fun openUrl(context: Context, url: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess

private enum class Tab(val label: String, val glyph: Glyph) { Home("الرئيسية", Glyph.Home), Explore("اكتشف", Glyph.Search), Saved("قائمتي", Glyph.Bookmark) }

/** Screens stacked on top of the current tab; Back pops them. */
private sealed interface Route {
    data class Detail(val title: Title, val season: Int? = null) : Route
    data class Play(val title: Title, val season: Int?, val episode: Int?) : Route
}

private data class HomeData(val trending: List<Title>, val movies: List<Title>, val series: List<Title>, val upcoming: List<Upcoming>, val hero: List<Title>)

private enum class SplashPhase { Showing, Leaving, Gone }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = darkColorScheme(primary = Mint, onPrimary = InkOnMint, background = Night, surface = SurfaceColor, onBackground = Foreground, onSurface = Foreground, outline = Border)) {
                    ProvideTextStyle(TextStyle(fontFamily = PlexArabic)) {
                        MashhadApp()
                    }
                }
            }
        }
    }
}

@Composable
private fun MashhadApp() {
    val context = LocalContext.current
    val store = remember { Store(context.applicationContext) }
    var home by remember { mutableStateOf<HomeData?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var label by remember { mutableStateOf("جارٍ تحميل الأنواع") }
    var phase by remember { mutableStateOf(SplashPhase.Showing) }

    // Launch: the splash stays until genres, the home rows and the first images are ready.
    LaunchedEffect(attempt) {
        val started = System.currentTimeMillis()
        loadFailed = false
        progress = .05f
        withTimeoutOrNull(5_000) { Tmdb.loadGenres() }
        progress = .2f; label = "جارٍ جلب العناوين"
        val data = withTimeoutOrNull(15_000) {
            coroutineScope {
                val trending = async { Tmdb.trending() }
                val movies = async { Tmdb.popular(Kind.Movie) }
                val series = async { Tmdb.popular(Kind.Tv) }
                val upcoming = async { Tmdb.upcoming() }
                val t = trending.await(); progress = .45f
                val m = movies.await(); val s = series.await(); progress = .65f
                val u = upcoming.await(); progress = .75f
                // New hero picks on every launch.
                HomeData(t, m, s, u, t.filter { it.backdrop != null }.shuffled().take(5))
            }
        }
        if (data == null || (data.trending.isEmpty() && data.movies.isEmpty())) {
            loadFailed = true
            phase = SplashPhase.Gone
            return@LaunchedEffect
        }
        label = "جارٍ تجهيز الصور"
        val images = data.hero.mapNotNull { it.backdropUrl } + data.trending.take(10).mapNotNull { it.posterUrl }
        var ready = 0
        withTimeoutOrNull(6_000) {
            coroutineScope {
                images.map { url -> async { context.imageLoader.execute(ImageRequest.Builder(context).data(url).build()); progress = .75f + .25f * ++ready / images.size } }.awaitAll()
            }
        }
        home = data
        progress = 1f
        delay((2_700 - (System.currentTimeMillis() - started)).coerceAtLeast(250))
        phase = SplashPhase.Leaving
        delay(760)
        phase = SplashPhase.Gone
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        val data = home
        when {
            data != null -> AppShell(data, store)
            loadFailed -> LoadError(onRetry = { phase = SplashPhase.Showing; progress = 0f; attempt++ })
        }
        if (phase != SplashPhase.Gone) MashhadSplash(progress, label, leaving = phase == SplashPhase.Leaving)
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Wordmark(size = 56.sp)
        Spacer(Modifier.height(24.dp))
        Text("تعذّر تحميل العناوين", color = Foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (Tmdb.hasKey) "تحقّق من اتصالك بالإنترنت ثم حاول مجددًا." else "مفتاح TMDB غير مضبوط في هذا الإصدار.", color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        PillButton("حاول مجددًا", null, primary = true, onClick = onRetry)
    }
}

@Composable
private fun AppShell(home: HomeData, store: Store) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(Tab.Home) }
    val stack = remember { mutableStateListOf<Route>() }
    var exploreKind by remember { mutableStateOf<Kind?>(null) }
    var exploreGenre by remember { mutableStateOf<String?>(null) }
    var exploreFocusSearch by remember { mutableIntStateOf(0) }
    fun open(title: Title, season: Int? = null) { stack.add(Route.Detail(title, season)) }
    fun play(title: Title, season: Int?, episode: Int?) { stack.add(Route.Play(title, season, episode)) }
    fun notify(message: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) } }
    fun toggleSaved(title: Title) { notify(if (store.toggleSaved(title)) "أُضيف «${title.name}» إلى قائمتي" else "أُزيل «${title.name}» من قائمتي") }
    fun browse(kind: Kind?) { exploreKind = kind; exploreGenre = null; stack.clear(); tab = Tab.Explore }

    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.Home) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else tab = Tab.Home
    }

    val top = stack.lastOrNull()
    if (top is Route.Play) {
        PlayerScreen(top.title, top.season, top.episode, store, onBack = { stack.removeAt(stack.lastIndex) })
        return
    }
    val detail = top as Route.Detail?
    Scaffold(
        containerColor = Night,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (detail == null) BottomBar(tab, savedCount = store.saved.size) { stack.clear(); tab = it } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (detail != null) {
                DetailScreen(
                    route = detail, store = store,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onPlay = { s, e -> play(detail.title, s, e) },
                    onOpen = { open(it) },
                    onToggleSaved = { toggleSaved(it) },
                    notify = { notify(it) },
                    openLink = { url -> if (!openUrl(context, url)) notify("لا يوجد تطبيق لفتح هذا الرابط") },
                )
            } else when (tab) {
                Tab.Home -> HomeScreen(home, store, onOpen = { t, s -> open(t, s) }, onResume = { entry -> play(entry.title, entry.season, entry.episode) }, onToggleSaved = { toggleSaved(it) }, onBrowse = { browse(it) }, onSearch = { exploreFocusSearch++; browse(null) })
                Tab.Explore -> ExploreScreen(
                    kind = exploreKind, genre = exploreGenre, focusSearch = exploreFocusSearch, store = store,
                    onKind = { exploreKind = it; if (exploreGenre != null && exploreGenre !in Tmdb.genreNames(it)) exploreGenre = null },
                    onGenre = { exploreGenre = it },
                    onReset = { exploreKind = null; exploreGenre = null },
                    onOpen = { open(it) },
                )
                Tab.Saved -> SavedScreen(store, onOpen = { open(it) }, onBrowse = { browse(null) })
            }
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Night.copy(alpha = .92f)))
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, savedCount: Int, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(SurfaceColor).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Tab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Tab) { onSelect(tab) }
                    .semantics { contentDescription = tab.label + if (tab == Tab.Saved && savedCount > 0) "، ${ar(savedCount)}" else "" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.width(64.dp).height(32.dp).background(if (active) Mint.copy(alpha = .14f) else Color.Transparent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    GlyphIcon(if (tab == Tab.Saved && active) Glyph.BookmarkFilled else tab.glyph, if (active) Mint else Muted, Modifier.size(22.dp))
                    if (tab == Tab.Saved && savedCount > 0) Text(
                        ar(savedCount), color = InkOnMint, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp).background(Mint, CircleShape).padding(horizontal = 5.dp),
                    )
                }
                Text(tab.label, color = if (active) Foreground else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TopBar(onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Wordmark(size = 30.sp)
        Text(TAGLINE, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onSearch).semantics { contentDescription = "بحث" }, contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.Search, Foreground, Modifier.size(22.dp))
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
private fun HomeScreen(home: HomeData, store: Store, onOpen: (Title, Int?) -> Unit, onResume: (HistoryEntry) -> Unit, onToggleSaved: (Title) -> Unit, onBrowse: (Kind?) -> Unit, onSearch: () -> Unit) {
    val context = LocalContext.current
    val omPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { openInOmPlayer(context, it) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        item { TopBar(onSearch) }
        if (home.hero.isNotEmpty()) item { HeroPager(home.hero, store, onOpen = { onOpen(it, null) }, onToggleSaved = onToggleSaved) }
        if (store.history.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("واصل من حيث توقّفت", subtitle = "سجلّ مشاهدتك")
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(store.history.take(12), key = { it.title.id }) { entry -> ContinueCard(entry, onClick = { onResume(entry) }, onForget = { store.forget(entry.title) }) }
                }
            }
        }
        item { PosterRow("الأكثر رواجًا هذا الأسبوع", "قائمة الأسبوع", home.trending, store, ranked = true, onOpen = { onOpen(it, null) }, onAll = { onBrowse(null) }) }
        item { PosterRow("أفلام رائجة", "أفلام", home.movies, store, onOpen = { onOpen(it, null) }, onAll = { onBrowse(Kind.Movie) }) }
        item { PosterRow("مسلسلات رائجة", "مسلسلات", home.series, store, onOpen = { onOpen(it, null) }, onAll = { onBrowse(Kind.Tv) }) }
        if (home.upcoming.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("قريبًا في السينما", subtitle = "مواعيد العرض")
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(home.upcoming, key = { it.title.id }) { UpcomingCard(it, onClick = { onOpen(it.title, null) }) }
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceColor).clickable(role = Role.Button) { omPicker.launch("video/*") }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GlyphIcon(Glyph.Play, Mint, Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("لديك ملف فيديو على جهازك؟", color = Foreground, fontWeight = FontWeight.SemiBold)
                    Text("شغّله في OM Player بدون إعلانات", color = Muted, fontSize = 12.5.sp)
                }
                Text("اختيار", color = Mint, fontWeight = FontWeight.SemiBold)
            }
        }
        item { Text("بيانات الأفلام من TMDB، ومعلومات المشاهدة من JustWatch.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) }
    }
}

@Composable
private fun HeroPager(titles: List<Title>, store: Store, onOpen: (Title) -> Unit, onToggleSaved: (Title) -> Unit) {
    val pager = rememberPagerState { titles.size }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 18.dp), pageSpacing = 12.dp) { page ->
            val title = titles[page]
            Box(Modifier.fillMaxWidth().aspectRatio(.78f).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button) { onOpen(title) }) {
                RemoteImage(title.backdropUrl, Modifier.fillMaxSize(), title.name)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .35f to Color.Transparent, 1f to Night.copy(alpha = .97f))))
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الأكثر رواجًا، ${kindLabel(title.kind)}", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(title.name, color = Foreground, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    MetaLine(title)
                    if (title.overview.isNotBlank()) Text(title.overview, color = Foreground.copy(alpha = .88f), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                        PillButton("التفاصيل والمشاهدة", Glyph.Play, primary = true, modifier = Modifier.weight(1f)) { onOpen(title) }
                        val saved = store.isSaved(title)
                        PillButton(if (saved) "في قائمتي" else "قائمتي", if (saved) Glyph.Check else Glyph.Add, primary = false) { onToggleSaved(title) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(titles.size) { index ->
                Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (index == pager.currentPage) 22.dp else 6.dp).background(if (index == pager.currentPage) Mint else Border, CircleShape))
            }
        }
    }
}

@Composable
private fun MetaLine(title: Title, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        title.rating?.let {
            GlyphIcon(Glyph.Star, Mint, Modifier.size(14.dp))
            Text(ar(String.format(java.util.Locale.US, "%.1f", it)), color = Foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(listOfNotNull(title.year?.let { ar(it) }, title.genres.take(2).joinToString("، ").ifBlank { null }, extra).joinToString("  |  "), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PosterRow(title: String, subtitle: String, titles: List<Title>, store: Store, ranked: Boolean = false, onOpen: (Title) -> Unit, onAll: () -> Unit) {
    if (titles.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title, subtitle = subtitle, action = "الكل", onAction = onAll)
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(titles.take(if (ranked) 10 else 20), key = { _, t -> t.id }) { index, t ->
                PosterCard(t, onClick = { onOpen(t) }, saved = store.isSaved(t), rank = if (ranked) index + 1 else null)
            }
        }
    }
}

@Composable
private fun ContinueCard(entry: HistoryEntry, onClick: () -> Unit, onForget: () -> Unit) {
    Column(Modifier.width(240.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceColor).clickable(role = Role.Button, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            RemoteImage(entry.title.backdropUrl, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Night.copy(alpha = .6f)), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Play, Foreground, Modifier.size(18.dp)) }
            if (entry.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)).height(3.dp).background(Mint))
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).clip(CircleShape).background(Night.copy(alpha = .7f)).clickable(role = Role.Button, onClick = onForget)
                    .semantics { contentDescription = "إزالة ${entry.title.name} من السجلّ" },
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.Close, Foreground, Modifier.size(14.dp)) }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title.name, color = Foreground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.season != null) "م${ar(entry.season)} ح${ar(entry.episode ?: 1)}" + (entry.episodeName?.let { ": $it" } ?: "") else kindLabel(entry.title.kind),
                color = Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpcomingCard(item: Upcoming, onClick: () -> Unit) {
    val date = runCatching { java.time.LocalDate.parse(item.date) }.getOrNull()
    val month = date?.month?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("ar-EG")).orEmpty()
    Row(Modifier.width(290.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceColor).clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.width(84.dp).heightIn(min = 120.dp).background(Raised).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(date?.dayOfMonth?.let { ar(it) } ?: "—", color = Mint, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(month, color = Muted, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("عرض أوّل في السينما", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(item.title.name, color = Foreground, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.title.genres.take(2).joinToString("، "), color = Muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- explore

@Composable
private fun ExploreScreen(kind: Kind?, genre: String?, focusSearch: Int, store: Store, onKind: (Kind?) -> Unit, onGenre: (String?) -> Unit, onReset: () -> Unit, onOpen: (Title) -> Unit) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Title>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val feed = remember { mutableStateListOf<Title>() }
    var page by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var feedKey by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch > 0) runCatching { focus.requestFocus() } }

    LaunchedEffect(query) {
        val text = query.trim()
        if (text.isEmpty()) { searchResults = null; searching = false; return@LaunchedEffect }
        searching = true
        delay(400)
        searchResults = Tmdb.search(text)
        searching = false
    }
    // Pages come straight from TMDB for the chosen type and genre; a stale page for an old filter is dropped.
    fun loadMore() {
        if (loading || done) return
        val key = "$kind|$genre"
        loading = true
        scope.launch {
            val (titles, finished) = Tmdb.discover(kind, genre, page + 1)
            if (key != feedKey) return@launch
            feed.addAll(titles.filter { new -> feed.none { it.id == new.id } })
            page += 1; done = finished; loading = false
        }
    }
    LaunchedEffect(kind, genre) {
        feedKey = "$kind|$genre"; feed.clear(); page = 0; done = false; loading = false
        grid.scrollToItem(0)
        loadMore()
    }
    LaunchedEffect(grid, kind, genre) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last -> if (query.isBlank() && last >= feed.size - 6) loadMore() }
    }

    val searchMode = query.isNotBlank()
    val results = if (searchMode) searchResults.orEmpty().filter { kind == null || it.kind == kind } else feed
    LazyVerticalGrid(
        GridCells.Adaptive(108.dp), state = grid, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("اكتشف", color = Foreground, fontFamily = Ruqaa, fontWeight = FontWeight.Bold, fontSize = 38.sp)
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("ابحث عن فيلم أو مسلسل أو ممثّل…", color = Muted) },
                    leadingIcon = { if (searching) MashhadSpinner(size = 22.dp, glyph = false) else GlyphIcon(Glyph.Search, Muted, Modifier.size(20.dp)) },
                    trailingIcon = if (query.isNotEmpty() || kind != null || genre != null) {
                        {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { query = ""; onReset() }.semantics { contentDescription = "مسح البحث والعودة إلى الكل" },
                                contentAlignment = Alignment.Center,
                            ) { GlyphIcon(Glyph.Close, Muted, Modifier.size(16.dp)) }
                        }
                    } else null,
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor, unfocusedContainerColor = SurfaceColor, focusedTextColor = Foreground, unfocusedTextColor = Foreground,
                        focusedIndicatorColor = Mint, unfocusedIndicatorColor = Border, cursorColor = Mint,
                    ),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf<Pair<Kind?, String>>(null to "الكل", Kind.Movie to "أفلام", Kind.Tv to "مسلسلات")) { (k, label) -> Chip(label, kind == k) { onKind(k) } }
                }
                if (!searchMode) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Chip("كل الأنواع", genre == null) { onGenre(null) } }
                    items(Tmdb.genreNames(kind)) { name -> Chip(name, genre == name) { onGenre(name) } }
                }
                Text(
                    if (searchMode) (if (searching) "جارٍ البحث عن «${query.trim()}»…" else "نتائج «${query.trim()}»")
                    else listOfNotNull(when (kind) { Kind.Movie -> "أفلام"; Kind.Tv -> "مسلسلات"; null -> "أفلام ومسلسلات" }, genre).joinToString("، ") + "، الأكثر رواجًا أوّلًا",
                    color = Muted, fontSize = 13.sp,
                )
            }
        }
        gridItems(results, key = { it.id }) { t -> PosterCard(t, onClick = { onOpen(t) }, width = null, saved = store.isSaved(t)) }
        if ((!searchMode && loading) || (searchMode && searching)) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { MashhadSpinner(size = 40.dp, glyph = false) }
        }
        if (results.isEmpty() && !loading && !searching) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlyphIcon(Glyph.Search, Muted, Modifier.size(30.dp))
                Text(if (searchMode) "لم نعثر على نتائج لـ «${query.trim()}»" else "لا توجد عناوين بهذه التصفية", color = Foreground, fontWeight = FontWeight.Bold)
                Text(if (searchMode) "جرّب الاسم الأصلي للعنوان بالإنجليزية." else "جرّب نوعًا آخر.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- details

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailScreen(route: Route.Detail, store: Store, onBack: () -> Unit, onPlay: (Int?, Int?) -> Unit, onOpen: (Title) -> Unit, onToggleSaved: (Title) -> Unit, notify: (String) -> Unit, openLink: (String) -> Unit) {
    val title = route.title
    var details by remember(title.id) { mutableStateOf<Details?>(null) }
    var availability by remember(title.id) { mutableStateOf<Availability?>(null) }
    var related by remember(title.id) { mutableStateOf<List<Title>?>(null) }
    val episodes = remember(title.id) { mutableStateMapOf<Int, List<Episode>>() }
    val last = store.lastWatched(title)
    var season by remember(title.id) { mutableIntStateOf(route.season ?: last?.season ?: 1) }

    LaunchedEffect(title.id) {
        launch { details = Tmdb.details(title) }
        launch { availability = Tmdb.availability(title) }
        launch { related = Tmdb.recommendations(title) }
    }
    LaunchedEffect(title.id, season) {
        if (title.kind == Kind.Tv && episodes[season] == null) episodes[season] = Tmdb.episodes(title.tmdbId, season)
    }
    val seasons = details?.seasons.orEmpty()
    val watchUrl = availability?.link
    fun openWhereToWatch() {
        store.recordWatch(title, last?.season, last?.episode, last?.episodeName)
        if (watchUrl != null) openLink(watchUrl) else notify("لا تتوفّر خدمة مشاهدة لهذا العنوان في منطقتك")
    }

    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp + navBar)) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 11f)) {
                RemoteImage(title.backdropUrl, Modifier.fillMaxSize(), title.name)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Night.copy(alpha = .55f), .3f to Color.Transparent, 1f to Night)))
                Box(
                    Modifier.statusBarsPadding().padding(10.dp).size(48.dp).clip(CircleShape).background(Night.copy(alpha = .55f)).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "رجوع" },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.Back, Foreground, Modifier.size(22.dp)) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                RemoteImage(title.posterUrl, Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).border(1.dp, Border, RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(kindLabel(title.kind), color = Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(title.name, color = Foreground, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                    MetaLine(
                        title.copy(genres = details?.genres?.ifEmpty { null } ?: title.genres),
                        extra = if (title.kind == Kind.Tv) seasons.size.takeIf { it > 0 }?.let { countLabel(it, "موسم واحد", "موسمان", "مواسم", "موسمًا") }
                        else details?.runtime?.let { "${ar(it / 60)} س ${ar(it % 60)} د" },
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (last?.season != null) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Mint.copy(alpha = .12f)).border(1.dp, Mint.copy(alpha = .45f), RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) { season = last.season }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlyphIcon(Glyph.Play, Mint, Modifier.size(18.dp))
                    Text("آخر ما شاهدته: م${ar(last.season)} ح${ar(last.episode ?: 1)}" + (last.episodeName?.let { ": $it" } ?: ""), color = Mint, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Resume the last episode (or the film) where it stopped; otherwise start from the beginning.
                    val resume = last?.takeIf { !it.finished }
                    val label = when {
                        resume?.season != null -> "تابِع م${ar(resume.season)} ح${ar(resume.episode ?: 1)}"
                        resume != null && resume.positionMs > 0 -> "تابِع المشاهدة"
                        else -> "شاهِد"
                    }
                    PillButton(label, Glyph.Play, primary = true, modifier = Modifier.weight(1f)) {
                        if (title.kind == Kind.Tv) onPlay(resume?.season ?: 1, resume?.episode ?: 1) else onPlay(null, null)
                    }
                    PillButton("أين تُشاهَد", Glyph.Globe, primary = false) { openWhereToWatch() }
                    val saved = store.isSaved(title)
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(if (saved) Mint.copy(alpha = .16f) else Foreground.copy(alpha = .08f))
                            .border(1.dp, if (saved) Mint else Foreground.copy(alpha = .2f), RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onToggleSaved(title) }.semantics { contentDescription = if (saved) "إزالة من قائمتي" else "أضف إلى قائمتي" },
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(if (saved) Glyph.BookmarkFilled else Glyph.Bookmark, if (saved) Mint else Foreground, Modifier.size(20.dp)) }
                }
                details?.tagline?.takeIf { it.isNotBlank() }?.let { Text("«$it»", color = Mint, fontFamily = Ruqaa, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                if (title.overview.isNotBlank()) Text(title.overview, color = Foreground.copy(alpha = .9f), fontSize = 15.sp, lineHeight = 25.sp)
                details?.cast?.takeIf { it.isNotEmpty() }?.let { Text("بطولة: ${it.joinToString("، ")}", color = Muted, fontSize = 13.sp, lineHeight = 20.sp) }
                if (details == null) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { MashhadSpinner(size = 32.dp, glyph = false) }
            }
        }
        // Where to watch, grouped the way services sell it.
        availability?.let { avail ->
            item {
                Column(Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceColor).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("أين تُشاهَد", color = Foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (avail.isEmpty) Text("لا تتوفّر خدمة مشاهدة لهذا العنوان في منطقتك حاليًا.", color = Muted, fontSize = 13.sp)
                    listOf("ضمن الاشتراك" to avail.stream, "للإيجار" to avail.rent, "للشراء" to avail.buy).filter { it.second.isNotEmpty() }.forEach { (label, providers) ->
                        Text(label, color = Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            providers.forEach { provider ->
                                Row(
                                    Modifier.clip(RoundedCornerShape(12.dp)).background(Raised).clickable(enabled = watchUrl != null, role = Role.Button) { openWhereToWatch() }.padding(end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    RemoteImage(provider.logoUrl, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), provider.name)
                                    Text(provider.name, color = Foreground, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Text("المنطقة: ${avail.country}، بيانات المشاهدة من JustWatch عبر TMDB.", color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (title.kind == Kind.Tv) {
            item {
                Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("الحلقات", subtitle = if (seasons.isNotEmpty()) countLabel(seasons.size, "موسم واحد", "موسمان", "مواسم", "موسمًا") else null)
                    if (seasons.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons, key = { it.number }) { s ->
                            val selected = s.number == season
                            Column(
                                Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) Mint.copy(alpha = .14f) else SurfaceColor)
                                    .border(1.dp, if (selected) Mint else Border, RoundedCornerShape(14.dp)).clickable(role = Role.Tab) { season = s.number }.padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text("الموسم ${ar(s.number)}", color = if (selected) Mint else Foreground, fontWeight = FontWeight.SemiBold)
                                Text(countLabel(s.episodeCount, "حلقة واحدة", "حلقتان", "حلقات", "حلقة"), color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            val list = episodes[season]
            if (list == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { MashhadSpinner(size = 34.dp, glyph = false) } }
            else items(list, key = { "${it.season}-${it.number}" }) { episode ->
                val isLast = last?.season == episode.season && last.episode == episode.number
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (isLast) Mint.copy(alpha = .1f) else Color.Transparent)
                        .clickable(role = Role.Button) { onPlay(episode.season, episode.number) }.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteImage(episode.stillUrl ?: title.backdropUrl, Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${ar(episode.number)}. ${episode.name}", color = if (isLast) Mint else Foreground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(episode.runtime?.let { "${ar(it)} دقيقة" }, if (isLast) "آخر حلقة شاهدتها" else null).joinToString("، "), color = Muted, fontSize = 12.sp)
                        if (episode.overview.isNotBlank()) Text(episode.overview, color = Muted, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                    }
                }
            }
        }
        item {
            val items = related
            if (items == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { MashhadSpinner(size = 32.dp, glyph = false) }
            else if (items.isNotEmpty()) Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("قد يعجبك أيضًا", subtitle = "ترشيحات بناءً على هذا العنوان")
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items, key = { it.id }) { t -> PosterCard(t, onClick = { onOpen(t) }, saved = store.isSaved(t)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- saved

@Composable
private fun SavedScreen(store: Store, onOpen: (Title) -> Unit, onBrowse: () -> Unit) {
    LazyVerticalGrid(
        GridCells.Adaptive(108.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = 14.dp)) {
                Text("قائمتي", color = Foreground, fontFamily = Ruqaa, fontWeight = FontWeight.Bold, fontSize = 38.sp)
                Text(
                    if (store.saved.isEmpty()) "لم تحفظ أي عنوان بعد" else countLabel(store.saved.size, "عنوان واحد محفوظ", "عنوانان محفوظان", "عناوين محفوظة", "عنوانًا محفوظًا"),
                    color = Muted, fontSize = 13.sp,
                )
            }
        }
        gridItems(store.saved, key = { it.id }) { t -> PosterCard(t, onClick = { onOpen(t) }, width = null, saved = true) }
        if (store.saved.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlyphIcon(Glyph.Bookmark, Mint, Modifier.size(34.dp))
                Text("قائمتك فارغة حتى الآن", color = Foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("احفظ ما تريد مشاهدته لاحقًا بزر «قائمتي».", color = Muted, textAlign = TextAlign.Center)
                PillButton("تصفّح العناوين", Glyph.Search, primary = true, onClick = onBrowse)
            }
        }
    }
}
