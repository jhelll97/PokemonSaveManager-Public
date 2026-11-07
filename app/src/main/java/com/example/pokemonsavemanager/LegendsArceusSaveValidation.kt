package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class LegendsArceusVersion(val gameId: Int, val displayName: String) {
    ARCEUS(47, "Pokémon Legends: Arceus")
}

sealed class LegendsArceusValidation {
    object Unrecognized : LegendsArceusValidation()
    data class Invalid(val reason: String) : LegendsArceusValidation()
    data class Verified(val version: LegendsArceusVersion, val blockCount: Int) : LegendsArceusValidation()
}

internal data class LegendsArceusDexBlock(val version: LegendsArceusVersion, val data: ByteArray)

internal data class LegendsArceusPokemonBlocks(val version: LegendsArceusVersion, val pastures: ByteArray, val party: ByteArray)

internal data class LegendsArceusTrainerBlocks(val version: LegendsArceusVersion, val status: ByteArray,
    val money: Long?, val meritCurrent: Long?, val meritTotal: Long?, val satchelUpgrades: Long?, val expeditionRank: Long?)

internal data class LegendsArceusPlayTimeBlock(val version: LegendsArceusVersion, val data: ByteArray)

internal data class LegendsArceusInventoryBlocks(val regular: ByteArray, val keyItems: ByteArray,
    val stored: ByteArray, val recipes: ByteArray, val favorites: ByteArray, val satchelUpgrades: Long)

internal data class LegendsArceusInventoryWriteResult(val bytes: ByteArray, val absoluteOffset: Int)
internal data class LegendsArceusDexWriteResult(val bytes: ByteArray, val absoluteOffset: Int)
internal data class LegendsArceusPokemonWriteResult(val bytes: ByteArray, val absoluteOffset: Int)
internal data class LegendsArceusPokemonBlocksWriteResult(
    val bytes: ByteArray,
    val pasturesAbsoluteOffset: Int,
    val partyAbsoluteOffset: Int
)
internal data class LegendsArceusTrainerWriteResult(
    val bytes: ByteArray,
    val nameOffset: Int,
    val trainerIdOffset: Int,
    val moneyOffset: Int,
    val playTimeOffset: Int
)
internal data class LegendsArceusMeritWriteResult(
    val bytes: ByteArray,
    val currentOffset: Int,
    val totalOffset: Int
)
internal data class LegendsArceusExpeditionRankWriteResult(
    val bytes: ByteArray,
    val rankOffset: Int
)
internal data class LegendsArceusSatchelUpgradeWriteResult(
    val bytes: ByteArray,
    val upgradeOffset: Int
)

internal data class LegendsArceusPastureNameBlock(
    val version: LegendsArceusVersion,
    val data: ByteArray
)
internal data class LegendsArceusPastureNameWriteResult(
    val bytes: ByteArray,
    val absoluteOffset: Int
)

/** Independent read-only container gate; pinned format evidence is in SWITCH_READ_ONLY_FORMATS.md. */
object LegendsArceusSaveValidation {
    private val knownSizes = setOf(0x136DDE, 0x13AD06)
    private const val STATUS_KEY = 0xF25C070EL
    private const val MONEY_KEY = 0x3279D927L
    private const val PLAY_TIME_KEY = 0xC4FA7C8CL
    private const val MERIT_CURRENT_KEY = 0x9D5D1CA5L
    private const val MERIT_TOTAL_KEY = 0xC25B0D5AL
    private const val EXPEDITION_RANK_KEY = 0x50FE632AL
    private const val SATCHEL_UPGRADE_KEY = 0x75CE2CF6L
    private const val PASTURE_NAME_KEY = 0x19722C89L
    private const val PASTURE_NAME_OBJECT_SIZE = 0x440
    private val requiredObjects = mapOf(0x47E1CEABL to 0x54600,
        0x2985FE5DL to 0x8D4, 0x02168706L to 0x1E460, STATUS_KEY to 0x80)
    private val intro = decodeHex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = decodeHex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = decodeHex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")
    private val inventoryObjects = mapOf(0x9FE2790AL to 0xA8C, 0x59A4D0C3L to 0x190,
        0x8E434F0DL to 0x2D0, 0xF5D9F4A5L to 0x118)

    fun inspect(source: ByteArray): LegendsArceusValidation = inspect(source, null)

    /** Complete validation must finish before detached object bodies escape this parser. */
    internal fun readPokemonBlocks(source: ByteArray): LegendsArceusPokemonBlocks {
        val captured = mutableMapOf(0x47E1CEABL to ByteArray(0), 0x2985FE5DL to ByteArray(0))
        val checked = inspect(source, captured)
        require(checked is LegendsArceusValidation.Verified) { "A validated native Legends: Arceus save is required." }
        return LegendsArceusPokemonBlocks(checked.version, captured.getValue(0x47E1CEABL), captured.getValue(0x2985FE5DL))
    }

    /** Required native dex object is detached only after the entire container has passed. */
    internal fun readDexBlock(source: ByteArray): LegendsArceusDexBlock {
        val captured = mutableMapOf(0x02168706L to ByteArray(0))
        val checked = inspect(source, captured)
        require(checked is LegendsArceusValidation.Verified) { "A validated native Legends: Arceus Pokédex save is required." }
        return LegendsArceusDexBlock(checked.version, captured.getValue(0x02168706L))
    }

    /** Optional counters stay absent rather than becoming invented zero values. */
    internal fun readTrainerBlocks(source: ByteArray): LegendsArceusTrainerBlocks {
        val uintKeys = setOf(0x3279D927L, 0x9D5D1CA5L, 0xC25B0D5AL, 0x75CE2CF6L, 0x50FE632AL)
        val captured = (uintKeys + STATUS_KEY).associateWith { ByteArray(0) }.toMutableMap()
        val checked = inspect(source, captured, uintKeys)
        require(checked is LegendsArceusValidation.Verified) { (checked as? LegendsArceusValidation.Invalid)?.reason ?: "A validated native Legends: Arceus trainer save is required." }
        fun optionalUint(key: Long): Long? = captured.getValue(key).takeIf { it.size == 4 }?.let { bytes ->
            (0..3).fold(0L) { value, index -> value or ((bytes[index].toLong() and 255) shl (index * 8)) }
        }
        return LegendsArceusTrainerBlocks(checked.version, captured.getValue(STATUS_KEY),
            optionalUint(0x3279D927L), optionalUint(0x9D5D1CA5L), optionalUint(0xC25B0D5AL),
            optionalUint(0x75CE2CF6L), optionalUint(0x50FE632AL))
    }

    /** Native four-byte recorded-time object, detached only after complete container validation. */
    internal fun readPlayTimeBlock(source: ByteArray): LegendsArceusPlayTimeBlock {
        val captured = mutableMapOf(PLAY_TIME_KEY to ByteArray(0))
        val checked = inspect(source, captured, objectSizes = mapOf(PLAY_TIME_KEY to 4))
        require(checked is LegendsArceusValidation.Verified) {
            (checked as? LegendsArceusValidation.Invalid)?.reason ?:
                "A validated native Legends: Arceus save is required."
        }
        val data = captured.getValue(PLAY_TIME_KEY)
        require(data.size == 4) { "The native Legends: Arceus Play Time object is missing." }
        return LegendsArceusPlayTimeBlock(checked.version, data)
    }

    /** Native 32-entry Pasture-name object, detached only after complete container validation. */
    internal fun readPastureNameBlock(source: ByteArray): LegendsArceusPastureNameBlock {
        val captured = mutableMapOf(PASTURE_NAME_KEY to ByteArray(0))
        val checked = inspect(
            source,
            captured,
            objectSizes = mapOf(PASTURE_NAME_KEY to PASTURE_NAME_OBJECT_SIZE)
        )
        require(checked is LegendsArceusValidation.Verified) {
            (checked as? LegendsArceusValidation.Invalid)?.reason ?:
                "A validated native Legends: Arceus save is required."
        }
        val data = captured.getValue(PASTURE_NAME_KEY)
        require(data.size == PASTURE_NAME_OBJECT_SIZE) {
            "The native Legends: Arceus Pasture-name object is missing."
        }
        return LegendsArceusPastureNameBlock(checked.version, data)
    }

    /** Inventory-specific shapes add checks without changing existing reader gates. */
    internal fun readInventoryBlocks(source: ByteArray): LegendsArceusInventoryBlocks {
        val captured = (inventoryObjects.keys + setOf(0x00EF4BAEL, 0x75CE2CF6L))
            .associateWith { ByteArray(0) }.toMutableMap()
        val checked = inspect(source, captured, setOf(0x75CE2CF6L), inventoryObjects, mapOf(0x00EF4BAEL to 0x140))
        require(checked is LegendsArceusValidation.Verified) {
            (checked as? LegendsArceusValidation.Invalid)?.reason ?: "A validated native Arceus save is required."
        }
        require(inventoryObjects.all { (key, size) -> captured.getValue(key).size == size } &&
            captured.getValue(0x00EF4BAEL).size == 0x140 && captured.getValue(0x75CE2CF6L).size == 4) {
            "Arceus inventory blocks or the saved satchel upgrade counter are missing."
        }
        val raw = captured.getValue(0x75CE2CF6L)
        val upgrades = (0..3).fold(0L) { value, i -> value or ((raw[i].toLong() and 255) shl (i * 8)) }
        return LegendsArceusInventoryBlocks(captured.getValue(0x9FE2790AL), captured.getValue(0x59A4D0C3L),
            captured.getValue(0x8E434F0DL), captured.getValue(0xF5D9F4A5L), captured.getValue(0x00EF4BAEL), upgrades)
    }

    /** Replaces selected decoded Trainer Data fields and rebuilds native container integrity. */
    internal fun writeTrainerFields(
        source: ByteArray,
        encodedName: ByteArray?,
        trainerId: Int?,
        money: Long?,
        playTime: ByteArray?
    ): LegendsArceusTrainerWriteResult {
        val blocks = readTrainerBlocks(source)
        if (playTime != null) readPlayTimeBlock(source)
        require(encodedName != null || trainerId != null || money != null || playTime != null) {
            "Select at least one Trainer Data field to change."
        }
        if (encodedName != null) require(encodedName.size == 26) {
            "A complete native Legends: Arceus trainer-name field is required."
        }
        if (trainerId != null) require(trainerId in 0..65535) {
            "Trainer ID must be between 0 and 65535."
        }
        if (money != null) {
            require(blocks.money != null) { "This save does not contain the native money counter." }
            require(money in 0L..9_999_999L) { "Money must be between 0 and 9,999,999." }
        }
        if (playTime != null) require(playTime.size == 4) {
            "The native Legends: Arceus Play Time field must be exactly four bytes."
        }
        val nameRelativeOffset = 0x20
        val trainerIdRelativeOffset = 0x10
        val blockSize = requiredObjects.getValue(STATUS_KEY)
        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var nameOffset = -1
        var trainerIdOffset = -1
        var moneyOffset = -1
        var playTimeOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val blockStart = cursor.position
            if (key == STATUS_KEY) {
                require(type == 4 && length == blockSize.toLong()) {
                    "The Legends: Arceus trainer status object has the wrong native type or size."
                }
                val decoded = ByteArray(length.toInt()) { byteIndex ->
                    (payload[blockStart + byteIndex].toInt() xor stream.next()).toByte()
                }
                if (trainerId != null) {
                    decoded[trainerIdRelativeOffset] = trainerId.toByte()
                    decoded[trainerIdRelativeOffset + 1] = (trainerId ushr 8).toByte()
                }
                if (encodedName != null) encodedName.copyInto(decoded, nameRelativeOffset)
                val replay = KeyStream(key)
                replay.next()
                replay.next32()
                decoded.forEachIndexed { byteIndex, value ->
                    payload[blockStart + byteIndex] = ((value.toInt() and 255) xor replay.next()).toByte()
                }
                nameOffset = blockStart + nameRelativeOffset
                trainerIdOffset = blockStart + trainerIdRelativeOffset
            } else if (key == MONEY_KEY) {
                require(type == 10 && length == 4L) {
                    "The Legends: Arceus money counter has the wrong native type or size."
                }
                if (money != null) {
                    repeat(4) { byteIndex ->
                        val decoded = ((money ushr (byteIndex * 8)).toInt() and 255)
                        payload[blockStart + byteIndex] = (decoded xor stream.next()).toByte()
                    }
                    moneyOffset = blockStart
                }
            } else if (key == PLAY_TIME_KEY) {
                require(type == 4 && length == 4L) {
                    "The Legends: Arceus Play Time object has the wrong native type or size."
                }
                if (playTime != null) {
                    playTime.forEachIndexed { byteIndex, value ->
                        payload[blockStart + byteIndex] = ((value.toInt() and 255) xor stream.next()).toByte()
                    }
                    playTimeOffset = blockStart
                }
            }
            cursor.skip(length.toInt())
        }
        require(nameOffset >= 0 && trainerIdOffset >= 0) {
            "The Legends: Arceus trainer status object is missing."
        }
        require(money == null || moneyOffset >= 0) { "The Legends: Arceus money counter is missing." }
        require(playTime == null || playTimeOffset >= 0) { "The Legends: Arceus Play Time object is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        readTrainerBlocks(edited)
        if (playTime != null) readPlayTimeBlock(edited)
        return LegendsArceusTrainerWriteResult(edited, nameOffset, trainerIdOffset, moneyOffset, playTimeOffset)
    }

    /** Replaces selected decoded Merit Point UInt32 records and rebuilds native container integrity. */
    internal fun writeMeritPoints(
        source: ByteArray,
        current: Long?,
        total: Long?
    ): LegendsArceusMeritWriteResult {
        val before = readTrainerBlocks(source)
        require(current != null || total != null) { "Select at least one Merit Point field to change." }
        if (current != null) {
            require(before.meritCurrent != null) { "This save does not contain Current Merit Points." }
            require(current in 0L..999_999_999L) {
                "Changed Current Merit Points must be between 0 and 999,999,999."
            }
        }
        if (total != null) {
            require(before.meritTotal != null) { "This save does not contain Total Merit Points earned." }
            require(total in 0L..999_999_999L) {
                "Changed Total Merit Points earned must be between 0 and 999,999,999."
            }
        }

        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var currentOffset = -1
        var totalOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val blockStart = cursor.position
            val replacement = when (key) {
                MERIT_CURRENT_KEY -> current
                MERIT_TOTAL_KEY -> total
                else -> null
            }
            if (key == MERIT_CURRENT_KEY || key == MERIT_TOTAL_KEY) {
                require(type == 10 && length == 4L) {
                    "A Legends: Arceus Merit Point record has the wrong native type or size."
                }
                if (replacement != null) {
                    repeat(4) { byteIndex ->
                        val decoded = ((replacement ushr (byteIndex * 8)).toInt() and 255)
                        payload[blockStart + byteIndex] = (decoded xor stream.next()).toByte()
                    }
                    if (key == MERIT_CURRENT_KEY) currentOffset = blockStart else totalOffset = blockStart
                }
            }
            cursor.skip(length.toInt())
        }
        require(current == null || currentOffset >= 0) { "The Current Merit Points record is missing." }
        require(total == null || totalOffset >= 0) { "The Total Merit Points earned record is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        readTrainerBlocks(edited)
        return LegendsArceusMeritWriteResult(edited, currentOffset, totalOffset)
    }

    /** Replaces the decoded Galaxy Expedition Team rank UInt32 and rebuilds container integrity. */
    internal fun writeExpeditionRank(
        source: ByteArray,
        rank: Long
    ): LegendsArceusExpeditionRankWriteResult {
        val before = readTrainerBlocks(source)
        require(before.expeditionRank != null) { "This save does not contain the Expedition Team rank." }
        require(rank in 0L..10L) { "Changed Expedition Team rank must be between 0 and 10." }

        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var rankOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val blockStart = cursor.position
            if (key == EXPEDITION_RANK_KEY) {
                require(type == 10 && length == 4L) {
                    "The Legends: Arceus Expedition Team rank has the wrong native type or size."
                }
                repeat(4) { byteIndex ->
                    val decoded = ((rank ushr (byteIndex * 8)).toInt() and 255)
                    payload[blockStart + byteIndex] = (decoded xor stream.next()).toByte()
                }
                rankOffset = blockStart
            }
            cursor.skip(length.toInt())
        }
        require(rankOffset >= 0) { "The Expedition Team rank record is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        readTrainerBlocks(edited)
        return LegendsArceusExpeditionRankWriteResult(edited, rankOffset)
    }

    /** Replaces the decoded Satchel upgrade count UInt32 and rebuilds container integrity. */
    internal fun writeSatchelUpgrades(
        source: ByteArray,
        upgrades: Long
    ): LegendsArceusSatchelUpgradeWriteResult {
        val before = readTrainerBlocks(source)
        require(before.satchelUpgrades != null) { "This save does not contain the Satchel upgrade count." }
        require(upgrades in 0L..39L) { "Changed Satchel upgrades must be between 0 and 39." }

        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var upgradeOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val blockStart = cursor.position
            if (key == SATCHEL_UPGRADE_KEY) {
                require(type == 10 && length == 4L) {
                    "The Legends: Arceus Satchel upgrade count has the wrong native type or size."
                }
                repeat(4) { byteIndex ->
                    val decoded = ((upgrades ushr (byteIndex * 8)).toInt() and 255)
                    payload[blockStart + byteIndex] = (decoded xor stream.next()).toByte()
                }
                upgradeOffset = blockStart
            }
            cursor.skip(length.toInt())
        }
        require(upgradeOffset >= 0) { "The Satchel upgrade count record is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        readTrainerBlocks(edited)
        return LegendsArceusSatchelUpgradeWriteResult(edited, upgradeOffset)
    }

    /** Replaces one decoded four-byte inventory record and rebuilds native container integrity. */
    internal fun writeInventoryRecord(source: ByteArray, blockKey: UInt, relativeOffset: Int,
        rawItemId: Int, rawCount: Int): LegendsArceusInventoryWriteResult {
        readInventoryBlocks(source)
        val keyValue = blockKey.toLong()
        val blockSize = inventoryObjects[keyValue]
            ?: throw IllegalArgumentException("The selected Legends: Arceus inventory block is unsupported.")
        require(relativeOffset in 0..blockSize - 4 && relativeOffset % 4 == 0) {
            "The native Legends: Arceus item slot is outside its inventory block."
        }
        require(rawItemId in 0..0xFFFF && rawCount in 0..0xFFFF) {
            "The native Legends: Arceus item record is outside its unsigned word range."
        }
        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var absoluteOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val blockStart = cursor.position
            if (key == keyValue) {
                require(type == 4 && length == blockSize.toLong()) {
                    "The Legends: Arceus inventory block has the wrong native type or size."
                }
                repeat(relativeOffset) { stream.next() }
                val decoded = byteArrayOf(
                    rawItemId.toByte(), (rawItemId ushr 8).toByte(),
                    rawCount.toByte(), (rawCount ushr 8).toByte()
                )
                repeat(decoded.size) { byteIndex ->
                    payload[blockStart + relativeOffset + byteIndex] =
                        (decoded[byteIndex].toInt() xor stream.next()).toByte()
                }
                absoluteOffset = blockStart + relativeOffset
            }
            cursor.skip(length.toInt())
        }
        require(absoluteOffset >= 0) { "The selected Legends: Arceus inventory block is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        readInventoryBlocks(edited)
        return LegendsArceusInventoryWriteResult(edited, absoluteOffset)
    }

    /** Replaces one native obtained-count word in the validated Hisui research object. */
    internal fun writeDexObtainedCount(source: ByteArray, speciesId: Int, count: Int): LegendsArceusDexWriteResult {
        readDexBlock(source)
        require(speciesId in 1..905) { "The selected species is outside the supported Legends: Arceus table." }
        require(count in 1..0xFFFF) { "The native obtained count is outside its unsigned word range." }
        val keyValue = 0x02168706L
        val relativeOffset = 0x70 + speciesId * 0x58 + 0xA
        val blockSize = requiredObjects.getValue(keyValue)
        require(relativeOffset in 0..blockSize - 2) { "The native research record is outside the Pokédex block." }
        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var absoluteOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val blockStart = cursor.position
            if (key == keyValue) {
                require(type == 4 && length == blockSize.toLong()) {
                    "The Legends: Arceus Pokédex block has the wrong native type or size."
                }
                repeat(relativeOffset) { stream.next() }
                val decoded = byteArrayOf(count.toByte(), (count ushr 8).toByte())
                repeat(decoded.size) { byteIndex ->
                    payload[blockStart + relativeOffset + byteIndex] =
                        (decoded[byteIndex].toInt() xor stream.next()).toByte()
                }
                absoluteOffset = blockStart + relativeOffset
            }
            cursor.skip(length.toInt())
        }
        require(absoluteOffset >= 0) { "The Legends: Arceus Pokédex block is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        readDexBlock(edited)
        return LegendsArceusDexWriteResult(edited, absoluteOffset)
    }

    /** Replaces one 34-byte name in the decoded Pasture-name object and rebuilds native integrity. */
    internal fun writePastureName(
        source: ByteArray,
        pasture: Int,
        encodedName: ByteArray
    ): LegendsArceusPastureNameWriteResult {
        val before = readPastureNameBlock(source)
        require(pasture in 1..32) { "Pasture must be between 1 and 32." }
        require(encodedName.size == 0x22) { "A complete 34-byte native Pasture name is required." }
        require(before.data.size == PASTURE_NAME_OBJECT_SIZE) {
            "The native Legends: Arceus Pasture-name object has the wrong size."
        }
        val relativeOffset = (pasture - 1) * 0x22
        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var absoluteOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) {
                "A native block length exceeds the remaining payload."
            }
            val blockStart = cursor.position
            if (key == PASTURE_NAME_KEY) {
                require(type == 4 && length == PASTURE_NAME_OBJECT_SIZE.toLong()) {
                    "The native Legends: Arceus Pasture-name object has the wrong type or size."
                }
                repeat(relativeOffset) { stream.next() }
                repeat(encodedName.size) { byteIndex ->
                    payload[blockStart + relativeOffset + byteIndex] =
                        (encodedName[byteIndex].toInt() xor stream.next()).toByte()
                }
                absoluteOffset = blockStart + relativeOffset
            }
            cursor.skip(length.toInt())
        }
        require(absoluteOffset >= 0) {
            "The native Legends: Arceus Pasture-name object is missing."
        }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        val reopened = readPastureNameBlock(edited)
        require(
            reopened.data.copyOfRange(relativeOffset, relativeOffset + encodedName.size)
                .contentEquals(encodedName)
        ) { "The rebuilt native Pasture name did not reopen exactly." }
        return LegendsArceusPastureNameWriteResult(edited, absoluteOffset)
    }

    /** Replaces one decoded native pasture PA8 slot, then rebuilds encryption and the SHA-256 footer. */
    internal fun writePastureRecord(
        source: ByteArray,
        pasture: Int,
        position: Int,
        record: ByteArray
    ): LegendsArceusPokemonWriteResult {
        val blocks = readPokemonBlocks(source)
        require(pasture in 1..32 && position in 1..30) {
            "The native Legends: Arceus pasture slot is outside storage."
        }
        require(record.size == LegendsArceusPokemonReadOnly.STORED_SIZE) {
            "A complete 360-byte native stored PA8 is required."
        }
        require(LegendsArceusPokemonReadOnly.read(record) is LegendsArceusSlotRead.Pokemon) {
            "The supplied native PA8 did not pass its stored-data checksum."
        }
        require(blocks.pastures.size == requiredObjects.getValue(0x47E1CEABL)) {
            "The native Legends: Arceus pasture object has the wrong size."
        }
        val relativeOffset = ((pasture - 1) * 30 + position - 1) * LegendsArceusPokemonReadOnly.STORED_SIZE
        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var absoluteOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val blockStart = cursor.position
            if (key == 0x47E1CEABL) {
                require(type == 4 && length == 0x54600L) {
                    "The native Legends: Arceus pasture object has the wrong type or size."
                }
                repeat(relativeOffset) { stream.next() }
                repeat(record.size) { byteIndex ->
                    payload[blockStart + relativeOffset + byteIndex] =
                        (record[byteIndex].toInt() xor stream.next()).toByte()
                }
                absoluteOffset = blockStart + relativeOffset
            }
            cursor.skip(length.toInt())
        }
        require(absoluteOffset >= 0) { "The native Legends: Arceus pasture object is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        LegendsArceusStorageReadOnly.read(edited)
        return LegendsArceusPokemonWriteResult(edited, absoluteOffset)
    }

    /** Replaces one decoded native party PA8 slot, then rebuilds encryption and the SHA-256 footer. */
    internal fun writePartyRecord(
        source: ByteArray,
        position: Int,
        record: ByteArray
    ): LegendsArceusPokemonWriteResult {
        val blocks = readPokemonBlocks(source)
        require(position in 1..6) { "The native Legends: Arceus party slot is outside the six-slot party." }
        require(record.size == LegendsArceusPokemonReadOnly.PARTY_SIZE) {
            "A complete 376-byte native party PA8 is required."
        }
        require(LegendsArceusPokemonReadOnly.read(record) is LegendsArceusSlotRead.Pokemon) {
            "The supplied native party PA8 did not pass its stored-data checksum."
        }
        require(blocks.party.size == requiredObjects.getValue(0x2985FE5DL)) {
            "The native Legends: Arceus party object has the wrong size."
        }
        val partyCount = blocks.party[6 * LegendsArceusPokemonReadOnly.PARTY_SIZE].toInt() and 255
        require(position <= partyCount) { "The selected native party slot is not occupied." }
        val relativeOffset = (position - 1) * LegendsArceusPokemonReadOnly.PARTY_SIZE
        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var absoluteOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val blockStart = cursor.position
            if (key == 0x2985FE5DL) {
                require(type == 4 && length == 0x8D4L) {
                    "The native Legends: Arceus party object has the wrong type or size."
                }
                repeat(relativeOffset) { stream.next() }
                repeat(record.size) { byteIndex ->
                    payload[blockStart + relativeOffset + byteIndex] =
                        (record[byteIndex].toInt() xor stream.next()).toByte()
                }
                absoluteOffset = blockStart + relativeOffset
            }
            cursor.skip(length.toInt())
        }
        require(absoluteOffset >= 0) { "The native Legends: Arceus party object is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        LegendsArceusStorageReadOnly.read(edited)
        return LegendsArceusPokemonWriteResult(edited, absoluteOffset)
    }

    /** Replaces the decoded native pasture and party objects together, then rebuilds the SHA-256 footer. */
    internal fun writePokemonBlocks(
        source: ByteArray,
        pastures: ByteArray,
        party: ByteArray
    ): LegendsArceusPokemonBlocksWriteResult {
        val before = readPokemonBlocks(source)
        require(pastures.size == 0x54600 && pastures.size == before.pastures.size) {
            "The native Legends: Arceus pasture object has the wrong size."
        }
        require(party.size == 0x8D4 && party.size == before.party.size) {
            "The native Legends: Arceus party object has the wrong size."
        }
        val partySize = LegendsArceusPokemonReadOnly.PARTY_SIZE
        val count = party[6 * partySize].toInt() and 255
        require(count in 1..6) {
            "The edited native Legends: Arceus party count must be one through six."
        }
        require((0 until count).all { index ->
            LegendsArceusPokemonReadOnly.read(
                party.copyOfRange(index * partySize, (index + 1) * partySize)
            ) is LegendsArceusSlotRead.Pokemon
        }) { "Every occupied edited party record must pass its native PA8 checksum." }

        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var pasturesAbsoluteOffset = -1
        var partyAbsoluteOffset = -1
        while (cursor.remaining > 0) {
            val key = cursor.uint32()
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            val length = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType).toLong()
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) {
                "A native block length exceeds the remaining payload."
            }
            val blockStart = cursor.position
            val replacement = when (key) {
                0x47E1CEABL -> pastures.also {
                    require(type == 4 && length == it.size.toLong()) {
                        "The native Legends: Arceus pasture object has the wrong type or size."
                    }
                    pasturesAbsoluteOffset = blockStart
                }
                0x2985FE5DL -> party.also {
                    require(type == 4 && length == it.size.toLong()) {
                        "The native Legends: Arceus party object has the wrong type or size."
                    }
                    partyAbsoluteOffset = blockStart
                }
                else -> null
            }
            if (replacement != null) repeat(replacement.size) { byteIndex ->
                payload[blockStart + byteIndex] =
                    (replacement[byteIndex].toInt() xor stream.next()).toByte()
            }
            cursor.skip(length.toInt())
        }
        require(pasturesAbsoluteOffset >= 0 && partyAbsoluteOffset >= 0) {
            "The native Legends: Arceus Pokémon storage objects are missing."
        }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        val reopened = readPokemonBlocks(edited)
        require(reopened.pastures.contentEquals(pastures) && reopened.party.contentEquals(party)) {
            "The rebuilt native Legends: Arceus Pokémon storage objects did not reopen exactly."
        }
        LegendsArceusStorageReadOnly.read(edited)
        return LegendsArceusPokemonBlocksWriteResult(
            edited, pasturesAbsoluteOffset, partyAbsoluteOffset
        )
    }

    private fun inspect(source: ByteArray, captured: MutableMap<Long, ByteArray>?, uintKeys: Set<Long> = emptySet(),
        objectSizes: Map<Long, Int> = emptyMap(), byteDataSizes: Map<Long, Int> = emptyMap()): LegendsArceusValidation {
        if (source.size !in knownSizes) return LegendsArceusValidation.Unrecognized
        val end = source.size - 32
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro); digest.update(source, 0, end); digest.update(outro)
        if (!MessageDigest.isEqual(digest.digest(), source.copyOfRange(end, source.size)))
            return LegendsArceusValidation.Invalid("The Legends: Arceus container hash failed.")
        // Only the detached payload is decrypted. The caller's bytes are never changed.
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] = (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        return try { inspectBlocks(payload, captured, uintKeys, objectSizes, byteDataSizes) }
        catch (e: IllegalArgumentException) { LegendsArceusValidation.Invalid(e.message ?: "Malformed Legends: Arceus blocks.") }
    }

    private fun inspectBlocks(bytes: ByteArray, captured: MutableMap<Long, ByteArray>?, uintKeys: Set<Long>,
        objectSizes: Map<Long, Int>, byteDataSizes: Map<Long, Int>): LegendsArceusValidation {
        val cursor = Cursor(bytes)
        val found = mutableSetOf<Long>()
        var previous = -1L
        var count = 0
        var gameId: Int? = null
        while (cursor.remaining > 0) {
            require(++count <= 65536) { "The block count exceeds the read-only parser limit." }
            val key = cursor.uint32()
            require(key > previous) { "Native block keys are duplicated or out of order." }
            previous = key
            val stream = KeyStream(key)
            val type = cursor.byte() xor stream.next()
            var arraySubtype: Int? = null
            val length: Long = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    arraySubtype = subType
                    elements * elementSize(subType)
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val expected = requiredObjects[key]
            if (expected != null) {
                require(type == 4 && length == expected.toLong()) { "A required Legends: Arceus block has the wrong type or size." }
                found += key
            }
            // Only trainer extraction requests these keys; identity/Pokémon gates retain their original behavior.
            if (key in uintKeys) require(type == 10 && length == 4L) { "A trainer counter must be a native UInt32." }
            objectSizes[key]?.let { expectedSize ->
                require(type == 4 && length == expectedSize.toLong()) {
                    "A requested Legends: Arceus object has the wrong native type or size."
                }
            }
            byteDataSizes[key]?.let { expectedSize ->
                require((type == 4 || (type == 5 && arraySubtype == 8)) && length == expectedSize.toLong()) {
                    "Arceus favorite flags must be a byte body of the native size."
                }
            }
            val capture = captured?.containsKey(key) == true
            if (key == STATUS_KEY || capture) {
                val decoded = if (capture) ByteArray(length.toInt()) else null
                // Consume the same stream used by the unaligned type/length prefix.
                repeat(length.toInt()) { offset ->
                    val value = cursor.byte() xor stream.next()
                    if (key == STATUS_KEY && offset == 0x14) gameId = value
                    if (decoded != null) decoded[offset] = value.toByte()
                }
                if (decoded != null) captured?.put(key, decoded)
            } else cursor.skip(length.toInt())
        }
        require(found.size == requiredObjects.size) { "Required Legends: Arceus identity blocks are missing." }
        val version = LegendsArceusVersion.values().firstOrNull { it.gameId == gameId }
            ?: throw IllegalArgumentException("The saved title ID is not Legends: Arceus.")
        return LegendsArceusValidation.Verified(version, count)
    }

    private fun elementSize(type: Int): Int = when (type) {
        3, 8, 12 -> 1
        9, 13 -> 2
        10, 14, 16 -> 4
        11, 15, 17 -> 8
        else -> throw IllegalArgumentException("Unsupported native block type $type.")
    }

    private class Cursor(private val bytes: ByteArray) {
        var position = 0
            private set
        val remaining: Int get() = bytes.size - position
        fun byte(): Int {
            require(remaining >= 1) { "The native block stream is truncated." }
            return bytes[position++].toInt() and 255
        }
        fun uint32(): Long {
            require(remaining >= 4) { "A native block prefix is truncated." }
            var result = 0L
            repeat(4) { result = result or (byte().toLong() shl (it * 8)) }
            return result
        }
        fun skip(length: Int) {
            require(length >= 0 && length <= remaining) { "The native block payload is truncated." }
            position += length
        }
    }

    private class KeyStream(seed: Long) {
        private var state = seed.toInt()
        private var index = 0
        init { repeat(Integer.bitCount(state)) { advance() } }
        private fun advance() {
            state = state xor (state shl 2)
            state = state xor (state ushr 15)
            state = state xor (state shl 13)
        }
        fun next(): Int {
            val value = (state ushr (index * 8)) and 255
            if (++index == 4) { index = 0; advance() }
            return value
        }
        fun next32(): Long {
            var result = 0L
            repeat(4) { result = result or (next().toLong() shl (it * 8)) }
            return result
        }
    }

    private fun decodeHex(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}


