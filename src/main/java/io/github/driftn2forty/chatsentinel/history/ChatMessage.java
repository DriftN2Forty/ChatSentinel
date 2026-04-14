package io.github.driftn2forty.chatsentinel.history;

import java.time.Instant;

public record ChatMessage(Type type, String senderName, String senderUuid, String targetName, String text, Instant timestamp) {

    public enum Type {
        CHAT,
        WHISPER,
        SIGN,
        BOOK,
        ANVIL
    }

    public static ChatMessage chat(String senderName, String senderUuid, String text) {
        return new ChatMessage(Type.CHAT, senderName, senderUuid, null, text, Instant.now());
    }

    public static ChatMessage whisper(String senderName, String senderUuid, String targetName, String text) {
        return new ChatMessage(Type.WHISPER, senderName, senderUuid, targetName, text, Instant.now());
    }

    public String sourceString() {
        return type.name().toLowerCase();
    }
}
