package com.example.pokemonsavemanager

data class SwordShieldStarTournamentPartnerRead(val name: String, val victories: Long)

data class SwordShieldStarTournamentRead(
    val version: SwordShieldVersion,
    val totalVictories: Long,
    val partners: List<SwordShieldStarTournamentPartnerRead>
)

/** Exact read-only projection of PKHeX's documented Galarian Star Tournament UInt32 records. */
object SwordShieldStarTournamentReadOnly {
    private val partnerNames = listOf(
        "Avery",
        "Klara",
        "Mustard",
        "Peony",
        "Leon",
        "Bede",
        "Marnie",
        "Milo",
        "Nessa",
        "Kabu",
        "Piers",
        "Raihan",
        "Bea",
        "Gordie",
        "Allister",
        "Melony",
        "Sordward",
        "Shielbert",
        "Hop",
        "Opal"
    )

    fun read(source: ByteArray): SwordShieldStarTournamentRead {
        val blocks = SwordShieldSaveValidation.readStarTournamentBlocks(source)
        require(blocks.partnerVictories.size == partnerNames.size) {
            "The native Sword/Shield Galarian Star Tournament partner table is incomplete."
        }
        return SwordShieldStarTournamentRead(
            blocks.version,
            uint32(blocks.totalVictories),
            partnerNames.indices.map { index ->
                SwordShieldStarTournamentPartnerRead(partnerNames[index], uint32(blocks.partnerVictories[index]))
            }
        )
    }

    private fun uint32(bytes: ByteArray): Long {
        require(bytes.size == 4) { "A native Sword/Shield Galarian Star Tournament record is truncated." }
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }
}
