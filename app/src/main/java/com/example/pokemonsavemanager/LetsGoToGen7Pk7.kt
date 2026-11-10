package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets

data class LetsGoToGen7Review(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

/**
 * Converts one reviewed native Let's Go PB7 Bank record into a native core-Generation-7
 * PK7 for Sun, Moon, Ultra Sun or Ultra Moon. PB7-only party data is never reinterpreted.
 */
object LetsGoToGen7Pk7 {
    private const val PB7_SIZE = 0x104
    private const val PK7_SIZE = 0xE8
    private const val LAST_CORE_GEN7_MOVE = 728
    private val orders = listOf("0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102","2301","3201",
        "1230","1320","2130","3120","2310","3210")
    private val origins = setOf(1,2,3,4,5,7,8,10,11,12,15,20,21,22,23,24,25,26,27,
        30,31,32,33,34,35,36,37,38,39,40,41,42,43)

    fun review(record: ByteArray): LetsGoToGen7Review {
        require(record.size == PB7_SIZE) {
            "A complete 260-byte native Let's Go PB7 record is required."
        }
        val pokemon = (LetsGoPokemonReadOnly.read(record) as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException(
                "The Let's Go PB7 checksum or native record structure is invalid."
            )
        require(!pokemon.isEgg) {
            "Egg PB7 records remain blocked from this PSE conversion."
        }
        require(!pokemon.isPartnerForm) {
            "Partner Pikachu and partner Eevee are save-bound and cannot enter a core Generation 7 save."
        }
        require(pokemon.species in 1..151) {
            "Species #${pokemon.species} has no native Sun, Moon, Ultra Sun or Ultra Moon species mapping."
        }
        require(pokemon.form == 0) {
            "Only verified default-form PB7 records can enter a core Generation 7 save."
        }
        require(pokemon.cachedLevel in 1..100) {
            "The PB7 cached level is outside 1-100."
        }

        val source = plainCore(record)
        val heldItem = u16(source, 0x0A)
        val abilityId = byte(source, 0x14)
        val abilityNumber = byte(source, 0x15) and 7
        val markings = u16(source, 0x16)
        val nature = byte(source, 0x1C)
        val gender = (byte(source, 0x1D) ushr 1) and 3
        val hiddenEvs = (0..5).map { byte(source, 0x1E + it) }
        val moves = (0..3).map { u16(source, 0x5A + it * 2) }
        val pp = (0..3).map { byte(source, 0x62 + it) }
        val ppUps = (0..3).map { byte(source, 0x66 + it) }
        val relearn = (0..3).map { u16(source, 0x6A + it * 2) }
        val iv32 = u32(source, 0x74)
        val currentHandler = byte(source, 0x93)
        val language = byte(source, 0xE3)
        val origin = byte(source, 0xDF)
        val ball = byte(source, 0xDC)
        val metLevel = byte(source, 0xDD) and 0x7F
        val hyperTraining = byte(source, 0xDE)

        require(heldItem == 0) {
            "Held item #$heldItem has no reviewed Let's Go to core Generation 7 mapping. Remove it before depositing again."
        }
        require(abilityId in 1..233 && abilityNumber in setOf(1, 2, 4)) {
            "Ability #$abilityId in slot $abilityNumber is outside the core Generation 7 catalogue."
        }
        require(markings and 0xF000 == 0) {
            "PB7 marking bits 0x${markings.toString(16).uppercase()} exceed the six native Generation 7 marking states."
        }
        require(nature in 0..24 && gender in 0..2) {
            "The PB7 nature or gender field is not destination-valid."
        }
        require(hiddenEvs.all { it == 0 }) {
            "The PB7 hidden EV bytes are nonzero (${hiddenEvs.joinToString("/")}); PSE will not reinterpret them."
        }
        require(byte(source, 0x2A) == 0) {
            "PB7 byte 0x2A is nonzero and would be reinterpreted as Generation 7 Resort state."
        }
        require(moves.all { it in 0..LAST_CORE_GEN7_MOVE } &&
            relearn.all { it in 0..LAST_CORE_GEN7_MOVE }) {
            "A Let's Go-exclusive move has no native Sun, Moon, Ultra Sun or Ultra Moon move mapping."
        }
        require(pp.all { it in 0..63 } && ppUps.all { it in 0..3 }) {
            "A move PP or PP-Up value is outside native Generation 7 storage limits."
        }
        require((0..3).all { moves[it] != 0 || (pp[it] == 0 && ppUps[it] == 0) }) {
            "An empty move slot contains PP or PP-Up data that cannot be reviewed safely."
        }
        require(currentHandler in 0..1) {
            "Current-handler value $currentHandler is not a reviewed native state."
        }
        require(language in setOf(1,2,3,4,5,7,8,9,10)) {
            "Language ID $language is not a reviewed Generation 7 value."
        }
        require(origin in origins) {
            "Origin game ID $origin has no reviewed native Generation 7 mapping."
        }
        require(ball in 1..26) {
            "Ball ID $ball is outside the core Generation 7 catalogue."
        }
        require(metLevel in 1..100) {
            "The met level is outside 1-100."
        }
        require(hyperTraining and 0xC0 == 0) {
            "The PB7 Hyper Training field contains unsupported bits."
        }
        require(date(byte(source,0xD1), byte(source,0xD2), byte(source,0xD3)) &&
            date(byte(source,0xD4), byte(source,0xD5), byte(source,0xD6))) {
            "A PB7 encounter date is invalid."
        }
        val nickname = text(source, 0x40, "nickname", allowBlank = false)
        text(source, 0x78, "Handling Trainer name", allowBlank = true)
        text(source, 0xB0, "Original Trainer name", allowBlank = false)

        val converted = source.copyOf()
        for (offset in 0x24..0x29) converted[offset] = 0
        writeChecksum(converted)
        require(converted.indices.all {
            it in setOf(0x06, 0x07, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29) ||
                converted[it] == source[it]
        }) {
            "The PB7 to PK7 adapter changed an unreviewed stored-Pokémon field."
        }

        val native = encryptCore(converted)
        val reopened = plainPk7(native)
        require(reopened.contentEquals(converted)) {
            "The generated PK7 did not reopen as the reviewed native record."
        }

        val effects = listOf(
            "Explicit Legalise: PSE Let's Go PB7 -> core Generation 7 PK7 conversion; not an official trade, Pokémon Bank transfer, encounter record, legality certification or verified provenance.",
            "Destination format: one native 232-byte PK7 in a selected empty Sun, Moon, Ultra Sun or Ultra Moon PC slot.",
            "Retained byte-for-byte in the PK7 core: species, default form, encryption constant, PID, TID/SID, nickname and trainer text, EXP, IVs, nature, gender, shiny state, friendship, Pokérus, origin, dates, locations, Ball, fateful flag, Hyper Training, markings, ribbons, memories, geography, moves, PP, PP Ups and relearn moves. Held item remains empty; ability ID $abilityId and slot $abilityNumber are retained without an encounter-legality claim.",
            "PB7 hidden EV bytes are verified zero, so native PK7 EVs remain zero.",
            "Let's Go Awakening Values ${pokemon.awakened.joinToString("/")} at PB7 0x24-0x29 (${hex(source,0x24,0x2A)}) have different PK7 semantics. This Legalise choice omits them and writes all six PK7 contest values as zero.",
            "The PB7 party cache at 0xE8-0x103 (${hex(record,PK7_SIZE,PB7_SIZE)}) is omitted. Cached level ${pokemon.cachedLevel}, HP, five battle stats and combat power are not copied; the destination game rebuilds party data from the retained stored record.",
            "Only checksum bytes 0x06-0x07 and reviewed PB7-only bytes 0x24-0x29 differ in the decrypted 232-byte core. Every other core byte is preserved.",
            "The PB7 Bank entry and original Let's Go save stay unchanged. PSE writes one reviewed empty native destination slot, updates required Pokédex and save integrity data, retains a recovery backup, and reopens the persisted destination."
        )
        return LetsGoToGen7Review(native, pokemon.species, nickname, effects)
    }

    private fun plainCore(record: ByteArray): ByteArray {
        require(u16(record, 4) == 0) { "The PB7 sanity field is not zero." }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xor(decoded, 8, PK7_SIZE, ec)
        xor(decoded, PK7_SIZE, PB7_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 0x38
            decoded.copyInto(plain, 8 + block * 0x38, from, from + 0x38)
        }
        var sum = 0
        for (offset in 8 until PK7_SIZE step 2) sum += u16(plain, offset)
        require((sum and 0xFFFF) == u16(record, 6)) { "The PB7 checksum failed." }
        return plain.copyOfRange(0, PK7_SIZE)
    }

    private fun plainPk7(record: ByteArray): ByteArray {
        require(record.size == PK7_SIZE) { "A complete 232-byte native PK7 is required." }
        require(u16(record, 4) == 0) { "The generated PK7 sanity field is not zero." }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xor(decoded, 8, PK7_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 0x38
            decoded.copyInto(plain, 8 + block * 0x38, from, from + 0x38)
        }
        var sum = 0
        for (offset in 8 until PK7_SIZE step 2) sum += u16(plain, offset)
        require((sum and 0xFFFF) == u16(record, 6)) { "The generated PK7 checksum failed." }
        return plain
    }

    private fun encryptCore(plain: ByteArray): ByteArray {
        require(plain.size == PK7_SIZE)
        val ec = u32(plain, 0)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val shuffled = plain.copyOf()
        for (block in 0..3) {
            val destination = 8 + (order[block] - '0') * 0x38
            plain.copyInto(shuffled, destination, 8 + block * 0x38, 8 + (block + 1) * 0x38)
        }
        xor(shuffled, 8, PK7_SIZE, ec)
        return shuffled
    }

    private fun writeChecksum(record: ByteArray) {
        var sum = 0
        for (offset in 8 until PK7_SIZE step 2) sum += u16(record, offset)
        w16(record, 6, sum and 0xFFFF)
    }

    private fun text(bytes: ByteArray, offset: Int, label: String, allowBlank: Boolean): String {
        var end = offset
        while (end < offset + 24 && u16(bytes, end) != 0) end += 2
        val value = String(bytes, offset, end - offset, StandardCharsets.UTF_16LE)
        require(value.length <= 12 && value.all { it.code >= 0x20 && it != '\uFFFD' }) {
            "The $label contains an invalid native glyph."
        }
        require(allowBlank || value.isNotBlank()) { "The $label is blank." }
        return value
    }

    private fun date(year: Int, month: Int, day: Int): Boolean =
        if (year == 0 && month == 0 && day == 0) true
        else year in 0..99 && month in 1..12 && day in 1..31 &&
            runCatching { java.time.LocalDate.of(2000 + year, month, day) }.isSuccess

    private fun hex(bytes: ByteArray, from: Int, end: Int): String =
        bytes.copyOfRange(from, end).joinToString("") { "%02X".format(it) }

    private fun xor(bytes: ByteArray, from: Int, end: Int, seed: Long) {
        var state = seed
        for (offset in from until end step 2) {
            state = (state * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
            w16(bytes, offset, u16(bytes, offset) xor (state ushr 16).toInt())
        }
    }

    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int): Int =
        byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun w16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
}
