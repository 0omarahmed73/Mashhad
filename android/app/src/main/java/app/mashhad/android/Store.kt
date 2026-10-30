package app.mashhad.android

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** The last episode (or film) the viewer opened, so they can return to it. */
data class HistoryEntry(
    val title: Title, val season: Int?, val episode: Int?, val episodeName: String?, val updatedAt: Long,
    /** Where playback stopped, when it happened in the in-app player. */
    val positionMs: Long = 0, val durationMs: Long = 0,
) {
    /** Past 95% the film or episode counts as finished. */
    val finished: Boolean get() = durationMs > 0 && positionMs >= durationMs * 95 / 100
}

/**
 * «قائمتي» and watch history, kept in SharedPreferences so they survive restarts.
 * Each entry stores a snapshot of the title, so lists render without a network call.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("mashhad", Context.MODE_PRIVATE)

    val saved = mutableStateListOf<Title>().apply { addAll(readTitles("saved")) }
    val history = mutableStateListOf<HistoryEntry>().apply { addAll(readHistory()) }

    fun isSaved(title: Title) = saved.any { it.id == title.id }

    fun toggleSaved(title: Title): Boolean {
        val wasSaved = isSaved(title)
        if (wasSaved) saved.removeAll { it.id == title.id } else saved.add(0, title)
        writeTitles("saved", saved)
        return !wasSaved
    }

    fun lastWatched(title: Title): HistoryEntry? = history.firstOrNull { it.title.id == title.id }

    fun recordWatch(title: Title, season: Int? = null, episode: Int? = null, episodeName: String? = null, positionMs: Long = 0, durationMs: Long = 0) {
        history.removeAll { it.title.id == title.id }
        history.add(0, HistoryEntry(title, season, episode, episodeName, System.currentTimeMillis(), positionMs, durationMs))
        while (history.size > 40) history.removeAt(history.lastIndex)
        writeHistory()
        if (durationMs > 0) {
            progress.put(progressKey(title, season, episode), JSONObject().put("position", positionMs).put("duration", durationMs))
            prefs.edit().putString("progress", progress.toString()).apply()
        }
    }

    // Saved position for every film and episode played, so each one resumes on its own.
    private val progress = runCatching { JSONObject(prefs.getString("progress", "{}") ?: "{}") }.getOrDefault(JSONObject())
    private fun progressKey(title: Title, season: Int?, episode: Int?) = "${title.id}|${season ?: 0}|${episode ?: 0}"

    /** Where to resume a film or episode: the saved position, or 0 when it was never played or was finished. */
    fun resumePosition(title: Title, season: Int?, episode: Int?): Long {
        val saved = progress.optJSONObject(progressKey(title, season, episode)) ?: return 0
        val position = saved.optLong("position"); val duration = saved.optLong("duration")
        return if (duration > 0 && position >= duration * 95 / 100) 0 else position
    }

    fun progressFraction(title: Title, season: Int?, episode: Int?): Float {
        val saved = progress.optJSONObject(progressKey(title, season, episode)) ?: return 0f
        val duration = saved.optLong("duration")
        return if (duration > 0) (saved.optLong("position").toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    fun forget(title: Title) {
        history.removeAll { it.title.id == title.id }
        writeHistory()
    }

    private fun writeHistory() {
        prefs.edit().putString("history", JSONArray(history.map { entry ->
            JSONObject().apply {
                put("title", Tmdb.toJson(entry.title)); put("updatedAt", entry.updatedAt)
                entry.season?.let { put("season", it) }; entry.episode?.let { put("episode", it) }; entry.episodeName?.let { put("episodeName", it) }
                put("position", entry.positionMs); put("duration", entry.durationMs)
            }
        }).toString()).apply()
    }

    private fun readTitles(key: String): List<Title> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        List(array.length()) { array.getJSONObject(it) }.mapNotNull(Tmdb::parseStored)
    }.getOrDefault(emptyList())

    private fun writeTitles(key: String, titles: List<Title>) {
        prefs.edit().putString(key, JSONArray(titles.map(Tmdb::toJson)).toString()).apply()
    }

    private fun readHistory(): List<HistoryEntry> = runCatching {
        val array = JSONArray(prefs.getString("history", "[]"))
        List(array.length()) { array.getJSONObject(it) }.mapNotNull { json ->
            Tmdb.parseStored(json.getJSONObject("title"))?.let { title ->
                HistoryEntry(
                    title, json.optInt("season").takeIf { it > 0 }, json.optInt("episode").takeIf { it > 0 },
                    json.optString("episodeName").ifBlank { null }, json.optLong("updatedAt"),
                    json.optLong("position"), json.optLong("duration"),
                )
            }
        }
    }.getOrDefault(emptyList())
}
