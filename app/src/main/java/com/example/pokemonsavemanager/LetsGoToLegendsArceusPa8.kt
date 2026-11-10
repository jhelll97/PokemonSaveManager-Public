package com.example.pokemonsavemanager

data class LetsGoToLegendsArceusReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

/** Converts one reviewed native Let's Go PB7 Bank record into a native Legends: Arceus PA8. */
object LetsGoToLegendsArceusPa8 {
    private const val PB7_SIZE = 0x104
    private const val CORE_SIZE = 0xE8
    private val orders = listOf("0123","0132","0213","0312","0231","0321","1023","1032",
        "2013","3012","2031","3021","1203","1302","2103","3102","2301","3201",
        "1230","1320","2130","3120","2310","3210")
    private val unavailableDefaultForms =
        setOf(58,59,100,101,157,211,503,549,550,570,571,628,705,706,713,724)

    fun inspectSource(record: ByteArray): LetsGoPb7Source {
        require(record.size == PB7_SIZE) { "A complete 260-byte native Let's Go PB7 record is required." }
        val pokemon = (LetsGoPokemonReadOnly.read(record) as? LetsGoSlotRead.Pokemon)?.value
            ?: throw IllegalArgumentException("The Let's Go PB7 checksum or native record structure is invalid.")
        require(!pokemon.isEgg) { "Egg PB7 records remain blocked from this PSE conversion." }
        require(!pokemon.isPartnerForm) {
            "Partner Pikachu and partner Eevee are save-bound and cannot enter Legends: Arceus."
        }
        require(pokemon.form == 0 && pokemon.species !in unavailableDefaultForms) {
            "This PB7 form has no reviewed native Legends: Arceus form mapping."
        }
        require(pokemon.species in 1..807 &&
            pokemon.species in LegendsArceusPokedexReadOnly.supportedSpecies) {
            "Species #${pokemon.species} has no destination-valid Legends: Arceus mapping."
        }
        require(pokemon.cachedLevel in 1..100) { "The PB7 cached level is outside 1-100." }
        val core = plainCore(record)
        val hiddenEvs = (0..5).map { byte(core, 0x1E + it) }
        require(hiddenEvs.all { it == 0 }) {
            "The PB7 hidden EV bytes are nonzero (${hiddenEvs.joinToString("/")}); this route will not reinterpret them."
        }
        val abilityId = byte(core, 0x14)
        val abilityNumber = byte(core, 0x15) and 7
        require(abilityId in 1..267 && abilityNumber in setOf(1, 2, 4)) {
            "The PB7 ability ID or slot has no reviewed PA8 storage mapping."
        }
        return LetsGoPb7Source(
            core,
            pokemon.species,
            pokemon.nickname,
            pokemon.cachedLevel,
            abilityId,
            abilityNumber,
            pokemon.awakened,
            record.copyOfRange(CORE_SIZE, PB7_SIZE)
        )
    }

    fun review(record: ByteArray): LetsGoToLegendsArceusReview =
        reviewInternal(record, null, null)

    fun review(
        record: ByteArray,
        destinationTrainer: LegendsArceusTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): LetsGoToLegendsArceusReview = reviewInternal(record, destinationTrainer, metadata)

    private fun reviewInternal(
        record: ByteArray,
        destinationTrainer: LegendsArceusTrainerRead?,
        metadata: Gen1ToSwordShieldMetadata?
    ): LetsGoToLegendsArceusReview {
        val source = inspectSource(record)
        val sanitizedCore = source.decryptedCore.copyOf()
        for (offset in 0x24..0x29) sanitizedCore[offset] = 0
        writeChecksum(sanitizedCore)

        val converted = if (destinationTrainer == null && metadata == null) {
            Gen7ToLegendsArceusPa8.review(sanitizedCore)
        } else {
            require(destinationTrainer != null && metadata != null) {
                "Destination trainer and documented species metadata must be reviewed together."
            }
            require(metadata.abilityId == source.abilityId &&
                metadata.abilityNumber == source.abilityNumber) {
                "The documented Legends: Arceus ability storage does not match the reviewed PB7 ability."
            }
            Gen7ToLegendsArceusPa8.review(sanitizedCore, destinationTrainer, metadata)
        }
        val reopened = LegendsArceusPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val party = LegendsArceusPokemonLevelEditing.partyRecordFromStored(converted.nativeRecord)
        val reopenedParty = (LegendsArceusPokemonReadOnly.read(party) as?
            LegendsArceusSlotRead.Pokemon)?.value
            ?: error("The generated PA8 did not reopen with a verified native party cache.")
        require(reopened.species == source.species &&
            reopenedParty.cachedLevel == source.cachedLevel) {
            "The generated PA8 level or species does not match the reviewed PB7."
        }

        val effects = converted.effects.map { effect ->
            when {
                effect.startsWith("Explicit Legalise:") ->
                    "Explicit Legalise: PSE Let's Go PB7 -> Legends: Arceus PA8 conversion; not an official HOME transfer, in-game trade, encounter record, or verified provenance."
                effect.startsWith("Species, reviewed default form,") ->
                    "Species, reviewed default form, PID, EC, TID/SID, EXP, IVs, nature, gender, shiny state, friendship, Pokérus, origin, dates, locations, fateful flag, Hyper Training, moves, current PP and relearn moves are retained. PB7 hidden EV bytes are verified zero, so PA8 raw EV storage remains zero."
                effect.startsWith("OT text, HT text") ->
                    effect.replace("Gen 7 text trash bytes", "PB7 text trash bytes")
                effect.startsWith("Unmapped PK7 metadata bytes") ->
                    effect.replace("PK7", "PB7 core")
                effect.startsWith("The PK7 Bank entry") ->
                    effect.replace("PK7", "PB7")
                else -> effect.replace("Gen 7", "PB7").replace("PK7", "PB7")
            }
        }.toMutableList()
        effects.add(2,
            "Let's Go Awakening Values ${source.awakenedValues.joinToString("/")} at PB7 0x24-0x29 (${hex(source.decryptedCore, 0x24, 0x2A)}) have no destination-valid PA8 mapping and are omitted only by this Legalise choice; PA8 contest values default to zero.")
        effects.add(
            "The PB7 party cache at 0xE8-0x103 (${hex(source.partyCacheBytes, 0, source.partyCacheBytes.size)}) is omitted. Cached level, HP, five battle stats and combat power are not copied; Legends: Arceus reopens level ${reopenedParty.cachedLevel}, rebuilds native stats from retained EXP/IVs/nature with zero Ganbaru Values, and restores full HP ${reopenedParty.maximumHp}.")
        return LetsGoToLegendsArceusReview(
            converted.nativeRecord,
            source.species,
            source.nickname,
            effects
        )
    }

    private fun plainCore(record: ByteArray): ByteArray {
        require(u16(record, 4) == 0) { "The PB7 sanity field is not zero." }
        val ec = u32(record, 0)
        val decoded = record.copyOf()
        xor(decoded, 8, CORE_SIZE, ec)
        xor(decoded, CORE_SIZE, PB7_SIZE, ec)
        val order = orders[((ec ushr 13).toInt() and 31) % 24]
        val plain = decoded.copyOf()
        for (block in 0..3) {
            val from = 8 + (order[block] - '0') * 56
            decoded.copyInto(plain, 8 + block * 56, from, from + 56)
        }
        var sum = 0
        for (offset in 8 until CORE_SIZE step 2) sum += u16(plain, offset)
        require((sum and 0xFFFF) == u16(record, 6)) { "The PB7 checksum failed." }
        return plain.copyOfRange(0, CORE_SIZE)
    }

    private fun writeChecksum(core: ByteArray) {
        var sum = 0
        for (offset in 8 until CORE_SIZE step 2) sum += u16(core, offset)
        w16(core, 6, sum and 0xFFFF)
    }

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
