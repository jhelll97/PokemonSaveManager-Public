package com.example.pokemonsavemanager

import java.util.Collections

data class LetsGoEquippedAppearanceValue(val label: String, val offset: Int, val value: ULong)

data class LetsGoEquippedAppearanceRead(
    val version: LetsGoVersion,
    val partner: List<LetsGoEquippedAppearanceValue>,
    val player: List<LetsGoEquippedAppearanceValue>
)

/** Exact equipped appearance identifiers from MyStatus; catalogue names are not inferred. */
object LetsGoEquippedAppearanceReadOnly {
    private const val STATUS = 0x1000
    private val partnerFields = listOf(
        "Hairstyle" to 0x0C0, "Hat" to 0x0C8, "Glasses" to 0x0D0,
        "Ear accessory" to 0x0D8, "Chest accessory" to 0x0E0,
        "Back accessory" to 0x0E8, "Tail accessory" to 0x0F0, "Clothes" to 0x0F8
    )
    private val playerFields = listOf(
        "Skin color" to 0x110, "Eye color" to 0x118, "Hair color" to 0x120,
        "Outerwear" to 0x128, "Pants" to 0x130, "Shoes" to 0x138,
        "Hat" to 0x140, "Bag" to 0x148
    )

    fun read(source: ByteArray): LetsGoEquippedAppearanceRead {
        val checked = LetsGoSaveValidation.inspect(source)
        require(checked is LetsGoValidation.Verified) { "A fully validated native Let's Go save is required." }
        return LetsGoEquippedAppearanceRead(
            checked.version,
            values(source, partnerFields),
            values(source, playerFields)
        )
    }

    private fun values(source: ByteArray, fields: List<Pair<String, Int>>): List<LetsGoEquippedAppearanceValue> =
        Collections.unmodifiableList(fields.map { (label, offset) ->
            LetsGoEquippedAppearanceValue(label, offset, u64(source, STATUS + offset))
        })

    private fun u64(bytes: ByteArray, offset: Int): ULong = (0 until 8).fold(0uL) { value, index ->
        value or ((bytes[offset + index].toInt() and 255).toULong() shl (index * 8))
    }
}
