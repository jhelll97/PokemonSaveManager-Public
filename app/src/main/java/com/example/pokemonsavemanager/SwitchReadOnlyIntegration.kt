package com.example.pokemonsavemanager

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Family selection is a browsing hint, never evidence of save identity. */
enum class SwitchGameFamily(val displayName: String, val generation: Int) {
    LETS_GO_PIKACHU_EEVEE("Let's Go Pikachu / Eevee", 7),
    SWORD_SHIELD("Sword / Shield", 8),
    BRILLIANT_DIAMOND_SHINING_PEARL("Brilliant Diamond / Shining Pearl", 8),
    LEGENDS_ARCEUS("Legends: Arceus", 8),
    SCARLET_VIOLET("Scarlet / Violet", 9),
    LEGENDS_ZA("Legends: Z-A", 9)
}

enum class SwitchReadCapability {
    SAVE_IDENTITY, TRAINER_PROFILE, PARTY, PC_OR_BOXES, PASTURES,
    POKEMON_DETAILS, POKEDEX, POKEDEX_AND_PROGRESS, PSE_BANK_READ_ONLY, SAFE_BACKUPS, INVENTORY
}

/** Implemented readers are separate from planned sections and per-file validation. */
class SwitchCapabilityProfile internal constructor(val family: SwitchGameFamily) {
    val writesEnabled: Boolean get() = false
    val plannedCapabilities: Set<SwitchReadCapability> get() = setOf(
        SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.TRAINER_PROFILE,
        SwitchReadCapability.PARTY,
        if (family == SwitchGameFamily.LEGENDS_ARCEUS) SwitchReadCapability.PASTURES
        else SwitchReadCapability.PC_OR_BOXES,
        SwitchReadCapability.POKEMON_DETAILS, SwitchReadCapability.POKEDEX, SwitchReadCapability.POKEDEX_AND_PROGRESS,
        SwitchReadCapability.PSE_BANK_READ_ONLY, SwitchReadCapability.SAFE_BACKUPS, SwitchReadCapability.INVENTORY
    )
    val capabilities: Set<SwitchReadCapability> get() =
        if (family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE) setOf(SwitchReadCapability.SAVE_IDENTITY,
            SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS,
            SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY, SwitchReadCapability.SAFE_BACKUPS)
        else if (family == SwitchGameFamily.SWORD_SHIELD) setOf(SwitchReadCapability.SAVE_IDENTITY,
            SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS,
            SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY, SwitchReadCapability.SAFE_BACKUPS)
        else if (family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL) setOf(SwitchReadCapability.SAVE_IDENTITY,
            SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS,
            SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY, SwitchReadCapability.SAFE_BACKUPS)
        else if (family == SwitchGameFamily.LEGENDS_ARCEUS) setOf(SwitchReadCapability.SAVE_IDENTITY,
            SwitchReadCapability.PARTY, SwitchReadCapability.PASTURES, SwitchReadCapability.POKEMON_DETAILS,
            SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY, SwitchReadCapability.SAFE_BACKUPS)
        else if (family == SwitchGameFamily.SCARLET_VIOLET) setOf(SwitchReadCapability.SAVE_IDENTITY,
            SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS,
            SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY, SwitchReadCapability.SAFE_BACKUPS)
        else if (family == SwitchGameFamily.LEGENDS_ZA) setOf(SwitchReadCapability.SAVE_IDENTITY,
            SwitchReadCapability.PARTY, SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS,
            SwitchReadCapability.TRAINER_PROFILE, SwitchReadCapability.POKEDEX, SwitchReadCapability.INVENTORY, SwitchReadCapability.SAFE_BACKUPS)
        else emptySet()
    fun supports(capability: SwitchReadCapability): Boolean = capability in capabilities
}

object SwitchReadOnlyCatalog {
    val profiles: List<SwitchCapabilityProfile> get() =
        SwitchGameFamily.values().map { SwitchCapabilityProfile(it) }
    fun profileFor(family: SwitchGameFamily): SwitchCapabilityProfile = SwitchCapabilityProfile(family)
}

/** Detached, in-memory bytes only. This does not establish format or checksum validity. */
class SwitchReadSnapshot private constructor(
    val family: SwitchGameFamily,
    private val sourceBytes: ByteArray
) {
    val byteCount: Int get() = sourceBytes.size
    val sourceSha256: String = MessageDigest.getInstance("SHA-256").digest(sourceBytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    fun bytesCopy(): ByteArray = sourceBytes.copyOf()
    internal fun inspectLetsGo(): LetsGoValidation = LetsGoSaveValidation.inspect(sourceBytes)
    internal fun inspectSwordShield(): SwordShieldValidation = SwordShieldSaveValidation.inspect(sourceBytes)
    internal fun inspectBdsp(): BdspValidation = BdspSaveValidation.inspect(sourceBytes)
    internal fun inspectLegendsZa(): LegendsZaValidation = LegendsZaSaveValidation.inspect(sourceBytes)
    internal fun inspectScarletViolet(): ScarletVioletValidation = ScarletVioletSaveValidation.inspect(sourceBytes)
    internal fun inspectLegendsArceus(): LegendsArceusValidation = LegendsArceusSaveValidation.inspect(sourceBytes)
    internal fun readLetsGoInventory(): LetsGoInventoryRead = LetsGoInventoryReadOnly.read(sourceBytes)
    internal fun readLetsGoPokedex(): LetsGoPokedexRead = LetsGoPokedexReadOnly.read(sourceBytes)
    internal fun readLetsGoDayCare(): LetsGoDayCareRead = LetsGoDayCareReadOnly.read(sourceBytes)
    internal fun readLetsGoCaptureRecords(): LetsGoCaptureRecordsRead = LetsGoCaptureRecordsReadOnly.read(sourceBytes)
    internal fun readLetsGoGoPark(): LetsGoGoParkRead = LetsGoGoParkReadOnly.read(sourceBytes)
    internal fun readLetsGoPlayerLocation(): LetsGoPlayerLocationRead = LetsGoPlayerLocationReadOnly.read(sourceBytes)
    internal fun readLetsGoAdventureBegin(): LetsGoAdventureBeginRead = LetsGoAdventureBeginReadOnly.read(sourceBytes)
    internal fun readLetsGoSecureValues(): LetsGoSecureValueRead = LetsGoSecureValueReadOnly.read(sourceBytes)
    internal fun readLetsGoGameSyncIdentity(): LetsGoGameSyncIdentityRead =
        LetsGoGameSyncIdentityReadOnly.read(sourceBytes)
    internal fun readLetsGoEquippedAppearance(): LetsGoEquippedAppearanceRead =
        LetsGoEquippedAppearanceReadOnly.read(sourceBytes)
    internal fun readLetsGoBlockDirectory(): LetsGoBlockDirectoryRead = LetsGoBlockDirectoryReadOnly.read(sourceBytes)
    internal fun readLetsGoMasterTrainerTitles(): LetsGoMasterTrainerTitlesRead =
        LetsGoMasterTrainerTitlesReadOnly.read(sourceBytes)
    internal fun readLetsGoEventWork(): LetsGoEventWorkRead = LetsGoEventWorkReadOnly.read(sourceBytes)
    internal fun readLetsGoMysteryGiftHistory(): LetsGoMysteryGiftHistoryRead =
        LetsGoMysteryGiftHistoryReadOnly.read(sourceBytes)
    internal fun readLetsGoPlayerAccessories(): LetsGoPlayerAccessoryRead = LetsGoPlayerAccessoryEditing.read(sourceBytes)
    internal fun readLetsGoStarterAccessories(): LetsGoPlayerAccessoryRead =
        LetsGoPlayerAccessoryEditing.read(sourceBytes, LetsGoAccessoryTarget.STARTER)
    internal fun readLetsGoGameOptions(): LetsGoGameOptionsRead = LetsGoGameOptionsReadOnly.read(sourceBytes)
    internal fun readLetsGoLastSaved(): LetsGoLastSavedRead = LetsGoLastSavedReadOnly.read(sourceBytes)
    internal fun readLetsGoPlayTime(): LetsGoPlayTimeRead = LetsGoPlayTimeReadOnly.read(sourceBytes)
    internal fun readLetsGoTrainer(): LetsGoTrainerRead = LetsGoTrainerReadOnly.read(sourceBytes)
    internal fun readLetsGoStorage(): LetsGoStorageRead = LetsGoStorageReadOnly.read(sourceBytes)
    internal fun readLetsGoRecord(index: Int): ByteArray {
        require(inspectLetsGo() is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
        val storage = readLetsGoStorage()
        require(index in 0 until storage.count) { "Let's Go storage index is out of range." }
        val start = 0x5C00 + index * LetsGoPokemonReadOnly.RECORD_SIZE
        return sourceBytes.copyOfRange(start, start + LetsGoPokemonReadOnly.RECORD_SIZE).also { record ->
            require(LetsGoPokemonReadOnly.read(record) is LetsGoSlotRead.Pokemon) {
                "Only a checksum-valid native Pokémon record can be added to Bank."
            }
        }
    }
    internal fun readSwordShieldInventory(): SwordShieldInventoryRead = SwordShieldInventoryReadOnly.read(sourceBytes)
    internal fun readSwordShieldPlayTime(): SwordShieldPlayTimeRead = SwordShieldPlayTimeReadOnly.read(sourceBytes)
    internal fun readSwordShieldPokedex(): SwordShieldPokedexRead = SwordShieldPokedexReadOnly.read(sourceBytes)
    internal fun readSwordShieldStorage(): SwordShieldStorageRead = SwordShieldStorageReadOnly.read(sourceBytes)
    internal fun readSwordShieldRecord(location: String): ByteArray {
        require(inspectSwordShield() is SwordShieldValidation.Verified) { "A validated native Sword/Shield save is required." }
        val blocks = SwordShieldSaveValidation.readPokemonBlocks(sourceBytes)
        val size = SwordShieldPokemonReadOnly.RECORD_SIZE
        val match = Regex("^(Box|Party) ([0-9]+) • Slot ([0-9]+)$").matchEntire(location)
            ?: throw IllegalArgumentException("Choose a Pokémon directly from its native box or party slot.")
        val area = match.groupValues[1]
        val first = match.groupValues[2].toInt()
        val slot = match.groupValues[3].toInt() - 1
        return if (area == "Box") {
            require(first in 1..32 && slot in 0..29) { "Sword/Shield box slot is out of range." }
            val start = ((first - 1) * 30 + slot) * size
            blocks.boxes.copyOfRange(start, start + size)
        } else {
            require(first == 1 && slot in 0..5) { "Sword/Shield party slot is out of range." }
            val count = blocks.party[6 * size].toInt() and 255
            require(slot < count) { "That party slot is not occupied." }
            val start = slot * size
            blocks.party.copyOfRange(start, start + size)
        }.also { record ->
            require(SwordShieldPokemonReadOnly.read(record) is SwordShieldSlotRead.Pokemon) {
                "Only a checksum-valid native Pokémon record can be added to Bank."
            }
        }
    }
    internal fun readSwordShieldTrainer(): SwordShieldTrainerRead = SwordShieldTrainerReadOnly.read(sourceBytes)
    internal fun readSwordShieldBattleTower(): SwordShieldBattleTowerRead =
        SwordShieldBattleTowerReadOnly.read(sourceBytes)
    internal fun readSwordShieldDaycare(): SwordShieldDaycareRead =
        SwordShieldDaycareReadOnly.read(sourceBytes)
    internal fun readSwordShieldBoxLayout(): SwordShieldBoxLayoutRead =
        SwordShieldBoxLayoutReadOnly.read(sourceBytes)
    internal fun readSwordShieldRallyDigging(): SwordShieldRallyDiggingRead =
        SwordShieldRallyDiggingReadOnly.read(sourceBytes)
    internal fun readSwordShieldStarterChoice(): SwordShieldStarterChoiceRead =
        SwordShieldStarterChoiceReadOnly.read(sourceBytes)
    internal fun readSwordShieldLeagueCard(): SwordShieldLeagueCardRead =
        SwordShieldLeagueCardReadOnly.read(sourceBytes)
    internal fun readSwordShieldIsleOfArmorProgress(): SwordShieldIsleOfArmorProgressRead =
        SwordShieldIsleOfArmorProgressReadOnly.read(sourceBytes)
    internal fun readSwordShieldCrownTundraProgress(): SwordShieldCrownTundraProgressRead =
        SwordShieldCrownTundraProgressReadOnly.read(sourceBytes)
    internal fun readSwordShieldStarTournament(): SwordShieldStarTournamentRead =
        SwordShieldStarTournamentReadOnly.read(sourceBytes)
    internal fun readSwordShieldSwordsOfJustice(): SwordShieldSwordsOfJusticeRead =
        SwordShieldSwordsOfJusticeReadOnly.read(sourceBytes)
    internal fun readSwordShieldAdventureSteps(): SwordShieldAdventureStepsRead =
        SwordShieldAdventureStepsReadOnly.read(sourceBytes)
    internal fun readSwordShieldHallOfFameTime(): SwordShieldHallOfFameTimeRead =
        SwordShieldHallOfFameTimeReadOnly.read(sourceBytes)
    internal fun readSwordShieldPokedexViewState(): SwordShieldPokedexViewStateRead =
        SwordShieldPokedexViewStateReadOnly.read(sourceBytes)
    internal fun readSwordShieldRestrictedSparring(): SwordShieldRestrictedSparringRead =
        SwordShieldRestrictedSparringReadOnly.read(sourceBytes)
    internal fun readBdspMenuSelection(): BdspMenuSelectionRead = BdspMenuSelectionReadOnly.read(sourceBytes)
    internal fun readBdspRandomGroups(): BdspRandomGroupsRead = BdspRandomGroupsReadOnly.read(sourceBytes)
    internal fun readBdspUnionRoom(): BdspUnionRoomRead = BdspUnionRoomReadOnly.read(sourceBytes)
    internal fun readBdspContestPhotoLanguages(): BdspContestPhotoLanguagesRead = BdspContestPhotoLanguagesReadOnly.read(sourceBytes)
    internal fun readBdspContest(): BdspContestRead = BdspContestReadOnly.read(sourceBytes)
    internal fun readBdspBallCapsules(): BdspBallCapsulesRead = BdspBallCapsulesReadOnly.read(sourceBytes)
    internal fun readBdspSealStickers(): BdspSealStickersRead = BdspSealStickersReadOnly.read(sourceBytes)
    internal fun readBdspUndergroundReturn(): BdspUndergroundReturnRead = BdspUndergroundReturnReadOnly.read(sourceBytes)
    internal fun readBdspUndergroundNpc(): BdspUndergroundNpcRead = BdspUndergroundNpcReadOnly.read(sourceBytes)
    internal fun readBdspSecretBase(): BdspSecretBaseRead = BdspSecretBaseReadOnly.read(sourceBytes)
    internal fun readBdspBerryPlots(): BdspBerryPlotsRead = BdspBerryPlotsReadOnly.read(sourceBytes)
    internal fun readBdspEncounter(): BdspEncounterRead = BdspEncounterReadOnly.read(sourceBytes)
    internal fun readBdspHoneyTrees(): BdspHoneyTreesRead = BdspHoneyTreesReadOnly.read(sourceBytes)
    internal fun readBdspBattleTrainerStatus(): BdspBattleTrainerStatusRead = BdspBattleTrainerStatusReadOnly.read(sourceBytes)
    internal fun readBdspAdditionalRecords(): BdspAdditionalRecordsRead = BdspAdditionalRecordsReadOnly.read(sourceBytes)
    internal fun readBdspRecordStatistics(): BdspRecordStatisticsRead = BdspRecordStatisticsReadOnly.read(sourceBytes)
    internal fun readBdspUndergroundActivity(): BdspUndergroundActivityRead = BdspUndergroundActivityReadOnly.read(sourceBytes)
    internal fun readBdspGameOptions(): BdspGameOptionsRead = BdspGameOptionsReadOnly.read(sourceBytes)
    internal fun readBdspPlayTime(): BdspPlayTimeRead = BdspPlayTimeReadOnly.read(sourceBytes)
    internal fun readBdspTimestamps(): BdspTimestampsRead = BdspTimestampsReadOnly.read(sourceBytes)
    internal fun readBdspTrainerAppearance(): BdspTrainerAppearanceRead = BdspTrainerAppearanceReadOnly.read(sourceBytes)
    internal fun readBdspPoffins(): BdspPoffinCaseRead = BdspPoffinReadOnly.read(sourceBytes)
    internal fun readBdspBattleTower(): BdspBattleTowerRead = BdspBattleTowerReadOnly.read(sourceBytes)
    internal fun readBdspNursery(): BdspNurseryRead = BdspNurseryReadOnly.read(sourceBytes)
    internal fun readBdspUnderground(): BdspUndergroundRead = BdspUndergroundReadOnly.read(sourceBytes)
    internal fun readBdspInventory(): BdspInventoryRead = BdspInventoryReadOnly.read(sourceBytes)
    internal fun readBdspPokedex(): BdspPokedexRead = BdspPokedexReadOnly.read(sourceBytes)
    internal fun readBdspTrainer(): BdspTrainerRead = BdspTrainerReadOnly.read(sourceBytes)
    internal fun readBdspStorage(): BdspStorageRead = BdspStorageReadOnly.read(sourceBytes)
    internal fun readBdspRecord(location: String): ByteArray {
        require(inspectBdsp() is BdspValidation.Verified) { "A validated native BDSP save is required." }
        readBdspStorage()
        val size = BdspPokemonReadOnly.RECORD_SIZE
        val box = Regex("^Box ([0-9]+) • Slot ([0-9]+)$").matchEntire(location)
        val party = Regex("^Party ([0-9]+)$").matchEntire(location)
        return when {
            box != null -> {
                val boxIndex = box.groupValues[1].toInt() - 1
                val slot = box.groupValues[2].toInt() - 1
                require(boxIndex in 0 until 40 && slot in 0 until 30) { "BDSP box slot is out of range." }
                val start = 0x14EF4 + (boxIndex * 30 + slot) * size
                sourceBytes.copyOfRange(start, start + size)
            }
            party != null -> {
                val slot = party.groupValues[1].toInt() - 1
                require(slot in 0 until 6) { "BDSP party slot is out of range." }
                val partyOffset = 0x14098
                val count = sourceBytes[partyOffset + 6 * size].toInt() and 255
                require(slot < count) { "That party slot is not occupied." }
                val start = partyOffset + slot * size
                sourceBytes.copyOfRange(start, start + size)
            }
            else -> throw IllegalArgumentException("Choose a Pokémon directly from its native BDSP box or party slot.")
        }.also { record ->
            require(BdspPokemonReadOnly.read(record) is BdspSlotRead.Pokemon) {
                "Only a checksum-valid native Pokémon record can be added to Bank."
            }
        }
    }

    internal fun readLegendsArceusInventory(): LegendsArceusInventorySnapshot = LegendsArceusInventoryReadOnly.read(sourceBytes)
    internal fun readLegendsArceusPokedex(): LegendsArceusPokedexRead = LegendsArceusPokedexReadOnly.read(sourceBytes)
    internal fun readLegendsArceusTrainer(): LegendsArceusTrainerRead = LegendsArceusTrainerReadOnly.read(sourceBytes)
    internal fun readLegendsArceusStorage(): LegendsArceusStorageRead = LegendsArceusStorageReadOnly.read(sourceBytes)
    internal fun readLegendsArceusRecord(location: String): ByteArray {
        require(inspectLegendsArceus() is LegendsArceusValidation.Verified) {
            "A validated native Legends: Arceus save is required."
        }
        readLegendsArceusStorage()
        val blocks = LegendsArceusSaveValidation.readPokemonBlocks(sourceBytes)
        val pasture = Regex("^Pasture ([0-9]+) • Slot ([0-9]+)$").matchEntire(location)
        val party = Regex("^Party ([0-9]+)$").matchEntire(location)
        return when {
            pasture != null -> {
                val pastureIndex = pasture.groupValues[1].toInt() - 1
                val slot = pasture.groupValues[2].toInt() - 1
                require(pastureIndex in 0 until 32 && slot in 0 until 30) {
                    "Legends: Arceus pasture slot is out of range."
                }
                val size = LegendsArceusPokemonReadOnly.STORED_SIZE
                val start = (pastureIndex * 30 + slot) * size
                blocks.pastures.copyOfRange(start, start + size)
            }
            party != null -> {
                val slot = party.groupValues[1].toInt() - 1
                require(slot in 0 until 6) { "Legends: Arceus party slot is out of range." }
                val size = LegendsArceusPokemonReadOnly.PARTY_SIZE
                val count = blocks.party[6 * size].toInt() and 255
                require(slot < count) { "That party slot is not occupied." }
                val start = slot * size
                blocks.party.copyOfRange(start, start + size)
            }
            else -> throw IllegalArgumentException(
                "Choose a Pokémon directly from its native pasture or party slot."
            )
        }.also { record ->
            require(LegendsArceusPokemonReadOnly.read(record) is LegendsArceusSlotRead.Pokemon) {
                "Only a checksum-valid native Pokémon record can be added to Bank."
            }
        }
    }

    internal fun readScarletVioletInventory(): ScarletVioletInventoryRead = ScarletVioletInventoryReadOnly.read(sourceBytes)
    internal fun readScarletVioletPokedex(): ScarletVioletPokedexRead = ScarletVioletPokedexReadOnly.read(sourceBytes)
    internal fun readScarletVioletTrainer(): ScarletVioletTrainerRead = ScarletVioletTrainerReadOnly.read(sourceBytes)
    internal fun readScarletVioletStorage(): ScarletVioletStorageRead = ScarletVioletStorageReadOnly.read(sourceBytes)

    internal fun readScarletVioletRecord(location: String): ByteArray {
        require(inspectScarletViolet() is ScarletVioletValidation.Verified) {
            "A validated native Scarlet/Violet save is required."
        }
        readScarletVioletStorage()
        val blocks = ScarletVioletSaveValidation.readPokemonBlocks(sourceBytes)
        val box = Regex("^Box ([0-9]+) • Slot ([0-9]+)$").matchEntire(location)
        val party = Regex("^Party ([0-9]+)$").matchEntire(location)
        val size = ScarletVioletPokemonReadOnly.RECORD_SIZE
        return when {
            box != null -> {
                val boxIndex = box.groupValues[1].toInt() - 1
                val slot = box.groupValues[2].toInt() - 1
                require(boxIndex in 0 until 32 && slot in 0 until 30) {
                    "Scarlet/Violet box slot is out of range."
                }
                val start = (boxIndex * 30 + slot) * size
                blocks.boxes.copyOfRange(start, start + size)
            }
            party != null -> {
                val slot = party.groupValues[1].toInt() - 1
                require(slot in 0 until 6) { "Scarlet/Violet party slot is out of range." }
                val count = blocks.party[6 * size].toInt() and 255
                require(slot < count) { "That party slot is not occupied." }
                val start = slot * size
                blocks.party.copyOfRange(start, start + size)
            }
            location == "Ride Pokémon" -> {
                val start = 960 * size
                blocks.boxes.copyOfRange(start, start + size)
            }
            else -> throw IllegalArgumentException(
                "Choose a Pokémon directly from its native Scarlet/Violet box, party, or ride slot."
            )
        }.also { record ->
            require(ScarletVioletPokemonReadOnly.read(record) is ScarletVioletSlotRead.Pokemon) {
                "Only a checksum-valid native Pokémon record can be added to Bank."
            }
        }
    }
    internal fun readLegendsZaInventory(): LegendsZaInventoryRead = LegendsZaInventoryReadOnly.read(sourceBytes)
    internal fun readLegendsZaPokedex(): LegendsZaPokedexRead = LegendsZaPokedexReadOnly.read(sourceBytes)
    internal fun readLegendsZaTrainer(): LegendsZaTrainerRead = LegendsZaTrainerReadOnly.read(sourceBytes)
    internal fun readLegendsZaStorage(): LegendsZaStorageRead = LegendsZaStorageReadOnly.read(sourceBytes)

    internal fun readLegendsZaRecord(location: String): ByteArray {
        require(inspectLegendsZa() is LegendsZaValidation.Verified) {
            "A validated native Legends: Z-A save is required."
        }
        val storage = readLegendsZaStorage()
        val blocks = LegendsZaSaveValidation.readPokemonBlocks(sourceBytes)
        val box = Regex("^Box ([0-9]+) • Slot ([0-9]+)$").matchEntire(location)
        val party = Regex("^Party ([0-9]+)$").matchEntire(location)
        val size = LegendsZaPokemonReadOnly.RECORD_SIZE
        return when {
            box != null -> {
                val boxIndex = box.groupValues[1].toInt() - 1
                val slot = box.groupValues[2].toInt() - 1
                require(boxIndex in 0 until 32 && slot in 0 until 30) {
                    "Legends: Z-A box slot is out of range."
                }
                val start = (boxIndex * 30 + slot) * LegendsZaStorageReadOnly.BOX_STRIDE
                blocks.boxes.copyOfRange(start, start + size)
            }
            party != null -> {
                val slot = party.groupValues[1].toInt() - 1
                require(slot in 0 until 6) { "Legends: Z-A party slot is out of range." }
                require(slot < storage.partyCount) { "That party slot is not occupied." }
                val start = slot * LegendsZaStorageReadOnly.PARTY_STRIDE
                blocks.party.copyOfRange(start, start + size)
            }
            else -> throw IllegalArgumentException(
                "Choose a Pokémon directly from its native Legends: Z-A box or party slot."
            )
        }.also { record ->
            require(LegendsZaPokemonReadOnly.read(record) is LegendsZaSlotRead.Pokemon) {
                "Only a checksum-valid native Pokémon record can be added to Bank."
            }
        }
    }
    companion object {
        // An intake resource limit, not a native format size or detection rule.
        const val MAX_SAVE_BYTES = 16 * 1024 * 1024

        fun capture(family: SwitchGameFamily, bytes: ByteArray): SwitchReadSnapshot {
            require(bytes.isNotEmpty()) { "The selected file is empty." }
            require(bytes.size <= MAX_SAVE_BYTES) { "The selected file exceeds the 16 MiB intake limit." }
            return SwitchReadSnapshot(family, bytes.copyOf())
        }

        /** Owns and closes the stream on success, malformed input and I/O failure. */
        fun readFrom(family: SwitchGameFamily, input: InputStream): SwitchReadSnapshot = input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = it.read(buffer, 0, minOf(buffer.size, MAX_SAVE_BYTES + 1 - output.size()))
                if (count < 0) break
                if (count == 0) {
                    val single = it.read()
                    if (single < 0) break
                    output.write(single)
                } else output.write(buffer, 0, count)
                require(output.size() <= MAX_SAVE_BYTES) { "The selected file exceeds the 16 MiB intake limit." }
            }
            val bytes = output.toByteArray()
            require(bytes.isNotEmpty()) { "The selected file is empty." }
            SwitchReadSnapshot(family, bytes)
        }
    }
}

class SwitchReadSession(val snapshot: SwitchReadSnapshot) {
    // Known-size native formats precede the Let's Go footer sniff: arbitrary payload
    // bytes must not accidentally be mistaken for a different game's marker.
    val swordShieldValidation: SwordShieldValidation = snapshot.inspectSwordShield()
    val bdspValidation: BdspValidation = if (swordShieldValidation is SwordShieldValidation.Unrecognized)
        snapshot.inspectBdsp() else BdspValidation.Unrecognized
    val legendsArceusValidation: LegendsArceusValidation = if (swordShieldValidation is SwordShieldValidation.Unrecognized &&
        bdspValidation is BdspValidation.Unrecognized) snapshot.inspectLegendsArceus() else LegendsArceusValidation.Unrecognized
    val scarletVioletValidation: ScarletVioletValidation = if (swordShieldValidation is SwordShieldValidation.Unrecognized &&
        bdspValidation is BdspValidation.Unrecognized && legendsArceusValidation is LegendsArceusValidation.Unrecognized)
        snapshot.inspectScarletViolet() else ScarletVioletValidation.Unrecognized
    val legendsZaValidation: LegendsZaValidation = if (swordShieldValidation is SwordShieldValidation.Unrecognized &&
        bdspValidation is BdspValidation.Unrecognized && legendsArceusValidation is LegendsArceusValidation.Unrecognized &&
        scarletVioletValidation is ScarletVioletValidation.Unrecognized) snapshot.inspectLegendsZa() else LegendsZaValidation.Unrecognized
    val validation: LetsGoValidation = if (swordShieldValidation is SwordShieldValidation.Unrecognized &&
        bdspValidation is BdspValidation.Unrecognized && legendsArceusValidation is LegendsArceusValidation.Unrecognized &&
        scarletVioletValidation is ScarletVioletValidation.Unrecognized && legendsZaValidation is LegendsZaValidation.Unrecognized)
        snapshot.inspectLetsGo() else LetsGoValidation.Unrecognized
    val profile: SwitchCapabilityProfile = SwitchReadOnlyCatalog.profileFor(
        if (swordShieldValidation is SwordShieldValidation.Verified) SwitchGameFamily.SWORD_SHIELD
        else if (bdspValidation is BdspValidation.Verified) SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL
        else if (legendsArceusValidation is LegendsArceusValidation.Verified) SwitchGameFamily.LEGENDS_ARCEUS
        else if (scarletVioletValidation is ScarletVioletValidation.Verified) SwitchGameFamily.SCARLET_VIOLET
        else if (legendsZaValidation is LegendsZaValidation.Verified) SwitchGameFamily.LEGENDS_ZA
        else if (validation is LetsGoValidation.Verified) SwitchGameFamily.LETS_GO_PIKACHU_EEVEE
        else snapshot.family
    )
    val displayName: String get() = (validation as? LetsGoValidation.Verified)?.version?.displayName
        ?: (swordShieldValidation as? SwordShieldValidation.Verified)?.version?.displayName
        ?: (bdspValidation as? BdspValidation.Verified)?.version?.displayName
        ?: (legendsArceusValidation as? LegendsArceusValidation.Verified)?.version?.displayName
        ?: (scarletVioletValidation as? ScarletVioletValidation.Verified)?.version?.displayName
        ?: (legendsZaValidation as? LegendsZaValidation.Verified)?.version?.displayName
        ?: profile.family.displayName
    val isReadOnly: Boolean get() = true
    val isFormatVerified: Boolean get() = validation is LetsGoValidation.Verified ||
        swordShieldValidation is SwordShieldValidation.Verified || bdspValidation is BdspValidation.Verified ||
        legendsArceusValidation is LegendsArceusValidation.Verified || scarletVioletValidation is ScarletVioletValidation.Verified ||
        legendsZaValidation is LegendsZaValidation.Verified
    private val storageAttempt = if (validation is LetsGoValidation.Verified) runCatching { snapshot.readLetsGoStorage() } else null
    private val swordShieldStorageAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldStorage() } else null
    private val bdspMenuSelectionAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspMenuSelection() } else null
    val bdspMenuSelection: BdspMenuSelectionRead? get() = bdspMenuSelectionAttempt?.getOrNull()
    val bdspMenuSelectionUnavailableReason: String? get() = bdspMenuSelectionAttempt?.exceptionOrNull()?.message
    private val bdspRandomGroupsAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspRandomGroups() } else null
    val bdspRandomGroups: BdspRandomGroupsRead? get() = bdspRandomGroupsAttempt?.getOrNull()
    val bdspRandomGroupsUnavailableReason: String? get() = bdspRandomGroupsAttempt?.exceptionOrNull()?.message
    private val bdspUnionRoomAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspUnionRoom() } else null
    val bdspUnionRoom: BdspUnionRoomRead? get() = bdspUnionRoomAttempt?.getOrNull()
    val bdspUnionRoomUnavailableReason: String? get() = bdspUnionRoomAttempt?.exceptionOrNull()?.message
    private val bdspContestPhotoLanguagesAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspContestPhotoLanguages() } else null
    val bdspContestPhotoLanguages: BdspContestPhotoLanguagesRead? get() = bdspContestPhotoLanguagesAttempt?.getOrNull()
    val bdspContestPhotoLanguagesUnavailableReason: String? get() = bdspContestPhotoLanguagesAttempt?.exceptionOrNull()?.message
    private val bdspContestAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspContest() } else null
    val bdspContest: BdspContestRead? get() = bdspContestAttempt?.getOrNull()
    val bdspContestUnavailableReason: String? get() = bdspContestAttempt?.exceptionOrNull()?.message
    private val bdspBallCapsulesAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspBallCapsules() } else null
    val bdspBallCapsules: BdspBallCapsulesRead? get() = bdspBallCapsulesAttempt?.getOrNull()
    val bdspBallCapsulesUnavailableReason: String? get() = bdspBallCapsulesAttempt?.exceptionOrNull()?.message
    private val bdspSealStickersAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspSealStickers() } else null
    val bdspSealStickers: BdspSealStickersRead? get() = bdspSealStickersAttempt?.getOrNull()
    val bdspSealStickersUnavailableReason: String? get() = bdspSealStickersAttempt?.exceptionOrNull()?.message
    private val bdspUndergroundReturnAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspUndergroundReturn() } else null
    val bdspUndergroundReturn: BdspUndergroundReturnRead? get() = bdspUndergroundReturnAttempt?.getOrNull()
    val bdspUndergroundReturnUnavailableReason: String? get() = bdspUndergroundReturnAttempt?.exceptionOrNull()?.message
    private val bdspUndergroundNpcAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspUndergroundNpc() } else null
    val bdspUndergroundNpc: BdspUndergroundNpcRead? get() = bdspUndergroundNpcAttempt?.getOrNull()
    val bdspUndergroundNpcUnavailableReason: String? get() = bdspUndergroundNpcAttempt?.exceptionOrNull()?.message
    private val bdspSecretBaseAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspSecretBase() } else null
    val bdspSecretBase: BdspSecretBaseRead? get() = bdspSecretBaseAttempt?.getOrNull()
    val bdspSecretBaseUnavailableReason: String? get() = bdspSecretBaseAttempt?.exceptionOrNull()?.message
    private val bdspBerryPlotsAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspBerryPlots() } else null
    val bdspBerryPlots: BdspBerryPlotsRead? get() = bdspBerryPlotsAttempt?.getOrNull()
    val bdspBerryPlotsUnavailableReason: String? get() = bdspBerryPlotsAttempt?.exceptionOrNull()?.message
    private val bdspEncounterAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspEncounter() } else null
    val bdspEncounter: BdspEncounterRead? get() = bdspEncounterAttempt?.getOrNull()
    val bdspEncounterUnavailableReason: String? get() = bdspEncounterAttempt?.exceptionOrNull()?.message
    private val bdspHoneyTreesAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspHoneyTrees() } else null
    val bdspHoneyTrees: BdspHoneyTreesRead? get() = bdspHoneyTreesAttempt?.getOrNull()
    val bdspHoneyTreesUnavailableReason: String? get() = bdspHoneyTreesAttempt?.exceptionOrNull()?.message
    private val bdspBattleTrainerStatusAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspBattleTrainerStatus() } else null
    val bdspBattleTrainerStatus: BdspBattleTrainerStatusRead? get() = bdspBattleTrainerStatusAttempt?.getOrNull()
    val bdspBattleTrainerStatusUnavailableReason: String? get() = bdspBattleTrainerStatusAttempt?.exceptionOrNull()?.message
    private val bdspAdditionalRecordsAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspAdditionalRecords() } else null
    val bdspAdditionalRecords: BdspAdditionalRecordsRead? get() = bdspAdditionalRecordsAttempt?.getOrNull()
    val bdspAdditionalRecordsUnavailableReason: String? get() = bdspAdditionalRecordsAttempt?.exceptionOrNull()?.message
    private val bdspRecordStatisticsAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspRecordStatistics() } else null
    val bdspRecordStatistics: BdspRecordStatisticsRead? get() = bdspRecordStatisticsAttempt?.getOrNull()
    val bdspRecordStatisticsUnavailableReason: String? get() = bdspRecordStatisticsAttempt?.exceptionOrNull()?.message
    private val bdspUndergroundActivityAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspUndergroundActivity() } else null
    val bdspUndergroundActivity: BdspUndergroundActivityRead? get() = bdspUndergroundActivityAttempt?.getOrNull()
    val bdspUndergroundActivityUnavailableReason: String? get() = bdspUndergroundActivityAttempt?.exceptionOrNull()?.message
    private val bdspGameOptionsAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspGameOptions() } else null
    val bdspGameOptions: BdspGameOptionsRead? get() = bdspGameOptionsAttempt?.getOrNull()
    val bdspGameOptionsUnavailableReason: String? get() = bdspGameOptionsAttempt?.exceptionOrNull()?.message
    private val bdspPlayTimeAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspPlayTime() } else null
    val bdspPlayTime: BdspPlayTimeRead? get() = bdspPlayTimeAttempt?.getOrNull()
    val bdspPlayTimeUnavailableReason: String? get() = bdspPlayTimeAttempt?.exceptionOrNull()?.message
    private val bdspTimestampsAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspTimestamps() } else null
    val bdspTimestamps: BdspTimestampsRead? get() = bdspTimestampsAttempt?.getOrNull()
    val bdspTimestampsUnavailableReason: String? get() = bdspTimestampsAttempt?.exceptionOrNull()?.message
    private val bdspTrainerAppearanceAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspTrainerAppearance() } else null
    val bdspTrainerAppearance: BdspTrainerAppearanceRead? get() = bdspTrainerAppearanceAttempt?.getOrNull()
    val bdspTrainerAppearanceUnavailableReason: String? get() = bdspTrainerAppearanceAttempt?.exceptionOrNull()?.message
    private val bdspPoffinsAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspPoffins() } else null
    val bdspPoffins: BdspPoffinCaseRead? get() = bdspPoffinsAttempt?.getOrNull()
    val bdspPoffinsUnavailableReason: String? get() = bdspPoffinsAttempt?.exceptionOrNull()?.message
    private val bdspBattleTowerAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspBattleTower() } else null
    val bdspBattleTower: BdspBattleTowerRead? get() = bdspBattleTowerAttempt?.getOrNull()
    val bdspBattleTowerUnavailableReason: String? get() = bdspBattleTowerAttempt?.exceptionOrNull()?.message
    private val bdspNurseryAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspNursery() } else null
    val bdspNursery: BdspNurseryRead? get() = bdspNurseryAttempt?.getOrNull()
    val bdspNurseryUnavailableReason: String? get() = bdspNurseryAttempt?.exceptionOrNull()?.message
    private val bdspUndergroundAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspUnderground() } else null
    val bdspUnderground: BdspUndergroundRead? get() = bdspUndergroundAttempt?.getOrNull()
    val bdspUndergroundUnavailableReason: String? get() = bdspUndergroundAttempt?.exceptionOrNull()?.message
    private val bdspInventoryAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspInventory() } else null
    val bdspInventory: BdspInventoryRead? get() = bdspInventoryAttempt?.getOrNull()
    private val swordShieldInventoryAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldInventory() } else null
    val swordShieldInventory: SwordShieldInventoryRead? get() = swordShieldInventoryAttempt?.getOrNull()
    private val swordShieldPlayTimeAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldPlayTime() } else null
    val swordShieldPlayTime: SwordShieldPlayTimeRead? get() = swordShieldPlayTimeAttempt?.getOrNull()
    val swordShieldPlayTimeUnavailableReason: String? get() = swordShieldPlayTimeAttempt?.exceptionOrNull()?.message
    private val swordShieldBattleTowerAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldBattleTower() } else null
    val swordShieldBattleTower: SwordShieldBattleTowerRead? get() = swordShieldBattleTowerAttempt?.getOrNull()
    val swordShieldBattleTowerUnavailableReason: String? get() =
        swordShieldBattleTowerAttempt?.exceptionOrNull()?.message
    private val swordShieldDaycareAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldDaycare() } else null
    val swordShieldDaycare: SwordShieldDaycareRead? get() = swordShieldDaycareAttempt?.getOrNull()
    val swordShieldDaycareUnavailableReason: String? get() =
        swordShieldDaycareAttempt?.exceptionOrNull()?.message
    private val swordShieldBoxLayoutAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldBoxLayout() } else null
    val swordShieldBoxLayout: SwordShieldBoxLayoutRead? get() = swordShieldBoxLayoutAttempt?.getOrNull()
    val swordShieldBoxLayoutUnavailableReason: String? get() =
        swordShieldBoxLayoutAttempt?.exceptionOrNull()?.message
    private val swordShieldRallyDiggingAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldRallyDigging() } else null
    val swordShieldRallyDigging: SwordShieldRallyDiggingRead? get() = swordShieldRallyDiggingAttempt?.getOrNull()
    val swordShieldRallyDiggingUnavailableReason: String? get() =
        swordShieldRallyDiggingAttempt?.exceptionOrNull()?.message
    private val swordShieldStarterChoiceAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldStarterChoice() } else null
    val swordShieldStarterChoice: SwordShieldStarterChoiceRead? get() = swordShieldStarterChoiceAttempt?.getOrNull()
    val swordShieldStarterChoiceUnavailableReason: String? get() =
        swordShieldStarterChoiceAttempt?.exceptionOrNull()?.message
    private val swordShieldLeagueCardAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldLeagueCard() } else null
    val swordShieldLeagueCard: SwordShieldLeagueCardRead? get() = swordShieldLeagueCardAttempt?.getOrNull()
    val swordShieldLeagueCardUnavailableReason: String? get() =
        swordShieldLeagueCardAttempt?.exceptionOrNull()?.message
    private val swordShieldIsleOfArmorProgressAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldIsleOfArmorProgress() } else null
    val swordShieldIsleOfArmorProgress: SwordShieldIsleOfArmorProgressRead? get() =
        swordShieldIsleOfArmorProgressAttempt?.getOrNull()
    val swordShieldIsleOfArmorProgressUnavailableReason: String? get() =
        swordShieldIsleOfArmorProgressAttempt?.exceptionOrNull()?.message
    private val swordShieldCrownTundraProgressAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldCrownTundraProgress() } else null
    val swordShieldCrownTundraProgress: SwordShieldCrownTundraProgressRead? get() =
        swordShieldCrownTundraProgressAttempt?.getOrNull()
    val swordShieldCrownTundraProgressUnavailableReason: String? get() =
        swordShieldCrownTundraProgressAttempt?.exceptionOrNull()?.message
    private val swordShieldStarTournamentAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldStarTournament() } else null
    val swordShieldStarTournament: SwordShieldStarTournamentRead? get() =
        swordShieldStarTournamentAttempt?.getOrNull()
    val swordShieldStarTournamentUnavailableReason: String? get() =
        swordShieldStarTournamentAttempt?.exceptionOrNull()?.message
    private val swordShieldSwordsOfJusticeAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldSwordsOfJustice() } else null
    val swordShieldSwordsOfJustice: SwordShieldSwordsOfJusticeRead? get() =
        swordShieldSwordsOfJusticeAttempt?.getOrNull()
    val swordShieldSwordsOfJusticeUnavailableReason: String? get() =
        swordShieldSwordsOfJusticeAttempt?.exceptionOrNull()?.message
    private val swordShieldAdventureStepsAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldAdventureSteps() } else null
    val swordShieldAdventureSteps: SwordShieldAdventureStepsRead? get() =
        swordShieldAdventureStepsAttempt?.getOrNull()
    val swordShieldAdventureStepsUnavailableReason: String? get() =
        swordShieldAdventureStepsAttempt?.exceptionOrNull()?.message
    private val swordShieldHallOfFameTimeAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldHallOfFameTime() } else null
    val swordShieldHallOfFameTime: SwordShieldHallOfFameTimeRead? get() =
        swordShieldHallOfFameTimeAttempt?.getOrNull()
    val swordShieldHallOfFameTimeUnavailableReason: String? get() =
        swordShieldHallOfFameTimeAttempt?.exceptionOrNull()?.message
    private val swordShieldPokedexViewStateAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldPokedexViewState() } else null
    val swordShieldPokedexViewState: SwordShieldPokedexViewStateRead? get() =
        swordShieldPokedexViewStateAttempt?.getOrNull()
    val swordShieldPokedexViewStateUnavailableReason: String? get() =
        swordShieldPokedexViewStateAttempt?.exceptionOrNull()?.message
    private val swordShieldRestrictedSparringAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldRestrictedSparring() } else null
    val swordShieldRestrictedSparring: SwordShieldRestrictedSparringRead? get() =
        swordShieldRestrictedSparringAttempt?.getOrNull()
    val swordShieldRestrictedSparringUnavailableReason: String? get() =
        swordShieldRestrictedSparringAttempt?.exceptionOrNull()?.message
    private val letsGoInventoryAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoInventory() } else null
    val letsGoInventory: LetsGoInventoryRead? get() = letsGoInventoryAttempt?.getOrNull()
    private val legendsArceusInventoryAttempt = if (legendsArceusValidation is LegendsArceusValidation.Verified)
        runCatching { snapshot.readLegendsArceusInventory() } else null
    val legendsArceusInventory: LegendsArceusInventorySnapshot? get() = legendsArceusInventoryAttempt?.getOrNull()
    private val scarletVioletInventoryAttempt = if (scarletVioletValidation is ScarletVioletValidation.Verified)
        runCatching { snapshot.readScarletVioletInventory() } else null
    val scarletVioletInventory: ScarletVioletInventoryRead? get() = scarletVioletInventoryAttempt?.getOrNull()
    private val legendsZaInventoryAttempt = if (legendsZaValidation is LegendsZaValidation.Verified)
        runCatching { snapshot.readLegendsZaInventory() } else null
    val legendsZaInventory: LegendsZaInventoryRead? get() = legendsZaInventoryAttempt?.getOrNull()
    val inventoryUnavailableReason: String? get() = letsGoInventoryAttempt?.exceptionOrNull()?.message
        ?: swordShieldInventoryAttempt?.exceptionOrNull()?.message
        ?: bdspInventoryAttempt?.exceptionOrNull()?.message
        ?: legendsArceusInventoryAttempt?.exceptionOrNull()?.message
        ?: scarletVioletInventoryAttempt?.exceptionOrNull()?.message
        ?: legendsZaInventoryAttempt?.exceptionOrNull()?.message
    private val legendsZaPokedexAttempt = if (legendsZaValidation is LegendsZaValidation.Verified)
        runCatching { snapshot.readLegendsZaPokedex() } else null
    val legendsZaPokedex: LegendsZaPokedexRead? get() = legendsZaPokedexAttempt?.getOrNull()
    private val scarletVioletPokedexAttempt = if (scarletVioletValidation is ScarletVioletValidation.Verified)
        runCatching { snapshot.readScarletVioletPokedex() } else null
    val scarletVioletPokedex: ScarletVioletPokedexRead? get() = scarletVioletPokedexAttempt?.getOrNull()
    private val legendsArceusPokedexAttempt = if (legendsArceusValidation is LegendsArceusValidation.Verified)
        runCatching { snapshot.readLegendsArceusPokedex() } else null
    val legendsArceusPokedex: LegendsArceusPokedexRead? get() = legendsArceusPokedexAttempt?.getOrNull()
    private val bdspPokedexAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspPokedex() } else null
    val bdspPokedex: BdspPokedexRead? get() = bdspPokedexAttempt?.getOrNull()
    private val swordShieldPokedexAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldPokedex() } else null
    val swordShieldPokedex: SwordShieldPokedexRead? get() = swordShieldPokedexAttempt?.getOrNull()
    private val letsGoPokedexAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoPokedex() } else null
    val letsGoPokedex: LetsGoPokedexRead? get() = letsGoPokedexAttempt?.getOrNull()
    val pokedexUnavailableReason: String? get() = letsGoPokedexAttempt?.exceptionOrNull()?.message
        ?: swordShieldPokedexAttempt?.exceptionOrNull()?.message
        ?: bdspPokedexAttempt?.exceptionOrNull()?.message
        ?: legendsArceusPokedexAttempt?.exceptionOrNull()?.message
        ?: scarletVioletPokedexAttempt?.exceptionOrNull()?.message
        ?: legendsZaPokedexAttempt?.exceptionOrNull()?.message
    private val letsGoDayCareAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoDayCare() } else null
    val letsGoDayCare: LetsGoDayCareRead? get() = letsGoDayCareAttempt?.getOrNull()
    val letsGoDayCareUnavailableReason: String? get() = letsGoDayCareAttempt?.exceptionOrNull()?.message
    private val letsGoCaptureRecordsAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoCaptureRecords() } else null
    val letsGoCaptureRecords: LetsGoCaptureRecordsRead? get() = letsGoCaptureRecordsAttempt?.getOrNull()
    val letsGoCaptureRecordsUnavailableReason: String? get() = letsGoCaptureRecordsAttempt?.exceptionOrNull()?.message
    private val letsGoGoParkAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoGoPark() } else null
    val letsGoGoPark: LetsGoGoParkRead? get() = letsGoGoParkAttempt?.getOrNull()
    val letsGoGoParkUnavailableReason: String? get() = letsGoGoParkAttempt?.exceptionOrNull()?.message
    private val letsGoPlayerLocationAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoPlayerLocation() } else null
    val letsGoPlayerLocation: LetsGoPlayerLocationRead? get() = letsGoPlayerLocationAttempt?.getOrNull()
    val letsGoPlayerLocationUnavailableReason: String? get() = letsGoPlayerLocationAttempt?.exceptionOrNull()?.message
    private val letsGoAdventureBeginAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoAdventureBegin() } else null
    val letsGoAdventureBegin: LetsGoAdventureBeginRead? get() = letsGoAdventureBeginAttempt?.getOrNull()
    val letsGoAdventureBeginUnavailableReason: String? get() = letsGoAdventureBeginAttempt?.exceptionOrNull()?.message
    private val letsGoSecureValuesAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoSecureValues() } else null
    val letsGoSecureValues: LetsGoSecureValueRead? get() = letsGoSecureValuesAttempt?.getOrNull()
    val letsGoSecureValuesUnavailableReason: String? get() = letsGoSecureValuesAttempt?.exceptionOrNull()?.message
    private val letsGoGameSyncIdentityAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoGameSyncIdentity() } else null
    val letsGoGameSyncIdentity: LetsGoGameSyncIdentityRead? get() = letsGoGameSyncIdentityAttempt?.getOrNull()
    val letsGoGameSyncIdentityUnavailableReason: String? get() =
        letsGoGameSyncIdentityAttempt?.exceptionOrNull()?.message
    private val letsGoEquippedAppearanceAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoEquippedAppearance() } else null
    val letsGoEquippedAppearance: LetsGoEquippedAppearanceRead? get() = letsGoEquippedAppearanceAttempt?.getOrNull()
    val letsGoEquippedAppearanceUnavailableReason: String? get() =
        letsGoEquippedAppearanceAttempt?.exceptionOrNull()?.message
    private val letsGoBlockDirectoryAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoBlockDirectory() } else null
    val letsGoBlockDirectory: LetsGoBlockDirectoryRead? get() = letsGoBlockDirectoryAttempt?.getOrNull()
    val letsGoBlockDirectoryUnavailableReason: String? get() = letsGoBlockDirectoryAttempt?.exceptionOrNull()?.message
    private val letsGoMasterTrainerTitlesAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoMasterTrainerTitles() } else null
    val letsGoMasterTrainerTitles: LetsGoMasterTrainerTitlesRead? get() =
        letsGoMasterTrainerTitlesAttempt?.getOrNull()
    val letsGoMasterTrainerTitlesUnavailableReason: String? get() =
        letsGoMasterTrainerTitlesAttempt?.exceptionOrNull()?.message
    private val letsGoEventWorkAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoEventWork() } else null
    val letsGoEventWork: LetsGoEventWorkRead? get() = letsGoEventWorkAttempt?.getOrNull()
    val letsGoEventWorkUnavailableReason: String? get() = letsGoEventWorkAttempt?.exceptionOrNull()?.message
    private val letsGoMysteryGiftHistoryAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoMysteryGiftHistory() } else null
    val letsGoMysteryGiftHistory: LetsGoMysteryGiftHistoryRead? get() = letsGoMysteryGiftHistoryAttempt?.getOrNull()
    val letsGoMysteryGiftHistoryUnavailableReason: String? get() =
        letsGoMysteryGiftHistoryAttempt?.exceptionOrNull()?.message
    private val letsGoPlayerAccessoriesAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoPlayerAccessories() } else null
    val letsGoPlayerAccessories: LetsGoPlayerAccessoryRead? get() = letsGoPlayerAccessoriesAttempt?.getOrNull()
    val letsGoPlayerAccessoriesUnavailableReason: String? get() = letsGoPlayerAccessoriesAttempt?.exceptionOrNull()?.message
    private val letsGoStarterAccessoriesAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoStarterAccessories() } else null
    val letsGoStarterAccessories: LetsGoPlayerAccessoryRead? get() = letsGoStarterAccessoriesAttempt?.getOrNull()
    val letsGoStarterAccessoriesUnavailableReason: String? get() = letsGoStarterAccessoriesAttempt?.exceptionOrNull()?.message
    private val letsGoGameOptionsAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoGameOptions() } else null
    val letsGoGameOptions: LetsGoGameOptionsRead? get() = letsGoGameOptionsAttempt?.getOrNull()
    val letsGoGameOptionsUnavailableReason: String? get() = letsGoGameOptionsAttempt?.exceptionOrNull()?.message
    private val letsGoLastSavedAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoLastSaved() } else null
    val letsGoLastSaved: LetsGoLastSavedRead? get() = letsGoLastSavedAttempt?.getOrNull()
    val letsGoLastSavedUnavailableReason: String? get() = letsGoLastSavedAttempt?.exceptionOrNull()?.message
    private val letsGoPlayTimeAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoPlayTime() } else null
    val letsGoPlayTime: LetsGoPlayTimeRead? get() = letsGoPlayTimeAttempt?.getOrNull()
    val letsGoPlayTimeUnavailableReason: String? get() = letsGoPlayTimeAttempt?.exceptionOrNull()?.message
    private val letsGoTrainerAttempt = if (validation is LetsGoValidation.Verified)
        runCatching { snapshot.readLetsGoTrainer() } else null
    val letsGoTrainer: LetsGoTrainerRead? get() = letsGoTrainerAttempt?.getOrNull()
    private val bdspTrainerAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspTrainer() } else null
    val bdspTrainer: BdspTrainerRead? get() = bdspTrainerAttempt?.getOrNull()
    private val legendsArceusTrainerAttempt = if (legendsArceusValidation is LegendsArceusValidation.Verified)
        runCatching { snapshot.readLegendsArceusTrainer() } else null
    val legendsArceusTrainer: LegendsArceusTrainerRead? get() = legendsArceusTrainerAttempt?.getOrNull()
    private val scarletVioletTrainerAttempt = if (scarletVioletValidation is ScarletVioletValidation.Verified)
        runCatching { snapshot.readScarletVioletTrainer() } else null
    val scarletVioletTrainer: ScarletVioletTrainerRead? get() = scarletVioletTrainerAttempt?.getOrNull()
    private val legendsZaTrainerAttempt = if (legendsZaValidation is LegendsZaValidation.Verified)
        runCatching { snapshot.readLegendsZaTrainer() } else null
    val legendsZaTrainer: LegendsZaTrainerRead? get() = legendsZaTrainerAttempt?.getOrNull()
    private val trainerAttempt = if (swordShieldValidation is SwordShieldValidation.Verified)
        runCatching { snapshot.readSwordShieldTrainer() } else null
    private val bdspStorageAttempt = if (bdspValidation is BdspValidation.Verified)
        runCatching { snapshot.readBdspStorage() } else null
    private val legendsArceusStorageAttempt = if (legendsArceusValidation is LegendsArceusValidation.Verified)
        runCatching { snapshot.readLegendsArceusStorage() } else null
    private val scarletVioletStorageAttempt = if (scarletVioletValidation is ScarletVioletValidation.Verified)
        runCatching { snapshot.readScarletVioletStorage() } else null
    private val legendsZaStorageAttempt = if (legendsZaValidation is LegendsZaValidation.Verified)
        runCatching { snapshot.readLegendsZaStorage() } else null
    val legendsZaStorage: LegendsZaStorageRead? get() = legendsZaStorageAttempt?.getOrNull()
    val scarletVioletStorage: ScarletVioletStorageRead? get() = scarletVioletStorageAttempt?.getOrNull()
    val legendsArceusStorage: LegendsArceusStorageRead? get() = legendsArceusStorageAttempt?.getOrNull()
    val letsGoStorage: LetsGoStorageRead? get() = storageAttempt?.getOrNull()
    val swordShieldStorage: SwordShieldStorageRead? get() = swordShieldStorageAttempt?.getOrNull()
    val bdspStorage: BdspStorageRead? get() = bdspStorageAttempt?.getOrNull()
    val swordShieldTrainer: SwordShieldTrainerRead? get() = trainerAttempt?.getOrNull()
    val trainerUnavailableReason: String? get() = trainerAttempt?.exceptionOrNull()?.message
        ?: letsGoTrainerAttempt?.exceptionOrNull()?.message
        ?: bdspTrainerAttempt?.exceptionOrNull()?.message
        ?: legendsArceusTrainerAttempt?.exceptionOrNull()?.message
        ?: scarletVioletTrainerAttempt?.exceptionOrNull()?.message
        ?: legendsZaTrainerAttempt?.exceptionOrNull()?.message
    val storageUnavailableReason: String? get() = storageAttempt?.exceptionOrNull()?.message
        ?: swordShieldStorageAttempt?.exceptionOrNull()?.message
        ?: bdspStorageAttempt?.exceptionOrNull()?.message
        ?: legendsArceusStorageAttempt?.exceptionOrNull()?.message
        ?: scarletVioletStorageAttempt?.exceptionOrNull()?.message
        ?: legendsZaStorageAttempt?.exceptionOrNull()?.message
    fun supports(capability: SwitchReadCapability): Boolean =
        isFormatVerified && (capability in setOf(SwitchReadCapability.SAVE_IDENTITY, SwitchReadCapability.SAFE_BACKUPS) ||
            ((letsGoInventory != null || swordShieldInventory != null || bdspInventory != null || legendsArceusInventory != null || scarletVioletInventory != null || legendsZaInventory != null) && capability == SwitchReadCapability.INVENTORY) ||
            ((letsGoPokedex != null || swordShieldPokedex != null || bdspPokedex != null || legendsArceusPokedex != null || scarletVioletPokedex != null || legendsZaPokedex != null) && capability == SwitchReadCapability.POKEDEX) ||
            (legendsArceusStorage != null && capability in setOf(SwitchReadCapability.PARTY,
                SwitchReadCapability.PASTURES, SwitchReadCapability.POKEMON_DETAILS)) ||
            ((swordShieldTrainer != null || letsGoTrainer != null || bdspTrainer != null || legendsArceusTrainer != null || scarletVioletTrainer != null || legendsZaTrainer != null) && capability == SwitchReadCapability.TRAINER_PROFILE) ||
            ((letsGoStorage != null || swordShieldStorage != null || bdspStorage != null || scarletVioletStorage != null || legendsZaStorage != null) && capability in setOf(SwitchReadCapability.PARTY,
                SwitchReadCapability.PC_OR_BOXES, SwitchReadCapability.POKEMON_DETAILS)))
}


