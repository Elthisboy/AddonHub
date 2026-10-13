package com.appmcpe.hub.catalog

/**
 * Busca en la descripción lo que el autor pide y CurseForge no declara como dependencia:
 * experimentos que hay que activar y frases tipo «necesitas descargar X primero».
 */
object Requirements {
    private val EXPERIMENTS = listOf(
        Regex("holiday\\s+creator", RegexOption.IGNORE_CASE) to "Holiday Creator Features",
        Regex("beta\\s+api", RegexOption.IGNORE_CASE) to "APIs Beta",
        Regex("upcoming\\s+creator", RegexOption.IGNORE_CASE) to "Upcoming Creator Features",
        Regex("custom\\s+biomes", RegexOption.IGNORE_CASE) to "Custom Biomes",
        Regex("game\\s*test", RegexOption.IGNORE_CASE) to "Gametest Framework",
        Regex("molang\\s+features", RegexOption.IGNORE_CASE) to "Molang Features",
    )

    private val REQUIRES = Regex(
        "\\b(requires?|required|you need|need to|must (?:install|download|have)|" +
            "depends? on|dependency|install first|first install|necesitas?|requiere|" +
            "debes (?:instalar|descargar|tener)|para que funcione)\\b",
        RegexOption.IGNORE_CASE,
    )

    private const val MAX_LINES = 4
    private const val MAX_LINE_CHARS = 180

    /** @return avisos listos para mostrar, sin repetir; vacío si la descripción no pide nada. */
    fun extract(description: String): List<String> {
        val out = LinkedHashSet<String>()

        val experiments = EXPERIMENTS.filter { (re, _) -> re.containsMatchIn(description) }.map { it.second }
        if (experiments.isNotEmpty()) {
            out += "Activa en Experimentos del mundo: ${experiments.joinToString(", ")}."
        }

        val lines = description.lines().map { it.trim() }
        for ((i, line) in lines.withIndex()) {
            if (out.size >= MAX_LINES + 1) break
            if (line.length < 12 || !REQUIRES.containsMatchIn(line)) continue
            // Muchas descripciones cortan la frase: «…you need to download» / «Farmer's Delight».
            val next = lines.getOrNull(i + 1).orEmpty()
            val text = if (!line.endsWith('.') && next.isNotEmpty() && next.length <= 70) "$line $next" else line
            out += text.take(MAX_LINE_CHARS).let { if (text.length > MAX_LINE_CHARS) "$it…" else it }
        }
        return out.toList()
    }
}
