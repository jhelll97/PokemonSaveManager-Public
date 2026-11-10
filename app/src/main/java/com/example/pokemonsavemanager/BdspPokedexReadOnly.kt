package com.example.pokemonsavemanager

import java.util.Collections

enum class BdspDexState(val displayName: String) { NONE("None"), HEARD_OF("Heard of"), SEEN("Seen"), CAUGHT("Caught") }
/** Native aligned boolean: only UInt32 value 1 is true; retain the entire raw word. */
data class BdspDexFlag(val rawValue: Long) { val value: Boolean get() = rawValue == 1L }
data class BdspDexFormRead(val formId: Int, val normal: BdspDexFlag, val shiny: BdspDexFlag)
data class BdspSpindaRead(val variant: String, val seenEc: Long, val caughtEc: Long)
data class BdspDexEntryRead(val speciesId: Int, val state: BdspDexState, val genderFlags: List<BdspDexFlag>,
    val languages: List<String>, val rawLanguageFlags: Long, val forms: List<BdspDexFormRead>, val spinda: List<BdspSpindaRead>) {
    val heard: Boolean get() = state.ordinal >= BdspDexState.HEARD_OF.ordinal
    val seen: Boolean get() = state.ordinal >= BdspDexState.SEEN.ordinal
    val caught: Boolean get() = state == BdspDexState.CAUGHT
}
class BdspPokedexRead internal constructor(val version: BdspVersion, val revision: BdspSaveRevision,
    val regionalDex: BdspDexFlag, val nationalDex: BdspDexFlag, val entries: List<BdspDexEntryRead>) {
    val heardCount: Int get() = entries.count { it.heard }
    val seenCount: Int get() = entries.count { it.seen }
    val caughtCount: Int get() = entries.count { it.caught }
    fun entry(species: Int): BdspDexEntryRead {
        require(species in 1..493) { "BDSP Pokédex species must be 1 through 493." }
        return entries[species - 1]
    }
}

/** Fixed native records after the complete revision/whole-file-MD5/title gate. No save-writing route. */
object BdspPokedexReadOnly {
    private const val DEX = 0x7A328
    private val languages = listOf("Japanese", "English", "French", "Italian", "German", "Spanish", "Korean",
        "Chinese (Simplified)", "Chinese (Traditional)")
    // Native species / form-count order. Normal array immediately precedes shiny array, four bytes per flag.
    private val formCounts = listOf(201 to 28, 351 to 4, 386 to 4, 412 to 3, 413 to 3, 414 to 3,
        421 to 2, 422 to 2, 423 to 2, 479 to 6, 487 to 2, 492 to 2, 493 to 18)
    fun read(source: ByteArray): BdspPokedexRead {
        val checked = BdspSaveValidation.inspect(source)
        require(checked is BdspValidation.Verified) { "A validated native BDSP Pokédex save is required." }
        val offsets = mutableMapOf<Int, Pair<Int, Int>>()
        var next = 493 * 4 * 5
        for ((species, count) in formCounts) { offsets[species] = next to count; next += count * 4 * 2 }
        check(next == 0x28FC)
        val variants = listOf("Male", "Female", "Shiny male", "Shiny female")
        val entries = (1..493).map { species ->
            val index = species - 1
            val stateId = u32(source, DEX + index * 4)
            require(stateId in 0L..3L) { "The saved BDSP Pokédex state for species #$species is outside the native range." }
            val gender = listOf(0x7B4, 0xF68, 0x171C, 0x1ED0).map { BdspDexFlag(u32(source, DEX + it + index * 4)) }
            val language = u32(source, DEX + 0x28FC + index * 4)
            val forms = offsets[species]?.let { (offset, count) -> (0 until count).map { form ->
                BdspDexFormRead(form, BdspDexFlag(u32(source, DEX + offset + form * 4)),
                    BdspDexFlag(u32(source, DEX + offset + (count + form) * 4)))
            } } ?: emptyList()
            val spinda = if (species == 327) variants.mapIndexed { group, label ->
                BdspSpindaRead(label, u32(source, 0xCEA34 + group * 4), u32(source, 0xCEA44 + group * 4))
            } else emptyList()
            BdspDexEntryRead(species, BdspDexState.values()[stateId.toInt()], immutable(gender),
                immutable(languages.filterIndexed { bit, _ -> language and (1L shl bit) != 0L }), language,
                immutable(forms), immutable(spinda))
        }
        return BdspPokedexRead(checked.version, checked.revision, BdspDexFlag(u32(source, DEX + 0x30B0)),
            BdspDexFlag(u32(source, DEX + 0x30B4)), immutable(entries))
    }
    private fun u32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { n, i -> n or ((bytes[offset + i].toLong() and 255) shl (i * 8)) }
    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
}
