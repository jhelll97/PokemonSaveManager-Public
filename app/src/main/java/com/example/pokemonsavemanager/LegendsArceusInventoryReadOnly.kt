package com.example.pokemonsavemanager

import java.util.Collections

/** Native signed Int16 values and their original words are both retained. */
data class LegendsArceusInventoryItem(val itemId: Int, val count: Int, val slot: Int,
    val rawItemId: Int, val rawCount: Int, val isFavorite: Boolean, val withinActiveCapacity: Boolean) {
    val itemName: String get() = LegendsArceusItemNames.name(itemId)
    val isRecorded: Boolean get() = rawItemId != 0 || rawCount != 0
}

class LegendsArceusInventoryPocket internal constructor(val name: String, val blockKey: UInt,
    val capacity: Int, val referenceMaxCount: Int, val allowsCataloguedAdd: Boolean,
    val items: List<LegendsArceusInventoryItem>) {
    val recordedItems: List<LegendsArceusInventoryItem> = immutable(items.filter { it.isRecorded })
    val residualCount: Int get() = recordedItems.count { !it.withinActiveCapacity }
}

class LegendsArceusInventorySnapshot internal constructor(val pockets: List<LegendsArceusInventoryPocket>,
    val rawSatchelUpgrades: Long, val favoriteItemIds: List<Int>) {
    val recordedItemCount: Int get() = pockets.sumOf { it.recordedItems.size }
}

/** Complete native title, size, hash and block validation always precedes extraction. */
object LegendsArceusInventoryReadOnly {
    const val REGULAR_BLOCK_KEY = 0x9FE2790Au
    const val KEY_BLOCK_KEY = 0x59A4D0C3u
    const val STORED_BLOCK_KEY = 0x8E434F0Du
    const val RECIPE_BLOCK_KEY = 0xF5D9F4A5u
    const val RECORD_BYTES = 4
    const val KEY_ITEM_CAPACITY = 100
    const val STORED_ITEM_CAPACITY = 180
    const val RECIPE_CAPACITY = 70

    fun read(source: ByteArray): LegendsArceusInventorySnapshot {
        val blocks = LegendsArceusSaveValidation.readInventoryBlocks(source)
        // Preserve the native UInt32 addition, including wraparound on unusual saves.
        val capacity = minOf(675L, (blocks.satchelUpgrades + 20L) and 0xFFFFFFFFL).toInt()
        return decode(blocks.regular, blocks.keyItems, blocks.stored, blocks.recipes,
            blocks.favorites, capacity, blocks.satchelUpgrades)
    }

    internal fun decode(regular: ByteArray, keyItems: ByteArray, stored: ByteArray,
        recipes: ByteArray, favorites: ByteArray, regularCapacity: Int,
        rawSatchelUpgrades: Long): LegendsArceusInventorySnapshot {
        require(regularCapacity in 0..675) { "Invalid Arceus satchel capacity." }
        require(favorites.size == 0x140) { "The Arceus favorite flags have the wrong size." }
        val flags = (0 until favorites.size * 8).filter { id ->
            (favorites[id / 8].toInt() and (1 shl (id % 8))) != 0
        }
        return LegendsArceusInventorySnapshot(immutable(listOf(
            pocket("Satchel", REGULAR_BLOCK_KEY, regular, 675, regularCapacity, 999, true, favorites),
            pocket("Key Items", KEY_BLOCK_KEY, keyItems, KEY_ITEM_CAPACITY, KEY_ITEM_CAPACITY, 1, false, favorites),
            pocket("Item Storage", STORED_BLOCK_KEY, stored, STORED_ITEM_CAPACITY, STORED_ITEM_CAPACITY, 999, true, favorites),
            pocket("Crafting Recipes", RECIPE_BLOCK_KEY, recipes, RECIPE_CAPACITY, RECIPE_CAPACITY, 1, false, favorites)
        )), rawSatchelUpgrades, immutable(flags))
    }

    private fun pocket(name: String, blockKey: UInt, data: ByteArray, slots: Int, capacity: Int,
        maximum: Int, allowsCataloguedAdd: Boolean, favorites: ByteArray): LegendsArceusInventoryPocket {
        require(data.size == slots * RECORD_BYTES) { "The Arceus $name block has the wrong size." }
        val items = (0 until slots).map { index ->
            val rawId = u16(data, index * RECORD_BYTES)
            val rawCount = u16(data, index * RECORD_BYTES + 2)
            val id = rawId.toShort().toInt()
            val favorite = id >= 0 && id / 8 < favorites.size &&
                (favorites[id / 8].toInt() and (1 shl (id % 8))) != 0
            LegendsArceusInventoryItem(id, rawCount.toShort().toInt(), index, rawId, rawCount,
                favorite, index < capacity)
        }
        return LegendsArceusInventoryPocket(name, blockKey, capacity, maximum, allowsCataloguedAdd, immutable(items))
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}

private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
