package com.oderommanager.app.util

/**
 * Handles ROM filename cleaning and display name generation.
 *
 * Rules:
 *  - Remove region tags: (USA), (Europe), (En), (U), (E), (J), (W), etc.
 *  - Remove revision tags: (Rev 1), (Rev A), (Rev 2), etc.
 *  - Remove version tags: (V1.0), (v1.1), etc.
 *  - Remove language tags that aren't translations: (En,Fr,De), etc.
 *  - KEEP translation credits: (T-En by ...) → shortened to (T-En)
 *  - KEEP (T-Es), (T-Fr), (T-De) etc. translation markers
 *  - Strip file extension
 *  - Clean up extra whitespace and dashes
 *
 * Examples:
 *   "Pokemon - Fire Red Version (USA).gba"    → "Pokemon - Fire Red Version"
 *   "Magical Vacation (T-En by Demiforce).gba" → "Magical Vacation (T-En)"
 *   "Metroid Fusion (USA, Europe).gba"         → "Metroid Fusion"
 *   "Zelda - A Link to the Past (USA) (Rev 1).gba" → "Zelda - A Link to the Past"
 */
object RomNameUtil {

    // Region identifiers to strip entirely
    private val REGION_PATTERNS = listOf(
        Regex("""\s*\(USA(?:[^)]*)\)"""),
        Regex("""\s*\(Europe(?:[^)]*)\)"""),
        Regex("""\s*\(Japan(?:[^)]*)\)"""),
        Regex("""\s*\(World(?:[^)]*)\)"""),
        Regex("""\s*\(U\)"""),
        Regex("""\s*\(E\)"""),
        Regex("""\s*\(J\)"""),
        Regex("""\s*\(W\)"""),
        Regex("""\s*\(UE\)"""),
        Regex("""\s*\(JU\)"""),
        Regex("""\s*\([A-Z]{1,3}(?:,[A-Z]{1,3})*\)"""),  // e.g. (En,Fr,De)
    )

    // Tags to strip: revisions, versions, beta/proto/demo, etc.
    private val STRIP_PATTERNS = listOf(
        Regex("""\s*\(Rev\s*[A-Z0-9]+\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(Version\s*[0-9.]+\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(v[0-9]+\.[0-9]+(?:\.[0-9]+)?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(V[0-9]+\.[0-9]+(?:\.[0-9]+)?\)"""),
        Regex("""\s*\(Beta(?:\s*[0-9]*)?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(Proto(?:type)?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(Demo\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(Sample\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(Unl\)"""),       // unlicensed
        Regex("""\s*\(Pirate\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(Hack\)""", RegexOption.IGNORE_CASE),
    )

    // Translation tags — we KEEP the language part, strip the author credit
    // e.g. "(T-En by Demiforce)" → "(T-En)"
    // e.g. "(T-En+Hack by GrayFox)" → "(T-En)"
    private val TRANSLATION_SIMPLIFY = Regex(
        """\(T-([A-Za-z]{2,3})(?:\+[A-Za-z]+)*(?:\s+by\s+[^)]+)?\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Clean a ROM filename into a display name.
     * @param fileName the raw filename including extension
     * @return cleaned display name without extension
     */
    fun cleanName(fileName: String): String {
        // Remove extension
        var name = fileName.substringBeforeLast('.')

        // Simplify translation tags first (before stripping other parens)
        name = TRANSLATION_SIMPLIFY.replace(name) { match ->
            val lang = match.groupValues[1].lowercase().replaceFirstChar { it.uppercase() }
            "(T-$lang)"
        }

        // Strip region tags
        for (pattern in REGION_PATTERNS) {
            name = pattern.replace(name, "")
        }

        // Strip other unwanted tags
        for (pattern in STRIP_PATTERNS) {
            name = pattern.replace(name, "")
        }

        // Clean up trailing whitespace, dashes, and underscores
        name = name
            .replace(Regex("""\s*-\s*$"""), "")
            .replace(Regex("""_"""), " ")
            .trim()

        return name
    }

    /**
     * Given a cleaned display name, propose a new filename (preserving extension).
     */
    fun toFileName(displayName: String, extension: String): String {
        val safe = displayName
            .replace(Regex("""[/\\:*?"<>|]"""), "")  // remove filesystem-illegal chars
            .trim()
        return "$safe.$extension"
    }

    /**
     * Determine if a filename looks like a ROM hack based on common patterns.
     */
    fun looksLikeHack(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.contains("hack") ||
                lower.contains("T-En", ignoreCase = true) ||
                lower.contains("T-Es", ignoreCase = true) ||
                lower.contains("T-Fr", ignoreCase = true) ||
                lower.contains("T-De", ignoreCase = true) ||
                lower.contains("T-Pt", ignoreCase = true) ||
                lower.contains("translated", ignoreCase = true) ||
                lower.contains("kaizo") ||
                lower.contains("romhack") ||
                lower.contains(" mod ") ||
                lower.contains("enhanced") ||
                lower.contains("randomizer") ||
                lower.contains("nuzlocke")
    }
}
