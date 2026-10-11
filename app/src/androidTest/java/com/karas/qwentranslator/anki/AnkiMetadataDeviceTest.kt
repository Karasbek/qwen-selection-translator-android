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
}
