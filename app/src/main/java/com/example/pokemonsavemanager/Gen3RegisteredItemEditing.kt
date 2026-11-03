package com.example.pokemonsavemanager

enum class Gen3RegisteredItemProfile(val sectionOffset: Int) {
    HOENN(0x496), FIRERED_LEAFGREEN(0x296)
}

/** SaveBlock1's unencrypted u16 registered SELECT item, in active save section 1. */
object Gen3RegisteredItemEditing {
    const val SECTION_ID = 1

    /** Bounded English labels from pinned native item constants; all other IDs stay numeric. */
    fun itemLabel(itemId: Int, profile: Gen3RegisteredItemProfile): String {
        require(itemId in 0..65535) { "The saved item ID must be an unsigned 16-bit value." }
        if (itemId == 0) return "No registered item"
        val name = when (itemId) {
            261 -> "Itemfinder"
            262 -> "Old Rod"
            263 -> "Good Rod"
            264 -> "Super Rod"
            259 -> if (profile == Gen3RegisteredItemProfile.HOENN) "Mach Bike" else null
            268 -> if (profile == Gen3RegisteredItemProfile.HOENN) "Wailmer Pail" else null
            272 -> if (profile == Gen3RegisteredItemProfile.HOENN) "Acro Bike" else null
            360 -> if (profile == Gen3RegisteredItemProfile.FIRERED_LEAFGREEN) "Bicycle" else null
            else -> null
        }
        return if (name == null) "Native item #" + itemId else name + " (#" + itemId + ")"
    }

    fun read(section1: ByteArray, profile: Gen3RegisteredItemProfile): Int {
        require(section1.size == 0xF80) { "Use a complete validated Gen 3 SaveBlock1 section." }
        val offset = profile.sectionOffset
        return (section1[offset].toInt() and 255) or ((section1[offset + 1].toInt() and 255) shl 8)
    }

    /** Explicitly unregister the shortcut; never remove an item from any Bag/PC pocket. */
    fun clear(section1: ByteArray, profile: Gen3RegisteredItemProfile): ByteArray {
        read(section1, profile)
        val offset = profile.sectionOffset
        val result = section1.copyOf()
        result[offset] = 0
        result[offset + 1] = 0
        require(read(result, profile) == 0 && result.indices.all {
            it in offset..offset + 1 || result[it] == section1[it]
        }) { "Clearing the shortcut changed inventory or unrelated save bytes." }
        return result
    }
}
