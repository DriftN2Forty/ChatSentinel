package io.github.driftn2forty.chatsentry.storage;

import java.time.Instant;

public final class PlayerData {

    private double score;
    private transient Instant lastDecayTimestamp;
    private String lastDecayTimestampStr;
    private transient Instant muteExpiry;
    private String muteExpiryStr;
    private int totalOffenses;
    private transient Instant lastOffense;
    private String lastOffenseStr;

    public PlayerData() {
        this.score = 0.0;
        this.lastDecayTimestampStr = Instant.now().toString();
        this.totalOffenses = 0;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Instant getLastDecayTimestamp() {
        if (lastDecayTimestamp == null && lastDecayTimestampStr != null) {
            lastDecayTimestamp = Instant.parse(lastDecayTimestampStr);
        }
        return lastDecayTimestamp;
    }

    public void setLastDecayTimestamp(Instant lastDecayTimestamp) {
        this.lastDecayTimestamp = lastDecayTimestamp;
        this.lastDecayTimestampStr = lastDecayTimestamp != null ? lastDecayTimestamp.toString() : null;
    }

    public Instant getMuteExpiry() {
        if (muteExpiry == null && muteExpiryStr != null) {
            muteExpiry = Instant.parse(muteExpiryStr);
        }
        return muteExpiry;
    }

    public void setMuteExpiry(Instant muteExpiry) {
        this.muteExpiry = muteExpiry;
        this.muteExpiryStr = muteExpiry != null ? muteExpiry.toString() : null;
    }

    public boolean isMuted() {
        final Instant expiry = getMuteExpiry();
        return expiry != null && Instant.now().isBefore(expiry);
    }

    public int getTotalOffenses() {
        return totalOffenses;
    }

    public void setTotalOffenses(int totalOffenses) {
        this.totalOffenses = totalOffenses;
    }

    public void incrementOffenses() {
        this.totalOffenses++;
    }

    public Instant getLastOffense() {
        if (lastOffense == null && lastOffenseStr != null) {
            lastOffense = Instant.parse(lastOffenseStr);
        }
        return lastOffense;
    }

    public void setLastOffense(Instant lastOffense) {
        this.lastOffense = lastOffense;
        this.lastOffenseStr = lastOffense != null ? lastOffense.toString() : null;
    }
}
