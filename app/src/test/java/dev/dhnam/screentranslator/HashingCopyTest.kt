package dev.dhnam.screentranslator

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class HashingCopyTest {
    @Test fun copy_knownInput_sha256AndBytes() {
        val out = ByteArrayOutputStream()
        val hash = HashingCopy.copy(ByteArrayInputStream("hello".toByteArray()), out) {}
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash)
        assertArrayEquals("hello".toByteArray(), out.toByteArray())
    }

    @Test fun copy_reportsFinalProgressAsTotal() {
        var last = -1L
        HashingCopy.copy(ByteArrayInputStream(ByteArray(100_000)), ByteArrayOutputStream()) { last = it }
        assertEquals(100_000L, last)
    }

    @Test fun copy_propagatesIoError() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("cut")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("cut")
        }
        assertThrows(IOException::class.java) { HashingCopy.copy(broken, ByteArrayOutputStream()) {} }
    }

    @Test fun copy_stopsWhenProgressThrows() {
        val out = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            HashingCopy.copy(ByteArrayInputStream(ByteArray(200_000)), out) { if (it >= 64 * 1024) throw IOException("cancelled") }
        }
        assertTrue(out.size() < 200_000)
    }

    @Test fun hashesMatch_mismatch_isFalse() = assertFalse(HashingCopy.hashesMatch("aa", "bb"))

    @Test fun hashesMatch_missingExpected_isTrue() = assertTrue(HashingCopy.hashesMatch(null, "bb"))

    @Test fun hashesMatch_caseInsensitive() = assertTrue(HashingCopy.hashesMatch("ABCD", "abcd"))
}
