package com.example.pokemonsavemanager

enum class Gen3PokeblockLayout(val caseOffset: Int) { RUBY_SAPPHIRE(0x7F8), EMERALD(0x848) }

data class Gen3PokeblockEntry(val color: Int = 0, val spicy: Int = 0, val dry: Int = 0,
    val sweet: Int = 0, val bitter: Int = 0, val sour: Int = 0, val feel: Int = 0) {
    fun values(): List<Int> = listOf(color, spicy, dry, sweet, bitter, sour, feel)
    fun requireValid(): Gen3PokeblockEntry {
        require(color in 0..14 && values().drop(1).all { it in 0..255 }) { "Choose a valid colour and values from 0 to 255." }
        require(color != 0 || values().all { it == 0 }) { "An empty Pokéblock slot must have zero flavour and feel values." }
        return this
    }
}

enum class Gen3PokeblockOrganisation { COMPACT, LEVEL_DESCENDING, COLOUR }

/** Forty native eight-byte slots; the seventh-index alignment byte is always preserved. */
object Gen3PokeblockEditing {
    // https://github.com/pret/pokeruby/blob/master/include/global.h (SaveBlock1 +0x7F8)
    // https://github.com/pret/pokeemerald/blob/master/include/global.h (SaveBlock1 +0x848)
    // https://github.com/pret/pokeemerald/blob/master/include/pokeblock.h (colour IDs)
    // https://github.com/kwsch/PKHeX/blob/master/PKHeX.Core/Saves/Substructures/Gen3/PokeBlock3.cs (8-byte stride, unused +7)
    val colorNames = listOf("Empty", "Red", "Blue", "Pink", "Green", "Yellow", "Purple", "Indigo",
        "Brown", "Light blue", "Olive", "Gray", "Black", "White", "Gold")

    fun read(section1: ByteArray, layout: Gen3PokeblockLayout): List<Gen3PokeblockEntry> {
        require(section1.size == 0xF80) { "Use a complete validated Gen 3 SaveBlock1 first section." }
        return List(40) { slot ->
            val start = layout.caseOffset + slot * 8
            val v = List(7) { section1[start + it].toInt() and 255 }
            Gen3PokeblockEntry(v[0], v[1], v[2], v[3], v[4], v[5], v[6]).requireValid()
        }
    }

    fun replace(section1: ByteArray, layout: Gen3PokeblockLayout, slot: Int,
        entry: Gen3PokeblockEntry): ByteArray {
        val before = read(section1, layout)
        require(slot in 0 until 40) { "Choose a Pokéblock slot from 1 to 40." }
        entry.requireValid()
        val start = layout.caseOffset + slot * 8
        val result = section1.copyOf()
        entry.values().forEachIndexed { index, value -> result[start + index] = value.toByte() }
        val expected = before.toMutableList().also { it[slot] = entry }
        require(result.indices.all { it in start..start + 6 || result[it] == section1[it] } && read(result, layout) == expected) {
            "The Pokéblock edit changed an unrelated slot, alignment byte or save field."
        }
        return result
    }

    fun organisationOrder(section1: ByteArray, layout: Gen3PokeblockLayout,
        mode: Gen3PokeblockOrganisation): List<Int> {
        val entries = read(section1, layout)
        val occupied = entries.indices.filter { entries[it].color != 0 }
        val ordered = when (mode) {
            Gen3PokeblockOrganisation.COMPACT -> occupied
            Gen3PokeblockOrganisation.LEVEL_DESCENDING -> occupied.sortedByDescending {
                entries[it].values().subList(1, 6).maxOrNull() ?: 0
            }
            Gen3PokeblockOrganisation.COLOUR -> occupied.sortedBy { entries[it].color }
        }
        return ordered + entries.indices.filter { entries[it].color == 0 }
    }

    /** Reorders gameplay fields only; padding belongs to the physical slot and never moves. */
    fun reorder(section1: ByteArray, layout: Gen3PokeblockLayout, order: List<Int>): ByteArray {
        val before = read(section1, layout)
        require(order.size == 40 && order.toSet() == (0 until 40).toSet()) {
            "Each of the 40 source slots must appear exactly once."
        }
        val expected = order.map { before[it] }
        val result = section1.copyOf()
        expected.forEachIndexed { slot, entry ->
            entry.values().forEachIndexed { field, value ->
                result[layout.caseOffset + slot * 8 + field] = value.toByte()
            }
        }
        val caseEnd = layout.caseOffset + 320
        require(result.indices.all { index ->
            (index in layout.caseOffset until caseEnd && (index - layout.caseOffset) % 8 < 7) ||
                result[index] == section1[index]
        } && read(result, layout) == expected &&
            expected.groupingBy { it }.eachCount() == before.groupingBy { it }.eachCount()) {
            "The case organisation changed a block's values, padding or unrelated save data."
        }
        return result
    }
}
