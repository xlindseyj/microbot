package net.runelite.client.plugins.microbot.aiautonomous.ai.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticKnowledge {
    private String situation;
    private String description;
    private double confidence;
    private int usageCount;
    private int successCount;
    private int failureCount;
    private Instant lastUpdated;
    private List<String> successfulActions;
    private List<String> failedActions;
    private Map<String, Integer> actionFrequency;

    public SemanticKnowledge() {
        this.confidence = 0.5;
        this.usageCount = 0;
        this.successCount = 0;
        this.failureCount = 0;
        this.lastUpdated = Instant.now();
        this.successfulActions = new ArrayList<>();
        this.failedActions = new ArrayList<>();
        this.actionFrequency = new HashMap<>();
    }

    public void addOutcome(boolean successful) {
        if (successful) {
            successCount++;
        } else {
            failureCount++;
        }
        updateConfidence();
    }

    public void addSuccessfulAction(String action) {
        if (!successfulActions.contains(action)) {
            successfulActions.add(action);
        }
        actionFrequency.merge(action, 1, Integer::sum);
    }

    public void addFailedAction(String action) {
        if (!failedActions.contains(action)) {
            failedActions.add(action);
        }
        actionFrequency.merge(action, 1, Integer::sum);
    }

    public void incrementUsage() {
        usageCount++;
        lastUpdated = Instant.now();
    }

    private void updateConfidence() {
        int totalOutcomes = successCount + failureCount;
        if (totalOutcomes > 0) {
            double successRate = (double) successCount / totalOutcomes;

            // Confidence increases with success rate and number of samples
            // Use a formula that approaches the success rate as samples increase
            double sampleWeight = Math.min(1.0, totalOutcomes / 10.0); // Full confidence after 10 samples
            confidence = successRate * sampleWeight + 0.5 * (1 - sampleWeight);
        }
    }

    public double getSuccessRate() {
        int total = successCount + failureCount;
        return total > 0 ? (double) successCount / total : 0.5;
    }

    public String getBestAction() {
        return successfulActions.stream()
                .max((a1, a2) -> Integer.compare(
                        actionFrequency.getOrDefault(a1, 0),
                        actionFrequency.getOrDefault(a2, 0)))
                .orElse(null);
    }

    public boolean isReliable() {
        return usageCount >= 5 && confidence > 0.7;
    }

    // Getters and setters
    public String getSituation() { return situation; }
    public void setSituation(String situation) { this.situation = situation; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }

    public List<String> getSuccessfulActions() { return successfulActions; }
    public void setSuccessfulActions(List<String> successfulActions) { this.successfulActions = successfulActions; }

    public List<String> getFailedActions() { return failedActions; }
    public void setFailedActions(List<String> failedActions) { this.failedActions = failedActions; }

    public Map<String, Integer> getActionFrequency() { return actionFrequency; }
    public void setActionFrequency(Map<String, Integer> actionFrequency) { this.actionFrequency = actionFrequency; }

    @Override
    public String toString() {
        return String.format("SemanticKnowledge{situation='%s', confidence=%.2f, usageCount=%d, successRate=%.2f}",
                situation, confidence, usageCount, getSuccessRate());
    }
}