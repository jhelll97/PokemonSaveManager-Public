package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Gen6Pk6FixedSlotLayoutTest {
    private val destination = Gen6ConverterGame.ALPHA_SAPPHIRE
    private val slotOffsets = intArrayOf(
        5,
        5 + Gen6Pk6Adapter.STORED_SIZE + 7
    )
    private val template =
        ByteArray(slotOffsets.last() + Gen6Pk6Adapter.STORED_SIZE + 4) { 0x5A }.apply {
            slotOffsets.forEach { offset ->
                fill(0.toByte(), offset, offset + Gen6Pk6Adapter.STORED_SIZE)
            }
        }
    private val vacancy = Gen6Pk6SlotVacancyValidator { bytes, game ->
        assertEquals(destination, game)
        bytes.all { it == 0.toByte() }
    }
    private val layout = Gen6Pk6FixedSlotLayout(destination, template, slotOffsets, vacancy)

    private fun record(species: Int, experience: Int): ByteArray {
        val plain = ByteArray(Gen6Pk6Adapter.STORED_SIZE)
        plain[0x08] = (species and 255).toByte()
        plain[0x09] = (species ushr 8).toByte()
        plain[0x10] = experience.toByte()
        val sum = species + experience
        plain[0x06] = (sum and 255).toByte()
        plain[0x07] = (sum ushr 8).toByte()
        return Gen6Pk6Adapter.encode(Gen6Pk6Adapter.decode(plain))
    }

    @Test
    fun fixedSlotsPlaceAndReopenOnlyTheTransferRecords() {
        val workingTemplate = template.copyOf()
        val original = workingTemplate.copyOf()
        val records = listOf(record(25, 10), record(64, 20))

        assertEquals(2, layout.availableSlots(workingTemplate, destination))
        val output = layout.insert(workingTemplate, destination, records)
        val reopened = layout.recordReader().readPlaced(output, destination, records.size)

        records.forEachIndexed { index, expected ->
            assertArrayEquals(
                Gen6Pk6Adapter.decode(expected).decryptedBytes,
                Gen6Pk6Adapter.decode(reopened[index]).decryptedBytes
            )
        }
        assertArrayEquals(original, workingTemplate)
        assertEquals(0x5A.toByte(), output[0])
        assertEquals(0x5A.toByte(), output[slotOffsets[0] - 1])
        assertEquals(0x5A.toByte(), output[slotOffsets[0] + Gen6Pk6Adapter.STORED_SIZE])
    }

    @Test
    fun layoutRejectsWrongRouteOverlappingOffsetsAndOutOfBoundsBuffers() {
        assertThrows(IllegalArgumentException::class.java) {
            layout.availableSlots(template, Gen6ConverterGame.OMEGA_RUBY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6Pk6FixedSlotLayout(
                destination,
                template,
                intArrayOf(4, 4 + Gen6Pk6Adapter.STORED_SIZE - 1),
                vacancy
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6Pk6FixedSlotLayout(destination, template, intArrayOf(-1), vacancy)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen6Pk6FixedSlotLayout(
                destination,
                ByteArray(Gen6Pk6Adapter.STORED_SIZE),
                slotOffsets,
                vacancy
            )
        }
    }

    @Test
    fun occupiedSlotIsRejectedBeforeLayoutCanBeUsed() {
        val occupied = template.copyOf()
        occupied[slotOffsets[1]] = 1
        val original = occupied.copyOf()

        assertThrows(IllegalArgumentException::class.java) {
            Gen6Pk6FixedSlotLayout(destination, occupied, slotOffsets, vacancy)
        }
        assertArrayEquals(original, occupied)
    }

    @Test
    fun vacancyValidatorCannotRewriteSlotToMakeItPass() {
        val mutatingValidator = Gen6Pk6SlotVacancyValidator { bytes, _ ->
            bytes[0] = 1
            true
        }
        assertThrows(IllegalStateException::class.java) {
            Gen6Pk6FixedSlotLayout(destination, template, slotOffsets, mutatingValidator)
        }
        val expected = ByteArray(template.size) { 0x5A }.apply {
            slotOffsets.forEach { offset ->
                fill(0.toByte(), offset, offset + Gen6Pk6Adapter.STORED_SIZE)
            }
        }
        assertArrayEquals(expected, template)
    }

    @Test
    fun modifiedOrDifferentTemplateIsRejectedBeforePlacement() {
        val changed = template.copyOf()
        changed[0] = (changed[0].toInt() xor 1).toByte()
        val original = changed.copyOf()

        assertThrows(IllegalArgumentException::class.java) {
            layout.availableSlots(changed, destination)
        }
        assertThrows(IllegalArgumentException::class.java) {
            layout.insert(changed, destination, listOf(record(25, 10)))
        }
        assertArrayEquals(original, changed)
    }

    @Test
    fun invalidRecordCannotPartiallyWriteDestination() {
        val workingTemplate = template.copyOf()
        val original = workingTemplate.copyOf()
        assertThrows(IllegalArgumentException::class.java) {
            layout.insert(
                workingTemplate,
                destination,
                listOf(record(25, 10), ByteArray(Gen6Pk6Adapter.STORED_SIZE))
            )
        }
        assertArrayEquals(original, workingTemplate)
    }
}
