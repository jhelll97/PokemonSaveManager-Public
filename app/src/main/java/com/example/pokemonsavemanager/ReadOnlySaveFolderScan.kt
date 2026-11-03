package com.example.pokemonsavemanager

import java.util.ArrayDeque

/** Provider-neutral traversal. Limits and inaccessible branches are reported, never silently called a full scan. */
class ReadOnlySaveFolderScan<T>(private val maxEntries: Int = 50000, private val maxDepth: Int = 64,
    private val maxResults: Int = 2000, private val maxFileBytes: Long = SwitchReadSnapshot.MAX_SAVE_BYTES.toLong()) {
    data class Node(val id: String, val name: String, val path: String, val directory: Boolean, val size: Long? = null)
    data class Result<T>(val saves: List<T>, val filesChecked: Int, val inaccessible: Int, val skippedLarge: Int,
        val limited: Boolean, val cancelled: Boolean)
    init { require(maxEntries > 0 && maxDepth >= 0 && maxResults > 0 && maxFileBytes > 0) }

    fun scan(root: Node, cancelled: () -> Boolean = { false }, children: (Node) -> List<Node>, inspect: (Node) -> T?): Result<T> {
        val queue = ArrayDeque<Pair<Node, Int>>()
        queue.add(root to 0)
        val visited = mutableSetOf<String>(); val found = mutableListOf<T>()
        var checked = 0; var inaccessible = 0; var large = 0; var limited = false; var stopped = false
        while (queue.isNotEmpty()) {
            if (cancelled()) { stopped = true; break }
            val (node, depth) = queue.removeFirst()
            if (node.id in visited) continue
            if (visited.size >= maxEntries || found.size >= maxResults) { limited = true; break }
            visited.add(node.id)
            if (node.directory) {
                if (depth > maxDepth) { limited = true; continue }
                try {
                    for (child in children(node)) {
                        if (cancelled()) { stopped = true; break }
                        if (queue.size + visited.size >= maxEntries) { limited = true; break }
                        queue.add(child to depth + 1)
                    }
                } catch (_: Exception) { inaccessible++ }
            } else if (node.size != null && node.size > maxFileBytes) {
                large++
            } else {
                checked++
                try { inspect(node)?.let { found.add(it) } } catch (_: Exception) { inaccessible++ }
            }
        }
        return Result(found.toList(), checked, inaccessible, large, limited, stopped || cancelled())
    }
}
