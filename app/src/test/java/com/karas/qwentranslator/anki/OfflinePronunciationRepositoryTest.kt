package com.karas.qwentranslator.anki

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class OfflinePronunciationRepositoryTest {
    @Test fun realBundledDictionaryWorksWithoutAnyNetworkDependency() = runBlocking {
        val repo = OfflinePronunciationRepository { File("src/main/assets/ipa/en_US.txt").readBytes() }
        assertEquals(listOf("/əˈbændən/"), repo.lookup("ABANDON", "en").variants)
        assertEquals(listOf("/ˈɹənɪŋ/"), repo.lookup("running", "en-US").variants)
        assertEquals(listOf("/ˈɹɛd/", "/ˈɹid/"), repo.lookup("read", "auto").variants)
        assertTrue(repo.lookup("qwen-word-not-in-dictionary", "en").variants.isEmpty())
        assertTrue(repo.lookup("give up", "en").variants.isEmpty())
        assertTrue(repo.lookup("abandon", "fr").variants.isEmpty())
    }
    @Test fun loadsOnlyOnceAcrossConcurrentAndRepeatedQueries() = runBlocking {
        val count = AtomicInteger()
        val repo = OfflinePronunciationRepository { count.incrementAndGet(); "abandon\t/əˈbændən/\nread\t/ɹɛd/, /ɹid/\n".toByteArray() }
        (1..20).map { async(Dispatchers.Default) { repo.lookup("abandon", "en") } }.awaitAll()
        repo.lookup("read", "en"); assertEquals(1, count.get())
    }
    @Test fun exactFormsOnlyNoPronunciationGuessing() = runBlocking {
        val repo = OfflinePronunciationRepository { "agree\t/əˈɡriː/\n".toByteArray() }
        assertTrue(repo.lookup("agreed", "en").variants.isEmpty())
        assertTrue(repo.lookup("agree's", "en").variants.isEmpty())
        assertTrue(repo.lookup("agre", "en").variants.isEmpty())
    }
    @Test fun variantsDeduplicateAndMalformedPronunciationsStayEmpty() = runBlocking {
        val repo = OfflinePronunciationRepository { "bad\tnot-ipa\nread\t/ɹɛd/, /ɹid/, /ɹɛd/\n".toByteArray() }
        assertTrue(repo.lookup("bad", "en").variants.isEmpty())
        assertEquals(listOf("/ɹɛd/", "/ɹid/"), repo.lookup("read", "en").variants)
    }
    @Test fun unsupportedAndEmptyWordsDoNotLoadDictionary() = runBlocking {
        val repo = OfflinePronunciationRepository { error("Must not load") }
        assertTrue(repo.lookup("", "auto").variants.isEmpty())
        assertTrue(repo.lookup("abandon", "zh").variants.isEmpty())
    }
    @Test fun emptyDictionaryIsSafe() = runBlocking {
        assertTrue(OfflinePronunciationRepository { byteArrayOf() }.lookup("agree", "en").variants.isEmpty())
    }
}
