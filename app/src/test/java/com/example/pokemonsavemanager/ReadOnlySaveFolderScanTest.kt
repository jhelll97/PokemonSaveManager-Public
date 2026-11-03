package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class ReadOnlySaveFolderScanTest {
    private fun dir(id: String) = ReadOnlySaveFolderScan.Node(id, id, id, true)
    private fun file(id: String, size: Long? = 1) = ReadOnlySaveFolderScan.Node(id, id, "folder/$id", false, size)

    @Test fun recursiveTraversalIgnoresNamesAndRetainsDistinctDocumentsWithSharedNames() {
        val root = dir("root"); val a = dir("a"); val b = dir("b")
        val graph = mapOf("root" to listOf(a, file("root/main")), "a" to listOf(b, file("a/main")), "b" to listOf(file("b/main"), file("b/renamed.bin")))
        val checked = mutableListOf<String>()
        val result = ReadOnlySaveFolderScan<String>().scan(root, children = { graph[it.id].orEmpty() }, inspect = { checked.add(it.id); it.id })
        assertEquals(listOf("root/main", "a/main", "b/main", "b/renamed.bin"), result.saves)
        assertEquals(result.saves, checked); assertEquals(4, result.filesChecked); assertFalse(result.limited); assertFalse(result.cancelled)
    }

    @Test fun repeatedProviderIdsAndDirectoryCyclesAreVisitedOnlyOnce() {
        val graph = mapOf("root" to listOf(dir("nested"), file("main"), file("main")), "nested" to listOf(dir("root"), file("main")))
        val result = ReadOnlySaveFolderScan<String>().scan(dir("root"), children = { graph[it.id].orEmpty() }, inspect = { it.id })
        assertEquals(listOf("main"), result.saves); assertEquals(1, result.filesChecked); assertFalse(result.limited)
    }

    @Test fun absentOrZeroProviderSizesAreReadButOversizedFilesAreSkipped() {
        val result = ReadOnlySaveFolderScan<String>(maxFileBytes = 16).scan(dir("root"),
            children = { listOf(file("null", null), file("zero", 0), file("unknown", -1), file("max", 16), file("large", 17)) }, inspect = { it.id })
        assertEquals(listOf("null", "zero", "unknown", "max"), result.saves); assertEquals(1, result.skippedLarge); assertEquals(4, result.filesChecked)
    }

    @Test fun inaccessibleDirectoriesAndFilesAreReportedWhileOtherBranchesContinue() {
        val result = ReadOnlySaveFolderScan<String>().scan(dir("root"), children = {
            if (it.id == "bad-dir") throw java.io.IOException("Permission revoked")
            listOf(dir("bad-dir"), file("bad-file"), file("good"), file("not-a-save"))
        }, inspect = { if (it.id == "bad-file") throw java.io.IOException("Unreadable") else if (it.id == "good") it.id else null })
        assertEquals(listOf("good"), result.saves); assertEquals(2, result.inaccessible); assertEquals(3, result.filesChecked); assertFalse(result.limited)
    }

    @Test fun cancellationStopsTraversalAndIsExplicitEvenAfterAValidResult() {
        var stop = false
        val result = ReadOnlySaveFolderScan<String>().scan(dir("root"), cancelled = { stop }, children = { listOf(file("first"), file("second")) },
            inspect = { stop = true; it.id })
        assertTrue(result.cancelled); assertEquals(listOf("first"), result.saves); assertEquals(1, result.filesChecked)
        val before = ReadOnlySaveFolderScan<String>().scan(dir("root"), cancelled = { true }, children = { fail("Must not enumerate"); emptyList() }, inspect = { null })
        assertTrue(before.cancelled); assertTrue(before.saves.isEmpty())
    }

    @Test fun depthEntryAndResultLimitsAlwaysReportPartialRatherThanFullCompletion() {
        val children = { _: ReadOnlySaveFolderScan.Node -> listOf(file("one"), file("two"), file("three")) }
        val byEntries = ReadOnlySaveFolderScan<String>(maxEntries = 3).scan(dir("root"), children = children, inspect = { it.id })
        assertTrue(byEntries.limited); assertEquals(2, byEntries.saves.size)
        val byResults = ReadOnlySaveFolderScan<String>(maxResults = 1).scan(dir("root"), children = children, inspect = { it.id })
        assertTrue(byResults.limited); assertEquals(listOf("one"), byResults.saves)
        val byDepth = ReadOnlySaveFolderScan<String>(maxDepth = 0).scan(dir("root"), children = { listOf(dir("nested"), file("direct")) }, inspect = { it.id })
        assertTrue(byDepth.limited); assertEquals(listOf("direct"), byDepth.saves)
        assertThrows(IllegalArgumentException::class.java) { ReadOnlySaveFolderScan<String>(maxEntries = 0) }
    }
}
