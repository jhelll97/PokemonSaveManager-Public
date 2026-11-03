package com.example.pokemonsavemanager

enum class Gen3ActivityProfile(val sectionOffset: Int, val keyOffset: Int?) {
    RUBY_SAPPHIRE(0x5C0, null), EMERALD(0x61C, 0xAC), FIRERED_LEAFGREEN(0x280, 0xF20)
}
data class Gen3ActivityCounter(val id: Int, val label: String, val value: Long)

data class Gen3HallOfFameRecord(val entryCount: Long, val rawTime: Long) {
    val hasRecordedEntry: Boolean get() = entryCount != 0L
    val hours: Int get() = ((rawTime ushr 16) and 0xFFFFL).toInt()
    val minutes: Int get() = ((rawTime ushr 8) and 255L).toInt()
    val seconds: Int get() = (rawTime and 255L).toInt()
    val timeFieldsValid: Boolean get() = minutes < 60 && seconds < 60
}

/** Evidenced native counters, filtered by title; no lifetime/progress inference. */
object Gen3RecordedActivityReadOnly {
    const val SECTION_ID = 2
    private val fields = listOf(0 to "Stored save counter", 5 to "Steps", 7 to "Total battles",
        8 to "Wild battles", 9 to "Trainer battles", 11 to "Pokémon captures",
        13 to "Hatched eggs", 14 to "Evolved Pokémon", 15 to "Pokémon Center uses",
        18 to "Cut field uses", 19 to "Rock Smash field uses", 21 to "Pokémon trades")

    fun read(trainer: ByteArray, section2: ByteArray, profile: Gen3ActivityProfile): List<Gen3ActivityCounter> {
        val decode = validatedDecoder(trainer, section2, profile)
        // Emerald increments slot 12 when a fishing encounter starts. The RS/FRLG
        // headers name it FISHING_CAPTURES; do not infer equivalent recording.
        val visibleFields = if (profile == Gen3ActivityProfile.EMERALD)
            (fields + (12 to "Fishing encounters")).sortedBy { it.first } else fields
        return visibleFields.map { (id, label) -> Gen3ActivityCounter(id, label, decode(id)) }
    }

    fun readHallOfFame(trainer: ByteArray, section2: ByteArray, profile: Gen3ActivityProfile): Gen3HallOfFameRecord {
        val decode = validatedDecoder(trainer, section2, profile)
        return Gen3HallOfFameRecord(entryCount = decode(10), rawTime = decode(1))
    }

    private fun validatedDecoder(trainer: ByteArray, section2: ByteArray, profile: Gen3ActivityProfile): (Int) -> Long {
        require(trainer.size == 0xF2C && section2.size == 0xF80) { "Use complete validated Gen 3 trainer and SaveBlock1 sections." }
        fun u32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { value, index ->
            value or ((bytes[offset + index].toLong() and 255L) shl (8 * index))
        }
        val key = profile.keyOffset?.let { u32(trainer, it) } ?: 0L
        return { id -> u32(section2, profile.sectionOffset + 4 * id) xor key }
    }
}
