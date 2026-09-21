package ch.electromel.plantinfo

import ch.electromel.plantinfo.util.StringProvider
import java.io.File

/**
 * [StringProvider] pour les tests JVM : sert les **vraies** chaînes d'une langue, lues directement
 * dans `res/values-<langue>/strings.xml`.
 *
 * Un mock renvoyant n'importe quoi ferait passer les tests d'avertissement toxique même si le texte
 * disait le contraire de ce qu'il doit dire. Ici, un test qui vérifie qu'un avertissement cite bien
 * l'espèce et le score continue de porter sur la phrase réellement affichée, et signale au passage
 * une clé manquante ou un `%1$s` de trop.
 */
class TestStrings(language: String = "fr") : StringProvider {

    private val byName: Map<String, String> = parse(language)

    private val nameById: Map<Int, String> = Class.forName("ch.electromel.plantinfo.R\$string")
        .fields
        .filter { it.type == Int::class.javaPrimitiveType }
        .associate { (it.get(null) as Int) to it.name }

    override fun get(id: Int): String {
        val name = nameById[id] ?: error("identifiant de ressource inconnu : $id")
        return byName[name] ?: error("chaîne absente de values-fr : $name")
    }

    override fun get(id: Int, vararg args: Any?): String = String.format(get(id), *args)

    private fun parse(language: String): Map<String, String> {
        // Le répertoire de travail des tests Gradle est le module (app/).
        val suffix = if (language == "en") "" else "-$language"
        val file = File("src/main/res/values$suffix/strings.xml")
        check(file.exists()) { "fichier de traduction introuvable : ${file.absolutePath}" }
        return ENTRY.findAll(file.readText())
            .associate { m -> m.groupValues[1] to unescape(m.groupValues[2]) }
    }

    /** Déséchappe ce que le format Android impose dans le XML (apostrophes, entités). */
    private fun unescape(raw: String): String = raw
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private companion object {
        val ENTRY = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    }
}
