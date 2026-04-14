package io.github.driftn2forty.chatsentinel.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfanityTrieTest {

    private ProfanityTrie trie;

    @BeforeEach
    void setUp() {
        trie = new ProfanityTrie();
    }

    @Test
    void insertAndContains() {
        trie.insert("hello");
        assertTrue(trie.contains("hello"));
        assertFalse(trie.contains("hell"));
        assertFalse(trie.contains("helloo"));
    }

    @Test
    void caseInsensitiveLookup() {
        trie.insert("BadWord");
        assertTrue(trie.contains("badword"));
        assertTrue(trie.contains("BADWORD"));
        assertTrue(trie.contains("Badword"));
    }

    @Test
    void prefixSharing() {
        trie.insert("test");
        trie.insert("testing");
        assertTrue(trie.contains("test"));
        assertTrue(trie.contains("testing"));
        assertFalse(trie.contains("tes"));
        assertEquals(2, trie.size());
    }

    @Test
    void scanFindsEmbeddedWords() {
        trie.insert("bad");
        trie.insert("word");
        final List<ProfanityTrie.MatchResult> results = trie.scan("this is a bad message with word");
        assertEquals(2, results.size());
        assertEquals("bad", results.get(0).matched());
        assertEquals("word", results.get(1).matched());
    }

    @Test
    void scanFindsOverlappingMatches() {
        trie.insert("ass");
        trie.insert("asset");
        final List<ProfanityTrie.MatchResult> results = trie.scan("asset");
        assertEquals(2, results.size());
        assertEquals("ass", results.get(0).matched());
        assertEquals("asset", results.get(1).matched());
    }

    @Test
    void scanEmptyInput() {
        trie.insert("word");
        assertTrue(trie.scan("").isEmpty());
        assertTrue(trie.scan(null).isEmpty());
    }

    @Test
    void containsNullAndEmpty() {
        assertFalse(trie.contains(null));
        assertFalse(trie.contains(""));
    }

    @Test
    void insertNullAndEmpty() {
        trie.insert(null);
        trie.insert("");
        assertEquals(0, trie.size());
    }

    @Test
    void unicodeWords() {
        trie.insert("мат");
        assertTrue(trie.contains("мат"));
        assertFalse(trie.contains("ма"));
    }

    @Test
    void sizeReflectsInsertions() {
        assertEquals(0, trie.size());
        trie.insert("one");
        trie.insert("two");
        trie.insert("three");
        assertEquals(3, trie.size());
        trie.insert("one");
        assertEquals(3, trie.size());
    }

    @Test
    void scanReturnsCorrectPositions() {
        trie.insert("bad");
        final List<ProfanityTrie.MatchResult> results = trie.scan("xbadx");
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).start());
        assertEquals(4, results.get(0).end());
    }
}
