package labs.dx.core.domain.util

import java.text.Normalizer
import java.util.Locale

fun normalizeWord(text: String): String {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
    return normalized
        .lowercase(Locale.getDefault())
        .replace(Regex("[^\\p{L}\\p{N}']"), "")
}
