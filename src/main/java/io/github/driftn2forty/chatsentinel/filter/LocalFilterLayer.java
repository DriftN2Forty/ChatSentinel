package io.github.driftn2forty.chatsentinel.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class LocalFilterLayer {

    private final ProfanityTrie trie;
    private final ChatNormalizer normalizer;
    private final AbbreviationExpander expander;
    private final boolean leetSpeakEnabled;
    private final boolean abbreviationsEnabled;

    public LocalFilterLayer(ProfanityTrie trie, ChatNormalizer normalizer, AbbreviationExpander expander, boolean leetSpeakEnabled, boolean abbreviationsEnabled) {
        this.trie = trie;
        this.normalizer = normalizer;
        this.expander = expander;
        this.leetSpeakEnabled = leetSpeakEnabled;
        this.abbreviationsEnabled = abbreviationsEnabled;
    }

    public FilterResult check(String message) {
        if (message == null || message.isEmpty()) {
            return new FilterResult(false, List.of(), message);
        }

        String processed = message;
        if (abbreviationsEnabled) {
            processed = expander.expand(processed);
        }
        if (leetSpeakEnabled) {
            processed = normalizer.normalize(processed);
        }

        final List<ProfanityTrie.MatchResult> matches = trie.scan(processed);
        if (matches.isEmpty()) {
            return new FilterResult(false, List.of(), message);
        }
        return new FilterResult(true, Collections.unmodifiableList(matches), processed);
    }

    public String mask(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String processed = message;
        if (abbreviationsEnabled) {
            processed = expander.expand(processed);
        }
        final String normalized = leetSpeakEnabled ? normalizer.normalize(processed) : processed.toLowerCase();

        final List<ProfanityTrie.MatchResult> matches = trie.scan(normalized);
        if (matches.isEmpty()) {
            return message;
        }

        final char[] result = processed.toCharArray();
        for (final ProfanityTrie.MatchResult match : matches) {
            for (int i = match.start(); i < match.end() && i < result.length; i++) {
                result[i] = '*';
            }
        }
        return new String(result);
    }

    public static ProfanityTrie buildTrie(List<String> languages, Set<String> whitelist) throws IOException {
        final ProfanityTrie trie = new ProfanityTrie();
        final Set<String> lowerWhitelist = whitelist == null ? Set.of() : Set.copyOf(whitelist.stream().map(String::toLowerCase).toList());

        for (final String lang : languages) {
            final String resourcePath = "words/" + lang + ".txt";
            try (final InputStream is = LocalFilterLayer.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    continue;
                }
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String word = line.trim().toLowerCase();
                        if (!word.isEmpty() && !lowerWhitelist.contains(word)) {
                            trie.insert(word);
                        }
                    }
                }
            }
        }
        return trie;
    }

    public static List<String> listAvailableLanguages() {
        return List.of("ar", "cs", "da", "de", "en", "eo", "es", "fa", "fi", "fil", "fr", "fr-CA-u-sd-caqc", "hi", "hu", "it", "ja", "kab", "ko", "nl", "no", "pl", "pt", "ru", "sv", "th", "tlh", "tr", "zh");
    }

    public record FilterResult(boolean flagged, List<ProfanityTrie.MatchResult> matches, String processedText) {}
}
