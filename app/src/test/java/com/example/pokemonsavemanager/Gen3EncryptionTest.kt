package com.example.pokemonsavemanager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Independent GBA wire vectors, not ciphertext produced by the codec under test.
 * Format reference: https://github.com/kwsch/PKHeX/blob/master/PKHeX.Core/PKM/Util/PokeCrypto.cs
 * Plaintext is bytes 00..2F; XOR owner ID is 0x1234ABCD; raw order is unsigned PID modulo 24.
 */
class Gen3EncryptionTest {
    private val plaintext = ByteArray(48) { it.toByte() }
    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val orderVectors = arrayOf(
        "cdaa3611c9ae3215c5a23e19c1a63a1dddba2601d9be2205d5b22e09d1b62a0ded8a1631e98e1235e5821e39e1861a3d",
        "ccaa3611c8ae3215c4a23e19c0a63a1ddcba2601d8be2205e88e1235e4821e39e0861a3dd4b22e09d0b62a0dec8a1631",
        "cfaa3611cbae3215c7a23e19d7b22e09d3b62a0def8a1631c3a63a1ddfba2601dbbe2205eb8e1235e7821e39e3861a3d",
        "ceaa3611caae3215c6a23e19d6b22e09d2b62a0dee8a1631ea8e1235e6821e39e2861a3dc2a63a1ddeba2601dabe2205",
        "c9aa3611cdae3215c1a23e19ed8e1235e1821e39e5861a3dc5a63a1dd9ba2601ddbe2205d1b22e09d5b62a0de98a1631",
        "c8aa3611ccae3215c0a23e19ec8e1235e0821e39e4861a3dd0b22e09d4b62a0de88a1631c4a63a1dd8ba2601dcbe2205",
        "c7a63a1ddbba2601dfbe2205cbaa3611cfae3215c3a23e19d3b22e09d7b62a0deb8a1631ef8e1235e3821e39e7861a3d",
        "c6a63a1ddaba2601debe2205caaa3611ceae3215c2a23e19ee8e1235e2821e39e6861a3dd2b22e09d6b62a0dea8a1631",
        "c9a63a1dd5ba2601d1be2205ddb22e09d9b62a0de58a1631c5aa3611c1ae3215cda23e19e18e1235ed821e39e9861a3d",
        "c8a63a1dd4ba2601d0be2205dcb22e09d8b62a0de48a1631e08e1235ec821e39e8861a3dc4aa3611c0ae3215cca23e19",
        "cba63a1dd7ba2601d3be2205e38e1235ef821e39eb861a3dc7aa3611c3ae3215cfa23e19dfb22e09dbb62a0de78a1631",
        "caa63a1dd6ba2601d2be2205e28e1235ee821e39ea861a3ddeb22e09dab62a0de68a1631c6aa3611c2ae3215cea23e19",
        "d9b22e09ddb62a0de18a1631c1aa3611c5ae3215c9a23e19cda63a1dd1ba2601d5be2205e58e1235e9821e39ed861a3d",
        "d8b22e09dcb62a0de08a1631c0aa3611c4ae3215c8a23e19e48e1235e8821e39ec861a3dcca63a1dd0ba2601d4be2205",
        "dbb22e09dfb62a0de38a1631cfa63a1dd3ba2601d7be2205c3aa3611c7ae3215cba23e19e78e1235eb821e39ef861a3d",
        "dab22e09deb62a0de28a1631cea63a1dd2ba2601d6be2205e68e1235ea821e39ee861a3dc2aa3611c6ae3215caa23e19",
        "c5b22e09c1b62a0dfd8a1631f98e1235f5821e39f1861a3dddaa3611d9ae3215d5a23e19d1a63a1dcdba2601c9be2205",
        "c4b22e09c0b62a0dfc8a1631f88e1235f4821e39f0861a3dd0a63a1dccba2601c8be2205dcaa3611d8ae3215d4a23e19",
        "fb8e1235f7821e39f3861a3ddfaa3611dbae3215d7a23e19d3a63a1dcfba2601cbbe2205c7b22e09c3b62a0dff8a1631",
        "fa8e1235f6821e39f2861a3ddeaa3611daae3215d6a23e19c6b22e09c2b62a0dfe8a1631d2a63a1dceba2601cabe2205",
        "fd8e1235f1821e39f5861a3dd5a63a1dc9ba2601cdbe2205d9aa3611ddae3215d1a23e19c1b22e09c5b62a0df98a1631",
        "fc8e1235f0821e39f4861a3dd4a63a1dc8ba2601ccbe2205c0b22e09c4b62a0df88a1631d8aa3611dcae3215d0a23e19",
        "ff8e1235f3821e39f7861a3dc3b22e09c7b62a0dfb8a1631dbaa3611dfae3215d3a23e19d7a63a1dcbba2601cfbe2205",
        "fe8e1235f2821e39f6861a3dc2b22e09c6b62a0dfa8a1631d6a63a1dcaba2601cebe2205daaa3611deae3215d2a23e19"
    )

    @Test
    fun everyGbaPidRemainderMatchesKnownEncryptionAndDecryptionBytes() {
        for (pid in 0 until 24) {
            val wire = hex(orderVectors[pid])
            val before = wire.copyOf()
            val plainBefore = plaintext.copyOf()
            assertArrayEquals(plaintext, Gen3Encryption.decrypt(wire, pid, 0x1234, 0xABCD))
            assertArrayEquals(wire, Gen3Encryption.encrypt(plaintext, pid, 0x1234, 0xABCD))
            assertArrayEquals(before, wire)
            assertArrayEquals(plainBefore, plaintext)
        }
    }

    @Test
    fun signedKotlinPidsUseTheirCompleteUnsignedGbaValue() {
        val cases = listOf(
            0x80000000L.toInt() to "c1a63a9dddba2681d9be2285d5b22e89d1b62a8ded8a16b1cdaa3691c9ae3295c5a23e99e98e12b5e5821eb9e1861abd",
            0x80000001L.toInt() to "c0a63a9ddcba2681d8be2285d4b22e89d0b62a8dec8a16b1e88e12b5e4821eb9e0861abdccaa3691c8ae3295c4a23e99",
            0xFFFFFFFEL.toInt() to "2b4dd1f62f49d5f21375e9ce3f59c5e22345d9fe2741ddfa3355c9ee3751cdea3b5dc1e61771edca1b7de1c61f79e5c2",
            0xFFFFFFFFL.toInt() to "2a4dd1f62e49d5f21275e9ce3e59c5e22245d9fe2641ddfa1671edca1a7de1c61e79e5c23255c9ee3651cdea3a5dc1e6",
            0xABCDEF01L.toInt() to "cc45fbbac841ffbec44df3b2c049f7b6dc55ebaad851efaee861df9ee46dd392e069d796d45de3a2d059e7a6ec65db9a"
        )
        for ((pid, encrypted) in cases) {
            val expected = hex(encrypted)
            assertArrayEquals(plaintext, Gen3Encryption.decrypt(expected, pid, 0x1234, 0xABCD))
            assertArrayEquals(expected, Gen3Encryption.encrypt(plaintext, pid, 0x1234, 0xABCD))
        }
    }

    @Test
    fun codecReopensKnownHighPidRecordWithoutChangingHeaderAndRejectsCorruption() {
        val raw = hex("01000080cdab341200000000000000000000000200000000000000a528420000c0a63a9ddcba2681d8be2285d4b22e89d0b62a8dec8a16b1e88e12b5e4821eb9e0861abdccaa3691c8ae3295c4a23e99")
        val expectedChecksum = 0x4228
        val original = raw.copyOf()
        val decoded = Gen3Pk3Codec.decode(raw)
        assertArrayEquals(plaintext, decoded.canonicalSection)
        assertEquals(expectedChecksum, decoded.storedChecksum)
        assertArrayEquals(raw, Gen3Pk3Codec.encode(decoded.header, decoded.personality,
            decoded.trainerId, decoded.secretId, decoded.canonicalSection))
        assertArrayEquals(original, raw)
        val corrupt = raw.copyOf()
        corrupt[79] = (corrupt[79].toInt() xor 1).toByte()
        val before = corrupt.copyOf()
        var rejected = false
        try { Gen3Pk3Codec.decode(corrupt) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertArrayEquals(before, corrupt)
    }
}
