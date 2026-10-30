package app.mashhad.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

internal val Ruqaa = FontFamily(
    Font(R.font.aref_ruqaa_regular, FontWeight.Normal),
    Font(R.font.aref_ruqaa_bold, FontWeight.Bold),
)
internal val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

internal const val TAGLINE = "عينك على المَشهد"

private fun arabicDigits(value: Int): String = value.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")

/**
 * The مَشهد wordmark. The fatha is drawn as a mint stroke over the meem (the brand's projector beam),
 * because Android can't colour a single diacritic inside shaped Arabic text.
 * [beam] scales that stroke in from the right during the splash.
 */
@Composable
internal fun Wordmark(size: TextUnit, modifier: Modifier = Modifier, color: Color = Foreground, beam: Float = 1f) {
    Box(modifier.drawWithContent {
        drawContent()
        if (beam <= 0f) return@drawWithContent
        val em = size.toPx()
        val right = this.size.width - em * .1f
        val top = this.size.height * .5f - em * .62f
        rotate(-24f, pivot = Offset(right, top)) {
            drawRoundRect(
                color = Mint,
                topLeft = Offset(right - em * .2f * beam, top - em * .028f),
                size = Size(em * .2f * beam, em * .056f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(em * .03f),
            )
        }
    }) {
        Text("مشهد", color = color, fontFamily = Ruqaa, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = size * 1.3f)
    }
}

/** Branded loader: a mint arc circling counter-clockwise (the RTL reading direction) around a pulsing meem. */
@Composable
internal fun MashhadSpinner(modifier: Modifier = Modifier, size: Dp = 56.dp, glyph: Boolean = true, onMint: Boolean = false) {
    val spin = rememberInfiniteTransition(label = "spinner")
    val rotation by spin.animateFloat(0f, -360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "rotation")
    val sweep by spin.animateFloat(14f, 150f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sweep")
    val pulse by spin.animateFloat(1f, .3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    val arcColor = if (onMint) InkOnMint else Mint
    Box(modifier.size(size).semantics { contentDescription = "جارٍ التحميل" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * .055f
            val inset = stroke / 2 + this.size.minDimension * .06f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(if (onMint) InkOnMint.copy(alpha = .25f) else Border, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            rotate(rotation) {
                drawArc(arcColor, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        if (glyph) {
            val em = with(LocalDensity.current) { (size * .4f).toSp() }
            Box(Modifier.graphicsLayer { translationY = -size.toPx() * .05f }.drawWithContent {
                drawContent()
                val e = em.toPx()
                val right = this.size.width - e * .05f
                val top = this.size.height * .5f - e * .55f
                rotate(-24f, pivot = Offset(right, top)) {
                    drawRect(Mint.copy(alpha = pulse), Offset(right - e * .34f, top - e * .04f), Size(e * .34f, e * .085f))
                }
            }) {
                Text("م", color = Foreground, fontFamily = Ruqaa, fontWeight = FontWeight.Bold, fontSize = em, lineHeight = em * 1.3f)
            }
        }
    }
}

/**
 * Shows [active] only after it has lasted [delayMs], then keeps it up for at least [minMs],
 * so quick loads never flash a spinner.
 */
@Composable
internal fun rememberDelayedFlag(active: Boolean, delayMs: Long = 160, minMs: Long = 420): Boolean {
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableStateOf(0L) }
    LaunchedEffect(active) {
        if (active) {
            delay(delayMs)
            visible = true
            shownAt = System.currentTimeMillis()
        } else if (visible) {
            delay((minMs - (System.currentTimeMillis() - shownAt)).coerceAtLeast(0))
            visible = false
        }
    }
    return visible
}

@Composable
internal fun RouteLoader(active: Boolean) {
    val visible = rememberDelayedFlag(active)
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "route-loader")
    if (alpha == 0f) return
    Box(
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }.background(Night.copy(alpha = .72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MashhadSpinner(size = 60.dp)
            Text("لحظة، نجهّز المشهد…", color = Muted, fontFamily = PlexArabic, fontSize = 14.sp)
        }
    }
}

private fun noiseBitmap(): ImageBitmap {
    val side = 128
    val pixels = IntArray(side * side) { val v = Random.nextInt(256); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
    return android.graphics.Bitmap.createBitmap(pixels, side, side, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val EaseInOutStrong = CubicBezierEasing(.7f, 0f, .3f, 1f)
private val RevealEasing = CubicBezierEasing(.6f, 0f, .2f, 1f)

/**
 * Launch splash. The sequence: viewfinder corners close in, the wordmark is revealed right to left,
 * the mint fatha slides in and casts a flickering projector beam, then the tagline rises.
 * [progress] is real loading progress; [leaving] plays the exit (corners open outward, everything fades).
 */
@Composable
internal fun MashhadSplash(progress: Float, label: String, leaving: Boolean) {
    val crop = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val beam = remember { Animatable(0f) }
    val cone = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val foot = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { delay(100); crop.animateTo(1f, tween(800, easing = CubicBezierEasing(.2f, .8f, .2f, 1f))) }
        launch { delay(300); foot.animateTo(1f, tween(500)) }
        launch { delay(450); reveal.animateTo(1f, tween(1000, easing = RevealEasing)) }
        launch { delay(1300); beam.animateTo(1f, tween(450, easing = CubicBezierEasing(.2f, .9f, .3f, 1.3f))) }
        launch { delay(1450); cone.animateTo(1f, tween(900)) }
        launch { delay(1700); tagline.animateTo(1f, tween(700)) }
    }
    LaunchedEffect(leaving) { if (leaving) exit.animateTo(1f, tween(750, easing = EaseInOutStrong)) }

    val ambient = rememberInfiniteTransition(label = "splash-ambient")
    val flicker by ambient.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 2600
        1f at 0; .72f at 1090; .95f at 1200; .82f at 1820; 1f at 2600
    }), label = "flicker")
    val grainStep by ambient.animateFloat(0f, 4f, infiniteRepeatable(tween(500, easing = LinearEasing)), label = "grain")
    val grain = remember { ShaderBrush(ImageShader(noiseBitmap(), TileMode.Repeated, TileMode.Repeated)) }
    val shownProgress by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "progress")

    val fade = 1f - ((exit.value - .6f) / .4f).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = fade }
            .background(Brush.radialGradient(listOf(SurfaceColor, Night), radius = 1400f))
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "جارٍ تحميل مَشهد" },
        contentAlignment = Alignment.Center,
    ) {
        // Film grain, jittered in four steps like a projector gate.
        Canvas(Modifier.fillMaxSize()) {
            val shift = listOf(Offset.Zero, Offset(-12f, 8f), Offset(9f, -14f), Offset(-6f, -6f))[grainStep.toInt().coerceIn(0, 3)]
            translate(shift.x, shift.y) { drawRect(grain, topLeft = Offset(-20f, -20f), size = Size(size.width + 40f, size.height + 40f), alpha = .06f) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(268.dp).height(176.dp), contentAlignment = Alignment.Center) {
                // Projector beam falling from the fatha onto the word.
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = cone.value * flicker * (1f - exit.value) }) {
                    val path = Path().apply {
                        moveTo(size.width * 1.02f, -size.height * .55f)
                        lineTo(size.width * .94f, -size.height * .55f)
                        lineTo(size.width * .18f, size.height * .78f)
                        lineTo(size.width * .66f, size.height * .78f)
                        close()
                    }
                    drawPath(path, Brush.linearGradient(listOf(Mint.copy(alpha = .4f), Mint.copy(alpha = 0f)), Offset(size.width, -size.height * .5f), Offset(size.width * .3f, size.height * .8f)))
                }
                // Viewfinder corners.
                Canvas(Modifier.fillMaxSize().graphicsLayer {
                    val s = 1.4f - .4f * crop.value + 2.4f * exit.value
                    scaleX = s; scaleY = s; alpha = crop.value * (1f - exit.value)
                }) {
                    val l = 26.dp.toPx(); val w = 2.dp.toPx()
                    listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f), Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f)).forEach { (corner, dir) ->
                        drawLine(Mint, corner, corner + Offset(l * dir.x, 0f), w, StrokeCap.Square)
                        drawLine(Mint, corner, corner + Offset(0f, l * dir.y), w, StrokeCap.Square)
                    }
                }
                Wordmark(
                    size = 104.sp,
                    beam = beam.value,
                    modifier = Modifier
                        .graphicsLayer { val s = 1f + .12f * exit.value; scaleX = s; scaleY = s; alpha = 1f - exit.value }
                        .clipToBounds()
                        .drawWithContent {
                            // Reveal from the right edge, the way Arabic is written.
                            clipRect(left = size.width * (1f - reveal.value) - 8f) { this@drawWithContent.drawContent() }
                        },
                )
            }
            Text(
                TAGLINE,
                color = Muted,
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = tagline.value * (1f - exit.value); translationY = (1f - tagline.value) * 8.dp.toPx() },
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 64.dp).graphicsLayer { alpha = foot.value * (1f - exit.value) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(Modifier.width(160.dp).height(2.dp)) {
                drawRect(Border)
                // Fills from the right, like the reading direction.
                drawRect(Mint, topLeft = Offset(size.width * (1f - shownProgress), 0f), size = Size(size.width * shownProgress, size.height))
            }
            Text(
                if (progress >= 1f) "جاهز" else "$label ${arabicDigits((progress * 100).toInt())}٪",
                color = Muted,
                fontFamily = PlexArabic,
                fontSize = 12.5.sp,
            )
        }
    }
}
