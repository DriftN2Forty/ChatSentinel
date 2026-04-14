package io.github.driftn2forty.chatsentinel.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatNormalizerTest {

    private ChatNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ChatNormalizer();
    }

    @Test
    void leetSpeakToPlainText() {
        assertEquals("ass", normalizer.normalize("@$$"));
        assertEquals("shit", normalizer.normalize("$h!t"));
    }

    @Test
    void atSignToA() {
        assertEquals("apple", normalizer.normalize("@pple"));
    }

    @Test
    void zeroToO() {
        assertEquals("cool", normalizer.normalize("c00l"));
    }

    @Test
    void dollarToS() {
        assertEquals("sass", normalizer.normalize("$a$$"));
    }

    @Test
    void threeToE() {
        assertEquals("elite", normalizer.normalize("3lit3"));
    }

    @Test
    void oneToI() {
        assertEquals("hit", normalizer.normalize("h1t"));
    }

    @Test
    void accentStripping() {
        assertEquals("cafe", normalizer.normalize("café"));
        assertEquals("uber", normalizer.normalize("über"));
        assertEquals("nino", normalizer.normalize("niño"));
    }

    @Test
    void mixedLeetAndAccents() {
        assertEquals("asse", normalizer.normalize("@$$é"));
    }

    @Test
    void emptyAndNull() {
        assertEquals("", normalizer.normalize(""));
        assertEquals("", normalizer.normalize(null));
    }

    @Test
    void plainTextPassthrough() {
        assertEquals("hello world", normalizer.normalize("hello world"));
    }

    @Test
    void allLowercase() {
        assertEquals("hello", normalizer.normalize("HELLO"));
        assertEquals("mixed", normalizer.normalize("MiXeD"));
    }
}
