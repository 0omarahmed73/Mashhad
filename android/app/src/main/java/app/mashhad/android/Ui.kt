package app.mashhad.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

internal val Night = Color(0xFF121D2B)
internal val SurfaceColor = Color(0xFF1B2A3A)
internal val Raised = Color(0xFF26384B)
internal val Border = Color(0xFF354A5D)
internal val Mint = Color(0xFF92DECC)
internal val InkOnMint = Color(0xFF0F2A25)
internal val Foreground = Color(0xFFEDF4F2)
internal val Muted = Color(0xFFA7B7C3)
internal val Rose = Color(0xFFD88F9C)

/** Arabic-Indic digits for anything shown to the viewer. */
internal fun ar(value: Any): String = value.toString().map { if (it in '0'..'9') "٠١٢٣٤٥٦٧٨٩"[it - '0'] else it }.joinToString("")

internal fun kindLabel(kind: Kind) = if (kind == Kind.Movie) "فيلم" else "مسلسل"

/** Arabic number agreement: موسم واحد، موسمان، ٣–١٠ مواسم، ١١+ موسمًا (and the same for حلقة). */
internal fun countLabel(count: Int, one: String, two: String, few: String, many: String) = when {
    count == 1 -> one
    count == 2 -> two
    count in 3..10 -> "${ar(count)} $few"
    else -> "${ar(count)} $many"
}

internal enum class Glyph { Home, Search, Bookmark, BookmarkFilled, Back, Play, Add, Check, Close, Info, Star, Film, Globe }

@Composable
internal fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = (size.minDimension * .085f).coerceAtLeast(1.6f), cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), stroke.width, cap = StrokeCap.Round)
        when (glyph) {
            Glyph.Home -> drawPath(Path().apply {
                moveTo(.1f * w, .48f * h); lineTo(.5f * w, .12f * h); lineTo(.9f * w, .48f * h)
                moveTo(.22f * w, .43f * h); lineTo(.22f * w, .88f * h); lineTo(.78f * w, .88f * h); lineTo(.78f * w, .43f * h)
                moveTo(.43f * w, .88f * h); lineTo(.43f * w, .63f * h); lineTo(.57f * w, .63f * h); lineTo(.57f * w, .88f * h)
            }, tint, style = stroke)
            Glyph.Search -> { drawCircle(tint, .29f * w, Offset(.43f * w, .4f * h), style = stroke); line(.63f, .61f, .9f, .88f) }
            Glyph.Bookmark, Glyph.BookmarkFilled -> {
                val path = Path().apply { moveTo(.25f * w, .12f * h); lineTo(.75f * w, .12f * h); lineTo(.75f * w, .9f * h); lineTo(.5f * w, .72f * h); lineTo(.25f * w, .9f * h); close() }
                if (glyph == Glyph.BookmarkFilled) drawPath(path, tint) else drawPath(path, tint, style = stroke)
            }
            // Points right: "back" in a right-to-left layout.
            Glyph.Back -> { line(.18f, .5f, .82f, .5f); line(.82f, .5f, .52f, .2f); line(.82f, .5f, .52f, .8f) }
            Glyph.Play -> drawPath(Path().apply { moveTo(.3f * w, .18f * h); lineTo(.8f * w, .5f * h); lineTo(.3f * w, .82f * h); close() }, tint)
            Glyph.Add -> { line(.5f, .2f, .5f, .8f); line(.2f, .5f, .8f, .5f) }
            Glyph.Check -> { line(.17f, .52f, .4f, .75f); line(.4f, .75f, .84f, .26f) }
            Glyph.Close -> { line(.22f, .22f, .78f, .78f); line(.78f, .22f, .22f, .78f) }
            Glyph.Info -> { drawCircle(tint, .38f * w, Offset(.5f * w, .5f * h), style = stroke); drawCircle(tint, .035f * w, Offset(.5f * w, .31f * h)); line(.5f, .45f, .5f, .7f) }
            Glyph.Star -> drawPath(Path().apply {
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) .45f else .2f
                    val a = Math.toRadians((-90 + i * 36).toDouble())
                    val x = (.5f + r * kotlin.math.cos(a).toFloat()) * w
                    val y = (.52f + r * kotlin.math.sin(a).toFloat()) * h
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }, tint)
            Glyph.Film -> {
                drawRoundRect(tint, Offset(.16f * w, .18f * h), Size(.68f * w, .64f * h), androidx.compose.ui.geometry.CornerRadius(.08f * w), style = stroke)
                line(.36f, .18f, .36f, .82f); line(.64f, .18f, .64f, .82f)
            }
            Glyph.Globe -> {
                drawCircle(tint, .38f * w, Offset(.5f * w, .5f * h), style = stroke)
                line(.12f, .5f, .88f, .5f)
                drawOval(tint, Offset(.33f * w, .12f * h), Size(.34f * w, .76f * h), style = stroke)
            }
        }
    }
}

/** A remote image with a spinner while it loads and a calm surface if it fails. */
@Composable
internal fun RemoteImage(url: String?, modifier: Modifier = Modifier, description: String? = null) {
    if (url == null) {
        Box(modifier.background(Brush.verticalGradient(listOf(Raised, SurfaceColor))), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.Film, Muted, Modifier.size(28.dp))
        }
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().background(SurfaceColor), contentAlignment = Alignment.Center) { MashhadSpinner(size = 28.dp, glyph = false) } },
        error = { Box(Modifier.fillMaxSize().background(SurfaceColor), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Film, Muted, Modifier.size(28.dp)) } },
    )
}

/** Poster card used in rows and grids. */
@Composable
internal fun PosterCard(title: Title, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = 120.dp, saved: Boolean = false, rank: Int? = null) {
    Column(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = (rank?.let { "المرتبة ${ar(it)}: " } ?: "") + "${kindLabel(title.kind)} ${title.name}" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
            RemoteImage(title.posterUrl, Modifier.fillMaxSize())
            Text(
                kindLabel(title.kind),
                color = if (title.kind == Kind.Tv) InkOnMint else Foreground,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    .background(if (title.kind == Kind.Tv) Mint else Night.copy(alpha = .82f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
            if (saved) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).background(Mint, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.BookmarkFilled, InkOnMint, Modifier.size(14.dp))
            }
            if (rank != null) Text(
                ar(rank), color = Foreground, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = PlexArabic,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Night.copy(alpha = .78f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        Text(title.name, color = Foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
        Text(listOfNotNull(title.year?.let { ar(it) }, title.genres.firstOrNull()).joinToString("، "), color = Muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(title, color = Foreground, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        if (action != null && onAction != null) {
            Text(
                "$action ›", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) InkOnMint else Muted,
        fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .then(if (selected) Modifier.background(Mint) else Modifier.border(1.dp, Border, RoundedCornerShape(22.dp)))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
internal fun PillButton(label: String, glyph: Glyph?, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp))
            .then(if (primary) Modifier.background(Mint) else Modifier.background(Foreground.copy(alpha = .08f)).border(1.dp, Foreground.copy(alpha = .2f), RoundedCornerShape(14.dp)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (glyph != null) GlyphIcon(glyph, if (primary) InkOnMint else Foreground, Modifier.size(18.dp))
        Text(label, color = if (primary) InkOnMint else Foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
