package io.github.driftn2forty.chatsentry.filter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbbreviationExpanderTest {

    @Test
    void expandsKnownAbbreviation() {
        final AbbreviationExpander expander = new AbbreviationExpander(Map.of("stfu", "shut the fuck up"));
        assertEquals("shut the fuck up", expander.expand("stfu"));
    }

    @Test
    void wholeWordOnly() {
        final AbbreviationExpander expander = new AbbreviationExpander(Map.of("fk", "fuck"));
        assertEquals("fuck", expander.expand("fk"));
        assertEquals("fork", expander.expand("fork"));
    }

    @Test
    void preservesNonAbbreviations() {
        final AbbreviationExpander expander = new AbbreviationExpander(Map.of("stfu", "shut the fuck up"));
        assertEquals("hello world", expander.expand("hello world"));
    }

    @Test
    void caseInsensitive() {
        final AbbreviationExpander expander = new AbbreviationExpander(Map.of("stfu", "shut the fuck up"));
        assertEquals("shut the fuck up", expander.expand("STFU"));
        assertEquals("shut the fuck up", expander.expand("Stfu"));
    }

    @Test
    void preservesWhitespace() {
        final AbbreviationExpander expander = new AbbreviationExpander(Map.of("stfu", "shut the fuck up"));
        assertEquals("hey shut the fuck up man", expander.expand("hey stfu man"));
    }

    @Test
    void emptyAndNull() {
        final AbbreviationExpander expander = new AbbreviationExpander(Map.of());
        assertEquals("", expander.expand(""));
        assertEquals("", expander.expand(null));
    }

    @Test
    void multipleAbbreviationsInOneSentence() {
        final AbbreviationExpander expander = new AbbreviationExpander(Map.of("fk", "fuck", "bs", "bullshit"));
        assertEquals("this is bullshit, fuck you", expander.expand("this is bs, fk you"));
    }

    @Test
    void loadFromResource() throws IOException {
        final Map<String, String> map = AbbreviationExpander.loadFromResource("abbreviations.txt");
        assertFalse(map.isEmpty());
        assertTrue(map.containsKey("stfu"));
        assertTrue(map.containsKey("kys"));
    }
}
