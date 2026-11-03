package com.example.pokemonsavemanager

import java.security.MessageDigest
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Explicit allowlist. A plan can change exactly one native field and its digest only. */
enum class BdspEditableSetting(val label: String, val offset: Int, val width: Int = 4) {
    TEXT_SPEED("text speed", 0x79B74), BATTLE_STYLE("Battle Style", 0x79B88),
    MOVE_ANIMATIONS("move animations", 0x79B84), PARTY_BOX("party/box preference", 0x79B8C),
    CAMERA_VERTICAL("vertical camera", 0x79B9C), CAMERA_HORIZONTAL("horizontal camera", 0x79BA0),
    INPUT_MODE("input mode", 0x79BA8),
    SHOW_NICKNAMES("nickname display", 0x79BAC, 1),
    NICKNAME_REGISTRATION("nickname registration", 0x79B90, 1),
    GYRO_SENSOR("gyro-sensor preference", 0x79B94, 1),
    FOSSIL_CAMERA_SHAKE("fossil camera shake", 0x79B98, 1),
    AUTO_REPORT("auto-report preference", 0x79BA4, 1),
    KANJI_SCRIPT("Kanji-script preference", 0x79B7C, 1);

    val choices: List<String> get() = when (this) {
        TEXT_SPEED -> listOf("Slow", "Normal", "Fast")
        BATTLE_STYLE -> listOf("Switch", "Set")
        MOVE_ANIMATIONS -> listOf("On", "Off")
        PARTY_BOX -> listOf("Select", "Send to Box")
        CAMERA_VERTICAL -> listOf("Normal", "Reverse")
        CAMERA_HORIZONTAL -> listOf("Normal", "Reverse")
        INPUT_MODE -> listOf("Easy", "Normal")
        SHOW_NICKNAMES -> listOf("Off", "On")
        NICKNAME_REGISTRATION -> listOf("Off", "On")
        GYRO_SENSOR -> listOf("Off", "On")
        FOSSIL_CAMERA_SHAKE -> listOf("Off", "On")
        AUTO_REPORT -> listOf("Off", "On")
        KANJI_SCRIPT -> listOf("Off", "On")
    }
    fun current(options: BdspGameOptionsRead): Long = when (this) {
        TEXT_SPEED -> options.textSpeedId
        BATTLE_STYLE -> options.battleStyleId.toLong()
        MOVE_ANIMATIONS -> options.moveAnimationsId.toLong()
        PARTY_BOX -> options.partyBoxId.toLong()
        CAMERA_VERTICAL -> options.cameraVerticalId.toLong()
        CAMERA_HORIZONTAL -> options.cameraHorizontalId.toLong()
        INPUT_MODE -> options.inputModeId.toLong()
        SHOW_NICKNAMES -> options.flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawByte.toLong()
        NICKNAME_REGISTRATION -> options.flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).rawByte.toLong()
        GYRO_SENSOR -> options.flag(BdspGameOptionFlag.GYRO_SENSOR).rawByte.toLong()
        FOSSIL_CAMERA_SHAKE -> options.flag(BdspGameOptionFlag.FOSSIL_CAMERA_SHAKE).rawByte.toLong()
        AUTO_REPORT -> options.flag(BdspGameOptionFlag.AUTO_REPORT).rawByte.toLong()
        KANJI_SCRIPT -> options.flag(BdspGameOptionFlag.KANJI).rawByte.toLong()
    }
    fun acceptsSaved(value: Long): Boolean = when (this) {
        TEXT_SPEED -> value in 0L..3L
        BATTLE_STYLE -> value in 0L..1L
        MOVE_ANIMATIONS -> value in 0L..1L
        PARTY_BOX -> value in 0L..1L
        CAMERA_VERTICAL -> value in 0L..1L
        CAMERA_HORIZONTAL -> value in 0L..1L
        INPUT_MODE -> value in 0L..1L
        SHOW_NICKNAMES -> value in 0L..1L
        NICKNAME_REGISTRATION -> value in 0L..1L
        GYRO_SENSOR -> value in 0L..1L
        FOSSIL_CAMERA_SHAKE -> value in 0L..1L
        AUTO_REPORT -> value in 0L..1L
        KANJI_SCRIPT -> value in 0L..1L
    }
    fun savedLabel(value: Long): String? = when (this) {
        TEXT_SPEED -> BdspGameOptionNames.textSpeed(value)
        BATTLE_STYLE -> if (acceptsSaved(value)) BdspGameOptionNames.battleStyle(value.toInt()) else null
        MOVE_ANIMATIONS -> if (acceptsSaved(value)) BdspGameOptionNames.moveAnimations(value.toInt()) else null
        PARTY_BOX -> if (acceptsSaved(value)) BdspGameOptionNames.partyBox(value.toInt()) else null
        CAMERA_VERTICAL -> if (acceptsSaved(value)) BdspGameOptionNames.camera(value.toInt()) else null
        CAMERA_HORIZONTAL -> if (acceptsSaved(value)) BdspGameOptionNames.camera(value.toInt()) else null
        INPUT_MODE -> if (acceptsSaved(value)) BdspGameOptionNames.inputMode(value.toInt()) else null
        SHOW_NICKNAMES -> if (acceptsSaved(value)) choices[value.toInt()] else null
        NICKNAME_REGISTRATION -> if (acceptsSaved(value)) choices[value.toInt()] else null
        GYRO_SENSOR -> if (acceptsSaved(value)) choices[value.toInt()] else null
        FOSSIL_CAMERA_SHAKE -> if (acceptsSaved(value)) choices[value.toInt()] else null
        AUTO_REPORT -> if (acceptsSaved(value)) choices[value.toInt()] else null
        KANJI_SCRIPT -> if (acceptsSaved(value)) choices[value.toInt()] else null
    }
}

/** Bounded native edits. Readers and all other Switch write capabilities remain isolated. */
object BdspTextSpeedEditing {
    const val OFFSET = 0x79B74
    private const val HASH_OFFSET = 0xE9818

    /** Empty output documents are allowed here; native source validation stays mandatory in stage. */
    fun readDocumentBytes(input: InputStream): ByteArray = input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val max = 0xEF0A4
        while (true) {
            val count = it.read(buffer, 0, minOf(buffer.size, max + 1 - output.size()))
            if (count < 0) break
            if (count == 0) {
                val single = it.read()
                if (single < 0) break
                output.write(single)
            } else output.write(buffer, 0, count)
            require(output.size() <= max) { "The document exceeds the supported BDSP save size." }
        }
        output.toByteArray()
    }

    fun stage(source: ByteArray, expectedSha256: String, speed: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, speed, BdspEditableSetting.TEXT_SPEED)

    fun stageBattleStyle(source: ByteArray, expectedSha256: String, style: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, style, BdspEditableSetting.BATTLE_STYLE)

    fun stageMoveAnimations(source: ByteArray, expectedSha256: String, animations: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, animations, BdspEditableSetting.MOVE_ANIMATIONS)

    fun stagePartyBox(source: ByteArray, expectedSha256: String, preference: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, preference, BdspEditableSetting.PARTY_BOX)

    fun stageVerticalCamera(source: ByteArray, expectedSha256: String, camera: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, camera, BdspEditableSetting.CAMERA_VERTICAL)

    fun stageHorizontalCamera(source: ByteArray, expectedSha256: String, camera: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, camera, BdspEditableSetting.CAMERA_HORIZONTAL)

    fun stageInputMode(source: ByteArray, expectedSha256: String, inputMode: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, inputMode, BdspEditableSetting.INPUT_MODE)

    fun stageShowNicknames(source: ByteArray, expectedSha256: String, enabled: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, enabled, BdspEditableSetting.SHOW_NICKNAMES)

    fun stageNicknameRegistration(source: ByteArray, expectedSha256: String, enabled: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, enabled, BdspEditableSetting.NICKNAME_REGISTRATION)

    fun stageGyroSensor(source: ByteArray, expectedSha256: String, enabled: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, enabled, BdspEditableSetting.GYRO_SENSOR)

    fun stageFossilCameraShake(source: ByteArray, expectedSha256: String, enabled: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, enabled, BdspEditableSetting.FOSSIL_CAMERA_SHAKE)

    fun stageAutoReport(source: ByteArray, expectedSha256: String, enabled: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, enabled, BdspEditableSetting.AUTO_REPORT)

    fun stageKanjiScript(source: ByteArray, expectedSha256: String, enabled: Int): BdspTextSpeedCopyPlan =
        stageSetting(source, expectedSha256, enabled, BdspEditableSetting.KANJI_SCRIPT)

    private fun stageSetting(source: ByteArray, expectedSha256: String, speed: Int, setting: BdspEditableSetting): BdspTextSpeedCopyPlan {
        val original = source.copyOf()
        require(sha256(original) == expectedSha256) { "The source changed. Reopen it from PSE." }
        require(speed in setting.choices.indices) { "Choose a supported ${setting.label}." }
        val before = BdspGameOptionsReadOnly.read(original)
        require(setting.acceptsSaved(setting.current(before))) { "Unknown saved ${setting.label}; editing is unavailable." }
        require(setting.current(before) != speed.toLong()) { "No ${setting.label} change selected." }
        val edited = original.copyOf()
        repeat(setting.width) { edited[setting.offset + it] = (speed ushr (8 * it)).toByte() }
        edited.fill(0, HASH_OFFSET, HASH_OFFSET + 16)
        MessageDigest.getInstance("MD5").digest(edited).copyInto(edited, HASH_OFFSET)
        verify(original, edited, speed, setting)
        return BdspTextSpeedCopyPlan(original, edited, speed, setting)
    }

    internal fun verify(original: ByteArray, edited: ByteArray, speed: Int, setting: BdspEditableSetting = BdspEditableSetting.TEXT_SPEED) {
        val before = BdspGameOptionsReadOnly.read(original)
        val after = BdspGameOptionsReadOnly.read(edited)
        require(speed in setting.choices.indices && setting.acceptsSaved(setting.current(before))) { "Unsupported setting value." }
        require(original.size == edited.size && before.version == after.version && before.revision == after.revision &&
            setting.current(after) == speed.toLong()) { "The edited save did not reopen exactly." }
        require(original.indices.all { it in setting.offset until setting.offset + setting.width || it in HASH_OFFSET until HASH_OFFSET + 16 || original[it] == edited[it] }) {
            "An unrelated save byte changed."
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Single-use export transaction to a newly created, empty document. Never replaces a source. */
class BdspTextSpeedCopyPlan internal constructor(original: ByteArray, edited: ByteArray, val speed: Int,
    val setting: BdspEditableSetting = BdspEditableSetting.TEXT_SPEED) {
    private val original = original.copyOf()
    private val edited = edited.copyOf()
    val sourceSha256 = BdspTextSpeedEditing.sha256(this.original)
    val editedSha256 = BdspTextSpeedEditing.sha256(this.edited)
    private var consumed = false

    @Synchronized fun saveNewCopy(
        readSource: () -> ByteArray,
        retainBackup: (ByteArray) -> ByteArray,
        readDestination: () -> ByteArray,
        writeDestination: (ByteArray) -> Unit,
        discardDestination: () -> Boolean
    ) {
        check(!consumed) { "This review was already used. Reopen the editor." }
        consumed = true
        fun requireSource() = require(readSource().contentEquals(original)) { "The selected source changed. Reopen it from PSE." }
        requireSource()
        require(readDestination().isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
        val backup = retainBackup(original.copyOf())
        require(backup.contentEquals(original)) { "The recovery backup did not verify. Nothing was written." }
        requireSource()
        require(readDestination().isEmpty()) { "The destination changed. Nothing was written." }
        var started = false
        try {
            BdspTextSpeedEditing.verify(original, edited, speed, setting)
            started = true
            writeDestination(edited.copyOf())
            val persisted = readDestination()
            require(persisted.contentEquals(edited)) { "The saved copy differs from the reviewed edit." }
            BdspTextSpeedEditing.verify(original, persisted, speed, setting)
            requireSource()
        } catch (failure: Exception) {
            val removed = started && runCatching { discardDestination() }.getOrDefault(false)
            throw IllegalStateException((failure.message ?: "Copy verification failed.") +
                if (removed) " The failed new copy was removed; the recovery backup is retained."
                else " The new copy could not be removed. Do not use it; the recovery backup is retained.", failure)
        }
    }
}
