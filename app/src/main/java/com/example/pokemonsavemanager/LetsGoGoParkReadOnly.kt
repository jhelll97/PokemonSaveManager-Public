package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets
import java.util.Collections

/**
 * Native Let's Go GO Park storage (PKHeX GoParkStorage.cs and GP1.cs,
 * pinned 0a7854f64e118ad228513f3cf4b84c308dac2fdc).
 */
data class LetsGoGoParkEntry(
    val index: Int,
    val area: Int,
    val slot: Int,
    val species: Int,
    val cp: Int,
    val level: Float,
    val storedHp: Int,
    val height: Float,
    val weight: Float,
    val ivHp: Int,
    val ivAttack: Int,
    val ivDefense: Int,
    val capturedDate: Int,
    val gender: Int,
    val form: Int,
    val shiny: Boolean,
    val fastMoveId: Int,
    val chargedMoveId: Int,
    val username: String,
    val secondUsername: String,
    val city: String,
    val nickname: String
) {
    val displayName: String get() = nickname.ifBlank { LetsGoSpeciesNames.name(species) }
    val year: Int get() = capturedDate / 10_000
    val month: Int get() = (capturedDate / 100) % 100
    val day: Int get() = capturedDate % 100
}

class LetsGoGoParkRead internal constructor(
    val version: LetsGoVersion,
    val entries: List<LetsGoGoParkEntry>
) {
    val occupiedCount: Int get() = entries.size
    fun area(area: Int): List<LetsGoGoParkEntry> {
        require(area in 1..LetsGoGoParkReadOnly.AREA_COUNT) { "GO Park area is out of range." }
        return entries.filter { it.area == area }
    }
}

/** Native block 18: 0x4E200, exactly 1,000 fixed-size GP1 records. */
object LetsGoGoParkReadOnly {
    internal const val BLOCK_OFFSET = 0x4E200
    internal const val RECORD_SIZE = 0x1B0
    internal const val SLOTS_PER_AREA = 50
    internal const val AREA_COUNT = 20
    internal const val RECORD_COUNT = SLOTS_PER_AREA * AREA_COUNT
    internal const val BLOCK_LENGTH = RECORD_SIZE * RECORD_COUNT

    fun read(source: ByteArray): LetsGoGoParkRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        require(source.size >= BLOCK_OFFSET + BLOCK_LENGTH) { "Native GO Park block is truncated." }

        fun u16(offset: Int): Int =
            (source[offset].toInt() and 0xFF) or ((source[offset + 1].toInt() and 0xFF) shl 8)
        fun i32(offset: Int): Int =
            (source[offset].toInt() and 0xFF) or
                ((source[offset + 1].toInt() and 0xFF) shl 8) or
                ((source[offset + 2].toInt() and 0xFF) shl 16) or
                (source[offset + 3].toInt() shl 24)
        fun f32(offset: Int): Float = Float.fromBits(i32(offset))
        fun ascii(offset: Int, length: Int): String {
            var end = offset
            val limit = offset + length
            while (end < limit && source[end].toInt() != 0) end++
            return String(source, offset, end - offset, StandardCharsets.US_ASCII)
                .filter { it >= ' ' && it != '\u007F' }
        }

        val entries = ArrayList<LetsGoGoParkEntry>()
        repeat(RECORD_COUNT) { index ->
            val base = BLOCK_OFFSET + index * RECORD_SIZE
            val species = u16(base + 0x28)
            if (species == 0) return@repeat
            entries += LetsGoGoParkEntry(
                index = index,
                area = index / SLOTS_PER_AREA + 1,
                slot = index % SLOTS_PER_AREA + 1,
                species = species,
                cp = i32(base + 0x2C),
                level = f32(base + 0x30),
                storedHp = i32(base + 0x34),
                height = f32(base + 0x48),
                weight = f32(base + 0x4C),
                ivHp = i32(base + 0x50),
                ivAttack = i32(base + 0x54),
                ivDefense = i32(base + 0x58),
                capturedDate = i32(base + 0x5C),
                gender = (source[base + 0x70].toInt() and 0xFF) - 1,
                form = source[base + 0x72].toInt() and 0xFF,
                shiny = source[base + 0x73].toInt() == 1,
                fastMoveId = i32(base + 0x74),
                chargedMoveId = i32(base + 0x78),
                username = ascii(base, 0x10),
                secondUsername = ascii(base + 0x10, 0x10),
                city = ascii(base + 0x7C, 0x60),
                nickname = ascii(base + 0x12D, 0x20)
            )
        }
        return LetsGoGoParkRead(checked.version, Collections.unmodifiableList(entries))
    }
}
