package net.runelite.client.plugins.microbot.aiautonomous.ai.memory;

import java.time.Duration;
import java.time.Instant;

public class GameSession {
    private String sessionId;
    private Instant startTime;
    private Instant endTime;
    private Instant lastActivity;
    private int decisionsCount;
    private int successfulActions;
    private int failedActions;
    private String notes;

    public GameSession() {
        this.startTime = Instant.now();
        this.lastActivity = Instant.now();
        this.decisionsCount = 0;
        this.successfulActions = 0;
        this.failedActions = 0;
        this.notes = "";
    }

    public Duration getSessionDuration() {
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(startTime, end);
    }

    public Duration getTimeSinceLastActivity() {
        return Duration.between(lastActivity, Instant.now());
    }

    public double getSuccessRate() {
        int total = successfulActions + failedActions;
        return total > 0 ? (double) successfulActions / total : 0.0;
    }

    public void incrementDecisions() {
        decisionsCount++;
        lastActivity = Instant.now();
    }

    public void incrementSuccesses() {
        successfulActions++;
        lastActivity = Instant.now();
    }

    public void incrementFailures() {
        failedActions++;
        lastActivity = Instant.now();
    }

    public boolean isActive() {
        return endTime == null;
    }

    // Getters and setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }

    public int getDecisionsCount() { return decisionsCount; }
    public void setDecisionsCount(int decisionsCount) { this.decisionsCount = decisionsCount; }

    public int getSuccessfulActions() { return successfulActions; }
    public void setSuccessfulActions(int successfulActions) { this.successfulActions = successfulActions; }

    public int getFailedActions() { return failedActions; }
    public void setFailedActions(int failedActions) { this.failedActions = failedActions; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return String.format("GameSession{id='%s', duration=%s, decisions=%d, successRate=%.2f}",
                sessionId, getSessionDuration(), decisionsCount, getSuccessRate());
    }
}