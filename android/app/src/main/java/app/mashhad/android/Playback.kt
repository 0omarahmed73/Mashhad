
package app.mashhad.android

/**
 * Generates the embedded player URL for a movie or series episode.
 *
 * Returns an embed URL to be loaded inside an Android WebView.
 */
object PlaybackSource {

    fun urlFor(
        title: Title,
        season: Int?,
        episode: Int?
    ): String? {
        val tmdbId = title.tmdbId

        return if (title.type == "series") {
            val seasonNumber = season ?: 1
            val episodeNumber = episode ?: 1

            "https://vidsrc.to/embed/tv/$tmdbId/$seasonNumber/$episodeNumber"
        } else {
            "https://vidsrc.to/embed/movie/$tmdbId"
        }
    }
}
