package com.example.pokemonsavemanager

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspTextSpeedEditingTest {
    private fun seal(b: ByteArray) {
        b.fill(0, 0xE9818, 0xE9828)
        MessageDigest.getInstance("MD5").digest(b).copyInto(b, 0xE9818)
    }
    private fun fixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 1): ByteArray =
        ByteArray(revision.fileSize) { (it * 37 + 11).toByte() }.also { b ->
            repeat(4) { b[it] = (revision.code ushr (8 * it)).toByte(); b[0x79B74 + it] = (old ushr (8 * it)).toByte() }
            b[0x79BDF] = title.toByte(); seal(b)
        }
    private fun plan(b: ByteArray, speed: Int = 2) = BdspTextSpeedEditing.stage(b, BdspTextSpeedEditing.sha256(b), speed)

    private fun battleFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        fixture(revision, title, 3).also { b ->
            repeat(4) { b[0x79B88 + it] = (old ushr (8 * it)).toByte() }
            seal(b)
        }
    private fun battlePlan(b: ByteArray, style: Int = 1) =
        BdspTextSpeedEditing.stageBattleStyle(b, BdspTextSpeedEditing.sha256(b), style)

    private fun animationFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        battleFixture(revision, title).also { b ->
            repeat(4) { b[0x79B84 + it] = (old ushr (8 * it)).toByte() }; seal(b)
        }
    private fun animationPlan(b: ByteArray, animations: Int = 1) =
        BdspTextSpeedEditing.stageMoveAnimations(b, BdspTextSpeedEditing.sha256(b), animations)

    @Test fun animationsBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeWordAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = animationFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = animationPlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.moveAnimationsId); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(if (target == 0) "On" else "Off", BdspGameOptionNames.moveAnimations(read.moveAnimationsId))
            assertEquals(BdspEditableSetting.MOVE_ANIMATIONS, p.setting)
            assertEquals(listOf(target, 0, 0, 0), output.slice(0x79B84..0x79B87).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79B84..0x79B87 || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun animationsRejectUnknownFullWordsInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = animationFixture(); val before = b.copyOf()
        for (word in listOf(2L, 0x100L, 0x101L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val bad = animationFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { animationPlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { animationPlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { animationPlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageMoveAnimations(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { animationPlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun animationScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = animationFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        animationPlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B74, 0x79B88)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.MOVE_ANIMATIONS)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE)) {
            val source = animationFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = if (setting == BdspEditableSetting.TEXT_SPEED) plan(source, target) else battlePlan(source, target)
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79B84] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun animationsRetainBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = animationFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                animationPlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    private fun partyBoxFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        animationFixture(revision, title).also { b ->
            repeat(4) { b[0x79B8C + it] = (old ushr (8 * it)).toByte() }; seal(b)
        }
    private fun partyBoxPlan(b: ByteArray, preference: Int = 1) =
        BdspTextSpeedEditing.stagePartyBox(b, BdspTextSpeedEditing.sha256(b), preference)

    @Test fun partyBoxBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeWordAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = partyBoxFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = partyBoxPlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.partyBoxId); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(if (target == 0) "Select" else "Send to Box", BdspGameOptionNames.partyBox(read.partyBoxId))
            assertEquals(BdspEditableSetting.PARTY_BOX, p.setting)
            assertEquals(listOf(target, 0, 0, 0), output.slice(0x79B8C..0x79B8F).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79B8C..0x79B8F || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun partyBoxRejectsUnknownFullWordsInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = partyBoxFixture(); val before = b.copyOf()
        for (word in listOf(2L, 0x100L, 0x101L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val bad = partyBoxFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { partyBoxPlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { partyBoxPlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { partyBoxPlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stagePartyBox(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { partyBoxPlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun partyBoxScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = partyBoxFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        partyBoxPlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B74, 0x79B88, 0x79B84)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.PARTY_BOX)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE, BdspEditableSetting.MOVE_ANIMATIONS)) {
            val source = partyBoxFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = when (setting) {
                BdspEditableSetting.TEXT_SPEED -> plan(source, target)
                BdspEditableSetting.BATTLE_STYLE -> battlePlan(source, target)
                BdspEditableSetting.MOVE_ANIMATIONS -> animationPlan(source, target)
                else -> error("Unexpected prior setting")
            }
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79B8C] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun partyBoxRetainsBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = partyBoxFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                partyBoxPlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    private fun verticalCameraFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        partyBoxFixture(revision, title).also { b ->
            repeat(4) { b[0x79B9C + it] = (old ushr (8 * it)).toByte() }; seal(b)
        }
    private fun verticalCameraPlan(b: ByteArray, camera: Int = 1) =
        BdspTextSpeedEditing.stageVerticalCamera(b, BdspTextSpeedEditing.sha256(b), camera)

    @Test fun verticalCameraBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeWordAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = verticalCameraFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = verticalCameraPlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.cameraVerticalId); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraHorizontalId, read.cameraHorizontalId)
            assertEquals(if (target == 0) "Normal" else "Reverse", BdspGameOptionNames.camera(read.cameraVerticalId))
            assertEquals(BdspEditableSetting.CAMERA_VERTICAL, p.setting)
            assertEquals(listOf(target, 0, 0, 0), output.slice(0x79B9C..0x79B9F).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79B9C..0x79B9F || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun verticalCameraRejectsUnknownFullWordsInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = verticalCameraFixture(); val before = b.copyOf()
        for (word in listOf(2L, 0x100L, 0x101L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val bad = verticalCameraFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { verticalCameraPlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { verticalCameraPlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { verticalCameraPlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageVerticalCamera(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { verticalCameraPlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun verticalCameraScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = verticalCameraFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        verticalCameraPlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B74, 0x79B88, 0x79B84, 0x79B8C)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.CAMERA_VERTICAL)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE, BdspEditableSetting.MOVE_ANIMATIONS, BdspEditableSetting.PARTY_BOX)) {
            val source = verticalCameraFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = when (setting) {
                BdspEditableSetting.TEXT_SPEED -> plan(source, target)
                BdspEditableSetting.BATTLE_STYLE -> battlePlan(source, target)
                BdspEditableSetting.MOVE_ANIMATIONS -> animationPlan(source, target)
                BdspEditableSetting.PARTY_BOX -> partyBoxPlan(source, target)
                else -> error("Unexpected prior setting")
            }
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79B9C] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun verticalCameraRetainsBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = verticalCameraFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                verticalCameraPlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    private fun horizontalCameraFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        verticalCameraFixture(revision, title).also { b ->
            repeat(4) { b[0x79BA0 + it] = (old ushr (8 * it)).toByte() }; seal(b)
        }
    private fun horizontalCameraPlan(b: ByteArray, camera: Int = 1) =
        BdspTextSpeedEditing.stageHorizontalCamera(b, BdspTextSpeedEditing.sha256(b), camera)

    @Test fun horizontalCameraBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeWordAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = horizontalCameraFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = horizontalCameraPlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.cameraHorizontalId); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraVerticalId, read.cameraVerticalId)
            assertEquals(if (target == 0) "Normal" else "Reverse", BdspGameOptionNames.camera(read.cameraHorizontalId))
            assertEquals(BdspEditableSetting.CAMERA_HORIZONTAL, p.setting)
            assertEquals(listOf(target, 0, 0, 0), output.slice(0x79BA0..0x79BA3).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79BA0..0x79BA3 || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun horizontalCameraRejectsUnknownFullWordsInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = horizontalCameraFixture(); val before = b.copyOf()
        for (word in listOf(2L, 0x100L, 0x101L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val bad = horizontalCameraFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { horizontalCameraPlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { horizontalCameraPlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { horizontalCameraPlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageHorizontalCamera(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { horizontalCameraPlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun horizontalCameraScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = horizontalCameraFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        horizontalCameraPlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B74, 0x79B88, 0x79B84, 0x79B8C, 0x79B9C)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.CAMERA_HORIZONTAL)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE, BdspEditableSetting.MOVE_ANIMATIONS, BdspEditableSetting.PARTY_BOX, BdspEditableSetting.CAMERA_VERTICAL)) {
            val source = horizontalCameraFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = when (setting) {
                BdspEditableSetting.TEXT_SPEED -> plan(source, target)
                BdspEditableSetting.BATTLE_STYLE -> battlePlan(source, target)
                BdspEditableSetting.MOVE_ANIMATIONS -> animationPlan(source, target)
                BdspEditableSetting.PARTY_BOX -> partyBoxPlan(source, target)
                BdspEditableSetting.CAMERA_VERTICAL -> verticalCameraPlan(source, target)
                else -> error("Unexpected prior setting")
            }
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79BA0] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun horizontalCameraRetainsBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = horizontalCameraFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                horizontalCameraPlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    private fun inputModeFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        horizontalCameraFixture(revision, title).also { b ->
            repeat(4) { b[0x79BA8 + it] = (old ushr (8 * it)).toByte() }; seal(b)
        }
    private fun inputModePlan(b: ByteArray, inputMode: Int = 1) =
        BdspTextSpeedEditing.stageInputMode(b, BdspTextSpeedEditing.sha256(b), inputMode)

    @Test fun inputModeBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeWordAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = inputModeFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = inputModePlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.inputModeId); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraVerticalId, read.cameraVerticalId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraHorizontalId, read.cameraHorizontalId)
            assertEquals(listOf("Easy", "Normal"), p.setting.choices)
            assertEquals(if (target == 0) "Easy" else "Normal", p.setting.savedLabel(target.toLong()))
            assertEquals(if (target == 0) "Easy" else "Normal", BdspGameOptionNames.inputMode(read.inputModeId))
            assertEquals(BdspEditableSetting.INPUT_MODE, p.setting)
            assertEquals(listOf(target, 0, 0, 0), output.slice(0x79BA8..0x79BAB).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79BA8..0x79BAB || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun inputModeRejectsUnknownFullWordsInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = inputModeFixture(); val before = b.copyOf()
        for (word in listOf(2L, 0x100L, 0x101L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val bad = inputModeFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { inputModePlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { inputModePlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { inputModePlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageInputMode(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { inputModePlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun inputModeScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = inputModeFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        inputModePlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B74, 0x79B88, 0x79B84, 0x79B8C, 0x79B9C, 0x79BA0)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.INPUT_MODE)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE, BdspEditableSetting.MOVE_ANIMATIONS, BdspEditableSetting.PARTY_BOX, BdspEditableSetting.CAMERA_VERTICAL, BdspEditableSetting.CAMERA_HORIZONTAL)) {
            val source = inputModeFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = when (setting) {
                BdspEditableSetting.TEXT_SPEED -> plan(source, target)
                BdspEditableSetting.BATTLE_STYLE -> battlePlan(source, target)
                BdspEditableSetting.MOVE_ANIMATIONS -> animationPlan(source, target)
                BdspEditableSetting.PARTY_BOX -> partyBoxPlan(source, target)
                BdspEditableSetting.CAMERA_VERTICAL -> verticalCameraPlan(source, target)
                BdspEditableSetting.CAMERA_HORIZONTAL -> horizontalCameraPlan(source, target)
                else -> error("Unexpected prior setting")
            }
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79BA8] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun inputModeRetainsBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = inputModeFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                inputModePlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    private fun nicknameFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        inputModeFixture(revision, title).also { b ->
            b[0x79BAC] = old.toByte()
            b[0x79BAD] = 0xA5.toByte(); b[0x79BAE] = 0x5A; b[0x79BAF] = 0xFF.toByte(); seal(b)
        }
    private fun nicknamePlan(b: ByteArray, nickname: Int = 1) =
        BdspTextSpeedEditing.stageShowNicknames(b, BdspTextSpeedEditing.sha256(b), nickname)

    @Test fun nicknameBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeByteAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = nicknameFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = nicknamePlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawByte); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraVerticalId, read.cameraVerticalId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraHorizontalId, read.cameraHorizontalId)
            assertEquals(listOf("Off", "On"), p.setting.choices)
            assertEquals(if (target == 0) "Off" else "On", p.setting.savedLabel(target.toLong()))
            assertEquals(target == 1, read.flag(BdspGameOptionFlag.SHOW_NICKNAMES).nativeEnabled)
            assertEquals(listOf(0xA5, 0x5A, 0xFF), output.slice(0x79BAD..0x79BAF).map { it.toInt() and 255 })
            assertEquals(BdspEditableSetting.SHOW_NICKNAMES, p.setting)
            assertEquals(1, p.setting.width)
            for (earlier in BdspEditableSetting.values().filter {
                it != BdspEditableSetting.SHOW_NICKNAMES && it != BdspEditableSetting.NICKNAME_REGISTRATION &&
                    it != BdspEditableSetting.GYRO_SENSOR && it != BdspEditableSetting.FOSSIL_CAMERA_SHAKE && it != BdspEditableSetting.AUTO_REPORT && it != BdspEditableSetting.KANJI_SCRIPT
            }) assertEquals(4, earlier.width)
            assertEquals(BdspGameOptionsReadOnly.read(original).inputModeId, read.inputModeId)
            assertEquals(listOf(target), output.slice(0x79BAC..0x79BAC).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79BAC..0x79BAC || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun nicknameRejectsUnknownBytesInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = nicknameFixture(); val before = b.copyOf()
        for (word in listOf(2L, 127L, 128L, 255L)) {
            val bad = nicknameFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { nicknamePlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { nicknamePlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { nicknamePlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageShowNicknames(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { nicknamePlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun nicknameScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = nicknameFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        nicknamePlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B74, 0x79B88, 0x79B84, 0x79B8C, 0x79B9C, 0x79BA0, 0x79BA8, 0x79BAD, 0x79BAE, 0x79BAF)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.SHOW_NICKNAMES)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE, BdspEditableSetting.MOVE_ANIMATIONS, BdspEditableSetting.PARTY_BOX, BdspEditableSetting.CAMERA_VERTICAL, BdspEditableSetting.CAMERA_HORIZONTAL, BdspEditableSetting.INPUT_MODE)) {
            val source = nicknameFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = when (setting) {
                BdspEditableSetting.TEXT_SPEED -> plan(source, target)
                BdspEditableSetting.BATTLE_STYLE -> battlePlan(source, target)
                BdspEditableSetting.MOVE_ANIMATIONS -> animationPlan(source, target)
                BdspEditableSetting.PARTY_BOX -> partyBoxPlan(source, target)
                BdspEditableSetting.CAMERA_VERTICAL -> verticalCameraPlan(source, target)
                BdspEditableSetting.CAMERA_HORIZONTAL -> horizontalCameraPlan(source, target)
                BdspEditableSetting.INPUT_MODE -> inputModePlan(source, target)
                else -> error("Unexpected prior setting")
            }
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79BAC] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun nicknameRetainsBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = nicknameFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                nicknamePlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    private fun registrationFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        nicknameFixture(revision, title).also { b ->
            b[0x79B90] = old.toByte()
            b[0x79B91] = 0xA5.toByte(); b[0x79B92] = 0x5A; b[0x79B93] = 0xFF.toByte(); seal(b)
        }
    private fun registrationPlan(b: ByteArray, registration: Int = 1) =
        BdspTextSpeedEditing.stageNicknameRegistration(b, BdspTextSpeedEditing.sha256(b), registration)

    @Test fun registrationBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeByteAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = registrationFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = registrationPlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).rawByte); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraVerticalId, read.cameraVerticalId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraHorizontalId, read.cameraHorizontalId)
            assertEquals(listOf("Off", "On"), p.setting.choices)
            assertEquals(if (target == 0) "Off" else "On", p.setting.savedLabel(target.toLong()))
            assertEquals(target == 1, read.flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).nativeEnabled)
            assertEquals(listOf(0xA5, 0x5A, 0xFF), output.slice(0x79B91..0x79B93).map { it.toInt() and 255 })
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawWord,
                read.flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawWord)
            assertEquals(BdspEditableSetting.NICKNAME_REGISTRATION, p.setting)
            assertEquals(1, p.setting.width)
            for (earlier in BdspEditableSetting.values().filter { it != p.setting })
                assertEquals(if (earlier == BdspEditableSetting.SHOW_NICKNAMES || earlier == BdspEditableSetting.GYRO_SENSOR || earlier == BdspEditableSetting.FOSSIL_CAMERA_SHAKE || earlier == BdspEditableSetting.AUTO_REPORT || earlier == BdspEditableSetting.KANJI_SCRIPT) 1 else 4, earlier.width)
            assertEquals(BdspGameOptionsReadOnly.read(original).inputModeId, read.inputModeId)
            assertEquals(listOf(target), output.slice(0x79B90..0x79B90).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79B90..0x79B90 || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun registrationRejectsUnknownBytesInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = registrationFixture(); val before = b.copyOf()
        for (word in listOf(2L, 127L, 128L, 255L)) {
            val bad = registrationFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { registrationPlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { registrationPlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { registrationPlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageNicknameRegistration(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { registrationPlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun registrationScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = registrationFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        registrationPlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B74, 0x79B88, 0x79B84, 0x79B8C, 0x79B9C, 0x79BA0, 0x79BA8, 0x79BAC, 0x79B91, 0x79B92, 0x79B93)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.NICKNAME_REGISTRATION)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE, BdspEditableSetting.MOVE_ANIMATIONS, BdspEditableSetting.PARTY_BOX, BdspEditableSetting.CAMERA_VERTICAL, BdspEditableSetting.CAMERA_HORIZONTAL, BdspEditableSetting.INPUT_MODE, BdspEditableSetting.SHOW_NICKNAMES)) {
            val source = registrationFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = when (setting) {
                BdspEditableSetting.TEXT_SPEED -> plan(source, target)
                BdspEditableSetting.BATTLE_STYLE -> battlePlan(source, target)
                BdspEditableSetting.MOVE_ANIMATIONS -> animationPlan(source, target)
                BdspEditableSetting.PARTY_BOX -> partyBoxPlan(source, target)
                BdspEditableSetting.CAMERA_VERTICAL -> verticalCameraPlan(source, target)
                BdspEditableSetting.CAMERA_HORIZONTAL -> horizontalCameraPlan(source, target)
                BdspEditableSetting.INPUT_MODE -> inputModePlan(source, target)
                BdspEditableSetting.SHOW_NICKNAMES -> nicknamePlan(source, target)
                else -> error("Unexpected prior setting")
            }
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79B90] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun registrationRetainsBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = registrationFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                registrationPlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    private fun gyroFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        registrationFixture(revision, title).also { b ->
            b[0x79B94] = old.toByte()
            b[0x79B95] = 0xA5.toByte(); b[0x79B96] = 0x5A; b[0x79B97] = 0xFF.toByte(); seal(b)
        }
    private fun gyroPlan(b: ByteArray, gyro: Int = 1) =
        BdspTextSpeedEditing.stageGyroSensor(b, BdspTextSpeedEditing.sha256(b), gyro)

    @Test fun gyroBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeByteAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = gyroFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = gyroPlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.flag(BdspGameOptionFlag.GYRO_SENSOR).rawByte); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraVerticalId, read.cameraVerticalId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraHorizontalId, read.cameraHorizontalId)
            assertEquals(listOf("Off", "On"), p.setting.choices)
            assertEquals(if (target == 0) "Off" else "On", p.setting.savedLabel(target.toLong()))
            assertEquals(target == 1, read.flag(BdspGameOptionFlag.GYRO_SENSOR).nativeEnabled)
            assertEquals(listOf(0xA5, 0x5A, 0xFF), output.slice(0x79B95..0x79B97).map { it.toInt() and 255 })
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawWord,
                read.flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).rawWord,
                read.flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).rawWord)
            assertEquals(BdspEditableSetting.GYRO_SENSOR, p.setting)
            assertEquals(1, p.setting.width)
            for (earlier in BdspEditableSetting.values().filter { it != p.setting })
                assertEquals(if (earlier == BdspEditableSetting.SHOW_NICKNAMES || earlier == BdspEditableSetting.NICKNAME_REGISTRATION || earlier == BdspEditableSetting.FOSSIL_CAMERA_SHAKE || earlier == BdspEditableSetting.AUTO_REPORT || earlier == BdspEditableSetting.KANJI_SCRIPT) 1 else 4, earlier.width)
            assertEquals(BdspGameOptionsReadOnly.read(original).inputModeId, read.inputModeId)
            assertEquals(listOf(target), output.slice(0x79B94..0x79B94).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79B94..0x79B94 || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun gyroRejectsUnknownBytesInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = gyroFixture(); val before = b.copyOf()
        for (word in listOf(2L, 127L, 128L, 255L)) {
            val bad = gyroFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { gyroPlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { gyroPlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { gyroPlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageGyroSensor(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { gyroPlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun gyroScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = gyroFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        gyroPlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B74, 0x79B88, 0x79B84, 0x79B8C, 0x79B9C, 0x79BA0, 0x79BA8, 0x79BAC, 0x79B90, 0x79B95, 0x79B96, 0x79B97)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.GYRO_SENSOR)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE, BdspEditableSetting.MOVE_ANIMATIONS, BdspEditableSetting.PARTY_BOX, BdspEditableSetting.CAMERA_VERTICAL, BdspEditableSetting.CAMERA_HORIZONTAL, BdspEditableSetting.INPUT_MODE, BdspEditableSetting.SHOW_NICKNAMES, BdspEditableSetting.NICKNAME_REGISTRATION)) {
            val source = gyroFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = when (setting) {
                BdspEditableSetting.TEXT_SPEED -> plan(source, target)
                BdspEditableSetting.BATTLE_STYLE -> battlePlan(source, target)
                BdspEditableSetting.MOVE_ANIMATIONS -> animationPlan(source, target)
                BdspEditableSetting.PARTY_BOX -> partyBoxPlan(source, target)
                BdspEditableSetting.CAMERA_VERTICAL -> verticalCameraPlan(source, target)
                BdspEditableSetting.CAMERA_HORIZONTAL -> horizontalCameraPlan(source, target)
                BdspEditableSetting.INPUT_MODE -> inputModePlan(source, target)
                BdspEditableSetting.SHOW_NICKNAMES -> nicknamePlan(source, target)
                BdspEditableSetting.NICKNAME_REGISTRATION -> registrationPlan(source, target)
                else -> error("Unexpected prior setting")
            }
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79B94] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun gyroRetainsBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = gyroFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                gyroPlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    private fun fossilShakeFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        gyroFixture(revision, title).also { b ->
            b[0x79B98] = old.toByte()
            b[0x79B99] = 0xA5.toByte(); b[0x79B9A] = 0x5A; b[0x79B9B] = 0xFF.toByte(); seal(b)
        }
    private fun fossilShakePlan(b: ByteArray, fossilShake: Int = 1) =
        BdspTextSpeedEditing.stageFossilCameraShake(b, BdspTextSpeedEditing.sha256(b), fossilShake)

    @Test fun fossilShakeBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeByteAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = fossilShakeFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = fossilShakePlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.flag(BdspGameOptionFlag.FOSSIL_CAMERA_SHAKE).rawByte); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraVerticalId, read.cameraVerticalId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraHorizontalId, read.cameraHorizontalId)
            assertEquals(listOf("Off", "On"), p.setting.choices)
            assertEquals(if (target == 0) "Off" else "On", p.setting.savedLabel(target.toLong()))
            assertEquals(target == 1, read.flag(BdspGameOptionFlag.FOSSIL_CAMERA_SHAKE).nativeEnabled)
            assertEquals(listOf(0xA5, 0x5A, 0xFF), output.slice(0x79B99..0x79B9B).map { it.toInt() and 255 })
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawWord,
                read.flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).rawWord,
                read.flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.GYRO_SENSOR).rawWord,
                read.flag(BdspGameOptionFlag.GYRO_SENSOR).rawWord)
            assertEquals(BdspEditableSetting.FOSSIL_CAMERA_SHAKE, p.setting)
            assertEquals(1, p.setting.width)
            for (earlier in BdspEditableSetting.values().filter { it != p.setting })
                assertEquals(if (earlier == BdspEditableSetting.SHOW_NICKNAMES || earlier == BdspEditableSetting.NICKNAME_REGISTRATION || earlier == BdspEditableSetting.GYRO_SENSOR || earlier == BdspEditableSetting.AUTO_REPORT || earlier == BdspEditableSetting.KANJI_SCRIPT) 1 else 4, earlier.width)
            assertEquals(BdspGameOptionsReadOnly.read(original).inputModeId, read.inputModeId)
            assertEquals(listOf(target), output.slice(0x79B98..0x79B98).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79B98..0x79B98 || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun fossilShakeRejectsUnknownBytesInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = fossilShakeFixture(); val before = b.copyOf()
        for (word in listOf(2L, 127L, 128L, 255L)) {
            val bad = fossilShakeFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { fossilShakePlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { fossilShakePlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { fossilShakePlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageFossilCameraShake(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { fossilShakePlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun fossilShakeScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = fossilShakeFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        fossilShakePlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B74, 0x79B88, 0x79B84, 0x79B8C, 0x79B9C, 0x79BA0, 0x79BA8, 0x79BAC, 0x79B90, 0x79B94, 0x79B99, 0x79B9A, 0x79B9B)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.FOSSIL_CAMERA_SHAKE)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE, BdspEditableSetting.MOVE_ANIMATIONS, BdspEditableSetting.PARTY_BOX, BdspEditableSetting.CAMERA_VERTICAL, BdspEditableSetting.CAMERA_HORIZONTAL, BdspEditableSetting.INPUT_MODE, BdspEditableSetting.SHOW_NICKNAMES, BdspEditableSetting.NICKNAME_REGISTRATION, BdspEditableSetting.GYRO_SENSOR)) {
            val source = fossilShakeFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = when (setting) {
                BdspEditableSetting.TEXT_SPEED -> plan(source, target)
                BdspEditableSetting.BATTLE_STYLE -> battlePlan(source, target)
                BdspEditableSetting.MOVE_ANIMATIONS -> animationPlan(source, target)
                BdspEditableSetting.PARTY_BOX -> partyBoxPlan(source, target)
                BdspEditableSetting.CAMERA_VERTICAL -> verticalCameraPlan(source, target)
                BdspEditableSetting.CAMERA_HORIZONTAL -> horizontalCameraPlan(source, target)
                BdspEditableSetting.INPUT_MODE -> inputModePlan(source, target)
                BdspEditableSetting.SHOW_NICKNAMES -> nicknamePlan(source, target)
                BdspEditableSetting.NICKNAME_REGISTRATION -> registrationPlan(source, target)
                BdspEditableSetting.GYRO_SENSOR -> gyroPlan(source, target)
                else -> error("Unexpected prior setting")
            }
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79B98] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun fossilShakeRetainsBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = fossilShakeFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                fossilShakePlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    private fun autoReportFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        fossilShakeFixture(revision, title).also { b ->
            b[0x79BA4] = old.toByte()
            b[0x79BA5] = 0xA5.toByte(); b[0x79BA6] = 0x5A; b[0x79BA7] = 0xFF.toByte(); seal(b)
        }
    private fun autoReportPlan(b: ByteArray, autoReport: Int = 1) =
        BdspTextSpeedEditing.stageAutoReport(b, BdspTextSpeedEditing.sha256(b), autoReport)

    @Test fun autoReportBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeByteAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = autoReportFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = autoReportPlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.flag(BdspGameOptionFlag.AUTO_REPORT).rawByte); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraVerticalId, read.cameraVerticalId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraHorizontalId, read.cameraHorizontalId)
            assertEquals(listOf("Off", "On"), p.setting.choices)
            assertEquals(if (target == 0) "Off" else "On", p.setting.savedLabel(target.toLong()))
            assertEquals(target == 1, read.flag(BdspGameOptionFlag.AUTO_REPORT).nativeEnabled)
            assertEquals(listOf(0xA5, 0x5A, 0xFF), output.slice(0x79BA5..0x79BA7).map { it.toInt() and 255 })
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawWord,
                read.flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).rawWord,
                read.flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.GYRO_SENSOR).rawWord,
                read.flag(BdspGameOptionFlag.GYRO_SENSOR).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.FOSSIL_CAMERA_SHAKE).rawWord,
                read.flag(BdspGameOptionFlag.FOSSIL_CAMERA_SHAKE).rawWord)
            assertEquals(BdspEditableSetting.AUTO_REPORT, p.setting)
            assertEquals(1, p.setting.width)
            for (earlier in BdspEditableSetting.values().filter { it != p.setting })
                assertEquals(if (earlier == BdspEditableSetting.SHOW_NICKNAMES || earlier == BdspEditableSetting.NICKNAME_REGISTRATION || earlier == BdspEditableSetting.GYRO_SENSOR || earlier == BdspEditableSetting.FOSSIL_CAMERA_SHAKE || earlier == BdspEditableSetting.KANJI_SCRIPT) 1 else 4, earlier.width)
            assertEquals(BdspGameOptionsReadOnly.read(original).inputModeId, read.inputModeId)
            assertEquals(listOf(target), output.slice(0x79BA4..0x79BA4).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79BA4..0x79BA4 || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun autoReportRejectsUnknownBytesInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = autoReportFixture(); val before = b.copyOf()
        for (word in listOf(2L, 127L, 128L, 255L)) {
            val bad = autoReportFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { autoReportPlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { autoReportPlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { autoReportPlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageAutoReport(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { autoReportPlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun autoReportScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = autoReportFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        autoReportPlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B74, 0x79B88, 0x79B84, 0x79B8C, 0x79B9C, 0x79BA0, 0x79BA8, 0x79BAC, 0x79B90, 0x79B94, 0x79B98, 0x79BA5, 0x79BA6, 0x79BA7)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.AUTO_REPORT)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE, BdspEditableSetting.MOVE_ANIMATIONS, BdspEditableSetting.PARTY_BOX, BdspEditableSetting.CAMERA_VERTICAL, BdspEditableSetting.CAMERA_HORIZONTAL, BdspEditableSetting.INPUT_MODE, BdspEditableSetting.SHOW_NICKNAMES, BdspEditableSetting.NICKNAME_REGISTRATION, BdspEditableSetting.GYRO_SENSOR, BdspEditableSetting.FOSSIL_CAMERA_SHAKE)) {
            val source = autoReportFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = when (setting) {
                BdspEditableSetting.TEXT_SPEED -> plan(source, target)
                BdspEditableSetting.BATTLE_STYLE -> battlePlan(source, target)
                BdspEditableSetting.MOVE_ANIMATIONS -> animationPlan(source, target)
                BdspEditableSetting.PARTY_BOX -> partyBoxPlan(source, target)
                BdspEditableSetting.CAMERA_VERTICAL -> verticalCameraPlan(source, target)
                BdspEditableSetting.CAMERA_HORIZONTAL -> horizontalCameraPlan(source, target)
                BdspEditableSetting.INPUT_MODE -> inputModePlan(source, target)
                BdspEditableSetting.SHOW_NICKNAMES -> nicknamePlan(source, target)
                BdspEditableSetting.NICKNAME_REGISTRATION -> registrationPlan(source, target)
                BdspEditableSetting.GYRO_SENSOR -> gyroPlan(source, target)
                BdspEditableSetting.FOSSIL_CAMERA_SHAKE -> fossilShakePlan(source, target)
                else -> error("Unexpected prior setting")
            }
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79BA4] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun autoReportRetainsBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = autoReportFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                autoReportPlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    private fun kanjiFixture(revision: BdspSaveRevision = BdspSaveRevision.V1_3, title: Int = 0, old: Long = 0): ByteArray =
        autoReportFixture(revision, title).also { b ->
            b[0x79B7C] = old.toByte()
            b[0x79B7D] = 0xA5.toByte(); b[0x79B7E] = 0x5A; b[0x79B7F] = 0xFF.toByte(); seal(b)
        }
    private fun kanjiPlan(b: ByteArray, kanji: Int = 1) =
        BdspTextSpeedEditing.stageKanjiScript(b, BdspTextSpeedEditing.sha256(b), kanji)

    @Test fun kanjiBothTitlesEveryRevisionAndDirectionTouchOnlyTheirNativeByteAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = kanjiFixture(revision, title, old.toLong()); val original = b.copyOf(); val target = 1 - old
            val p = kanjiPlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.flag(BdspGameOptionFlag.KANJI).rawByte); assertEquals(3L, read.textSpeedId); assertEquals(0, read.battleStyleId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraVerticalId, read.cameraVerticalId)
            assertEquals(BdspGameOptionsReadOnly.read(original).cameraHorizontalId, read.cameraHorizontalId)
            assertEquals(listOf("Off", "On"), p.setting.choices)
            assertEquals(if (target == 0) "Off" else "On", p.setting.savedLabel(target.toLong()))
            assertEquals(target == 1, read.flag(BdspGameOptionFlag.KANJI).nativeEnabled)
            assertEquals(listOf(0xA5, 0x5A, 0xFF), output.slice(0x79B7D..0x79B7F).map { it.toInt() and 255 })
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawWord,
                read.flag(BdspGameOptionFlag.SHOW_NICKNAMES).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).rawWord,
                read.flag(BdspGameOptionFlag.NICKNAME_REGISTRATION).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.GYRO_SENSOR).rawWord,
                read.flag(BdspGameOptionFlag.GYRO_SENSOR).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.FOSSIL_CAMERA_SHAKE).rawWord,
                read.flag(BdspGameOptionFlag.FOSSIL_CAMERA_SHAKE).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).flag(BdspGameOptionFlag.AUTO_REPORT).rawWord,
                read.flag(BdspGameOptionFlag.AUTO_REPORT).rawWord)
            assertEquals(BdspGameOptionsReadOnly.read(original).languageId, read.languageId)
            assertEquals(BdspEditableSetting.KANJI_SCRIPT, p.setting)
            assertEquals(1, p.setting.width)
            for (earlier in BdspEditableSetting.values().filter { it != p.setting })
                assertEquals(if (earlier == BdspEditableSetting.SHOW_NICKNAMES || earlier == BdspEditableSetting.NICKNAME_REGISTRATION || earlier == BdspEditableSetting.GYRO_SENSOR || earlier == BdspEditableSetting.FOSSIL_CAMERA_SHAKE || earlier == BdspEditableSetting.AUTO_REPORT) 1 else 4, earlier.width)
            assertEquals(BdspGameOptionsReadOnly.read(original).inputModeId, read.inputModeId)
            assertEquals(listOf(target), output.slice(0x79B7C..0x79B7C).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79B7C..0x79B7C || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun kanjiRejectsUnknownBytesInvalidTargetsNoOpsAndInvalidNativeFiles() {
        val b = kanjiFixture(); val before = b.copyOf()
        for (word in listOf(2L, 127L, 128L, 255L)) {
            val bad = kanjiFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { kanjiPlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { kanjiPlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { kanjiPlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageKanjiScript(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { kanjiPlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun kanjiScopeIsExclusiveAndUnknownUnselectedStyleRemainsByteExact() {
        val b = kanjiFixture().also { bytes -> repeat(4) { bytes[0x79B88 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        kanjiPlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(-1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        for (otherWord in listOf(0x79B78, 0x79B80, 0x79B74, 0x79B88, 0x79B84, 0x79B8C, 0x79B9C, 0x79BA0, 0x79BA8, 0x79BAC, 0x79B90, 0x79B94, 0x79B98, 0x79BA4, 0x79B7D, 0x79B7E, 0x79B7F)) {
            val changedBoth = output.copyOf().also { it[otherWord] = (it[otherWord].toInt() xor 1).toByte(); seal(it) }
            assertTrue(BdspSaveValidation.inspect(changedBoth) is BdspValidation.Verified)
            assertThrows(IllegalArgumentException::class.java) {
                BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.KANJI_SCRIPT)
            }
        }
        for (setting in listOf(BdspEditableSetting.TEXT_SPEED, BdspEditableSetting.BATTLE_STYLE, BdspEditableSetting.MOVE_ANIMATIONS, BdspEditableSetting.PARTY_BOX, BdspEditableSetting.CAMERA_VERTICAL, BdspEditableSetting.CAMERA_HORIZONTAL, BdspEditableSetting.INPUT_MODE, BdspEditableSetting.SHOW_NICKNAMES, BdspEditableSetting.NICKNAME_REGISTRATION, BdspEditableSetting.GYRO_SENSOR, BdspEditableSetting.FOSSIL_CAMERA_SHAKE, BdspEditableSetting.AUTO_REPORT)) {
            val source = kanjiFixture(); val target = if (setting == BdspEditableSetting.TEXT_SPEED) 2 else 1
            val p = when (setting) {
                BdspEditableSetting.TEXT_SPEED -> plan(source, target)
                BdspEditableSetting.BATTLE_STYLE -> battlePlan(source, target)
                BdspEditableSetting.MOVE_ANIMATIONS -> animationPlan(source, target)
                BdspEditableSetting.PARTY_BOX -> partyBoxPlan(source, target)
                BdspEditableSetting.CAMERA_VERTICAL -> verticalCameraPlan(source, target)
                BdspEditableSetting.CAMERA_HORIZONTAL -> horizontalCameraPlan(source, target)
                BdspEditableSetting.INPUT_MODE -> inputModePlan(source, target)
                BdspEditableSetting.SHOW_NICKNAMES -> nicknamePlan(source, target)
                BdspEditableSetting.NICKNAME_REGISTRATION -> registrationPlan(source, target)
                BdspEditableSetting.GYRO_SENSOR -> gyroPlan(source, target)
                BdspEditableSetting.FOSSIL_CAMERA_SHAKE -> fossilShakePlan(source, target)
                BdspEditableSetting.AUTO_REPORT -> autoReportPlan(source, target)
                else -> error("Unexpected prior setting")
            }
            var edited = byteArrayOf()
            p.saveNewCopy({ source }, { it }, { edited.copyOf() }, { edited = it }, { true })
            edited[0x79B7C] = 1; seal(edited)
            assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(source, edited, target, setting) }
        }
    }

    @Test fun kanjiRetainsBackupRaceReopenAndCleanupFailureProtections() {
        for (mode in 0..8) {
            val b = kanjiFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                kanjiPlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    @Test fun battleStyleBothTitlesAllRevisionsAndDirectionsPreserveTextSpeedAndEveryUnrelatedByte() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (old in 0..1) {
            val b = battleFixture(revision, title, old.toLong()); val original = b.copyOf()
            val target = 1 - old; val p = battlePlan(b, target); var backup = byteArrayOf(); var output = byteArrayOf()
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); it }, { output.copyOf() }, { output = it }, { fail("No cleanup on success"); false })
            val read = BdspGameOptionsReadOnly.read(output)
            assertEquals(target, read.battleStyleId); assertEquals(3L, read.textSpeedId)
            assertEquals(BdspEditableSetting.BATTLE_STYLE, p.setting)
            assertEquals(listOf(target, 0, 0, 0), output.slice(0x79B88..0x79B8B).map { it.toInt() and 255 })
            assertTrue(original.indices.all { it in 0x79B88..0x79B8B || it in 0xE9818..0xE9827 || original[it] == output[it] })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(title, read.version.romCode); assertEquals(revision, read.revision)
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun battleStyleRejectsUnknownFullWordsInvalidTargetsNoOpsAndMalformedSources() {
        val b = battleFixture(); val before = b.copyOf()
        for (word in listOf(2L, 0x100L, 0x101L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val bad = battleFixture(old = word); val original = bad.copyOf()
            assertThrows(IllegalArgumentException::class.java) { battlePlan(bad) }; assertArrayEquals(original, bad)
        }
        for (target in listOf(-1, 2, 3, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { battlePlan(b, target) }
        assertThrows(IllegalArgumentException::class.java) { battlePlan(b, 0) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stageBattleStyle(b, "stale", 1) }
        val title = b.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val revision = b.copyOf().also { it[0] = 0x35; seal(it) }
        val digest = b.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(title, revision, digest, b.copyOf(b.size - 1), byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { battlePlan(bad) }
        assertArrayEquals(before, b)
    }

    @Test fun perSettingScopeCannotAuthorizeTheOtherWordAndUnknownUnselectedDataIsPreserved() {
        val b = battleFixture().also { bytes -> repeat(4) { bytes[0x79B74 + it] = 0xFF.toByte() }; seal(bytes) }
        var output = byteArrayOf()
        battlePlan(b).saveNewCopy({ b }, { it }, { output.copyOf() }, { output = it }, { true })
        assertEquals(0xFFFFFFFFL, BdspGameOptionsReadOnly.read(output).textSpeedId)
        assertEquals(1, BdspGameOptionsReadOnly.read(output).battleStyleId)
        val changedBoth = output.copyOf().also { it[0x79B74] = 2; seal(it) }
        assertThrows(IllegalArgumentException::class.java) {
            BdspTextSpeedEditing.verify(b, changedBoth, 1, BdspEditableSetting.BATTLE_STYLE)
        }
        val textSource = battleFixture()
        var textOutput = byteArrayOf()
        plan(textSource).saveNewCopy({ textSource }, { it }, { textOutput.copyOf() }, { textOutput = it }, { true })
        textOutput[0x79B88] = 1; seal(textOutput)
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(textSource, textOutput, 2) }
    }

    @Test fun battleStyleUsesAllBackupRaceReopenAndFailedCopyCleanupProtections() {
        for (mode in 0..8) {
            val b = battleFixture(); val original = b.copyOf(); var output = byteArrayOf(); var backup = byteArrayOf()
            var sourceReads = 0; var targetReads = 0; var writes = 0; var deletions = 0
            val failure = assertThrows(Exception::class.java) {
                battlePlan(b).saveNewCopy(
                    { sourceReads++; if ((mode == 0 && sourceReads == 1) || (mode == 3 && sourceReads == 2) || (mode == 7 && sourceReads == 3)) byteArrayOf(9) else b },
                    { backup = it.copyOf(); if (mode == 1) byteArrayOf(9) else backup.copyOf() },
                    { targetReads++; if ((mode == 2 && targetReads == 1) || (mode == 4 && targetReads == 2)) byteArrayOf(9) else output.copyOf() },
                    { writes++; output = it; if (mode == 5 || mode == 8) { output = it.copyOf(100); throw IOException("partial") }
                        if (mode == 6) output[99] = (output[99].toInt() xor 1).toByte() },
                    { deletions++; if (mode == 8) false else { output = byteArrayOf(); true } })
            }
            assertArrayEquals(original, b)
            if (mode <= 4) { assertEquals(0, writes); assertEquals(0, deletions) }
            else {
                assertEquals(1, writes); assertEquals(1, deletions); assertArrayEquals(original, backup)
                assertTrue(failure.message!!.contains(if (mode == 8) "Do not use it" else "was removed"))
                if (mode != 8) assertTrue(output.isEmpty())
            }
        }
    }

    @Test fun bothTitlesEveryRevisionAndSelectableSpeedChangeOnlyTheNativeWordAndDigest() {
        for (revision in BdspSaveRevision.values()) for (title in 0..1) for (speed in 0..2) {
            val b = fixture(revision, title, 3); val original = b.copyOf(); var backup = byteArrayOf(); var output = byteArrayOf()
            val p = plan(b, speed)
            p.saveNewCopy({ b.copyOf() }, { backup = it.copyOf(); backup.copyOf() }, { output.copyOf() }, { output = it }, { fail("No rollback on success"); false })
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
            assertEquals(speed.toLong(), BdspGameOptionsReadOnly.read(output).textSpeedId)
            assertEquals(title, (BdspSaveValidation.inspect(output) as BdspValidation.Verified).version.romCode)
            assertTrue(original.indices.all { it in 0x79B74..0x79B77 || it in 0xE9818..0xE9827 || original[it] == output[it] })
            assertEquals(listOf(speed, 0, 0, 0), output.slice(0x79B74..0x79B77).map { it.toInt() and 255 })
            val zeroed = output.copyOf().also { it.fill(0, 0xE9818, 0xE9828) }
            assertArrayEquals(MessageDigest.getInstance("MD5").digest(zeroed), output.copyOfRange(0xE9818, 0xE9828))
            assertEquals(BdspTextSpeedEditing.sha256(output), p.editedSha256)
            assertThrows(IllegalStateException::class.java) { p.saveNewCopy({ b }, { it }, { byteArrayOf() }, {}, { true }) }
        }
    }

    @Test fun invalidIdentityIntegrityUnknownSpeedAndNoOpRejectWithoutMutation() {
        val valid = fixture(); val original = valid.copyOf()
        for (speed in listOf(-1, 3, 4, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { plan(valid, speed) }
        assertThrows(IllegalArgumentException::class.java) { plan(valid, 1) }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.stage(valid, "stale", 2) }
        for (old in listOf(4L, 0x100L, 0xFFFFFFFFL)) assertThrows(IllegalArgumentException::class.java) { plan(fixture(old = old)) }
        val badTitle = valid.copyOf().also { it[0x79BDF] = 2; seal(it) }
        val badRevision = valid.copyOf().also { it[0] = 0x35; seal(it) }
        val badHash = valid.copyOf().also { it[0xE9818] = (it[0xE9818].toInt() xor 1).toByte() }
        for (bad in listOf(badTitle, badRevision, badHash, valid.copyOf(valid.size - 1), byteArrayOf())) {
            val before = bad.copyOf(); assertThrows(IllegalArgumentException::class.java) { plan(bad) }; assertArrayEquals(before, bad)
        }
        assertArrayEquals(original, valid)
        val unrelated = valid.copyOf().also {
            repeat(4) { i -> it[0x79B74 + i] = (if (i == 0) 2 else 0).toByte() }
            it[0x79B78] = (it[0x79B78].toInt() xor 1).toByte()
            seal(it)
        }
        assertTrue(BdspSaveValidation.inspect(unrelated) is BdspValidation.Verified)
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.verify(valid, unrelated, 2) }
    }

    @Test fun staleSourceOrExistingDestinationNeverBacksUpWritesOrDeletesAnything() {
        val b = fixture()
        for (stale in listOf(true, false)) {
            val p = plan(b)
            assertThrows(IllegalArgumentException::class.java) {
                p.saveNewCopy({ if (stale) byteArrayOf(9) else b }, { fail("No backup"); it },
                    { byteArrayOf(7) }, { fail("No write") }, { fail("No delete"); false })
            }
        }
    }

    @Test fun failedBackupAndSourceOrDestinationRaceStopBeforeWrite() {
        val b = fixture()
        for (mode in 0..3) {
            var sourceReads = 0; var destinationReads = 0
            assertThrows(Exception::class.java) {
                plan(b).saveNewCopy(
                    { sourceReads++; if (mode == 2 && sourceReads > 1) byteArrayOf(8) else b },
                    { if (mode == 0) throw IOException("Backup write"); if (mode == 1) byteArrayOf(1) else it },
                    { destinationReads++; if (mode == 3 && destinationReads > 1) byteArrayOf(1) else byteArrayOf() },
                    { fail("No write after failed backup or race") }, { fail("No delete"); false })
            }
        }
    }

    @Test fun partialWriteAndCorruptReopenRemoveOnlyTheFailedNewCopyAndRetainBackup() {
        val b = fixture(); val original = b.copyOf()
        for (partial in listOf(true, false)) {
            var backup = byteArrayOf(); var output = byteArrayOf(); var deleted = false
            val error = assertThrows(IllegalStateException::class.java) {
                plan(b).saveNewCopy({ b }, { backup = it; it.copyOf() }, { output.copyOf() }, {
                    output = if (partial) it.copyOf(100) else it.also { bytes -> bytes[99] = (bytes[99].toInt() xor 1).toByte() }
                    if (partial) throw IOException("Partial stream failure")
                }, { deleted = true; output = byteArrayOf(); true })
            }
            assertTrue(deleted); assertTrue(output.isEmpty()); assertTrue(error.message!!.contains("failed new copy was removed"))
            assertArrayEquals(original, b); assertArrayEquals(original, backup)
        }
    }

    @Test fun cleanupFailureIsExplicitAndSourceChangesAfterWriteInvalidateTheNewCopy() {
        val b = fixture()
        for (cleanupSucceeds in listOf(true, false)) {
            var output = byteArrayOf(); var sourceReads = 0; var cleanupCalled = false
            val error = assertThrows(IllegalStateException::class.java) {
                plan(b).saveNewCopy({ sourceReads++; if (sourceReads >= 3) byteArrayOf(5) else b }, { it },
                    { output.copyOf() }, { output = it }, { cleanupCalled = true; cleanupSucceeds })
            }
            assertTrue(cleanupCalled)
            assertTrue(error.message!!.contains(if (cleanupSucceeds) "was removed" else "Do not use it"))
        }
    }

    @Test fun planOwnsDetachedBytesAndNeverLeaksMutableStateToCallbacks() {
        val b = fixture(); val original = b.copyOf(); val p = plan(b); b.fill(0); var output = byteArrayOf()
        p.saveNewCopy({ original.copyOf() }, { val saved = it.copyOf(); it.fill(0); saved }, { output.copyOf() }, { output = it }, { true })
        assertEquals(2L, BdspGameOptionsReadOnly.read(output).textSpeedId)
        assertEquals(BdspTextSpeedEditing.sha256(original), p.sourceSha256)
    }

    @Test fun boundedDocumentReadsAllowEmptyTargetsCloseStreamsAndRejectOversizeOrReadFailures() {
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf()) { override fun close() { closed = true; super.close() } }
        assertTrue(BdspTextSpeedEditing.readDocumentBytes(input).isEmpty()); assertTrue(closed)
        val b = fixture(); assertArrayEquals(b, BdspTextSpeedEditing.readDocumentBytes(ByteArrayInputStream(b)))
        closed = false
        val large = object : ByteArrayInputStream(ByteArray(0xEF0A5)) { override fun close() { closed = true; super.close() } }
        assertThrows(IllegalArgumentException::class.java) { BdspTextSpeedEditing.readDocumentBytes(large) }; assertTrue(closed)
        closed = false
        val broken = object : java.io.InputStream() { override fun read(): Int = throw IOException("read"); override fun close() { closed = true } }
        assertThrows(IOException::class.java) { BdspTextSpeedEditing.readDocumentBytes(broken) }; assertTrue(closed)
    }
}
