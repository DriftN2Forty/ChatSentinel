package io.github.driftn2forty.chatsentinel.filter;

import java.text.Normalizer;
import java.util.Map;

public final class ChatNormalizer {

    private static final Map<Character, Character> LEET_MAP = Map.ofEntries(
            Map.entry('@', 'a'),
            Map.entry('4', 'a'),
            Map.entry('8', 'b'),
            Map.entry('(', 'c'),
            Map.entry('3', 'e'),
            Map.entry('6', 'g'),
            Map.entry('#', 'h'),
            Map.entry('!', 'i'),
            Map.entry('1', 'i'),
            Map.entry('|', 'l'),
            Map.entry('0', 'o'),
            Map.entry('5', 's'),
            Map.entry('$', 's'),
            Map.entry('+', 't'),
            Map.entry('7', 't'),
            Map.entry('2', 'z'),
            Map.entry('9', 'g')
    );

    public String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        final String stripped = stripAccents(input);
        final StringBuilder sb = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            final char c = stripped.charAt(i);
            final Character replacement = LEET_MAP.get(c);
            if (replacement != null) {
                sb.append(replacement);
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private String stripAccents(String input) {
        final String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "");
    }
}
