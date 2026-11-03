package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Compares Android Storage Access Framework document URIs by provider and
 * decoded document id. A provider can issue different URI strings for the same
 * backing document (for example, a direct document URI and a tree-scoped URI),
 * so raw string comparison is not a sufficient overwrite guard.
 */
object Gen6DocumentIdentity {
    private data class DocumentKey(
        val authority: String,
        val documentId: String
    )

    fun sameDocument(first: String?, second: String?): Boolean {
        if (first == null || second == null) return false
        val firstValue = first.trim()
        val secondValue = second.trim()
        if (firstValue == secondValue) return true

        val firstKey = documentKey(firstValue) ?: return false
        val secondKey = documentKey(secondValue) ?: return false
        return firstKey == secondKey
    }

    private fun documentKey(value: String): DocumentKey? =
        runCatching {
            val uri = URI(value)
            if (!uri.scheme.equals("content", ignoreCase = true)) return null
            val authority = uri.rawAuthority
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val segments = uri.rawPath
                ?.split('/')
                ?.filter { it.isNotEmpty() }
                ?: return null
            val documentIndex = segments.indexOfLast { it == "document" }
            if (documentIndex < 0 || documentIndex + 1 >= segments.size) {
                return null
            }
            val documentId = decodePathSegment(segments[documentIndex + 1])
                ?: return null
            DocumentKey(authority, documentId)
        }.getOrNull()

    /**
     * Percent-decodes one URI path segment without treating '+' as a space.
     * Invalid escapes fail closed to raw-string comparison only.
     */
    private fun decodePathSegment(raw: String): String? {
        val bytes = ByteArrayOutputStream(raw.length)
        var index = 0
        while (index < raw.length) {
            if (raw[index] == '%') {
                if (index + 2 >= raw.length) return null
                val high = raw[index + 1].digitToIntOrNull(16) ?: return null
                val low = raw[index + 2].digitToIntOrNull(16) ?: return null
                bytes.write((high shl 4) or low)
                index += 3
            } else {
                raw[index].toString()
                    .toByteArray(StandardCharsets.UTF_8)
                    .forEach { bytes.write(it.toInt()) }
                index += 1
            }
        }
        return bytes.toByteArray().toString(StandardCharsets.UTF_8)
    }
}
