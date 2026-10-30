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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import org.json.JSONArray
import org.json.JSONObject

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
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "اختر مشغّلًا",
        )
        try {
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }
}

internal val Night = Color(0xFF121D2B)
internal val SurfaceColor = Color(0xFF1B2A3A)
internal val Raised = Color(0xFF26384B)
internal val Border = Color(0xFF354A5D)
internal val Mint = Color(0xFF92DECC)
internal val InkOnMint = Color(0xFF0F2A25)
internal val Foreground = Color(0xFFEDF4F2)
internal val Muted = Color(0xFFA7B7C3)
internal val Rose = Color(0xFFD88F9C)

private data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val durationMinutes: Int,
)

private data class CatalogTitle(
    val id: String,
    val title: String,
    val originalTitle: String,
    val type: String,
    val year: Int,
    val rating: String,
    val genres: List<String>,
    val synopsis: String,
    val artStyle: String,
    val featured: Boolean,
    val episodes: List<Episode>,
    val tmdbId: String? = null,
    val posterPath: String? = null,
)

private data class Catalog(
    val titles: MutableList<CatalogTitle>,
    val genres: List<String>,
    val featuredIds: List<String>,
    val topTenIds: List<String>,
)

private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

private fun loadCatalog(context: Context): Catalog {
    val raw = context.assets.open("catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    val root = JSONObject(raw)
    val titles = root.getJSONArray("titles").let { list ->
        List(list.length()) { index ->
            val item = list.getJSONObject(index)
            val episodes = item.optJSONArray("episodes") ?: JSONArray()
            CatalogTitle(
                id = item.getString("id"),
                title = item.getString("title"),
                originalTitle = item.optString("originalTitle", ""),
                type = item.optString("type", "film"),
                year = item.optInt("year", 0),
                rating = item.optString("rating", "").ifBlank { "عام" },
                genres = item.optJSONArray("genres")?.strings().orEmpty(),
                synopsis = item.optString("synopsis", ""),
                artStyle = item.optString("artStyle", "city"),
                featured = item.optBoolean("featured", false),
                tmdbId = item.optString("tmdbId").ifBlank { null },
                posterPath = item.optString("posterPath").ifBlank { null },
                episodes = List(episodes.length()) { episodeIndex ->
                    val episode = episodes.getJSONObject(episodeIndex)
                    Episode(
                        id = episode.getString("id"),
                        season = episode.optInt("season", 1),
                        number = episode.optInt("number", episodeIndex + 1),
                        title = episode.optString("title", "حلقة ${episodeIndex + 1}"),
                        durationMinutes = episode.optInt("durationMinutes", 45),
                    )
                },
            )
        }
    }
    return Catalog(
        titles = titles.toMutableList(),
        genres = root.optJSONArray("genres")?.strings().orEmpty(),
        featuredIds = root.optJSONArray("featuredIds")?.strings().orEmpty(),
        topTenIds = root.optJSONArray("topTenIds")?.strings().orEmpty(),
    )
}

// Set tmdb.apiKey in android/local.properties; it is compiled into BuildConfig and never committed with the source.
private val TMDB_API_KEY: String = BuildConfig.TMDB_API_KEY

private fun fetchJson(url: String): JSONObject? = try {
    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection)
    conn.connectTimeout = 15000
    conn.readTimeout = 15000
    if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
} catch (_: Exception) { null }

// Runs on the IO dispatcher and fetches titles in parallel; onProgress reports finished requests for the splash bar.
private suspend fun fetchTmdbTitles(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<CatalogTitle> = withContext(Dispatchers.IO) {
    if (TMDB_API_KEY.isBlank()) return@withContext emptyList()
    val genreMap = mapOf(
        28 to "أكشن", 12 to "مغامرة", 16 to "رسوم", 35 to "كوميديا", 80 to "تشويق", 99 to "وثائقي",
        18 to "دراما", 10751 to "عائلي", 14 to "خيال", 36 to "تاريخي", 27 to "رعب", 10402 to "موسيقى",
        9648 to "غموض", 10749 to "رومانسي", 878 to "خيال علمي", 53 to "تشويق",
        10752 to "حرب", 37 to "غربي", 10765 to "خيال علمي", 10759 to "أكشن ومغامرة",
    )
    fun mapGenres(ids: org.json.JSONArray?): List<String> {
        val names = (0 until (ids?.length() ?: 0)).mapNotNull { genreMap[ids!!.optInt(it)] }.distinct()
        return if (names.isEmpty()) listOf("دراما") else names.take(3)
    }
    val perList = 20
    val total = 2 + perList + perList * 2
    val done = java.util.concurrent.atomic.AtomicInteger()
    fun tracked(url: String): JSONObject? = fetchJson(url).also { onProgress(done.incrementAndGet(), total) }

    val lists = listOf("movie", "tv").map { kind ->
        async { tracked("https://api.themoviedb.org/3/$kind/popular?api_key=$TMDB_API_KEY&language=ar-SA&page=1")?.optJSONArray("results") }
    }.awaitAll()
    val movies = lists[0]
    val shows = lists[1]

    val movieTitles = (0 until minOf(movies?.length() ?: 0, perList)).map { i ->
        async {
            val m = movies!!.getJSONObject(i)
            val detail = tracked("https://api.themoviedb.org/3/movie/${m.getInt("id")}?api_key=$TMDB_API_KEY&language=ar-SA")
            val merged = detail ?: m
            val year = (merged.optString("release_date", m.optString("release_date"))).take(4).toIntOrNull() ?: 0
            CatalogTitle(
                id = "tmdb-movie-${m.getInt("id")}",
                title = merged.optString("original_title").ifBlank { merged.optString("title") }.ifBlank { "Untitled" },
                originalTitle = merged.optString("original_title").ifBlank { merged.optString("title") },
                type = "film",
                year = year,
                rating = if (merged.optBoolean("adult", m.optBoolean("adult"))) "+18" else "+13",
                genres = mapGenres(merged.optJSONArray("genre_ids") ?: m.optJSONArray("genre_ids")),
                synopsis = merged.optString("overview").ifBlank { "لا يوجد وصف." },
                artStyle = "city",
                featured = i < 3,
                episodes = emptyList(),
                tmdbId = m.getInt("id").toString(),
                posterPath = (merged.optString("poster_path").ifBlank { m.optString("poster_path") }).ifBlank { null },
            )
        }
    }
    val showTitles = (0 until minOf(shows?.length() ?: 0, perList)).map { i ->
        async {
            val m = shows!!.getJSONObject(i)
            val id = m.getInt("id")
            // Details in Arabic, season 1 episode names in English (Arabic ones are usually just "الحلقة N").
            val detailJob = async { tracked("https://api.themoviedb.org/3/tv/$id?api_key=$TMDB_API_KEY&language=ar-SA") }
            val seasonJob = async { tracked("https://api.themoviedb.org/3/tv/$id/season/1?api_key=$TMDB_API_KEY&language=en-US") }
            val merged = detailJob.await() ?: m
            val seasonJson = seasonJob.await()
            val year = (merged.optString("first_air_date", m.optString("first_air_date"))).take(4).toIntOrNull() ?: 0
            val episodes = mutableListOf<Episode>()
            val eps = seasonJson?.optJSONArray("episodes")
            if (eps != null) {
                for (ei in 0 until eps.length()) {
                    val e = eps.getJSONObject(ei)
                    episodes.add(Episode(
                        id = "tmdb-tv-$id-s1-e${e.optInt("episode_number", ei + 1)}",
                        season = 1,
                        number = e.optInt("episode_number", ei + 1),
                        title = e.optString("name").ifBlank { "Episode ${ei + 1}" },
                        durationMinutes = e.optInt("runtime", merged.optInt("episode_run_time", 0).let { if (it > 0) it else 45 }),
                    ))
                }
            }
            if (episodes.isEmpty()) {
                for (ei in 0 until 8) episodes.add(Episode("tmdb-tv-$id-s1-e${ei + 1}", 1, ei + 1, "Episode ${ei + 1}", 45))
            }
            CatalogTitle(
                id = "tmdb-tv-$id",
                title = merged.optString("original_name").ifBlank { merged.optString("name") }.ifBlank { "Untitled" },
                originalTitle = merged.optString("original_name").ifBlank { merged.optString("name") },
                type = "series",
                year = year,
                rating = "+13",
                genres = mapGenres(merged.optJSONArray("genre_ids") ?: m.optJSONArray("genre_ids")),
                synopsis = merged.optString("overview").ifBlank { "لا يوجد وصف." },
                artStyle = "alley",
                featured = false,
                episodes = episodes,
                tmdbId = id.toString(),
                posterPath = (merged.optString("poster_path").ifBlank { m.optString("poster_path") }).ifBlank { null },
            )
        }
    }
    movieTitles.awaitAll() + showTitles.awaitAll()
}

private fun searchTmdbTitles(query: String): List<CatalogTitle> {
    if (TMDB_API_KEY.isBlank() || query.isBlank()) return emptyList()
    val result = mutableListOf<CatalogTitle>()
    val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
    fun mapGenres(ids: org.json.JSONArray?): List<String> {
        val genreMap = mapOf(28 to "أكشن", 12 to "مغامرة", 16 to "رسوم", 35 to "كوميديا", 80 to "تشويق", 99 to "وثائقي", 18 to "دراما", 10751 to "عائلي", 14 to "خيال", 36 to "تاريخي", 27 to "رعب", 10402 to "موسيقى", 9648 to "غموض", 10749 to "رومانسي", 878 to "خيال علمي", 53 to "تشويق", 10752 to "حرب", 37 to "غربي")
        val names = (0 until (ids?.length() ?: 0)).mapNotNull { genreMap[ids!!.optInt(it)] }.distinct()
        return if (names.isEmpty()) listOf("دراما") else names.take(3)
    }
    val movies = fetchJson("https://api.themoviedb.org/3/search/movie?api_key=$TMDB_API_KEY&language=ar-SA&query=$q&page=1")?.optJSONArray("results")
    val shows = fetchJson("https://api.themoviedb.org/3/search/tv?api_key=$TMDB_API_KEY&language=ar-SA&query=$q&page=1")?.optJSONArray("results")
    movies?.let { arr ->
        for (i in 0 until minOf(arr.length(), 20)) {
            val m = arr.getJSONObject(i)
            result.add(CatalogTitle(
                id = "tmdb-movie-${m.getInt("id")}",
                title = m.optString("original_title").ifBlank { m.optString("title") }.ifBlank { "Untitled" },
                originalTitle = m.optString("original_title").ifBlank { m.optString("title") },
                type = "film",
                year = m.optString("release_date").take(4).toIntOrNull() ?: 0,
                rating = if (m.optBoolean("adult")) "+18" else "+13",
                genres = mapGenres(m.optJSONArray("genre_ids")),
                synopsis = m.optString("overview").ifBlank { "لا يوجد وصف." },
                artStyle = "city",
                featured = false,
                episodes = emptyList(),
                tmdbId = m.getInt("id").toString(),
                posterPath = m.optString("poster_path").ifBlank { null },
            ))
        }
    }
    shows?.let { arr ->
        for (i in 0 until minOf(arr.length(), 20)) {
            val m = arr.getJSONObject(i)
            result.add(CatalogTitle(
                id = "tmdb-tv-${m.getInt("id")}",
                title = m.optString("original_name").ifBlank { m.optString("name") }.ifBlank { "Untitled" },
                originalTitle = m.optString("original_name").ifBlank { m.optString("name") },
                type = "series",
                year = m.optString("first_air_date").take(4).toIntOrNull() ?: 0,
                rating = "+13",
                genres = mapGenres(m.optJSONArray("genre_ids")),
                synopsis = m.optString("overview").ifBlank { "لا يوجد وصف." },
                artStyle = "alley",
                featured = false,
                episodes = emptyList(),
                tmdbId = m.getInt("id").toString(),
                posterPath = m.optString("poster_path").ifBlank { null },
            ))
        }
    }
    return result
}

data class WatchProvider(val id: Int, val name: String, val logoPath: String?, val link: String?)

private fun fetchWatchProviders(tmdbId: String, type: String): Pair<String, List<WatchProvider>> {
    if (TMDB_API_KEY.isBlank()) return "US" to emptyList()
    val endpoint = if (type == "film") "movie" else "tv"
    val root = fetchJson("https://api.themoviedb.org/3/$endpoint/$tmdbId/watch/providers?api_key=$TMDB_API_KEY") ?: return "US" to emptyList()
    val results = root.optJSONObject("results") ?: return "US" to emptyList()
    val country = if (results.has("US")) "US" else results.keys().asSequence().firstOrNull() ?: "US"
    val entry = results.optJSONObject(country) ?: return country to emptyList()
    val link = entry.optString("link").ifBlank { null }
    val seen = mutableSetOf<Int>()
    val providers = mutableListOf<WatchProvider>()
    for (key in listOf("flatrate", "rent", "buy")) {
        val arr = entry.optJSONArray(key) ?: org.json.JSONArray()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val id = p.getInt("provider_id")
            if (id in seen) continue
            seen.add(id)
            providers.add(WatchProvider(id, p.optString("provider_name"), p.optString("logo_path").ifBlank { null }, link))
        }
    }
    return country to providers
}

private enum class AppTab(val label: String, val glyph: Glyph) {
    Home("الرئيسية", Glyph.Home),
    Explore("اكتشف", Glyph.Search),
    Saved("قائمتي", Glyph.Bookmark),
    Downloads("التنزيلات", Glyph.Download),
    Profile("حسابي", Glyph.Person),
}

private sealed interface Destination {
    data class Root(val tab: AppTab) : Destination
    data class Detail(val titleId: String, val returnTo: AppTab) : Destination
    data class Player(val titleId: String, val returnTo: AppTab) : Destination
}

private enum class Glyph { Home, Search, Bookmark, Download, Person, Back, Play, Add, Check, Close, Info, Gear }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MashhadApp()
            }
        }
    }
}

@Composable
private fun MashhadApp() {
    val context = LocalContext.current
    val catalogResult = remember(context) { runCatching { loadCatalog(context) } }
    var liveCatalog by remember { mutableStateOf(catalogResult.getOrNull()) }
    var bootProgress by remember { mutableFloatStateOf(0f) }
    var bootLabel by remember { mutableStateOf("جارٍ قراءة المكتبة") }
    var splashPhase by remember { mutableStateOf(SplashPhase.Showing) }
    // Launch: the splash stays up until the catalog, the TMDB titles and the first posters are ready.
    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        val base = catalogResult.getOrNull()
        bootProgress = .1f
        if (base != null) {
            bootLabel = "جارٍ جلب العناوين"
            // A slow or offline network falls back to the bundled sample titles after 12 seconds.
            val tmdb = withTimeoutOrNull(12_000) {
                fetchTmdbTitles { done, total -> bootProgress = .1f + .7f * done / total }
            }.orEmpty()
            if (tmdb.isNotEmpty()) {
                liveCatalog = base.copy(titles = (tmdb + base.titles).toMutableList(), featuredIds = tmdb.filter { it.featured }.map { it.id } + base.featuredIds, topTenIds = (tmdb.map { it.id } + base.topTenIds).take(10))
            }
            val catalog = liveCatalog ?: base
            val byId = catalog.titles.associateBy(CatalogTitle::id)
            val posters = (catalog.featuredIds + catalog.topTenIds).distinct().mapNotNull { byId[it]?.posterPath }
            bootLabel = "جارٍ تجهيز الملصقات"
            bootProgress = .8f
            if (posters.isNotEmpty()) {
                var ready = 0
                withTimeoutOrNull(6_000) {
                    posters.map { path ->
                        async {
                            context.imageLoader.execute(ImageRequest.Builder(context).data("https://image.tmdb.org/t/p/w500$path").build())
                            bootProgress = .8f + .2f * ++ready / posters.size
                        }
                    }.awaitAll()
                }
            }
        }
        bootProgress = 1f
        delay((2_700 - (System.currentTimeMillis() - started)).coerceAtLeast(250))   // let the logo sequence finish
        splashPhase = SplashPhase.Leaving
        delay(760)
        splashPhase = SplashPhase.Gone
    }
    val palette = androidx.compose.material3.darkColorScheme(
        primary = Mint,
        onPrimary = InkOnMint,
        secondary = Rose,
        background = Night,
        surface = SurfaceColor,
        onBackground = Foreground,
        onSurface = Foreground,
        outline = Border,
    )
    MaterialTheme(colorScheme = palette) {
      ProvideTextStyle(TextStyle(fontFamily = PlexArabic)) {
      Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            val catalog = liveCatalog
            if (catalog == null) {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("تعذّر فتح مكتبة العيّنة", color = Foreground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("تأكّد من وجود ملف catalog.json ضمن أصول التطبيق.", color = Muted, textAlign = TextAlign.Center)
                }
                return@Surface
            }

            var destination by remember { mutableStateOf<Destination>(Destination.Root(AppTab.Home)) }
            var watchProviders by remember { mutableStateOf<Pair<String, List<WatchProvider>>?>(null) }
            val savedIds = remember { mutableStateListOf("ramal", "shubbak") }
            val downloadIds = remember { mutableStateListOf("madar") }
            var confirmDeleteId by remember { mutableStateOf<String?>(null) }
            val titleById = remember(catalog) { catalog.titles.associateBy(CatalogTitle::id) }
            val currentTab = (destination as? Destination.Root)?.tab

            fun back() {
                destination = when (val screen = destination) {
                    is Destination.Root -> screen
                    is Destination.Detail -> Destination.Root(screen.returnTo)
                    is Destination.Player -> Destination.Detail(screen.titleId, screen.returnTo)
                }
            }

            var routeLoading by remember { mutableStateOf(false) }
            LaunchedEffect(destination) {
                val detail = destination as? Destination.Detail
                val t = detail?.let { titleById[it.titleId] }
                if (t?.tmdbId != null) {
                    routeLoading = true
                    watchProviders = withContext(Dispatchers.IO) { fetchWatchProviders(t.tmdbId!!, t.type) }
                } else watchProviders = null
                routeLoading = false
            }
            BackHandler(enabled = destination !is Destination.Root) { back() }

            Scaffold(
                containerColor = Night,
                contentWindowInsets = WindowInsets.navigationBars,
                topBar = {
                    when (val screen = destination) {
                        is Destination.Root -> MashhadTopBar(screen.tab, onProfile = { destination = Destination.Root(AppTab.Profile) })
                        is Destination.Detail -> DetailTopBar(onBack = ::back)
                        is Destination.Player -> PlayerTopBar(onBack = ::back)
                    }
                },
                bottomBar = {
                    if (destination is Destination.Root) {
                        BottomNavigationBar(
                            selected = currentTab ?: AppTab.Home,
                            onSelect = { destination = Destination.Root(it) },
                        )
                    }
                },
            ) { scaffoldPadding ->
                Box(Modifier.fillMaxSize().padding(scaffoldPadding)) {
                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen-transition",
                    ) { screen ->
                        when (screen) {
                            is Destination.Root -> when (screen.tab) {
                                AppTab.Home -> HomeScreen(
                                    catalog = catalog,
                                    savedIds = savedIds,
                                    onOpenTitle = { destination = Destination.Detail(it.id, AppTab.Home) },
                                    onPlay = { destination = Destination.Player(it.id, AppTab.Home) },
                                    onBrowse = { destination = Destination.Root(AppTab.Explore) },
                                )
                                AppTab.Explore -> ExploreScreen(
                                    catalog = catalog,
                                    savedIds = savedIds,
                                    onOpenTitle = { destination = Destination.Detail(it.id, AppTab.Explore) },
                                )
                                AppTab.Saved -> SavedScreen(
                                    titles = savedIds.mapNotNull(titleById::get),
                                    onOpenTitle = { destination = Destination.Detail(it.id, AppTab.Saved) },
                                    onRemove = { savedIds.remove(it) },
                                )
                                AppTab.Downloads -> DownloadsScreen(
                                    titles = downloadIds.mapNotNull(titleById::get),
                                    onOpenTitle = { destination = Destination.Detail(it.id, AppTab.Downloads) },
                                    onDelete = { confirmDeleteId = it },
                                )
                                AppTab.Profile -> ProfileScreen()
                            }
                            is Destination.Detail -> titleById[screen.titleId]?.let { title ->
                                DetailScreen(
                                    title = title,
                                    isSaved = title.id in savedIds,
                                    isDownloaded = title.id in downloadIds,
                                    watchProviders = if (title.tmdbId != null) watchProviders else null,
                                    onToggleSaved = {
                                        if (title.id in savedIds) savedIds.remove(title.id) else savedIds.add(title.id)
                                    },
                                    onToggleDownload = {
                                        if (title.id in downloadIds) downloadIds.remove(title.id) else downloadIds.add(title.id)
                                    },
                                    onPlay = { destination = Destination.Player(title.id, screen.returnTo) },
                                    onPlayInOm = { uri -> openInOmPlayer(context, uri) },
                                )
                            }
                            is Destination.Player -> titleById[screen.titleId]?.let { title ->
                                DemoPlayer(title = title, onBack = ::back)
                            }
                        }
                    }
                    RouteLoader(active = routeLoading && destination is Destination.Detail)
                }
            }

            confirmDeleteId?.let { id ->
                val title = titleById[id]
                Dialog(onDismissRequest = { confirmDeleteId = null }) {
                    Card(colors = CardDefaults.cardColors(containerColor = Raised), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("إزالة من التنزيلات؟", color = Foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(title?.title.orEmpty(), color = Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { confirmDeleteId = null }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                    Text("إلغاء")
                                }
                                Button(
                                    onClick = { downloadIds.remove(id); confirmDeleteId = null },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Color(0xFF2A1218)),
                                ) { Text("إزالة") }
                            }
                        }
                    }
                }
            }
        }
        if (splashPhase != SplashPhase.Gone) MashhadSplash(bootProgress, bootLabel, leaving = splashPhase == SplashPhase.Leaving)
      }
      }
    }
}

private enum class SplashPhase { Showing, Leaving, Gone }

@Composable
private fun MashhadTopBar(tab: AppTab, onProfile: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Night).statusBarsPadding().padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (tab == AppTab.Home) {
                Wordmark(size = 30.sp)
            } else {
                Text(tab.label, color = Foreground, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == AppTab.Home) Text(TAGLINE, color = Muted, fontSize = 12.sp) else if (tab != AppTab.Profile) Wordmark(size = 20.sp)
            if (tab != AppTab.Profile) {
                // 48dp touch target around a 36dp avatar.
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onProfile).semantics { contentDescription = "الحساب والإعدادات" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Mint), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.Person, tint = InkOnMint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Night).statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphAction("رجوع", Glyph.Back, onBack)
        Spacer(Modifier.width(10.dp))
        Text("التفاصيل", color = Foreground, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}

@Composable
private fun PlayerTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF080D14)).statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphAction("إغلاق المعاينة", Glyph.Close, onBack, tint = Foreground, background = Color.Transparent)
        Spacer(Modifier.width(8.dp))
        Text("مشغّل المعاينة", color = Foreground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BottomNavigationBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(SurfaceColor)
            .navigationBarsPadding()
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.filter { it != AppTab.Profile }.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier.weight(1f)
                    .heightIn(min = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Tab, onClick = { onSelect(tab) })
                    .semantics { contentDescription = tab.label }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (active) Mint.copy(alpha = .14f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                ) {
                    GlyphIcon(tab.glyph, tint = if (active) Mint else Muted, modifier = Modifier.size(22.dp))
                }
                Text(tab.label, color = if (active) Foreground else Muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    catalog: Catalog,
    savedIds: List<String>,
    onOpenTitle: (CatalogTitle) -> Unit,
    onPlay: (CatalogTitle) -> Unit,
    onBrowse: () -> Unit,
) {
    val context = LocalContext.current
    val omPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { if (!openInOmPlayer(context, it)) { /* no player installed */ } }
    }
    val titleById = remember(catalog) { catalog.titles.associateBy(CatalogTitle::id) }
    val featured = catalog.featuredIds.mapNotNull(titleById::get).firstOrNull() ?: catalog.titles.first()
    val topTen = catalog.topTenIds.mapNotNull(titleById::get)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlyphIcon(Glyph.Play, tint = Mint, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("فتح في OM Player", color = Foreground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("اختر فيديو من جهازك وشغّله بدون إعلانات", color = Muted, fontSize = 12.sp)
                    }
                    Button(onClick = { omPicker.launch("video/*") }, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = InkOnMint)) {
                        Text("اختيار", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            HeroCard(
                title = featured,
                isSaved = featured.id in savedIds,
                onDetails = { onOpenTitle(featured) },
                onPlay = { onPlay(featured) },
            )
        }
        item {
            SectionHeading("أكمل المشاهدة", "متابعة من حيث توقفت")
            Spacer(Modifier.height(12.dp))
            ContinueWatchingCard(title = catalog.titles.getOrElse(1) { featured }, onClick = { onPlay(catalog.titles.getOrElse(1) { featured }) })
        }
        item {
            SectionHeading("الأكثر مشاهدة", "هذا الأسبوع")
            Spacer(Modifier.height(12.dp))
            PosterRail(titles = topTen, savedIds = savedIds.toSet(), onOpen = onOpenTitle)
        }
        item {
            SectionHeading("اختيارات مَشهد", "حكايات تستحق وقتك")
            Spacer(Modifier.height(12.dp))
            PosterRail(titles = catalog.titles.take(5).reversed(), savedIds = savedIds.toSet(), onOpen = onOpenTitle)
        }
        item {
            SectionHeading("اكتشف حسب النوع", "")
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(catalog.genres.take(7)) { genre ->
                    FilterChip(selected = false, onClick = onBrowse, label = { Text(genre) })
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HeroCard(title: CatalogTitle, isSaved: Boolean, onDetails: () -> Unit, onPlay: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(418.dp).clip(RoundedCornerShape(24.dp)).background(Raised),
    ) {
        Artwork(style = title.artStyle, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD080E16)))))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("اختيار المحرّر", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("•", color = Muted)
                Text(if (title.type == "series") "مسلسل" else "فيلم", color = Muted, fontSize = 12.sp)
            }
            Text(
                title.title,
                color = Foreground,
                fontFamily = Ruqaa,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(title.synopsis, color = Foreground.copy(alpha = .86f), fontSize = 14.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = InkOnMint),
                ) {
                    GlyphIcon(Glyph.Play, tint = InkOnMint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ابدأ المشاهدة", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("التفاصيل")
                }
            }
            if (isSaved) Text("أُضيف إلى قائمتك", color = Mint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Foreground, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PosterRail(titles: List<CatalogTitle>, savedIds: Set<String>, onOpen: (CatalogTitle) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 2.dp)) {
        items(titles, key = CatalogTitle::id) { title ->
            PosterCard(title = title, isSaved = title.id in savedIds, onClick = { onOpen(title) })
        }
    }
}

@Composable
private fun PosterCard(title: CatalogTitle, isSaved: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(126.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "تفاصيل ${title.title}" },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Raised)) {
            CoverArt(title = title, modifier = Modifier.fillMaxSize())
            Text(title.rating, color = InkOnMint, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp).clip(RoundedCornerShape(6.dp)).background(Mint).padding(horizontal = 6.dp, vertical = 4.dp))
            if (isSaved) GlyphIcon(Glyph.Bookmark, tint = Mint, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(18.dp))
        }
        Text(title.title, color = Foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${title.year}  •  ${if (title.type == "series") "مسلسل" else "فيلم"}", color = Muted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ContinueWatchingCard(title: CatalogTitle, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(104.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp))) {
                Artwork(title.artStyle, Modifier.fillMaxSize())
                GlyphIcon(Glyph.Play, tint = Foreground, modifier = Modifier.align(Alignment.Center).size(32.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title.title, color = Foreground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (title.type == "series") "الحلقة ٣، بقيت ٢٤ دقيقة" else "بقيت ٢٤ دقيقة", color = Muted, fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Border)) {
                    Box(Modifier.fillMaxWidth(.42f).height(3.dp).background(Mint))
                }
            }
        }
    }
}

@Composable
private fun ExploreScreen(catalog: Catalog, savedIds: List<String>, onOpenTitle: (CatalogTitle) -> Unit) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("الكل") }
    var genreFilter by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<CatalogTitle>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.isBlank()) { searchResults = null; searching = false; return@LaunchedEffect }
        searching = true
        kotlinx.coroutines.delay(400)
        val r = withContext(Dispatchers.IO) { searchTmdbTitles(query) }
        searchResults = r
        searching = false
        r.forEach { t -> if (catalog.titles.none { it.id == t.id }) catalog.titles.add(t) }
    }
    val baseList = searchResults ?: catalog.titles
    val filtered = baseList.filter { title ->
        val matchesQuery = searchResults != null || query.isBlank() || title.title.contains(query.trim(), ignoreCase = true) || title.originalTitle.contains(query.trim(), ignoreCase = true)
        val matchesType = typeFilter == "الكل" || (typeFilter == "أفلام" && title.type == "film") || (typeFilter == "مسلسلات" && title.type == "series")
        val matchesGenre = genreFilter == null || genreFilter in title.genres
        matchesQuery && matchesType && matchesGenre
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ابحث عن فيلم أو مسلسل") },
                trailingIcon = if (searching) { { MashhadSpinner(size = 22.dp, glyph = false) } } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceColor,
                    unfocusedContainerColor = SurfaceColor,
                    focusedTextColor = Foreground,
                    unfocusedTextColor = Foreground,
                    focusedLabelColor = Mint,
                    unfocusedLabelColor = Muted,
                    focusedIndicatorColor = Mint,
                    unfocusedIndicatorColor = Border,
                    cursorColor = Mint,
                ),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("الكل", "أفلام", "مسلسلات").forEach { label ->
                    FilterChip(selected = typeFilter == label, onClick = { typeFilter = label }, label = { Text(label) })
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = genreFilter == null, onClick = { genreFilter = null }, label = { Text("كل الأنواع") }) }
                items(catalog.genres) { genre ->
                    FilterChip(selected = genreFilter == genre, onClick = { genreFilter = if (genreFilter == genre) null else genre }, label = { Text(genre) })
                }
            }
        }
        item { Text("${filtered.size} نتائج", color = Muted, fontSize = 12.sp) }
        if (filtered.isEmpty()) {
            item { EmptyState("لم نعثر على نتائج", "جرّب كلمة أخرى أو غيّر عوامل التصفية.", Glyph.Search) }
        } else {
            items(filtered.chunked(2)) { rowTitles ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowTitles.forEach { title ->
                        Box(Modifier.weight(1f)) { PosterCard(title, title.id in savedIds, onClick = { onOpenTitle(title) }) }
                    }
                    if (rowTitles.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SavedScreen(titles: List<CatalogTitle>, onOpenTitle: (CatalogTitle) -> Unit, onRemove: (String) -> Unit) {
    if (titles.isEmpty()) {
        EmptyState("قائمتك فارغة حتى الآن", "احفظ العناوين التي تريد العودة إليها لاحقًا.", Glyph.Bookmark)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("عناوين حفظتها لتعود إليها في أي وقت.", color = Muted, fontSize = 14.sp) }
        items(titles, key = CatalogTitle::id) { title ->
            LibraryRow(title = title, actionLabel = "إزالة من قائمتي", actionGlyph = Glyph.Close, onOpen = { onOpenTitle(title) }, onAction = { onRemove(title.id) })
        }
    }
}

@Composable
private fun DownloadsScreen(titles: List<CatalogTitle>, onOpenTitle: (CatalogTitle) -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = Raised, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlyphIcon(Glyph.Info, tint = Mint, modifier = Modifier.size(20.dp))
                Text("حالات تنزيل تجريبية فقط؛ لا يُحفظ أي فيديو على جهازك.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        if (titles.isEmpty()) {
            EmptyState("لا توجد تنزيلات", "يمكنك إضافة عنوان من صفحة التفاصيل لمعاينة حالة التنزيل.", Glyph.Download)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(titles, key = CatalogTitle::id) { title ->
                    LibraryRow(title, "إزالة", Glyph.Close, { onOpenTitle(title) }, { onDelete(title.id) }, subline = "جاهز للمعاينة، غير محفوظ فعليًا")
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    title: CatalogTitle,
    actionLabel: String,
    actionGlyph: Glyph,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    subline: String = "${title.year}، ${title.genres.take(2).joinToString("، ")}",
) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(86.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)) {
                CoverArt(title = title, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title.title, color = Foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subline, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            GlyphAction(actionLabel, actionGlyph, onAction, tint = Muted, background = Color.Transparent)
        }
    }
}

@Composable
private fun ProfileScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeading("الإعدادات", "تفضيلات محلية") }
        item { PreferenceRow("اللغة", "العربية", Glyph.Info) }
        item { PreferenceRow("جودة العرض", "تلقائية", Glyph.Gear) }
        item { PreferenceRow("الإشعارات", "محلية فقط", Glyph.Info) }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(TAGLINE, color = Foreground, fontFamily = Ruqaa, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("مَشهد، كتالوج واكتشاف. التشغيل في OM Player.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PreferenceRow(label: String, value: String, glyph: Glyph) {
    Surface(color = SurfaceColor, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlyphIcon(glyph, tint = Mint, modifier = Modifier.size(20.dp))
            Text(label, color = Foreground, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(value, color = Muted, fontSize = 12.sp, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun DetailScreen(
    title: CatalogTitle,
    isSaved: Boolean,
    isDownloaded: Boolean,
    watchProviders: Pair<String, List<WatchProvider>>?,
    onToggleSaved: () -> Unit,
    onToggleDownload: () -> Unit,
    onPlay: () -> Unit,
    onPlayInOm: (Uri) -> Boolean,
) {
    var selectedSeason by remember(title.id) { mutableStateOf(title.episodes.firstOrNull()?.season ?: 1) }
    val seasons = title.episodes.map(Episode::season).distinct().sorted()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(246.dp).background(Raised)) {
                Artwork(title.artStyle, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Night.copy(alpha = .98f)))))
                Text(title.title, color = Foreground, fontFamily = Ruqaa, fontSize = 27.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${title.year}", color = Muted, fontSize = 13.sp)
                    Text("•", color = Border)
                    Text(if (title.type == "series") "مسلسل، " + when (val n = seasons.size.coerceAtLeast(1)) { 1 -> "موسم واحد"; 2 -> "موسمان"; in 3..10 -> "$n مواسم"; else -> "$n موسمًا" } else "فيلم، ${title.year}", color = Muted, fontSize = 13.sp)
                    Text("•", color = Border)
                    Text(title.rating, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(title.genres) { genre ->
                        Surface(color = Raised, shape = CircleShape) { Text(genre, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) }
                    }
                }
                Text(title.synopsis, color = Foreground.copy(alpha = .9f), fontSize = 15.sp, lineHeight = 24.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = InkOnMint),
                    ) {
                        GlyphIcon(Glyph.Play, tint = InkOnMint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("فتح في OM Player", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(onClick = onToggleSaved, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        GlyphIcon(if (isSaved) Glyph.Check else Glyph.Bookmark, tint = if (isSaved) Mint else Foreground, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isSaved) "في قائمتي" else "أضف إلى قائمتي")
                    }
                }
                OutlinedButton(onClick = onToggleDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    GlyphIcon(if (isDownloaded) Glyph.Check else Glyph.Download, tint = if (isDownloaded) Mint else Foreground, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (isDownloaded) "حالة تنزيل تجريبية مضافة" else "أضف حالة تنزيل تجريبية")
                }
                val context = LocalContext.current
                val omPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    uri?.let { onPlayInOm(it) }
                }
                OutlinedButton(onClick = { omPicker.launch("video/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    GlyphIcon(Glyph.Play, tint = Mint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("فتح ملف في OM Player")
                }
            }
        }
        if (title.episodes.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading("الحلقات", "${title.episodes.size} حلقة تجريبية")
                    if (seasons.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons) { season ->
                                FilterChip(selected = selectedSeason == season, onClick = { selectedSeason = season }, label = { Text("الموسم $season") })
                            }
                        }
                    }
                    title.episodes.filter { it.season == selectedSeason }.forEach { episode ->
                        EpisodeRow(episode = episode, onClick = onPlay)
                    }
                }
            }
        }
        if (watchProviders != null && watchProviders!!.second.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeading("أين تُشاهَد", "خدمات بثّ، ${watchProviders!!.first}")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(watchProviders!!.second) { p ->
                            Surface(color = Raised, shape = RoundedCornerShape(20.dp)) {
                                Text(p.name, color = Foreground, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
            }
        }
        item { Text("مَشهد كتالوج واكتشاف. التشغيل في OM Player أو خدمة بثّ.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Raised), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Play, tint = Mint, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${episode.number}. ${episode.title}", color = Foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${episode.durationMinutes} دقيقة، معاينة فقط", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DemoPlayer(title: CatalogTitle, onBack: () -> Unit) {
    var playing by remember(title.id) { mutableStateOf(false) }
    var progress by remember(title.id) { mutableFloatStateOf(.18f) }
    var buffering by remember(title.id) { mutableStateOf(true) }
    LaunchedEffect(title.id) { delay(1100); buffering = false }
    LaunchedEffect(playing) {
        while (playing && progress < 1f) {
            delay(1000)
            progress = (progress + .002f).coerceAtMost(1f)
        }
        if (progress >= 1f) playing = false
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF080D14))) {
        Artwork(title.artStyle, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0xCC080D14)))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${title.title}، نموذج مشغّل", color = Foreground, fontSize = 14.sp, textAlign = TextAlign.Center)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (buffering) MashhadSpinner(size = 76.dp, glyph = false) else Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Mint).clickable(role = Role.Button) { playing = !playing }
                        .semantics { contentDescription = if (playing) "إيقاف المعاينة" else "تشغيل المعاينة" },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(if (playing) Glyph.Close else Glyph.Play, tint = InkOnMint, modifier = Modifier.size(31.dp))
                }
                Text(if (playing) "المعاينة تعمل" else "اضغط لتشغيل الحركة التجريبية", color = Foreground, fontWeight = FontWeight.SemiBold)
                Text("لا يوجد فيديو أو صوت متصل بهذا المشغّل.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(value = progress, onValueChange = { progress = it }, colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint, inactiveTrackColor = Foreground.copy(alpha = .28f)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${(progress * 42).toInt()}:10", color = Foreground, fontSize = 12.sp)
                        Text("42:10", color = Muted, fontSize = 12.sp)
                    }
                }
                TextButtonCompat(label = "العودة للتفاصيل", onClick = onBack)
            }
        }
    }
}

@Composable
private fun TextButtonCompat(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

@Composable
private fun EmptyState(title: String, body: String, glyph: Glyph) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Raised), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, tint = Mint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Foreground, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        Text(body, color = Muted, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GlyphAction(
    label: String,
    glyph: Glyph,
    onClick: () -> Unit,
    tint: Color = Foreground,
    background: Color = Raised,
) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = (size.minDimension * .085f).coerceAtLeast(1.6f), cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), stroke.width, cap = StrokeCap.Round)
        when (glyph) {
            Glyph.Home -> {
                val path = Path().apply {
                    moveTo(.1f * w, .48f * h); lineTo(.5f * w, .12f * h); lineTo(.9f * w, .48f * h)
                    moveTo(.22f * w, .43f * h); lineTo(.22f * w, .88f * h); lineTo(.78f * w, .88f * h); lineTo(.78f * w, .43f * h)
                    moveTo(.43f * w, .88f * h); lineTo(.43f * w, .63f * h); lineTo(.57f * w, .63f * h); lineTo(.57f * w, .88f * h)
                }
                drawPath(path, tint, style = stroke)
            }
            Glyph.Search -> {
                drawCircle(tint, radius = .29f * w, center = Offset(.43f * w, .4f * h), style = stroke)
                line(.63f, .61f, .9f, .88f)
            }
            Glyph.Bookmark -> {
                val path = Path().apply {
                    moveTo(.25f * w, .12f * h); lineTo(.75f * w, .12f * h); lineTo(.75f * w, .9f * h)
                    lineTo(.5f * w, .72f * h); lineTo(.25f * w, .9f * h); close()
                }
                drawPath(path, tint, style = stroke)
            }
            Glyph.Download -> {
                line(.5f, .12f, .5f, .62f); line(.29f, .43f, .5f, .64f); line(.71f, .43f, .5f, .64f)
                line(.2f, .75f, .2f, .88f); line(.2f, .88f, .8f, .88f); line(.8f, .88f, .8f, .75f)
            }
            Glyph.Person -> {
                drawCircle(tint, radius = .18f * w, center = Offset(.5f * w, .32f * h), style = stroke)
                drawArc(tint, startAngle = 190f, sweepAngle = 160f, useCenter = false, topLeft = Offset(.16f * w, .48f * h), size = Size(.68f * w, .45f * h), style = stroke)
            }
            Glyph.Back -> { line(.18f, .5f, .82f, .5f); line(.18f, .5f, .48f, .2f); line(.18f, .5f, .48f, .8f) }
            Glyph.Play -> {
                val path = Path().apply { moveTo(.34f * w, .2f * h); lineTo(.78f * w, .5f * h); lineTo(.34f * w, .8f * h); close() }
                drawPath(path, tint)
            }
            Glyph.Add -> { line(.5f, .2f, .5f, .8f); line(.2f, .5f, .8f, .5f) }
            Glyph.Check -> { line(.17f, .52f, .4f, .75f); line(.4f, .75f, .84f, .26f) }
            Glyph.Close -> { line(.22f, .22f, .78f, .78f); line(.78f, .22f, .22f, .78f) }
            Glyph.Info -> {
                drawCircle(tint, radius = .38f * w, center = Offset(.5f * w, .5f * h), style = stroke)
                drawCircle(tint, radius = .035f * w, center = Offset(.5f * w, .31f * h))
                line(.5f, .45f, .5f, .7f)
            }
            Glyph.Gear -> {
                drawCircle(tint, radius = .28f * w, center = Offset(.5f * w, .5f * h), style = stroke)
                drawCircle(tint, radius = .1f * w, center = Offset(.5f * w, .5f * h), style = stroke)
                listOf(0f, 45f, 90f, 135f).forEach { angle ->
                    val rad = Math.toRadians(angle.toDouble())
                    val dx = kotlin.math.cos(rad).toFloat(); val dy = kotlin.math.sin(rad).toFloat()
                    line(.5f + dx * .34f, .5f + dy * .34f, .5f + dx * .45f, .5f + dy * .45f)
                    line(.5f - dx * .34f, .5f - dy * .34f, .5f - dx * .45f, .5f - dy * .45f)
                }
            }
        }
    }
}

private data class ArtPalette(val sky: Color, val horizon: Color, val ground: Color, val light: Color)

private fun artPalette(style: String): ArtPalette = when (style) {
    "desert" -> ArtPalette(Color(0xFF26172B), Color(0xFF9A5550), Color(0xFF38252C), Color(0xFFFFD39B))
    "sea" -> ArtPalette(Color(0xFF152B44), Color(0xFF557E8A), Color(0xFF132A3E), Color(0xFFEFCB9B))
    "space" -> ArtPalette(Color(0xFF0D1730), Color(0xFF4A3B70), Color(0xFF14182B), Color(0xFFB5D9EE))
    "train" -> ArtPalette(Color(0xFF2D2334), Color(0xFFAC694E), Color(0xFF251B22), Color(0xFFFFD292))
    "cafe" -> ArtPalette(Color(0xFF492D2B), Color(0xFFB7774B), Color(0xFF38211F), Color(0xFFFFD09B))
    "hospital" -> ArtPalette(Color(0xFF193344), Color(0xFF668C8A), Color(0xFF14232C), Color(0xFFAFDFD0))
    "alley" -> ArtPalette(Color(0xFF33233A), Color(0xFF9D5A58), Color(0xFF211B2B), Color(0xFFF1BC87))
    else -> ArtPalette(Color(0xFF1A2940), Color(0xFF667990), Color(0xFF182536), Color(0xFFD8C9A8))
}

@Composable
private fun CoverArt(title: CatalogTitle, modifier: Modifier = Modifier) {
    if (title.posterPath != null) {
        SubcomposeAsyncImage(
            model = "https://image.tmdb.org/t/p/w500${title.posterPath}",
            contentDescription = title.title,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            loading = { Box(Modifier.fillMaxSize().background(SurfaceColor), contentAlignment = Alignment.Center) { MashhadSpinner(size = 30.dp, glyph = false) } },
        )
    } else {
        Artwork(title.artStyle, modifier, title.title)
    }
}

@Composable
private fun Artwork(style: String, modifier: Modifier = Modifier, title: String? = null) {
    val palette = remember(style) { artPalette(style) }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(brush = Brush.verticalGradient(listOf(palette.sky, palette.horizon, palette.ground)))
            drawCircle(palette.light.copy(alpha = .8f), radius = minOf(w, h) * .105f, center = Offset(w * .72f, h * .27f))
            val back = Path().apply {
                moveTo(0f, h * .7f)
                quadraticTo(w * .18f, h * .47f, w * .38f, h * .67f)
                quadraticTo(w * .62f, h * .42f, w, h * .68f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(back, palette.horizon.copy(alpha = .82f))
            val front = Path().apply {
                moveTo(0f, h * .82f)
                quadraticTo(w * .27f, h * .63f, w * .52f, h * .8f)
                quadraticTo(w * .76f, h * .64f, w, h * .84f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(front, palette.ground)
            if (style == "city" || style == "alley" || style == "cafe" || style == "hospital") {
                val block = palette.ground.copy(alpha = .78f)
                drawRect(block, topLeft = Offset(w * .07f, h * .45f), size = Size(w * .16f, h * .39f))
                drawRect(block.copy(alpha = .9f), topLeft = Offset(w * .77f, h * .37f), size = Size(w * .2f, h * .48f))
                repeat(3) { i ->
                    val y = h * (.52f + i * .075f)
                    drawRect(palette.light.copy(alpha = .52f), topLeft = Offset(w * .105f, y), size = Size(w * .035f, h * .025f))
                    drawRect(palette.light.copy(alpha = .38f), topLeft = Offset(w * .825f, y), size = Size(w * .04f, h * .025f))
                }
            }
            if (style == "train") {
                drawRect(palette.ground.copy(alpha = .78f), topLeft = Offset(w * .28f, h * .68f), size = Size(w * .46f, h * .14f))
                drawRect(palette.light.copy(alpha = .48f), topLeft = Offset(w * .38f, h * .71f), size = Size(w * .08f, h * .04f))
                drawRect(palette.light.copy(alpha = .4f), topLeft = Offset(w * .55f, h * .71f), size = Size(w * .08f, h * .04f))
            }
            drawRect(brush = Brush.verticalGradient(0f to Color.Transparent, .62f to Color.Transparent, 1f to Color(0xDD080E16)))
        }
        if (!title.isNullOrBlank()) {
            Text(title, color = Foreground, fontFamily = Ruqaa, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
