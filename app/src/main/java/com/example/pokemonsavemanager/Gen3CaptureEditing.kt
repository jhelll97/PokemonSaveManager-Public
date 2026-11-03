package com.example.pokemonsavemanager

data class Gen3CaptureMetadata(val metLevel: Int = 0, val ball: Int = 4, val otGender: Int = 0) {
    fun requireValid(): Gen3CaptureMetadata {
        require(metLevel in 0..100) { "Met level must be between 0 and 100." }
        require(ball in 1..12) { "Choose a supported Gen 3 Poké Ball." }
        require(otGender in 0..1) { "Original Trainer gender must be Male or Female." }
        return this
    }
}

/** Immutable capture-field edits for stored/party PK3 records, preserving origin game and location. */
object Gen3CaptureEditing {
    // Misc +2: level bits 0..6, origin game 7..10, ball 11..14, OT gender 15.
    // https://github.com/pret/pokeemerald/blob/master/include/pokemon.h
    // https://github.com/pret/pokeemerald/blob/master/include/constants/items.h
    val ballNames: List<String> = listOf("Master Ball", "Ultra Ball", "Great Ball", "Poké Ball",
        "Safari Ball", "Net Ball", "Dive Ball", "Nest Ball", "Repeat Ball", "Timer Ball", "Luxury Ball", "Premier Ball")

    private fun decode(record: ByteArray): Gen3Pk3DecodedRecord {
        require(record.size == 80 || record.size == 100) { "Use a complete stored or party PK3 record." }
        require((record[0x13].toInt() and 3) == 2) { "The PK3 is empty or flagged as a Bad Egg." }
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        require((record[0x13].toInt() and 4) == 0 &&
            (decoded.canonicalSection[43].toInt() and 0x40) == 0) {
            "Capture metadata cannot be edited on an Egg."
        }
        return decoded
    }

    private fun originWord(canonical: ByteArray): Int =
        (canonical[38].toInt() and 255) or ((canonical[39].toInt() and 255) shl 8)

    fun read(record: ByteArray): Gen3CaptureMetadata {
        val word = originWord(decode(record).canonicalSection)
        return Gen3CaptureMetadata(word and 0x7F, (word ushr 11) and 15, word ushr 15).requireValid()
    }

    fun replace(record: ByteArray, values: Gen3CaptureMetadata): ByteArray {
        values.requireValid()
        read(record) // Reject malformed source metadata rather than silently repairing it.
        val before = decode(record)
        val canonical = before.canonicalSection.copyOf()
        val word = (originWord(canonical) and 0x0780) or values.metLevel or
            (values.ball shl 11) or (values.otGender shl 15)
        canonical[38] = word.toByte()
        canonical[39] = (word ushr 8).toByte()
        val stored = Gen3Pk3Codec.encode(before.header, before.personality,
            before.trainerId, before.secretId, canonical)
        val result = record.copyOf().also { stored.copyInto(it) }
        val after = decode(result)
        require(after.canonicalSection.contentEquals(canonical) &&
            canonical.indices.all { it in 38..39 || canonical[it] == before.canonicalSection[it] } &&
            (originWord(canonical) and 0x0780) == (originWord(before.canonicalSection) and 0x0780) &&
            (0 until 32).all { it in 28..29 || result[it] == record[it] } &&
            (80 until record.size).all { result[it] == record[it] } && read(result) == values) {
            "The PK3 capture edit failed exact field or integrity verification."
        }
        return result
    }
}
