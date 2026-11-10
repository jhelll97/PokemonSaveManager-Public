package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class Gen3ToLegendsArceusReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

/**
 * Converts one exact checksum-valid PK3 Bank entry through a Legends: Arceus-specific PK7 bridge.
 *
 * The 80-byte PK3 remains the transaction source. Legends: Arceus species, profile, move catalogue,
 * native PA8 parser and restore transaction validate the generated destination record.
 */
object Gen3ToLegendsArceusPa8 {
    private const val PK7_SIZE = 0xE8

    fun review(
        record: ByteArray,
        destinationTrainer: LegendsArceusTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen3ToLegendsArceusReview {
        val source = Gen3ToSwordShieldPk8.inspectSource(record)
        require(destinationTrainer.languageId == 2) {
            "This bounded route currently requires an English Legends: Arceus destination trainer."
        }
        require(metadata.baseFriendship in 0..255 && metadata.genderRate in -1..8) {
            "The documented species friendship or gender mapping is invalid."
        }
        require(metadata.abilityId in 1..255 &&
            metadata.abilityNumber == source.abilitySlot) {
            "The documented Legends: Arceus ability does not match PK3 ability slot " +
                source.abilitySlot + "."
        }
        require(source.species in LegendsArceusPokedexReadOnly.supportedSpecies) {
            "Species #${source.species} is not available in the reviewed Legends: Arceus destination catalogue."
        }
        val profile = LegendsArceusPersonalCatalogue.profiles(source.species, 0)
            .distinctBy {
                listOf(
                    it.hp, it.attack, it.defense, it.speed,
                    it.specialAttack, it.specialDefense, it.growth
                )
            }
            .singleOrNull()
            ?: error("This species has no single verified default Legends: Arceus profile.")
        require(source.experience <= minimumExperience(100, profile.growth)) {
            "The source EXP exceeds the reviewed Legends: Arceus level-100 value."
        }
        val normalizedPp = source.currentPp.copyOf()
        source.moves.forEachIndexed { index, move ->
            if (move != 0) {
                require(LegendsArceusMoveCatalog.choice(move) != null) {
                    "Move #$move is not usable in Legends: Arceus."
                }
                normalizedPp[index] = minOf(
                    source.currentPp[index],
                    LegendsArceusMoveCatalog.maximumPp(move)
                )
            }
        }
        val mappedEvs = mappedEvs(source.evs)
        val modernPid = preserveShiny(
            source.personality, source.trainerId, source.secretId, source.shiny
        )
        val nature = (source.personality % 25L).toInt()
        val gender = gender(source.personality, metadata.genderRate)
        val speciesName = LegendsArceusSpeciesNames.name(source.species)
        require(speciesName.isNotBlank()) { "The Legends: Arceus species name is unavailable." }
        val nickname = source.nickname.ifBlank { speciesName }
        val nicknamed = source.nickname.isNotBlank() &&
            !source.nickname.equals(speciesName, ignoreCase = true)
        val metLevel = source.metLevel.coerceAtLeast(1)
        val ball = if (source.ball == 0) 4 else source.ball
        val seed = MessageDigest.getInstance("SHA-256").apply {
            update(record)
            update(destinationTrainer.name.toByteArray(StandardCharsets.UTF_8))
            update(byteArrayOf(
                destinationTrainer.gender.toByte(),
                destinationTrainer.languageId.toByte()
            ))
            update(intBytes(metadata.abilityId))
            update(intBytes(metadata.abilityNumber))
        }.digest()
        val pk = ByteArray(PK7_SIZE)
        w32(pk, 0, u32(seed, 0))
        w16(pk, 4, 0)
        w16(pk, 8, source.species)
        w16(pk, 0x0A, 0)
        w16(pk, 0x0C, source.trainerId)
        w16(pk, 0x0E, source.secretId)
        w32(pk, 0x10, source.experience)
        pk[0x14] = metadata.abilityId.toByte()
        pk[0x15] = metadata.abilityNumber.toByte()
        w32(pk, 0x18, modernPid)
        pk[0x1C] = nature.toByte()
        pk[0x1D] = ((gender and 3) shl 1).toByte()
        for (index in 0..5) {
            pk[0x1E + index] = mappedEvs[index].toByte()
            pk[0x24 + index] = source.contest[index].toByte()
        }
        pk[0x2B] = source.pokerus.toByte()
        putText(pk, 0x40, nickname)
        for (index in 0..3) {
            w16(pk, 0x5A + index * 2, source.moves[index])
            if (source.moves[index] != 0) {
                pk[0x62 + index] = normalizedPp[index].toByte()
                pk[0x66 + index] = 0
            }
        }
        var packedIvs = 0L
        for (index in 0..5) {
            packedIvs = packedIvs or
                ((source.ivs[index].toLong() and 31L) shl (index * 5))
        }
        if (nicknamed) packedIvs = packedIvs or 0x80000000L
        w32(pk, 0x74, packedIvs)
        putText(pk, 0x78, destinationTrainer.name)
        pk[0x92] = destinationTrainer.gender.toByte()
        pk[0x93] = 1
        pk[0xA2] = source.friendship.toByte()
        putText(pk, 0xB0, source.originalTrainer)
        pk[0xCA] = source.friendship.toByte()
        w16(pk, 0xD8, 0)
        w16(pk, 0xDA, 0)
        pk[0xDC] = ball.toByte()
        pk[0xDD] = (metLevel or (source.originalTrainerGender shl 7)).toByte()
        pk[0xDE] = 0
        pk[0xDF] = source.originGame.toByte()
        pk[0xE3] = 2
        var checksum = 0
        for (offset in 8 until PK7_SIZE step 2) checksum += u16(pk, offset)
        w16(pk, 6, checksum and 0xFFFF)

        val converted = Gen7ToLegendsArceusPa8.review(pk)
        val downstream = converted.effects.filterNot {
            it.startsWith("Explicit Legalise:") ||
                it.startsWith("The PK7 Bank entry")
        }
        val effects = buildList {
            add("Explicit Legalise: direct PSE Gen 3 PK3 -> deterministic in-memory PK7 bridge -> Legends: Arceus PA8 conversion; not an official transfer, HOME move, trade, or verified provenance.")
            add("The exact selected checksum-valid 80-byte PK3 remains the bound Bank source. Species #" +
                source.species + ", OT/TID/SID, nickname, exact EXP, friendship, IVs, " +
                "contest values, Pokérus, Generation 3 origin game, met level, " +
                "nature, gender and shiny state are retained.")
            add("Move IDs are retained. Current PP " +
                source.currentPp.joinToString("/") + " maps to Legends: Arceus base limits " +
                normalizedPp.joinToString("/") +
                "; values above the destination limit are explicitly capped. PK3 PP-Up counts " +
                source.ppUps.joinToString("/") +
                " are explicitly cleared because PA8 has no PP-Up representation.")
            add("Gen 3 EVs " + source.evs.joinToString("/") +
                " map to stored PA8 EV bytes " + mappedEvs.joinToString("/") +
                " by capping each stat at 252 and reducing the largest values until the total is 510.")
            if (source.heldItem == 0) {
                add("The source holds no item; PA8 has no held-item field.")
            } else {
                add("Held item #" + source.heldItem +
                    " is explicitly removed because PA8 has no held-item field.")
            }
            add("Documented ability " + metadata.abilityId +
                " retains native source slot " + source.abilitySlot +
                " as PA8 storage data; encounter, ability and moveset legality are not certified.")
            if (modernPid == source.personality) {
                add("PID 0x" + source.personality.toString(16).uppercase()
                    .padStart(8, '0') + " is retained.")
            } else {
                add("PID changes from 0x" +
                    source.personality.toString(16).uppercase().padStart(8, '0') +
                    " to 0x" + modernPid.toString(16).uppercase().padStart(8, '0') +
                    " only to preserve the Gen 3 non-shiny state under the later-generation shiny threshold; this is an explicit PID/provenance Legalise choice.")
            }
            add("A deterministic encryption constant is bound to the exact PK3, Legends: Arceus trainer and reviewed ability mapping. The Legends: Arceus trainer becomes current handling trainer.")
            add("Source Ball #" + source.ball +
                (if (source.ball == 0) " first normalizes to a Poké Ball and" else "") +
                " becomes the native PA8 Strange Ball for a Pokémon originating outside Hisui.")
            add("Source met-location byte " + source.metLocation +
                ", markings 0x" + source.markings.toString(16).uppercase().padStart(2, '0') +
                " and ribbon/obedience word 0x" +
                source.ribbonWord.toString(16).uppercase().padStart(8, '0') +
                " are explicitly omitted because this bounded route has no exact PA8 mapping.")
            if (source.metLevel == 0) {
                add("Unset Gen 3 met level 0 is normalized to destination met level 1.")
            }
            add("Alpha, Noble, Ganbaru, move-shop, move-record, battle-state and HOME tracker fields remain at reviewed native defaults.")
            addAll(downstream)
            add("The PK3 Bank entry and Legends: Arceus source remain unchanged. One reviewed empty native PA8 pasture slot is written only to a new copy with the required native obtained count, retained recovery, persisted reopen verification and two-sided rollback.")
        }
        return Gen3ToLegendsArceusReview(
            converted.nativeRecord.copyOf(),
            source.species,
            source.nickname,
            effects
        )
    }

    private fun mappedEvs(source: IntArray): IntArray {
        val result = IntArray(6) { source[it].coerceIn(0, 252) }
        while (result.sum() > 510) {
            val index = result.indices.maxByOrNull { result[it] } ?: 0
            result[index]--
        }
        return result
    }

    private fun preserveShiny(
        pid: Long,
        tid: Int,
        sid: Int,
        shiny: Boolean
    ): Long {
        val value = pid and 0xFFFFFFFFL
        val modern = ((tid xor sid xor value.toInt() xor
            (value ushr 16).toInt()) and 0xFFFF) < 16
        return if (modern == shiny) value else value xor 0x10000000L
    }

    private fun gender(pid: Long, rate: Int): Int = when {
        rate < 0 -> 2
        rate == 0 -> 0
        rate >= 8 -> 1
        (pid.toInt() and 0xFF) < rate * 32 -> 1
        else -> 0
    }

    private fun minimumExperience(level: Int, growth: Int): Long {
        if (level <= 1) return 0
        val n = level.toLong()
        val cube = n * n * n
        return when (growth) {
            0 -> cube
            1 -> when {
                level <= 50 -> cube * (100 - n) / 50
                level <= 68 -> cube * (150 - n) / 100
                level <= 98 -> {
                    val remainder = n % 3
                    cube * (1274 + remainder * remainder - 9 * remainder -
                        20 * (n / 3)) / 1000
                }
                else -> cube * (160 - n) / 100
            }
            2 -> when {
                level <= 15 -> cube * (24 + (n + 1) / 3) / 50
                level <= 35 -> cube * (14 + n) / 50
                else -> cube * (32 + n / 2) / 50
            }
            3 -> maxOf(0L, 6 * cube / 5 - 15 * n * n + 100 * n - 140)
            4 -> 4 * cube / 5
            5 -> 5 * cube / 4
            else -> error("The Legends: Arceus growth group is unsupported.")
        }
    }

    private fun putText(bytes: ByteArray, offset: Int, value: String) {
        val encoded = value.toByteArray(StandardCharsets.UTF_16LE)
        require(encoded.size <= 24) { "Converted text exceeds 12 characters." }
        encoded.copyInto(bytes, offset)
    }

    private fun intBytes(value: Int) = byteArrayOf(
        value.toByte(), (value ushr 8).toByte(),
        (value ushr 16).toByte(), (value ushr 24).toByte()
    )

    private fun byte(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 255
    private fun u16(bytes: ByteArray, offset: Int) =
        byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)
    private fun u32(bytes: ByteArray, offset: Int) =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
    private fun w16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun w32(bytes: ByteArray, offset: Int, value: Long) {
        w16(bytes, offset, value.toInt())
        w16(bytes, offset + 2, (value ushr 16).toInt())
    }
}
