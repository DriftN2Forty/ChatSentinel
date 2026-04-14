package io.github.driftn2forty.chatsentinel.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ProfanityTrie {

    private final TrieNode root = new TrieNode();

    public void insert(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        final String lower = word.toLowerCase();
        TrieNode current = root;
        for (int i = 0; i < lower.length(); i++) {
            current = current.getOrCreateChild(lower.charAt(i));
        }
        current.setEndOfWord(true);
    }

    public boolean contains(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        final String lower = word.toLowerCase();
        TrieNode current = root;
        for (int i = 0; i < lower.length(); i++) {
            current = current.getChild(lower.charAt(i));
            if (current == null) {
                return false;
            }
        }
        return current.isEndOfWord();
    }

    public List<MatchResult> scan(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        final String lower = text.toLowerCase();
        final List<MatchResult> matches = new ArrayList<>();
        for (int i = 0; i < lower.length(); i++) {
            TrieNode current = root;
            for (int j = i; j < lower.length(); j++) {
                current = current.getChild(lower.charAt(j));
                if (current == null) {
                    break;
                }
                if (current.isEndOfWord()) {
                    matches.add(new MatchResult(i, j + 1, text.substring(i, j + 1)));
                }
            }
        }
        return matches;
    }

    public int size() {
        return countWords(root);
    }

    private int countWords(TrieNode node) {
        int count = node.isEndOfWord() ? 1 : 0;
        for (int i = 0; i < node.childCount(); i++) {
            count += countWords(node.childAt(i));
        }
        return count;
    }

    public record MatchResult(int start, int end, String matched) {}

    static final class TrieNode {

        private char[] keys = new char[0];
        private TrieNode[] children = new TrieNode[0];
        private boolean endOfWord;

        TrieNode getChild(char c) {
            final int idx = Arrays.binarySearch(keys, c);
            return idx >= 0 ? children[idx] : null;
        }

        TrieNode getOrCreateChild(char c) {
            final int idx = Arrays.binarySearch(keys, c);
            if (idx >= 0) {
                return children[idx];
            }
            final int insertionPoint = -(idx + 1);
            final char[] newKeys = new char[keys.length + 1];
            final TrieNode[] newChildren = new TrieNode[children.length + 1];
            System.arraycopy(keys, 0, newKeys, 0, insertionPoint);
            System.arraycopy(children, 0, newChildren, 0, insertionPoint);
            newKeys[insertionPoint] = c;
            final TrieNode child = new TrieNode();
            newChildren[insertionPoint] = child;
            System.arraycopy(keys, insertionPoint, newKeys, insertionPoint + 1, keys.length - insertionPoint);
            System.arraycopy(children, insertionPoint, newChildren, insertionPoint + 1, children.length - insertionPoint);
            keys = newKeys;
            children = newChildren;
            return child;
        }

        boolean isEndOfWord() {
            return endOfWord;
        }

        void setEndOfWord(boolean endOfWord) {
            this.endOfWord = endOfWord;
        }

        int childCount() {
            return keys.length;
        }

        TrieNode childAt(int index) {
            return children[index];
        }
    }
}
