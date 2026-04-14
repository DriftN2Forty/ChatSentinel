package io.github.driftn2forty.chatsentinel.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AbbreviationExpander {

    private final Map<String, String> abbreviations;

    public AbbreviationExpander(Map<String, String> abbreviations) {
        final Map<String, String> map = new HashMap<>();
        for (final Map.Entry<String, String> entry : abbreviations.entrySet()) {
            map.put(entry.getKey().toLowerCase(), entry.getValue().toLowerCase());
        }
        this.abbreviations = Collections.unmodifiableMap(map);
    }

    public String expand(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        final List<String> tokens = tokenize(input);
        final StringBuilder sb = new StringBuilder();
        for (final String token : tokens) {
            final String lower = token.toLowerCase();
            final String expansion = abbreviations.get(lower);
            if (expansion != null) {
                sb.append(expansion);
            } else {
                sb.append(token);
            }
        }
        return sb.toString();
    }

    public Map<String, String> getAbbreviations() {
        return abbreviations;
    }

    private List<String> tokenize(String input) {
        final List<String> tokens = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean inWord = false;
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            final boolean isWordChar = Character.isLetterOrDigit(c);
            if (isWordChar) {
                if (!inWord) {
                    if (!current.isEmpty()) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    inWord = true;
                }
                current.append(c);
            } else {
                if (inWord) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inWord = false;
                }
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    public static Map<String, String> loadFromResource(String resourcePath) throws IOException {
        final Map<String, String> map = new HashMap<>();
        try (final InputStream is = AbbreviationExpander.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return map;
            }
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    final int eq = trimmed.indexOf('=');
                    if (eq > 0 && eq < trimmed.length() - 1) {
                        map.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
                    }
                }
            }
        }
        return map;
    }
}
