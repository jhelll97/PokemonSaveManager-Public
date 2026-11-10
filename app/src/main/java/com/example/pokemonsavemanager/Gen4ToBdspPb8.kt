package com.example.pokemonsavemanager

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class Gen4ToBdspReview(
    val nativeRecord: ByteArray,
    val sourceSpecies: Int,
    val sourceNickname: String,
    val effects: List<String>
)

/**
 * Converts one exact checksum-valid boxed PK4 Bank entry through a BDSP-specific PK7 bridge.
 *
 * The 136-byte PK4 remains the transaction source. BDSP's own profile, move catalogue,
 * native PB8 parser and restore transaction validate the generated destination record.
 */
object Gen4ToBdspPb8 {
    private const val PK7_SIZE = 0xE8

    fun review(
        record: ByteArray,
        destinationTrainer: BdspTrainerRead,
        metadata: Gen1ToSwordShieldMetadata
    ): Gen4ToBdspReview {
        val source = Gen4ToSwordShieldPk8.inspectSource(record)
        require(destinationTrainer.languageId == 2) {
            "This bounded route currently requires an English BDSP destination trainer."
        }
        require(metadata.baseFriendship in 0..255 && metadata.genderRate in -1..8) {
            "The documented species friendship or gender mapping is invalid."
        }
        require(metadata.abilityId == source.abilityId &&
            metadata.abilityNumber == source.abilitySlot) {
            "The documented BDSP ability does not exactly match PK4 ability #" +
                source.abilityId + " in slot " + source.abilitySlot + "."
        }
        require(gender(source.personality, metadata.genderRate) == source.gender) {
            "The source gender does not match the documented BDSP species ratio."
        }
        val profile = BdspPersonalCatalogue.profiles(source.species, 0)
            .distinctBy {
                listOf(
                    it.hp, it.attack, it.defense, it.speed,
                    it.specialAttack, it.specialDefense, it.growth
                )
            }
            .singleOrNull()
            ?: error("This species has no single verified default BDSP profile.")
        require(source.experience <= minimumExperience(100, profile.growth)) {
            "The source EXP exceeds the reviewed BDSP level-100 value."
        }
        val normalizedPp = source.currentPp.copyOf()
        source.moves.forEachIndexed { index, move ->
            if (move != 0) {
                require(BdspMoveCatalog.choice(move) != null) {
                    "Move #$move is not usable in BDSP."
                }
                normalizedPp[index] = minOf(
                    source.currentPp[index],
                    BdspMoveCatalog.maximumPp(move, source.ppUps[index])
                )
            }
        }
        val mappedEvs = mappedEvs(source.evs)
        val modernPid = preserveShiny(
            source.personality, source.trainerId, source.secretId, source.shiny
        )
        val speciesName = SwordShieldSpeciesNames.name(source.species)
        require(speciesName.isNotBlank()) { "The BDSP species name is unavailable." }
        val nickname = source.nickname.ifBlank { speciesName }
        val nicknamed = source.isNicknamed ||
            (source.nickname.isNotBlank() &&
                !source.nickname.equals(speciesName, ignoreCase = true))
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
        pk[0x14] = source.abilityId.toByte()
        pk[0x15] = source.abilitySlot.toByte()
        w32(pk, 0x18, modernPid)
        pk[0x1C] = (source.personality % 25L).toByte()
        pk[0x1D] = (((source.gender and 3) shl 1) or
            (if (source.fatefulEncounter) 1 else 0)).toByte()
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
                pk[0x66 + index] = source.ppUps[index].toByte()
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
        pk[0xD1] = source.eggYear.toByte()
        pk[0xD2] = source.eggMonth.toByte()
        pk[0xD3] = source.eggDay.toByte()
        pk[0xD4] = source.metYear.toByte()
        pk[0xD5] = source.metMonth.toByte()
        pk[0xD6] = source.metDay.toByte()
        w16(pk, 0xD8, 0)
        w16(pk, 0xDA, 30001)
        pk[0xDC] = source.ball.toByte()
        pk[0xDD] = (source.metLevel or
            (source.originalTrainerGender shl 7)).toByte()
        pk[0xDE] = 0
        pk[0xDF] = source.originGame.toByte()
        pk[0xE3] = 2
        var checksum = 0
        for (offset in 8 until PK7_SIZE step 2) checksum += u16(pk, offset)
        w16(pk, 6, checksum and 0xFFFF)

        val converted = Gen7ToBdspPb8.review(pk)
        val downstream = converted.effects.filterNot {
            it.startsWith("Explicit Legalise:") ||
                it.startsWith("The PK7 Bank entry")
        }
        val effects = buildList {
            add("Explicit Legalise: direct PSE Gen 4 PK4 -> deterministic in-memory PK7 bridge -> BDSP PB8 conversion; not an official transfer, HOME move, trade, or verified provenance.")
            add("The exact selected checksum-valid 136-byte boxed PK4 remains the bound Bank source. Species #" +
                source.species + ", OT/TID/SID, nickname, exact EXP, friendship, IVs, " +
                "contest values, Pokérus, source title, met level/date, ball, nature, " +
                "gender, fateful flag and shiny state are retained.")
            add("Move IDs and PP-Up counts are retained. Current PP " +
                source.currentPp.joinToString("/") + " maps to " +
                normalizedPp.joinToString("/") +
                "; values above BDSP limits are explicitly capped.")
            add("Gen 4 EVs " + source.evs.joinToString("/") +
                " map to BDSP EVs " + mappedEvs.joinToString("/") +
                " by capping each stat at 252 and reducing the largest values until the total is 510.")
            add("The source holds no item; the destination held-item field remains empty.")
            add("Documented ability " + source.abilityId +
                " retains native source slot " + source.abilitySlot +
                "; encounter and moveset legality are not certified.")
            if (modernPid == source.personality) {
                add("PID 0x" + source.personality.toString(16).uppercase()
                    .padStart(8, '0') + " is retained.")
            } else {
                add("PID changes from 0x" +
                    source.personality.toString(16).uppercase().padStart(8, '0') +
                    " to 0x" + modernPid.toString(16).uppercase().padStart(8, '0') +
                    " only to preserve the Generation 4 non-shiny state under the later-generation shiny threshold; this is an explicit PID/provenance Legalise choice.")
            }
            add("A deterministic encryption constant is bound to the exact PK4, BDSP trainer and reviewed ability mapping. The BDSP trainer becomes current handling trainer.")
            add("Generation 4 egg location #" + source.eggLocation +
                " maps to 0 and met location #" + source.metLocation +
                " maps to the documented later-generation transfer marker 30001; source egg and met dates are retained.")
            add("Markings 0x" +
                source.markings.toString(16).uppercase().padStart(2, '0') +
                " and ribbon words 0x" +
                source.ribbonWordA.toString(16).uppercase().padStart(8, '0') +
                "/0x" +
                source.ribbonWordB.toString(16).uppercase().padStart(8, '0') +
                " are explicitly omitted because this bounded route has no exact PB8 mapping.")
            addAll(downstream)
            add("The PK4 Bank entry and BDSP source remain unchanged. One reviewed empty native PB8 slot is written only to a new copy with caught registration, retained recovery, persisted reopen verification and two-sided rollback.")
        }
        return Gen4ToBdspReview(
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
            else -> error("The BDSP growth group is unsupported.")
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
