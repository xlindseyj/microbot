package net.runelite.client.plugins.microbot.aiautonomous.ai;

import net.runelite.client.plugins.microbot.aiautonomous.ai.DecisionEngine.DecisionPriority;
import net.runelite.client.plugins.microbot.aiautonomous.ai.DecisionEngine.DecisionCategory;

import java.time.Instant;

public class AiDecision {

    private String originalSituation;
    private String action;
    private String reasoning;
    private int confidence; // 0-100
    private DecisionPriority priority;
    private DecisionCategory category;
    private Instant timestamp;
    private boolean valid;
    private String rawResponse;

    // Execution tracking
    private boolean executed = false;
    private Instant executionTime;
    private boolean successful = false;
    private String executionResult;

    public AiDecision() {
        this.timestamp = Instant.now();
    }

    // Getters and setters
    public String getOriginalSituation() {
        return originalSituation;
    }

    public void setOriginalSituation(String originalSituation) {
        this.originalSituation = originalSituation;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(int confidence) {
        this.confidence = Math.max(0, Math.min(100, confidence));
    }

    public DecisionPriority getPriority() {
        return priority;
    }

    public void setPriority(DecisionPriority priority) {
        this.priority = priority;
    }

    public DecisionCategory getCategory() {
        return category;
    }

    public void setCategory(DecisionCategory category) {
        this.category = category;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
        if (executed && executionTime == null) {
            this.executionTime = Instant.now();
        }
    }

    public Instant getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Instant executionTime) {
        this.executionTime = executionTime;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public String getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(String executionResult) {
        this.executionResult = executionResult;
    }

    // Utility methods
    public boolean shouldExecute() {
        return valid && !executed && confidence > 20;
    }

    public boolean isHighPriority() {
        return priority == DecisionPriority.HIGH || priority == DecisionPriority.URGENT;
    }

    public boolean isUrgent() {
        return priority == DecisionPriority.URGENT;
    }

    public long getAgeInSeconds() {
        return java.time.Duration.between(timestamp, Instant.now()).getSeconds();
    }

    public boolean isStale(long maxAgeSeconds) {
        return getAgeInSeconds() > maxAgeSeconds;
    }

    @Override
    public String toString() {
        return String.format("AiDecision{action='%s', confidence=%d, priority=%s, category=%s, valid=%s}",
                action, confidence, priority, category, valid);
    }

    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AI Decision:\n");
        sb.append("  Situation: ").append(originalSituation).append("\n");
        sb.append("  Action: ").append(action).append("\n");
        sb.append("  Reasoning: ").append(reasoning).append("\n");
        sb.append("  Confidence: ").append(confidence).append("%\n");
        sb.append("  Priority: ").append(priority).append("\n");
        sb.append("  Category: ").append(category).append("\n");
        sb.append("  Valid: ").append(valid).append("\n");
        sb.append("  Executed: ").append(executed).append("\n");
        if (executed) {
            sb.append("  Successful: ").append(successful).append("\n");
            sb.append("  Result: ").append(executionResult).append("\n");
        }
        sb.append("  Age: ").append(getAgeInSeconds()).append("s\n");
        return sb.toString();
    }
}