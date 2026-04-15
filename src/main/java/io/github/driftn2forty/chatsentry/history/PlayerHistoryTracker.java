package io.github.driftn2forty.chatsentry.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerHistoryTracker {

    private final int messagesPerPlayer;
    private final boolean includeWhispers;
    private final ConcurrentHashMap<UUID, RingBuffer> playerBuffers = new ConcurrentHashMap<>();

    public PlayerHistoryTracker(int messagesPerPlayer, boolean includeWhispers) {
        this.messagesPerPlayer = messagesPerPlayer;
        this.includeWhispers = includeWhispers;
    }

    public void record(UUID playerUuid, ChatMessage message) {
        if (!includeWhispers && message.type() == ChatMessage.Type.WHISPER) {
            return;
        }
        playerBuffers.computeIfAbsent(playerUuid, k -> new RingBuffer(messagesPerPlayer)).add(message);
    }

    public List<ChatMessage> getHistory(UUID playerUuid) {
        final RingBuffer buffer = playerBuffers.get(playerUuid);
        if (buffer == null) {
            return List.of();
        }
        return buffer.snapshot();
    }

    public void clear(UUID playerUuid) {
        playerBuffers.remove(playerUuid);
    }

    public void clearAll() {
        playerBuffers.clear();
    }

    public int getTrackedPlayerCount() {
        return playerBuffers.size();
    }

    private static final class RingBuffer {
        private final ChatMessage[] buffer;
        private int head = 0;
        private int size = 0;

        RingBuffer(int capacity) {
            this.buffer = new ChatMessage[capacity];
        }

        synchronized void add(ChatMessage message) {
            buffer[head] = message;
            head = (head + 1) % buffer.length;
            if (size < buffer.length) {
                size++;
            }
        }

        synchronized List<ChatMessage> snapshot() {
            if (size == 0) {
                return List.of();
            }
            final List<ChatMessage> result = new ArrayList<>(size);
            final int start = (head - size + buffer.length) % buffer.length;
            for (int i = 0; i < size; i++) {
                result.add(buffer[(start + i) % buffer.length]);
            }
            return Collections.unmodifiableList(result);
        }
    }
}
