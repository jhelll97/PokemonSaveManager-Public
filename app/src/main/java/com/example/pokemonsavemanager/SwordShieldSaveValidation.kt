package com.example.pokemonsavemanager

import java.security.MessageDigest

enum class SwordShieldVersion(val gameId: Int, val displayName: String) {
    SWORD(44, "Pokémon Sword"), SHIELD(45, "Pokémon Shield")
}

sealed class SwordShieldValidation {
    object Unrecognized : SwordShieldValidation()
    data class Invalid(val reason: String) : SwordShieldValidation()
    data class Verified(val version: SwordShieldVersion, val blockCount: Int) : SwordShieldValidation()
}

internal data class SwordShieldPokemonBlocks(val version: SwordShieldVersion, val boxes: ByteArray, val party: ByteArray)
internal data class SwordShieldInventoryBlock(val version: SwordShieldVersion, val items: ByteArray)
internal data class SwordShieldInventoryWriteResult(val bytes: ByteArray, val absoluteOffset: Int)
internal data class SwordShieldDexWriteResult(val bytes: ByteArray, val absoluteOffset: Int)
internal data class SwordShieldPokemonWriteResult(val bytes: ByteArray, val absoluteOffset: Int)
internal data class SwordShieldTrainerBlock(val version: SwordShieldVersion, val status: ByteArray)
internal data class SwordShieldBattleTowerBlocks(
    val version: SwordShieldVersion,
    val singlesVictories: ByteArray,
    val doublesVictories: ByteArray,
    val singlesStreak: ByteArray,
    val doublesStreak: ByteArray
)
internal data class SwordShieldDaycareBlock(val version: SwordShieldVersion, val data: ByteArray)
internal data class SwordShieldBoxLayoutBlocks(
    val version: SwordShieldVersion,
    val names: ByteArray,
    val currentBox: ByteArray,
    val boxesUnlocked: ByteArray
)
internal data class SwordShieldRallyDiggingBlocks(
    val version: SwordShieldVersion,
    val rotoRallyScore: ByteArray,
    val diggingDuoSkillStreak: ByteArray,
    val diggingDuoStaminaStreak: ByteArray,
    val diggingPaWatts: ByteArray
)
internal data class SwordShieldStarterChoiceBlock(val version: SwordShieldVersion, val value: ByteArray)
internal data class SwordShieldLeagueCardBlock(val version: SwordShieldVersion, val data: ByteArray)
internal data class SwordShieldIsleOfArmorProgressBlocks(
    val version: SwordShieldVersion,
    val alolanDiglettFound: ByteArray,
    val dojoDonationProgress: ByteArray,
    val dojoDonationTotal: ByteArray,
    val cramOMaticUses: ByteArray,
    val restrictedSparringTypesCleared: ByteArray
)
internal data class SwordShieldCrownTundraProgressBlocks(
    val version: SwordShieldVersion,
    val regiPattern: ByteArray,
    val cobalionFootprints: ByteArray,
    val terrakionFootprints: ByteArray,
    val virizionFootprints: ByteArray,
    val notedSpecies1: ByteArray,
    val notedSpecies2: ByteArray,
    val notedSpecies3: ByteArray,
    val endlessStreak: ByteArray,
    val disconnectStreak: ByteArray,
    val peoniaSpeciesHint: ByteArray
)
internal data class SwordShieldStarTournamentBlocks(
    val version: SwordShieldVersion,
    val totalVictories: ByteArray,
    val partnerVictories: List<ByteArray>
)
internal data class SwordShieldSwordsOfJusticeBlocks(
    val version: SwordShieldVersion,
    val questStatus: ByteArray,
    val questProgress: ByteArray,
    val cobalionProgress: ByteArray,
    val terrakionProgress: ByteArray,
    val virizionProgress: ByteArray,
    val keldeoProgress: ByteArray
)
internal data class SwordShieldAdventureStepBlocks(
    val version: SwordShieldVersion,
    val bikeBoostChargeSteps: ByteArray,
    val bikeBoostChargeLimit: ByteArray,
    val eggHatchCycleSteps: ByteArray,
    val friendshipWalkSteps: ByteArray
)
internal data class SwordShieldHallOfFameTimeBlock(val version: SwordShieldVersion, val value: ByteArray)
internal data class SwordShieldPlayTimeBlock(val version: SwordShieldVersion, val value: ByteArray)
internal data class SwordShieldPokedexViewStateBlocks(
    val version: SwordShieldVersion,
    val currentDex: ByteArray,
    val galarEntry: ByteArray,
    val armorEntry: ByteArray,
    val crownEntry: ByteArray
)
internal data class SwordShieldRestrictedSparringBlocks(
    val version: SwordShieldVersion,
    val streaks: List<ByteArray>
)

internal data class SwordShieldDexBlocks(val version: SwordShieldVersion, val galar: ByteArray,
    val armor: ByteArray?, val crown: ByteArray?)

/** Native container gate plus the bounded inventory-word writer; format evidence is pinned in SWITCH_READ_ONLY_FORMATS.md. */
object SwordShieldSaveValidation {
    private val knownSizes = setOf(0x1716B3, 0x17195E, 0x180B19, 0x180AD0,
        0x1876B1, 0x187693, 0x187668, 0x18764A)
    private const val STATUS_KEY = 0xF25C070EL
    private val requiredObjects = mapOf(0x0D66012CL to 0x50A00,
        0x2985FE5DL to 0x814, 0x4716C404L to 0x4B00, STATUS_KEY to 0x110)
    private val intro = decodeHex("9ec99cd70ed33c44fb9303dceb39b42a1947e9634ba2334416bf82a2ba6355b63d9df24b5f7b6ab2621dc21b68e5c8b53a059000e8a8103de2ecf00cb2ed4f6d")
    private val outro = decodeHex("d6c01c598bc8b8cb46e153fc828c757513e045df32693c75f059f8d9a25fb217e08052dbea8973997579afcb2e8007e6f126e0030ae66ff641bf7e59c2ae55fd")
    private val pad = decodeHex("a092d10607db32a1ae01f5c51e844fe353ca37f4a7b04da018b7c297da5f532b75fa4816f8d48a6f6105f4e2fd04b5a30ffc4492cb32e61bb9b12e01b0565336d2d1503dde5b2e0e52fddf2f7bca6350a4675d2317c052e1a6307c2bb670365b2a276933f5637b363f269ba3ed7a5300a448b3509e14a052de7e102b1b776e")

    fun inspect(source: ByteArray): SwordShieldValidation = inspect(source, null)

    /** Extraction uses the complete identity gate again; never returns a partial parse. */
    internal fun readPokemonBlocks(source: ByteArray): SwordShieldPokemonBlocks {
        val (checked, captured) = captureObjects(source, setOf(0x0D66012CL, 0x2985FE5DL))
        return SwordShieldPokemonBlocks(checked.version, captured.getValue(0x0D66012CL), captured.getValue(0x2985FE5DL))
    }

    internal fun readTrainerBlock(source: ByteArray): SwordShieldTrainerBlock {
        val (checked, captured) = captureObjects(source, setOf(STATUS_KEY))
        return SwordShieldTrainerBlock(checked.version, captured.getValue(STATUS_KEY))
    }

    /** Reads the four documented native Battle Tower counters after the complete container gate passes. */
    internal fun readBattleTowerBlocks(source: ByteArray): SwordShieldBattleTowerBlocks {
        val singlesVictoriesKey = 0x436CAF2BL
        val doublesVictoriesKey = 0x0D477836L
        val singlesStreakKey = 0x6226F5ADL
        val doublesStreakKey = 0x5F74FCEEL
        val (checked, captured) = captureObjects(
            source,
            setOf(singlesVictoriesKey, doublesVictoriesKey, singlesStreakKey, doublesStreakKey),
            mapOf(
                singlesVictoriesKey to (14 to 4),
                doublesVictoriesKey to (14 to 4),
                singlesStreakKey to (13 to 2),
                doublesStreakKey to (13 to 2)
            )
        )
        fun block(key: Long, expectedSize: Int): ByteArray = captured.getValue(key).also {
            require(it.size == expectedSize) {
                "A documented Sword/Shield Battle Tower block is missing or has the wrong native type."
            }
        }
        return SwordShieldBattleTowerBlocks(
            checked.version,
            block(singlesVictoriesKey, 4),
            block(doublesVictoriesKey, 4),
            block(singlesStreakKey, 2),
            block(doublesStreakKey, 2)
        )
    }

    /** Reads PKHeX's documented two-nursery object after the complete container gate passes. */
    internal fun readDaycareBlock(source: ByteArray): SwordShieldDaycareBlock {
        val key = 0x2D6FBA6AL
        val (checked, captured) = captureObjects(
            source,
            setOf(key),
            objectLayouts = mapOf(key to 0x570)
        )
        return SwordShieldDaycareBlock(checked.version, captured.getValue(key))
    }

    /** Reads PKHeX's documented 32 native box names and box-state scalars after the complete container gate passes. */
    internal fun readBoxLayoutBlocks(source: ByteArray): SwordShieldBoxLayoutBlocks {
        val namesKey = 0x19722C89L
        val currentBoxKey = 0x017C3CBBL
        val boxesUnlockedKey = 0x71825204L
        val (checked, captured) = captureObjects(
            source,
            setOf(namesKey, currentBoxKey, boxesUnlockedKey),
            scalarLayouts = mapOf(
                currentBoxKey to (8 to 1),
                boxesUnlockedKey to (8 to 1)
            ),
            objectLayouts = mapOf(namesKey to 0x440)
        )
        return SwordShieldBoxLayoutBlocks(
            checked.version,
            captured.getValue(namesKey),
            captured.getValue(currentBoxKey),
            captured.getValue(boxesUnlockedKey)
        )
    }

    /** Reads PKHeX's documented Roto Rally and digging record scalars after the complete container gate passes. */
    internal fun readRallyDiggingBlocks(source: ByteArray): SwordShieldRallyDiggingBlocks {
        val rallyKey = 0x38548020L
        val skillKey = 0xA0F49CFBL
        val staminaKey = 0x066F38F5L
        val diggingPaKey = 0x68BBA8B1L
        val keys = setOf(rallyKey, skillKey, staminaKey, diggingPaKey)
        val (checked, captured) = captureObjects(
            source,
            keys,
            scalarLayouts = keys.associateWith { 14 to 4 }
        )
        fun block(key: Long): ByteArray = captured.getValue(key).also {
            require(it.size == 4) { "A documented Sword/Shield rally or digging record is missing or has the wrong native type." }
        }
        return SwordShieldRallyDiggingBlocks(
            checked.version,
            block(rallyKey),
            block(skillKey),
            block(staminaKey),
            block(diggingPaKey)
        )
    }

    /** Reads PKHeX's documented starter-choice record after the complete container gate passes. */
    internal fun readStarterChoiceBlock(source: ByteArray): SwordShieldStarterChoiceBlock {
        val key = 0x3677602DL
        val (checked, captured) = captureObjects(
            source,
            setOf(key),
            scalarLayouts = mapOf(key to (14 to 4))
        )
        val value = captured.getValue(key)
        require(value.size == 4) { "The native Sword/Shield starter-choice record is missing or has the wrong native type." }
        return SwordShieldStarterChoiceBlock(checked.version, value)
    }

    /** Reads PKHeX's documented native League Card object after the complete container gate passes. */
    internal fun readLeagueCardBlock(source: ByteArray): SwordShieldLeagueCardBlock {
        val key = 0x874DA6FAL
        val (checked, captured) = captureObjects(
            source,
            setOf(key),
            objectLayouts = mapOf(key to 0x1D0)
        )
        return SwordShieldLeagueCardBlock(checked.version, captured.getValue(key))
    }

    /** Reads PKHeX's documented Isle of Armor progress records after the complete container gate passes. */
    internal fun readIsleOfArmorProgressBlocks(source: ByteArray): SwordShieldIsleOfArmorProgressBlocks {
        val diglettKey = 0x4AEA5A7EL
        val donationProgressKey = 0x0CBEB855L
        val donationTotalKey = 0xC7161487L
        val cramOMaticKey = 0xB9C0ECFCL
        val sparringTypesKey = 0xBB1DE8EFL
        val keys = setOf(diglettKey, donationProgressKey, donationTotalKey, cramOMaticKey, sparringTypesKey)
        val (checked, captured) = captureObjects(
            source,
            keys,
            scalarLayouts = keys.associateWith { 14 to 4 }
        )
        fun block(key: Long): ByteArray = captured.getValue(key).also {
            require(it.size == 4) { "A documented Sword/Shield Isle of Armor record is missing or has the wrong native type." }
        }
        return SwordShieldIsleOfArmorProgressBlocks(
            checked.version,
            block(diglettKey),
            block(donationProgressKey),
            block(donationTotalKey),
            block(cramOMaticKey),
            block(sparringTypesKey)
        )
    }

    /** Reads PKHeX's documented Crown Tundra progress records after the complete container gate passes. */
    internal fun readCrownTundraProgressBlocks(source: ByteArray): SwordShieldCrownTundraProgressBlocks {
        val regiPatternKey = 0xCF90B39AL
        val cobalionFootprintsKey = 0x4D50B655L
        val terrakionFootprintsKey = 0x771E4C88L
        val virizionFootprintsKey = 0xAD67A297L
        val notedSpecies1Key = 0x6F669A35L
        val notedSpecies2Key = 0x6F66951CL
        val notedSpecies3Key = 0x6F6696CFL
        val endlessStreakKey = 0x7F4B4B10L
        val disconnectStreakKey = 0x8EAEB8FFL
        val peoniaSpeciesHintKey = 0xF26B9151L
        val keys = setOf(
            regiPatternKey,
            cobalionFootprintsKey,
            terrakionFootprintsKey,
            virizionFootprintsKey,
            notedSpecies1Key,
            notedSpecies2Key,
            notedSpecies3Key,
            endlessStreakKey,
            disconnectStreakKey,
            peoniaSpeciesHintKey
        )
        val (checked, captured) = captureObjects(
            source,
            keys,
            scalarLayouts = keys.associateWith { 14 to 4 }
        )
        fun block(key: Long): ByteArray = captured.getValue(key).also {
            require(it.size == 4) { "A documented Sword/Shield Crown Tundra record is missing or has the wrong native type." }
        }
        return SwordShieldCrownTundraProgressBlocks(
            checked.version,
            block(regiPatternKey),
            block(cobalionFootprintsKey),
            block(terrakionFootprintsKey),
            block(virizionFootprintsKey),
            block(notedSpecies1Key),
            block(notedSpecies2Key),
            block(notedSpecies3Key),
            block(endlessStreakKey),
            block(disconnectStreakKey),
            block(peoniaSpeciesHintKey)
        )
    }

    /** Reads PKHeX's documented Galarian Star Tournament victory records after the complete container gate passes. */
    internal fun readStarTournamentBlocks(source: ByteArray): SwordShieldStarTournamentBlocks {
        val totalKey = 0x9D6727F6L
        val partnerKeys = listOf(
            0x3934BEC0L,
            0xE9131991L,
            0x7742B542L,
            0x9A535FA0L,
            0xFEE68CE1L,
            0x2D0520CDL,
            0xFB5133BCL,
            0x24B9E3CCL,
            0xB19386DEL,
            0x3576EE34L,
            0xB95B1BB9L,
            0x343E6FC1L,
            0x6371183BL,
            0xA2C094C7L,
            0x9E32AE34L,
            0x06C0FBC8L,
            0xE3ED8F16L,
            0xC0D49E2DL,
            0xEB07C276L,
            0xDBE374D7L
        )
        val keys = partnerKeys.toSet() + totalKey
        val (checked, captured) = captureObjects(
            source,
            keys,
            scalarLayouts = keys.associateWith { 14 to 4 }
        )
        fun block(key: Long): ByteArray = captured.getValue(key).also {
            require(it.size == 4) { "A documented Sword/Shield Galarian Star Tournament record is missing or has the wrong native type." }
        }
        return SwordShieldStarTournamentBlocks(
            checked.version,
            block(totalKey),
            partnerKeys.map(::block)
        )
    }

    /** Reads PKHeX's documented Crown Tundra Swords of Justice quest records after the complete container gate passes. */
    internal fun readSwordsOfJusticeBlocks(source: ByteArray): SwordShieldSwordsOfJusticeBlocks {
        val questStatusKey = 0x4DBB9B79L
        val questProgressKey = 0xCB135C68L
        val cobalionProgressKey = 0xC35F6291L
        val terrakionProgressKey = 0x03C69A96L
        val virizionProgressKey = 0xB98F962BL
        val keldeoProgressKey = 0x9A39C8FCL
        val keys = setOf(
            questStatusKey,
            questProgressKey,
            cobalionProgressKey,
            terrakionProgressKey,
            virizionProgressKey,
            keldeoProgressKey
        )
        val (checked, captured) = captureObjects(
            source,
            keys,
            scalarLayouts = keys.associateWith { 14 to 4 }
        )
        fun block(key: Long): ByteArray = captured.getValue(key).also {
            require(it.size == 4) { "A documented Sword/Shield Swords of Justice quest record is missing or has the wrong native type." }
        }
        return SwordShieldSwordsOfJusticeBlocks(
            checked.version,
            block(questStatusKey),
            block(questProgressKey),
            block(cobalionProgressKey),
            block(terrakionProgressKey),
            block(virizionProgressKey),
            block(keldeoProgressKey)
        )
    }

    /** Reads PKHeX's documented bike, Egg-cycle, and friendship step records after the complete container gate passes. */
    internal fun readAdventureStepBlocks(source: ByteArray): SwordShieldAdventureStepBlocks {
        val bikeStepsKey = 0x57F29628L
        val bikeLimitKey = 0xF64719D9L
        val eggStepsKey = 0x6C99F9A0L
        val friendshipStepsKey = 0xE2798DDEL
        val (checked, captured) = captureObjects(
            source,
            setOf(bikeStepsKey, bikeLimitKey, eggStepsKey, friendshipStepsKey),
            scalarLayouts = mapOf(
                bikeStepsKey to (14 to 4),
                bikeLimitKey to (14 to 4),
                eggStepsKey to (13 to 2),
                friendshipStepsKey to (8 to 1)
            )
        )
        fun block(key: Long, expectedSize: Int): ByteArray = captured.getValue(key).also {
            require(it.size == expectedSize) { "A documented Sword/Shield adventure step record is missing or has the wrong native type." }
        }
        return SwordShieldAdventureStepBlocks(
            checked.version,
            block(bikeStepsKey, 4),
            block(bikeLimitKey, 4),
            block(eggStepsKey, 2),
            block(friendshipStepsKey, 1)
        )
    }

    /** Reads the documented four-byte Sword/Shield playtime counter after the complete container gate passes. */
    internal fun readPlayTimeBlock(source: ByteArray): SwordShieldPlayTimeBlock {
        val key = 0x8CBBFD90L
        val (checked, captured) = captureObjects(source, setOf(key), objectLayouts = mapOf(key to 8))
        val value = captured.getValue(key)
        require(value.size == 8) { "The native Sword/Shield playtime block is missing or has the wrong native type." }
        return SwordShieldPlayTimeBlock(checked.version, value)
    }

    /** Reads PKHeX's documented Hall of Fame Unix timestamp after the complete container gate passes. */
    internal fun readHallOfFameTimeBlock(source: ByteArray): SwordShieldHallOfFameTimeBlock {
        val key = 0xE2F6E456L
        val (checked, captured) = captureObjects(
            source,
            setOf(key),
            scalarLayouts = mapOf(key to (15 to 8))
        )
        val value = captured.getValue(key)
        require(value.size == 8) { "The native Sword/Shield Hall of Fame timestamp is missing or has the wrong native type." }
        return SwordShieldHallOfFameTimeBlock(checked.version, value)
    }

    /** Reads PKHeX's documented regional Pokédex selector and last-viewed species IDs after the complete container gate passes. */
    internal fun readPokedexViewStateBlocks(source: ByteArray): SwordShieldPokedexViewStateBlocks {
        val currentDexKey = 0x9CF58395L
        val galarEntryKey = 0x62743428L
        val armorEntryKey = 0x789FF72DL
        val crownEntryKey = 0x759FF274L
        val (checked, captured) = captureObjects(
            source,
            setOf(currentDexKey, galarEntryKey, armorEntryKey, crownEntryKey),
            scalarLayouts = mapOf(
                currentDexKey to (14 to 4),
                galarEntryKey to (13 to 2),
                armorEntryKey to (13 to 2),
                crownEntryKey to (13 to 2)
            )
        )
        fun block(key: Long, expectedSize: Int): ByteArray = captured.getValue(key).also {
            require(it.size == expectedSize) { "A documented Sword/Shield Pokédex view-state record is missing or has the wrong native type." }
        }
        return SwordShieldPokedexViewStateBlocks(
            checked.version,
            block(currentDexKey, 4),
            block(galarEntryKey, 2),
            block(armorEntryKey, 2),
            block(crownEntryKey, 2)
        )
    }

    /** Reads PKHeX's documented 18 type-specific Restricted Sparring best streaks after the complete container gate passes. */
    internal fun readRestrictedSparringBlocks(source: ByteArray): SwordShieldRestrictedSparringBlocks {
        val keys = listOf(
            0xDB5E16CBL,
            0xD25E08A0L,
            0xD55BCEC2L,
            0xD35BCB9CL,
            0xD65BD055L,
            0xD15BC876L,
            0xDA5E1538L,
            0xDC5E185EL,
            0xDF5E1D17L,
            0xDD5E19F1L,
            0xD45BCD2FL,
            0xE15E203DL,
            0xDE5E1B84L,
            0xE05E1EAAL,
            0xD25BCA09L,
            0xCF5BC550L,
            0xD35E0A33L,
            0xD05BC6E3L
        )
        val (checked, captured) = captureObjects(
            source,
            keys.toSet(),
            scalarLayouts = keys.associateWith { 14 to 4 }
        )
        return SwordShieldRestrictedSparringBlocks(
            checked.version,
            keys.map { key -> captured.getValue(key).also {
                require(it.size == 4) { "A documented Sword/Shield Restricted Sparring streak is missing or has the wrong native type." }
            } }
        )
    }

    /** Optional region type/length rules affect dex extraction only, never existing readers. */
    internal fun readDexBlocks(source: ByteArray): SwordShieldDexBlocks {
        val layouts = mapOf(0x3F936BA9L to 0x2790, 0x3C9366F0L to 0x2760)
        val captured = (layouts.keys + 0x4716C404L).associateWith { ByteArray(0) }.toMutableMap()
        val checked = inspect(source, captured, layouts)
        require(checked is SwordShieldValidation.Verified) { (checked as? SwordShieldValidation.Invalid)?.reason ?: "A validated native Sword/Shield Pokédex save is required." }
        return SwordShieldDexBlocks(checked.version, captured.getValue(0x4716C404L),
            captured.getValue(0x3F936BA9L).takeIf { it.isNotEmpty() }, captured.getValue(0x3C9366F0L).takeIf { it.isNotEmpty() })
    }

    /** The optional bag must be one native object of exactly 0x12F8 bytes. */
    internal fun readInventoryBlock(source: ByteArray): SwordShieldInventoryBlock {
        val captured = mutableMapOf(0x1177C2C4L to ByteArray(0))
        val checked = inspect(source, captured, inventoryLayout = true)
        require(checked is SwordShieldValidation.Verified) { (checked as? SwordShieldValidation.Invalid)?.reason ?: "A validated native Sword/Shield inventory save is required." }
        val items = captured.getValue(0x1177C2C4L)
        require(items.size == 0x12F8) { "The native Sword/Shield inventory object is missing." }
        return SwordShieldInventoryBlock(checked.version, items)
    }

    /** Replaces one decoded inventory word, then rebuilds the container encryption and SHA-256 footer. */
    internal fun writeInventoryWord(source: ByteArray, relativeOffset: Int, rawValue: Long): SwordShieldInventoryWriteResult {
        readInventoryBlock(source)
        require(relativeOffset in 0..0x12F4 && relativeOffset % 4 == 0) {
            "The native Sword/Shield item slot is outside the inventory object."
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
            if (key == 0x1177C2C4L) {
                require(type == 4 && length == 0x12F8L) { "The Sword/Shield inventory block has the wrong native type or size." }
                repeat(relativeOffset) { stream.next() }
                repeat(4) { byteIndex ->
                    val decoded = ((rawValue ushr (byteIndex * 8)) and 0xFF).toInt()
                    payload[blockStart + relativeOffset + byteIndex] = (decoded xor stream.next()).toByte()
                }
                absoluteOffset = blockStart + relativeOffset
            }
            cursor.skip(length.toInt())
        }
        require(absoluteOffset >= 0) { "The native Sword/Shield inventory object is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        readInventoryBlock(edited)
        return SwordShieldInventoryWriteResult(edited, absoluteOffset)
    }


    /** Replaces one decoded 0x30-byte regional Pokédex record, then rebuilds encryption and the SHA-256 footer. */
    internal fun writeDexRecord(
        source: ByteArray,
        region: SwordShieldDexRegion,
        number: Int,
        decodedRecord: ByteArray
    ): SwordShieldDexWriteResult {
        val blocks = readDexBlocks(source)
        require(number in 1..region.entryCount) { "The regional Pokédex number is outside the native table." }
        require(decodedRecord.size == 0x30) { "A complete native Sword/Shield Pokédex record is required." }
        when (region) {
            SwordShieldDexRegion.GALAR -> require(blocks.galar.size == 0x4B00)
            SwordShieldDexRegion.ARMOR -> require(blocks.armor?.size == 0x2790) { "The Isle of Armor Pokédex is not allocated in this save." }
            SwordShieldDexRegion.CROWN -> require(blocks.crown?.size == 0x2760) { "The Crown Tundra Pokédex is not allocated in this save." }
        }

        val key = when (region) {
            SwordShieldDexRegion.GALAR -> 0x4716C404L
            SwordShieldDexRegion.ARMOR -> 0x3F936BA9L
            SwordShieldDexRegion.CROWN -> 0x3C9366F0L
        }
        val expectedLength = region.entryCount * 0x30
        val relativeOffset = (number - 1) * 0x30
        val end = source.size - 32
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()

        val cursor = Cursor(payload)
        var absoluteOffset = -1
        while (cursor.remaining > 0) {
            val blockKey = cursor.uint32()
            val stream = KeyStream(blockKey)
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
            if (blockKey == key) {
                require(type == 4 && length == expectedLength.toLong()) {
                    "The selected regional Pokédex block has the wrong native type or size."
                }
                repeat(relativeOffset) { stream.next() }
                repeat(decodedRecord.size) { byteIndex ->
                    payload[blockStart + relativeOffset + byteIndex] =
                        (decodedRecord[byteIndex].toInt() xor stream.next()).toByte()
                }
                absoluteOffset = blockStart + relativeOffset
            }
            cursor.skip(length.toInt())
        }
        require(absoluteOffset >= 0) { "The selected regional Pokédex block is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        SwordShieldPokedexReadOnly.read(edited)
        return SwordShieldDexWriteResult(edited, absoluteOffset)
    }

    /** Replaces one decoded native PC-box PK8 slot, then rebuilds encryption and the SHA-256 footer. */
    internal fun writeBoxRecord(
        source: ByteArray,
        box: Int,
        position: Int,
        record: ByteArray
    ): SwordShieldPokemonWriteResult {
        val blocks = readPokemonBlocks(source)
        require(box in 1..32 && position in 1..30) { "The native Sword/Shield box slot is outside PC storage." }
        require(record.size == SwordShieldPokemonReadOnly.RECORD_SIZE) { "A complete 344-byte native PK8 is required." }
        require(SwordShieldPokemonReadOnly.read(record) is SwordShieldSlotRead.Pokemon) {
            "The supplied native PK8 did not pass its stored-data checksum."
        }
        require(blocks.boxes.size == 0x50A00) { "The native Sword/Shield PC storage object has the wrong size." }
        val relativeOffset = ((box - 1) * 30 + position - 1) * SwordShieldPokemonReadOnly.RECORD_SIZE
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
            if (key == 0x0D66012CL) {
                require(type == 4 && length == 0x50A00L) {
                    "The native Sword/Shield PC storage object has the wrong type or size."
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
        require(absoluteOffset >= 0) { "The native Sword/Shield PC storage object is missing." }

        val edited = source.copyOf()
        for (index in payload.indices) edited[index] =
            (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro)
        digest.update(edited, 0, end)
        digest.update(outro)
        digest.digest().copyInto(edited, end)
        SwordShieldStorageReadOnly.read(edited)
        return SwordShieldPokemonWriteResult(edited, absoluteOffset)
    }

    private fun captureObjects(
        source: ByteArray,
        keys: Set<Long>,
        scalarLayouts: Map<Long, Pair<Int, Int>> = emptyMap(),
        objectLayouts: Map<Long, Int> = emptyMap()
    ): Pair<SwordShieldValidation.Verified, Map<Long, ByteArray>> {
        val captured = keys.associateWith { ByteArray(0) }.toMutableMap()
        val checked = inspect(source, captured, scalarLayouts = scalarLayouts, objectLayouts = objectLayouts)
        require(checked is SwordShieldValidation.Verified) { "A validated native Sword/Shield save is required." }
        return checked to captured
    }

    private fun inspect(
        source: ByteArray,
        captured: MutableMap<Long, ByteArray>?,
        dexLayouts: Map<Long, Int> = emptyMap(),
        inventoryLayout: Boolean = false,
        scalarLayouts: Map<Long, Pair<Int, Int>> = emptyMap(),
        objectLayouts: Map<Long, Int> = emptyMap()
    ): SwordShieldValidation {
        if (source.size !in knownSizes) return SwordShieldValidation.Unrecognized
        val end = source.size - 32
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(intro); digest.update(source, 0, end); digest.update(outro)
        if (!MessageDigest.isEqual(digest.digest(), source.copyOfRange(end, source.size)))
            return SwordShieldValidation.Invalid("The Sword/Shield container hash failed.")
        // Only the detached payload is decrypted. The caller's bytes are never changed.
        val payload = source.copyOfRange(0, end)
        for (index in payload.indices) payload[index] = (payload[index].toInt() xor pad[index % pad.size].toInt()).toByte()
        return try { inspectBlocks(payload, captured, dexLayouts, inventoryLayout, scalarLayouts, objectLayouts) }
        catch (e: IllegalArgumentException) { SwordShieldValidation.Invalid(e.message ?: "Malformed Sword/Shield blocks.") }
    }

    private fun inspectBlocks(
        bytes: ByteArray,
        captured: MutableMap<Long, ByteArray>?,
        dexLayouts: Map<Long, Int>,
        inventoryLayout: Boolean,
        scalarLayouts: Map<Long, Pair<Int, Int>>,
        objectLayouts: Map<Long, Int>
    ): SwordShieldValidation {
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
            val length: Long = when (type) {
                1, 2, 3 -> 0L
                4 -> cursor.uint32() xor stream.next32()
                5 -> {
                    val elements = cursor.uint32() xor stream.next32()
                    val subType = cursor.byte() xor stream.next()
                    elements * elementSize(subType)
                }
                else -> elementSize(type).toLong()
            }
            require(length <= cursor.remaining.toLong()) { "A native block length exceeds the remaining payload." }
            val expected = requiredObjects[key]
            if (expected != null) {
                require(type == 4 && length == expected.toLong()) { "A required Sword/Shield block has the wrong type or size." }
                found += key
            }
            // Dex-only native layout checks leave generic identity/trainer/Pokémon gates unchanged.
            dexLayouts[key]?.let { size -> require(type == 4 && length == size.toLong()) { "An optional Sword/Shield Pokédex block has the wrong native type or size." } }
            if (inventoryLayout && key == 0x1177C2C4L)
                require(type == 4 && length == 0x12F8L) { "The Sword/Shield inventory block has the wrong native type or size." }
            scalarLayouts[key]?.let { (expectedType, expectedSize) ->
                require(type == expectedType && length == expectedSize.toLong()) {
                    "A documented Sword/Shield scalar block has the wrong native type or size."
                }
            }
            objectLayouts[key]?.let { expectedSize ->
                require(type == 4 && length == expectedSize.toLong()) {
                    "A documented Sword/Shield object block has the wrong native type or size."
                }
            }
            val capture = captured?.containsKey(key) == true
            if (key == STATUS_KEY || capture) {
                // Consume the same stream used by the unaligned type/length prefix.
                val decoded = if (capture) ByteArray(length.toInt()) else null
                repeat(length.toInt()) { offset ->
                    val value = cursor.byte() xor stream.next()
                    if (key == STATUS_KEY && offset == 0xA4) gameId = value
                    if (decoded != null) decoded[offset] = value.toByte()
                }
                if (decoded != null) captured?.put(key, decoded)
            } else cursor.skip(length.toInt())
        }
        require(found.size == requiredObjects.size) { "Required Sword/Shield identity blocks are missing." }
        val version = SwordShieldVersion.values().firstOrNull { it.gameId == gameId }
            ?: throw IllegalArgumentException("The saved title ID is not Sword or Shield.")
        return SwordShieldValidation.Verified(version, count)
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

