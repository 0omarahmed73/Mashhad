package app.mashhad.android

import android.app.Activity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Full-screen in-app player. The video comes from [PlaybackSource.urlFor]; the exact position is saved every
 * few seconds and when leaving, and each film or episode resumes where it stopped.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerScreen(title: Title, startSeason: Int?, startEpisode: Int?, store: Store, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val isSeries = title.kind == Kind.Tv
    var season by remember { mutableStateOf(if (isSeries) startSeason ?: 1 else null) }
    var episode by remember { mutableStateOf(if (isSeries) startEpisode ?: 1 else null) }
    var url by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var pickerOpen by remember { mutableStateOf(false) }
    var seasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    val episodes = remember { mutableStateMapOf<Int, List<Episode>>() }

    val player = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    val currentEpisode = season?.let { s -> episodes[s]?.firstOrNull { it.number == episode } }
    fun save() {
        val duration = player.duration.takeIf { it > 0 } ?: return
        store.recordWatch(title, season, episode, currentEpisode?.name, player.currentPosition, duration)
    }

    // Immersive while playing; system bars come back on exit.
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            save()
            controller.show(WindowInsetsCompat.Type.systemBars())
            player.release()
        }
    }

    if (isSeries) {
        LaunchedEffect(title.id) { seasons = Tmdb.details(title)?.seasons.orEmpty() }
        LaunchedEffect(season) { season?.let { s -> if (episodes[s] == null) episodes[s] = Tmdb.episodes(title.tmdbId, s) } }
    }

    // Resolve the stream for the current film/episode and resume at its saved position.
    LaunchedEffect(season, episode) {
        resolving = true
        url = PlaybackSource.urlFor(title, season, episode)
        resolving = false
        url?.let {
            player.setMediaItem(MediaItem.fromUri(it), store.resumePosition(title, season, episode))
            player.prepare()
        }
    }
    // Save the position every five seconds while playing.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            if (player.isPlaying) save()
        }
    }

    // The next episode: next in this season, else the first of the next season.
    val next: Pair<Int, Int>? = if (!isSeries) null else season?.let { s ->
        val list = episodes[s]
        when {
            list != null && list.any { it.number == (episode ?: 0) + 1 } -> s to (episode ?: 0) + 1
            seasons.any { it.number == s + 1 } -> s + 1 to 1
            else -> null
        }
    }
    fun play(s: Int, e: Int) { save(); season = s; episode = e; pickerOpen = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> controlsVisible = visibility == android.view.View.VISIBLE })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (url == null && !resolving) Column(
            Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlyphIcon(Glyph.Film, Muted, Modifier.size(34.dp))
            Text("هذا العنوان غير متاح للتشغيل حاليًا", color = Foreground, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("لم يُرجع مصدر التشغيل رابطًا لهذا العنوان.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        if (resolving) Box(Modifier.align(Alignment.Center)) { MashhadSpinner(size = 64.dp, glyph = false) }

        // Top bar, shown with the player's own controls.
        if (controlsVisible || url == null) Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent))).padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Night.copy(alpha = .55f)).clickable(role = Role.Button) { save(); onBack() }.semantics { contentDescription = "إغلاق المشغّل" }, contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Back, Foreground, Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title.name, color = Foreground, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val label = if (season != null) "م${ar(season!!)} ح${ar(episode ?: 1)}" + (currentEpisode?.name?.let { ": $it" } ?: "") else kindLabel(title.kind)
                Text(label + if (PlaybackSource.USING_SAMPLES) "  ·  فيلم تجريبي مفتوح الترخيص" else "", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (next != null) PlayerChip("الحلقة التالية") { play(next.first, next.second) }
            if (isSeries) PlayerChip("الحلقات") { pickerOpen = true }
        }

        if (pickerOpen && isSeries) EpisodePicker(
            title = title, seasons = seasons, episodes = episodes, store = store,
            currentSeason = season ?: 1, currentEpisode = episode ?: 1,
            onPick = { s, e -> play(s, e) }, onClose = { pickerOpen = false },
        )
    }
}

@Composable
private fun PlayerChip(label: String, onClick: () -> Unit) {
    Text(
        label, color = Foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(Night.copy(alpha = .62f))
            .border(1.dp, Foreground.copy(alpha = .22f), RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun EpisodePicker(
    title: Title, seasons: List<Season>, episodes: MutableMap<Int, List<Episode>>, store: Store,
    currentSeason: Int, currentEpisode: Int, onPick: (Int, Int) -> Unit, onClose: () -> Unit,
) {
    var shownSeason by remember { mutableIntStateOf(currentSeason) }
    LaunchedEffect(shownSeason) { if (episodes[shownSeason] == null) episodes[shownSeason] = Tmdb.episodes(title.tmdbId, shownSeason) }
    val list = episodes[shownSeason]
    val listState = rememberLazyListState()
    LaunchedEffect(list) { list?.indexOfFirst { shownSeason == currentSeason && it.number == currentEpisode }?.takeIf { it >= 0 }?.let { listState.scrollToItem(it) } }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(380.dp).background(Night.copy(alpha = .97f)).clickable(enabled = false) {}.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("اختر الحلقة", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(title.name, color = Foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "إغلاق قائمة الحلقات" }, contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.Close, Foreground, Modifier.size(18.dp))
                }
            }
            if (seasons.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasons, key = { it.number }) { s -> Chip("الموسم ${ar(s.number)}", s.number == shownSeason) { shownSeason = s.number } }
            }
            if (list == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { MashhadSpinner(size = 34.dp, glyph = false) }
            else LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { "${it.season}-${it.number}" }) { ep ->
                    val playing = ep.season == currentSeason && ep.number == currentEpisode
                    val watched = store.progressFraction(title, ep.season, ep.number)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (playing) Mint.copy(alpha = .12f) else Color.Transparent)
                            .border(1.dp, if (playing) Mint.copy(alpha = .5f) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { onPick(ep.season, ep.number) }.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
                            RemoteImage(ep.stillUrl ?: title.backdropUrl, Modifier.fillMaxSize())
                            if (watched > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(watched).heightIn(min = 3.dp).background(Mint))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${ar(ep.number)}. ${ep.name}", color = if (playing) Mint else Foreground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (playing) "تُعرض الآن" else ep.runtime?.let { "${ar(it)} دقيقة" }.orEmpty(), color = if (playing) Mint else Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
