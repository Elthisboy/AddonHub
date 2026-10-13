package com.appmcpe.hub.parental

/**
 * Listas de palabras del filtro (inglés y español). Se comparan sin tildes ni emojis, por palabra
 * completa y aceptando plural simple ("gun" → "guns").
 *
 * Los niveles son acumulativos: a menor edad se bloquea más.
 *
 *   SIEMPRE  → nadie lo ve, ni siquiera en modo adulto
 *   +18      → solo en modo adulto (verificado)
 *   MADURO   → bloqueado a menores de 13
 *   PEQUES   → bloqueado además a menores de 10
 */
object ContentTerms {
    /** Siempre bloqueado, a cualquier edad, aunque sea el modo adulto y aunque un adulto lo "permita". */
    val ALWAYS = listOf(
        "sex", "sexy", "sexo", "sexual", "porn", "porno", "pornography", "nsfw", "nsfl",
        "nude", "nudes", "nudity", "naked", "desnudo", "desnuda", "hentai", "erotic", "erotica",
        "erotico", "xxx", "onlyfans", "lewd", "fetish", "boobs", "boob", "tits", "penis", "vagina",
        "orgasm", "stripper", "bdsm", "rape", "violacion",
    )

    /** Solo para mayores de 18 verificados: drogas, alcohol, apuestas, autolesión, tortura. */
    val ADULT_ONLY = listOf(
        "cocaine", "cocaina", "heroin", "heroina", "meth", "methamphetamine",
        "marijuana", "marihuana", "cannabis", "drugs", "drogas", "suicide", "suicidio",
        "alcohol", "vodka", "whisky", "whiskey", "beer", "cerveza", "drunk", "borracho",
        "gambling", "casino", "apuestas", "torture", "tortura",
    )

    /** Marcas con símbolos que la normalización borraría ("18+"); se buscan en el texto crudo. */
    val ADULT_ONLY_RAW = listOf(
        Regex("(?<![\\d.])18\\s*\\+"),
        Regex("\\+\\s*18(?![\\d.])"),
        Regex("adults?\\s+only"),
        Regex("mature\\s+audiences?"),
    )

    /** Bloqueado a menores de 13: terror, sangre, demonios. */
    val MATURE = listOf(
        "horror", "terror", "scary", "creepy", "gore", "gory", "blood", "bloody", "murder",
        "murderer", "demon", "demonic", "satan", "satanic", "haunted", "nightmare", "jumpscare",
        "jump scare", "fnaf", "five nights", "backrooms", "poppy playtime", "huggy wuggy",
        "siren head", "scp", "slender", "slenderman", "creepypasta",
        "sangre", "sangriento", "asesino", "asesinato", "miedo", "pesadilla", "demonio", "diabolico",
    )

    /** Además bloqueado a menores de 10: armas y violencia. */
    val KIDS_ONLY = listOf(
        "gun", "guns", "pistol", "rifle", "shotgun", "sniper", "machine gun", "ak47",
        "war", "guerra", "kill", "killer", "killing", "death", "dead", "muerte",
        "nuke", "herobrine", "arma", "armas", "pistola", "disparos",
    )

    /** Subcategorías de CurseForge bloqueadas a menores de 13 (Horror de addons). */
    val MATURE_CATEGORY_IDS = setOf(8833)
    val MATURE_CATEGORY_SLUGS = setOf("horror")
}
