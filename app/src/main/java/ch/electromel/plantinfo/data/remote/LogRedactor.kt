package ch.electromel.plantinfo.data.remote

/**
 * Masque les secrets dans une ligne de log HTTP. Pl@ntNet n'accepte sa clé qu'en paramètre d'URL
 * (`api-key=`) et HttpLoggingInterceptor (OkHttp 4) logge l'URL complète sans option de masquage :
 * on filtre donc chaque ligne avant qu'elle n'atteigne logcat.
 */
object LogRedactor {

    /** Paramètres d'URL porteurs d'une clé, chez tous les fournisseurs appelés par l'app. */
    private val SECRET_QUERY_PARAMS = listOf("api-key", "key", "api_key", "apikey", "access_token")

    // (?<=[?&]) : ne touche que des paramètres, pas un « key= » au milieu d'un chemin ou d'un corps.
    private val queryRegex = Regex(
        "(?<=[?&])(" + SECRET_QUERY_PARAMS.joinToString("|") { Regex.escape(it) } + ")=[^&#\\s]*",
        RegexOption.IGNORE_CASE,
    )

    /** En-têtes à masquer si le niveau de log est un jour relevé à HEADERS. */
    val SECRET_HEADERS = listOf("Authorization", "x-api-key", "x-goog-api-key")

    fun redact(message: String): String = queryRegex.replace(message) { "${it.groupValues[1]}=██" }
}
