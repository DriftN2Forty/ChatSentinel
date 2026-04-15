package io.github.driftn2forty.chatsentry.storage;

import java.time.Instant;
import java.util.List;

public final class ModerationEntry {

    private String player;
    private String uuid;
    private String message;
    private String source;
    private int layer;
    private String verdict;
    private List<String> categories;
    private double moderationScore;
    private double playerScoreBefore;
    private double playerScoreAfter;
    private String actionTaken;
    private long actionDuration;
    private long responseTimeMs;
    private String timestamp;

    public ModerationEntry() {
        this.categories = List.of();
        this.timestamp = Instant.now().toString();
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public double getModerationScore() {
        return moderationScore;
    }

    public void setModerationScore(double moderationScore) {
        this.moderationScore = moderationScore;
    }

    public double getPlayerScoreBefore() {
        return playerScoreBefore;
    }

    public void setPlayerScoreBefore(double playerScoreBefore) {
        this.playerScoreBefore = playerScoreBefore;
    }

    public double getPlayerScoreAfter() {
        return playerScoreAfter;
    }

    public void setPlayerScoreAfter(double playerScoreAfter) {
        this.playerScoreAfter = playerScoreAfter;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public long getActionDuration() {
        return actionDuration;
    }

    public void setActionDuration(long actionDuration) {
        this.actionDuration = actionDuration;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
