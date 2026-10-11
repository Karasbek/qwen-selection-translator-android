package com.karas.qwentranslator.anki

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in device check: skips without an installed, enabled and user-authorized AnkiDroid. */
class AnkiMetadataDeviceTest {
    @Test fun readsOnlyDeckAndModelMetadata() {
        val gateway = AndroidAnkiGateway(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue(gateway.availability().status == AnkiStatus.CONNECTED)
        val metadata = AnkiConnection(gateway).load()
        assertEquals(AnkiStatus.CONNECTED, metadata.availability.status)
        assertTrue(metadata.decks.all { it.id > 0 })
        assertTrue(metadata.models.all { it.id > 0 && it.fields.isNotEmpty() })
        // Deliberately do not log collection metadata or inspect user notes.
    }
    @Test fun officialChecksumQueryIsAvailableWithoutWriting() {
        val gateway = AndroidAnkiGateway(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue(gateway.availability().status == AnkiStatus.CONNECTED)
        val model = gateway.models().firstOrNull { it.compatible }
        assumeTrue(model != null)
        assertFalse(gateway.hasExactDuplicate(model!!.id, "Qwen read-only API probe ${java.util.UUID.randomUUID()}"))
    }
    @Test fun generatedHtmlChecksumMatchesDecodedUnicodePlainText() {
        val gateway = AndroidAnkiGateway(InstrumentationRegistry.getInstrumentation().targetContext)
        val text = "Unicode café & < > \" ' { } :"
        val front = ClozeCardBuilder.escape(text) + "<br><br>tail"
        val method = gateway.javaClass.getDeclaredMethod("checksum", String::class.java).apply { isAccessible = true }
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest((text + "tail").toByteArray(Charsets.UTF_8))
        val expected = digest.take(4).fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 255) }
        assertEquals(expected, method.invoke(gateway, front))
    }

}
