package com.example.pokemonsavemanager

enum class Gen3GameOptionsProfile(val frameCount: Int, val firstButtonLabel: String) {
    HOENN(20, "Normal"), FIRERED_LEAFGREEN(10, "Help")
}

data class Gen3GameOptions(
    val buttonMode: Int, val textSpeed: Int, val frame: Int,
    val sound: Int, val battleStyle: Int, val battleSceneOff: Int
)

/** SaveBlock2 options shared by R/S/E and FR/LG; map zoom and reserved bits are preserved. */
object Gen3GameOptionsEditing {
    const val SECTION_ID = 0
    const val BUTTON_OFFSET = 0x13
    const val OPTIONS_OFFSET = 0x14
    const val EDITABLE_MASK = 0x07FF

    private fun validate(options: Gen3GameOptions, profile: Gen3GameOptionsProfile) {
        require(options.buttonMode in 0..2) { "The button mode is not supported by this game." }
        require(options.textSpeed in 0..2) { "The text speed is not supported by this game." }
        require(options.frame in 0 until profile.frameCount) { "The frame is not supported by this game." }
        require(options.sound in 0..1 && options.battleStyle in 0..1 && options.battleSceneOff in 0..1) {
            "Sound, battle style and battle scene must use native option values."
        }
    }

    /** Defaults for the six displayed settings; Hoenn's native routine leaves button mode alone. */
    fun defaultSelections(current: Gen3GameOptions, profile: Gen3GameOptionsProfile): Gen3GameOptions {
        validate(current, profile)
        return current.copy(
            buttonMode = if (profile == Gen3GameOptionsProfile.FIRERED_LEAFGREEN) 0 else current.buttonMode,
            textSpeed = 1, frame = 0, sound = 0, battleStyle = 0, battleSceneOff = 0
        )
    }

    fun read(section0: ByteArray, profile: Gen3GameOptionsProfile): Gen3GameOptions {
        require(section0.size == 0xF2C) { "Use a complete validated Gen 3 trainer section." }
        val word = (section0[OPTIONS_OFFSET].toInt() and 255) or
            ((section0[OPTIONS_OFFSET + 1].toInt() and 255) shl 8)
        return Gen3GameOptions(section0[BUTTON_OFFSET].toInt() and 255,
            word and 7, (word ushr 3) and 31, (word ushr 8) and 1,
            (word ushr 9) and 1, (word ushr 10) and 1).also { validate(it, profile) }
    }

    fun replace(section0: ByteArray, profile: Gen3GameOptionsProfile, options: Gen3GameOptions): ByteArray {
        read(section0, profile) // Reject malformed loaded values rather than silently normalizing them.
        validate(options, profile)
        val result = section0.copyOf()
        val oldWord = (section0[OPTIONS_OFFSET].toInt() and 255) or
            ((section0[OPTIONS_OFFSET + 1].toInt() and 255) shl 8)
        val editedWord = (oldWord and EDITABLE_MASK.inv()) or options.textSpeed or
            (options.frame shl 3) or (options.sound shl 8) or
            (options.battleStyle shl 9) or (options.battleSceneOff shl 10)
        result[BUTTON_OFFSET] = options.buttonMode.toByte()
        result[OPTIONS_OFFSET] = editedWord.toByte()
        result[OPTIONS_OFFSET + 1] = (editedWord ushr 8).toByte()
        require(read(result, profile) == options && result.indices.all {
            it in BUTTON_OFFSET..OPTIONS_OFFSET + 1 || result[it] == section0[it]
        } && ((oldWord xor editedWord) and EDITABLE_MASK.inv()) == 0) {
            "The options edit changed an unrelated field, map zoom or reserved bit."
        }
        return result
    }
}
