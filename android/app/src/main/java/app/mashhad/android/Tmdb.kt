package app.mashhad.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

enum class Kind(val path: String) { Movie("movie"), Tv("tv") }

/** One film or series. [id] is "movie-155" / "tv-1399" so both kinds share one key space. */
data class Title(
    val tmdbId: Int,
    val kind: Kind,
    val name: String,
    val overview: String,
    val year: Int?,
    val poster: String?,
    val backdrop: String?,
    val genres: List<String>,
    val rating: Double?,
    val popularity: Double = 0.0,
) {
    val id: String get() = "${kind.path}-$tmdbId"
    val posterUrl: String? get() = poster?.let { "https://image.tmdb.org/t/p/w500$it" }
    val backdropUrl: String? get() = (backdrop ?: poster)?.let { "https://image.tmdb.org/t/p/w1280$it" }
}

data class Season(val number: Int, val episodeCount: Int)
data class Episode(val season: Int, val number: Int, val name: String, val overview: String, val runtime: Int?, val still: String?) {
    val stillUrl: String? get() = still?.let { "https://image.tmdb.org/t/p/w300$it" }
}
data class Details(val runtime: Int?, val seasons: List<Season>, val genres: List<String>, val cast: List<String>, val trailerKey: String?, val tagline: String)
data class Provider(val name: String, val logo: String?) {
    val logoUrl: String? get() = logo?.let { "https://image.tmdb.org/t/p/w92$it" }
}
data class Availability(val country: String, val link: String?, val stream: List<Provider>, val rent: List<Provider>, val buy: List<Provider>) {
    val isEmpty: Boolean get() = stream.isEmpty() && rent.isEmpty() && buy.isEmpty()
}
data class Upcoming(val title: Title, val date: String)

object Tmdb {
    private const val BASE = "https://api.themoviedb.org/3"
    val hasKey: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    /** The viewer's country for availability, from the phone's locale; Egypt when the locale has none. */
    val country: String = Locale.getDefault().country.ifBlank { "EG" }.uppercase()

    private val movieGenres = mutableMapOf<Int, String>()
    private val tvGenres = mutableMapOf<Int, String>()

    private suspend fun get(path: String, params: Map<String, String> = emptyMap(), language: String = "ar-SA"): JSONObject? = withContext(Dispatchers.IO) {
        if (!hasKey) return@withContext null
        val query = (params + mapOf("api_key" to BuildConfig.TMDB_API_KEY, "language" to language))
            .entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        try {
            val connection = URL("$BASE$path?$query").openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            if (connection.responseCode !in 200..299) null else connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

    /** TMDB's official Arabic genre names, loaded once. */
    suspend fun loadGenres() = coroutineScope {
        if (movieGenres.isNotEmpty()) return@coroutineScope
        val (movie, tv) = listOf("movie", "tv").map { async { get("/genre/$it/list") } }.awaitAll()
        movie?.optJSONArray("genres").objects().forEach { movieGenres[it.getInt("id")] = it.getString("name") }
        tv?.optJSONArray("genres").objects().forEach { tvGenres[it.getInt("id")] = it.getString("name") }
    }

    fun genreNames(kind: Kind?): List<String> = when (kind) {
        Kind.Movie -> movieGenres.values.toList()
        Kind.Tv -> tvGenres.values.toList()
        null -> (movieGenres.values + tvGenres.values).distinct()
    }

    private fun parse(item: JSONObject, hint: Kind?): Title? {
        val kind = when (item.optString("media_type")) { "movie" -> Kind.Movie; "tv" -> Kind.Tv; "" -> hint; else -> null } ?: return null
        val names = if (kind == Kind.Movie) movieGenres else tvGenres
        val date = item.optString(if (kind == Kind.Movie) "release_date" else "first_air_date")
        val genres = item.optJSONArray("genre_ids")?.let { ids -> List(ids.length()) { ids.optInt(it) }.mapNotNull { names[it] } }
            ?: item.optJSONArray("genres").objects().map { it.optString("name") }
        return Title(
            tmdbId = item.getInt("id"),
            kind = kind,
            name = item.optString(if (kind == Kind.Movie) "title" else "name").ifBlank { item.optString(if (kind == Kind.Movie) "original_title" else "original_name") },
            overview = item.optString("overview"),
            year = date.take(4).toIntOrNull(),
            poster = item.optString("poster_path").takeIf { it.startsWith("/") },
            backdrop = item.optString("backdrop_path").takeIf { it.startsWith("/") },
            genres = genres.take(3),
            rating = item.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
            popularity = item.optDouble("popularity", 0.0),
        )
    }

    private fun list(json: JSONObject?, hint: Kind?): List<Title> =
        json?.optJSONArray("results").objects().mapNotNull { parse(it, hint) }.filter { it.poster != null }

    suspend fun trending(): List<Title> = list(get("/trending/all/week"), null)
    suspend fun popular(kind: Kind): List<Title> = list(get("/${kind.path}/popular"), kind)

    /** One page of the catalog by type and genre, straight from TMDB, most popular first. */
    suspend fun discover(kind: Kind?, genre: String?, page: Int): Pair<List<Title>, Boolean> = coroutineScope {
        loadGenres()
        val kinds = kind?.let { listOf(it) } ?: listOf(Kind.Movie, Kind.Tv)
        val pages = kinds.mapNotNull { k ->
            val genreId = genre?.let { name -> (if (k == Kind.Movie) movieGenres else tvGenres).entries.firstOrNull { it.value == name }?.key ?: return@mapNotNull null }
            async {
                val params = mutableMapOf("sort_by" to "popularity.desc", "vote_count.gte" to "40", "page" to "$page")
                if (genreId != null) params["with_genres"] = "$genreId"
                k to get("/discover/${k.path}", params)
            }
        }.awaitAll()
        val titles = pages.flatMap { (k, json) -> list(json, k) }.sortedByDescending { it.popularity }
        val done = pages.all { (_, json) -> json == null || page >= json.optInt("total_pages", 1) }
        titles to done
    }

    suspend fun search(query: String): List<Title> =
        list(get("/search/multi", mapOf("query" to query, "include_adult" to "false")), null).sortedByDescending { it.popularity }

    suspend fun details(title: Title): Details? {
        val json = get("/${title.kind.path}/${title.tmdbId}", mapOf("append_to_response" to "credits,videos", "include_video_language" to "ar,en,null")) ?: return null
        val videos = json.optJSONObject("videos")?.optJSONArray("results").objects().filter { it.optString("site") == "YouTube" }
        val trailer = videos.firstOrNull { it.optString("type") == "Trailer" } ?: videos.firstOrNull { it.optString("type") == "Teaser" }
        return Details(
            runtime = json.optInt("runtime").takeIf { it > 0 } ?: json.optJSONArray("episode_run_time")?.optInt(0)?.takeIf { it > 0 },
            seasons = json.optJSONArray("seasons").objects().filter { it.optInt("season_number") > 0 && it.optInt("episode_count") > 0 }
                .map { Season(it.getInt("season_number"), it.getInt("episode_count")) },
            genres = json.optJSONArray("genres").objects().map { it.optString("name") },
            cast = json.optJSONObject("credits")?.optJSONArray("cast").objects().take(6).map { it.optString("name") },
            trailerKey = trailer?.optString("key"),
            tagline = json.optString("tagline"),
        )
    }

    /** Episodes of a season: English names (Arabic ones are usually just «الحلقة N»), Arabic overviews when available. */
    suspend fun episodes(tmdbId: Int, season: Int): List<Episode> = coroutineScope {
        val en = async { get("/tv/$tmdbId/season/$season", language = "en-US") }
        val ar = async { get("/tv/$tmdbId/season/$season") }
        val arabic = ar.await()?.optJSONArray("episodes").objects().associateBy { it.optInt("episode_number") }
        (en.await() ?: ar.await())?.optJSONArray("episodes").objects().map {
            val number = it.optInt("episode_number")
            Episode(
                season = season, number = number,
                name = it.optString("name").ifBlank { "الحلقة $number" },
                overview = arabic[number]?.optString("overview").orEmpty().ifBlank { it.optString("overview") },
                runtime = it.optInt("runtime").takeIf { r -> r > 0 },
                still = it.optString("still_path").takeIf { p -> p.startsWith("/") },
            )
        }
    }

    suspend fun recommendations(title: Title): List<Title> = coroutineScope {
        val rec = async { list(get("/${title.kind.path}/${title.tmdbId}/recommendations"), title.kind) }
        val sim = async { list(get("/${title.kind.path}/${title.tmdbId}/similar"), title.kind) }
        (rec.await() + sim.await()).distinctBy { it.id }.filter { it.id != title.id }.take(15)
    }

    /** The most anticipated films whose first release is still ahead, soonest first. */
    suspend fun upcoming(): List<Upcoming> {
        val today = java.time.LocalDate.now().toString()
        val json = get("/discover/movie", mapOf("sort_by" to "popularity.desc", "primary_release_date.gte" to today, "with_release_type" to "2|3"))
        return json?.optJSONArray("results").objects()
            .mapNotNull { item -> parse(item, Kind.Movie)?.takeIf { it.poster != null }?.let { Upcoming(it, item.optString("release_date")) } }
            .sortedBy { it.date }.take(8)
    }

    /** Where to watch in the viewer's country (falls back to the US list when the country has none). */
    suspend fun availability(title: Title): Availability? {
        val results = get("/${title.kind.path}/${title.tmdbId}/watch/providers")?.optJSONObject("results") ?: return null
        val code = listOf(country, "US").firstOrNull { results.has(it) } ?: return Availability(country, null, emptyList(), emptyList(), emptyList())
        val entry = results.getJSONObject(code)
        fun group(key: String) = entry.optJSONArray(key).objects().map { Provider(it.optString("provider_name"), it.optString("logo_path").takeIf { p -> p.startsWith("/") }) }
        return Availability(code, entry.optString("link").ifBlank { null }, group("flatrate"), group("rent"), group("buy"))
    }

    fun parseStored(json: JSONObject): Title? = runCatching {
        Title(
            tmdbId = json.getInt("tmdbId"), kind = Kind.valueOf(json.getString("kind")), name = json.getString("name"),
            overview = json.optString("overview"), year = json.optInt("year").takeIf { it > 0 },
            poster = json.optString("poster").ifBlank { null }, backdrop = json.optString("backdrop").ifBlank { null },
            genres = json.optJSONArray("genres")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(),
            rating = json.optDouble("rating").takeIf { !it.isNaN() },
        )
    }.getOrNull()

    fun toJson(title: Title): JSONObject = JSONObject().apply {
        put("tmdbId", title.tmdbId); put("kind", title.kind.name); put("name", title.name); put("overview", title.overview)
        put("year", title.year ?: 0); put("poster", title.poster ?: ""); put("backdrop", title.backdrop ?: "")
        put("genres", JSONArray(title.genres)); title.rating?.let { put("rating", it) }
    }
}
