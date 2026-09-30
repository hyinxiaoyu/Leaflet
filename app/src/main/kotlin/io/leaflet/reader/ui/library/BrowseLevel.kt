package io.leaflet.reader.ui.library

import io.leaflet.model.Document

/**
 * One row at the current browse level (REQ-0001 A1).
 *
 * Directories come from the indexed folder paths (LT-3, [browseLevel]'s
 * `directories` argument) unioned with the parent segments of indexed
 * documents. The union keeps a pre-LT-3 index browsable: an old library has
 * documents but no directory rows until its next successful scan.
 */
sealed interface BrowseLevelRow {
    val key: String

    data class Directory(
        val name: String,
        val relativePath: String,
    ) : BrowseLevelRow {
        override val key: String = "dir:$relativePath"
    }

    data class File(
        val document: Document,
    ) : BrowseLevelRow {
        override val key: String = "doc:${document.id.value}"
    }
}

/**
 * Direct children of [prefix]. Subdirectories come first, then files.
 * Each group is ordered by name, case-insensitively, then by the raw name.
 * [prefix] is empty at the library root and uses `/` separators elsewhere.
 *
 * [directories] are the library's indexed folder paths (`/`-separated,
 * relative to the root). They make a folder with no indexed document visible;
 * a folder that only holds documents is still derived from those documents,
 * so the result is identical whether or not the directory index is populated.
 */
fun browseLevel(
    documents: List<Document>,
    prefix: String,
    directories: List<String> = emptyList(),
): List<BrowseLevelRow> {
    val normalized = prefix.trim('/')
    val found = linkedMapOf<String, String>()
    for (directory in directories) {
        val child = directChild(normalized, directory.trim('/')) ?: continue
        found.putIfAbsent(child, childPath(normalized, child))
    }
    val files = mutableListOf<Document>()
    for (document in documents) {
        val path = document.relativePath.trim('/')
        val rest =
            when {
                normalized.isEmpty() -> path
                path == normalized -> continue
                path.startsWith("$normalized/") -> path.removePrefix("$normalized/")
                else -> continue
            }
        if (rest.isEmpty()) continue
        val slash = rest.indexOf('/')
        if (slash < 0) {
            files.add(document)
        } else {
            val name = rest.substring(0, slash)
            if (name.isNotEmpty()) {
                found.putIfAbsent(name, childPath(normalized, name))
            }
        }
    }
    val directoryRows =
        found.entries
            .sortedWith(compareBy(nameOrder) { it.key })
            .map { (name, path) -> BrowseLevelRow.Directory(name, path) }
    val fileRows =
        files
            .sortedWith(compareBy(nameOrder) { fileName(it) })
            .map { BrowseLevelRow.File(it) }
    return directoryRows + fileRows
}

/**
 * The next path segment of [path] below [normalizedPrefix], or null when
 * [path] is not a descendant, is the prefix itself, or is the root.
 */
private fun directChild(
    normalizedPrefix: String,
    path: String,
): String? {
    if (path.isEmpty()) return null
    val rest =
        when {
            normalizedPrefix.isEmpty() -> path
            path == normalizedPrefix -> return null
            path.startsWith("$normalizedPrefix/") -> path.removePrefix("$normalizedPrefix/")
            else -> return null
        }
    if (rest.isEmpty()) return null
    val slash = rest.indexOf('/')
    val name = if (slash < 0) rest else rest.substring(0, slash)
    return name.ifEmpty { null }
}

private fun childPath(
    normalizedPrefix: String,
    name: String,
): String = if (normalizedPrefix.isEmpty()) name else "$normalizedPrefix/$name"

/** Parent prefix, or null when [prefix] is already the library root. */
fun parentPrefix(prefix: String): String? {
    val normalized = prefix.trim('/')
    if (normalized.isEmpty()) return null
    val slash = normalized.lastIndexOf('/')
    return if (slash < 0) "" else normalized.substring(0, slash)
}

fun childPrefix(
    prefix: String,
    directoryName: String,
): String {
    val name = directoryName.trim('/')
    val normalized = prefix.trim('/')
    return if (normalized.isEmpty()) name else "$normalized/$name"
}

private val nameOrder: Comparator<String> =
    Comparator { left, right ->
        val ignored = String.CASE_INSENSITIVE_ORDER.compare(left, right)
        if (ignored != 0) ignored else left.compareTo(right)
    }

private fun fileName(document: Document): String {
    val segment = document.relativePath.trim('/').substringAfterLast('/')
    return segment.ifEmpty { document.displayName }
}
