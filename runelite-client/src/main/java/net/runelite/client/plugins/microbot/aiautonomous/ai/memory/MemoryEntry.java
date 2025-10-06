package net.runelite.client.plugins.microbot.aiautonomous.ai.memory;

import java.time.Duration;
import java.time.Instant;

public class MemoryEntry {
    private MemoryType type;
    private String situation;
    private String decision;
    private String outcome;
    private String context;
    private int confidence;
    private boolean successful;
    private Instant timestamp;
    private String sessionId;
    private double relevanceScore;

    public MemoryEntry() {
        this.timestamp = Instant.now();
        this.relevanceScore = 1.0;
    }

    public boolean isExpired(Duration maxAge) {
        return Duration.between(timestamp, Instant.now()).compareTo(maxAge) > 0;
    }

    public double calculateRelevanceScore(String queryString) {
        double score = 0.0;

        // Base relevance
        if (situation != null && queryString.toLowerCase().contains(situation.toLowerCase())) {
            score += 1.0;
        }

        // Recent memories are more relevant
        long ageInHours = Duration.between(timestamp, Instant.now()).toHours();
        double ageScore = Math.max(0, 1.0 - (ageInHours / (24.0 * 7))); // Decay over a week
        score += ageScore * 0.5;

        // Successful outcomes are more relevant
        if (successful) {
            score += 0.3;
        }

        // Higher confidence is more relevant
        score += (confidence / 100.0) * 0.2;

        this.relevanceScore = score;
        return score;
    }

    // Getters and setters
    public MemoryType getType() { return type; }
    public void setType(MemoryType type) { this.type = type; }

    public String getSituation() { return situation; }
    public void setSituation(String situation) { this.situation = situation; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }

    public boolean isSuccessful() { return successful; }
    public void setSuccessful(boolean successful) { this.successful = successful; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }

    @Override
    public String toString() {
        return String.format("MemoryEntry{type=%s, situation='%s', decision='%s', successful=%s, timestamp=%s}",
                type, situation, decision, successful, timestamp);
    }
}