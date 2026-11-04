package com.example.pokemonsavemanager

data class LetsGoEventWorkValue(val id: Int, val value: Int)

data class LetsGoEventWorkCategory(
    val name: String,
    val total: Int,
    val nonzeroValues: List<LetsGoEventWorkValue>
)

data class LetsGoEventFlagCategory(
    val name: String,
    val total: Int,
    val setIds: List<Int>
)

data class LetsGoEventWorkRead(
    val version: LetsGoVersion,
    val workCategories: List<LetsGoEventWorkCategory>,
    val flagCategories: List<LetsGoEventFlagCategory>
) {
    val nonzeroWorkCount: Int get() = workCategories.sumOf { it.nonzeroValues.size }
    val setFlagCount: Int get() = flagCategories.sumOf { it.setIds.size }
}

/** Reads PKHeX's documented EventWork layout while retaining unknown values as numeric data. */
object LetsGoEventWorkReadOnly {
    private const val BLOCK_OFFSET = 0x01200
    private const val WORK_COUNT = 1000
    private const val FLAG_OFFSET = BLOCK_OFFSET + WORK_COUNT * 4

    private data class Range(val name: String, val start: Int, val count: Int)
    private val workRanges = listOf(
        Range("Zone", 0, 32),
        Range("System", 32, 128),
        Range("Scene", 160, 512),
        Range("Event", 672, 256),
        Range("Unclassified", 928, 72)
    )
    private val flagRanges = listOf(
        Range("Zone", 0, 128),
        Range("System", 128, 512),
        Range("Vanish", 640, 1536),
        Range("Event", 2176, 1920)
    )

    fun read(bytes: ByteArray): LetsGoEventWorkRead {
        val verified = LetsGoSaveValidation.inspect(bytes) as? LetsGoValidation.Verified
            ?: throw IllegalArgumentException("A checksum-valid native Let's Go save is required.")
        val work = workRanges.map { range ->
            val values = (0 until range.count).mapNotNull { id ->
                val value = i32(bytes, BLOCK_OFFSET + (range.start + id) * 4)
                if (value == 0) null else LetsGoEventWorkValue(id, value)
            }
            LetsGoEventWorkCategory(range.name, range.count, values)
        }
        val flags = flagRanges.map { range ->
            val setIds = (0 until range.count).filter { id ->
                val rawId = range.start + id
                val value = bytes[FLAG_OFFSET + (rawId ushr 3)].toInt() and 0xFF
                value and (1 shl (rawId and 7)) != 0
            }
            LetsGoEventFlagCategory(range.name, range.count, setIds)
        }
        return LetsGoEventWorkRead(verified.version, work, flags)
    }

    private fun i32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            (bytes[offset + 3].toInt() shl 24)
}
