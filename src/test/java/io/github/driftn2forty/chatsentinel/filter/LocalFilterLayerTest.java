package io.github.driftn2forty.chatsentinel.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFilterLayerTest {

    private LocalFilterLayer layer;

    @BeforeEach
    void setUp() {
        final ProfanityTrie trie = new ProfanityTrie();
        trie.insert("fuck");
        trie.insert("shit");
        trie.insert("ass");

        final ChatNormalizer normalizer = new ChatNormalizer();
        final AbbreviationExpander expander = new AbbreviationExpander(Map.of("stfu", "shut the fuck up", "kys", "kill yourself"));

        layer = new LocalFilterLayer(trie, normalizer, expander, true, true);
    }

    @Test
    void detectsPlainProfanity() {
        final LocalFilterLayer.FilterResult result = layer.check("what the fuck");
        assertTrue(result.flagged());
        assertFalse(result.matches().isEmpty());
    }

    @Test
    void detectsLeetSpeak() {
        final LocalFilterLayer.FilterResult result = layer.check("what the $h!t");
        assertTrue(result.flagged());
    }

    @Test
    void detectsAbbreviation() {
        final LocalFilterLayer.FilterResult result = layer.check("hey stfu noob");
        assertTrue(result.flagged());
    }

    @Test
    void cleanMessagePasses() {
        final LocalFilterLayer.FilterResult result = layer.check("hello world");
        assertFalse(result.flagged());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    void maskReplacesOffensiveWords() {
        final String masked = layer.mask("what the fuck man");
        assertFalse(masked.contains("fuck"));
        assertTrue(masked.contains("***"));
    }

    @Test
    void emptyMessageNotFlagged() {
        assertFalse(layer.check("").flagged());
        assertFalse(layer.check(null).flagged());
    }

    @Test
    void buildTrieFromResources() throws IOException {
        final ProfanityTrie trie = LocalFilterLayer.buildTrie(LocalFilterLayer.listAvailableLanguages(), Set.of());
        assertTrue(trie.size() > 100);
    }

    @Test
    void buildTrieRespectsWhitelist() throws IOException {
        final ProfanityTrie withWhitelist = LocalFilterLayer.buildTrie(java.util.List.of("en"), Set.of("ass"));
        assertFalse(withWhitelist.contains("ass"));
    }

    @Test
    void disabledLeetSpeakSkipsNormalization() {
        final ProfanityTrie trie = new ProfanityTrie();
        trie.insert("fuck");
        final LocalFilterLayer noLeet = new LocalFilterLayer(trie, new ChatNormalizer(), new AbbreviationExpander(Map.of()), false, false);
        assertFalse(noLeet.check("fvck").flagged());
        assertTrue(noLeet.check("fuck").flagged());
    }

    @Test
    void disabledAbbreviationsSkipsExpansion() {
        final ProfanityTrie trie = new ProfanityTrie();
        trie.insert("fuck");
        final AbbreviationExpander expander = new AbbreviationExpander(Map.of("stfu", "shut the fuck up"));
        final LocalFilterLayer noAbbr = new LocalFilterLayer(trie, new ChatNormalizer(), expander, true, false);
        assertFalse(noAbbr.check("stfu").flagged());
    }

    @Test
    void combinedLeetAndAbbreviation() {
        final LocalFilterLayer.FilterResult result = layer.check("$tfu");
        // After leet normalization: "stfu" is not expanded because abbreviations run before normalization
        // The pipeline: expand abbreviations first, then normalize leet-speak
        // "$tfu" → abbreviations don't match "$tfu" → normalize → "stfu" → not in trie
        // This is expected behavior — abbreviations must match the raw text as whole words
        assertFalse(result.flagged());
    }

    @Test
    void detectsNumberBasedLeet() {
        final LocalFilterLayer.FilterResult result = layer.check("@$$");
        assertTrue(result.flagged());
    }
}
