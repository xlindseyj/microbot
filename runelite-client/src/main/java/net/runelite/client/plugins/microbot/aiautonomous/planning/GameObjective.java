package net.runelite.client.plugins.microbot.aiautonomous.planning;

import lombok.Data;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Data
public class GameObjective {
    private final String id;
    private final String name;
    private final ObjectiveType type;
    private ObjectivePriority priority;
    private ObjectiveStatus status;

    // Timing information
    private LocalDateTime creationTime;
    private LocalDateTime startTime;
    private LocalDateTime completionTime;
    private int estimatedDurationMinutes;

    // Steps and progress
    private List<ObjectiveStep> steps;
    private int currentStepIndex;
    private Map<String, Object> metadata;

    // Success tracking
    private int successfulSteps;
    private int failedSteps;
    private String failureReason;

    // Strategy and requirements
    private String strategy;
    private List<String> requirements;
    private String target; // For combat objectives

    public GameObjective(String id, String name, ObjectiveType type, ObjectivePriority priority) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.priority = priority;
        this.status = ObjectiveStatus.PLANNED;
        this.creationTime = LocalDateTime.now();
        this.steps = new ArrayList<>();
        this.currentStepIndex = 0;
        this.metadata = new HashMap<>();
        this.successfulSteps = 0;
        this.failedSteps = 0;
        this.requirements = new ArrayList<>();
        this.estimatedDurationMinutes = estimateBaseDuration();
    }

    public void addStep(ObjectiveStep step) {
        step.setObjectiveId(this.id);
        step.setStepIndex(steps.size());
        steps.add(step);

        // Update estimated duration
        estimatedDurationMinutes += step.getEstimatedDurationMinutes();
    }

    public ObjectiveStep getCurrentStep() {
        if (currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }

    public ObjectiveStep getNextStep(GameStateAnalyzer.GameState currentState) {
        ObjectiveStep currentStep = getCurrentStep();
        if (currentStep != null && currentStep.getStatus() != StepStatus.COMPLETED) {
            return currentStep;
        }

        // Move to next step if current is completed
        if (currentStep != null && currentStep.getStatus() == StepStatus.COMPLETED) {
            currentStepIndex++;
            return getCurrentStep();
        }

        return null;
    }

    public void updateStepProgress(String stepId, boolean successful, String outcome) {
        Optional<ObjectiveStep> stepOpt = steps.stream()
                .filter(step -> step.getId().equals(stepId))
                .findFirst();

        if (stepOpt.isPresent()) {
            ObjectiveStep step = stepOpt.get();

            if (successful) {
                step.setStatus(StepStatus.COMPLETED);
                step.setCompletionTime(LocalDateTime.now());
                successfulSteps++;

                // Auto-advance to next step if this was the current step
                if (steps.indexOf(step) == currentStepIndex) {
                    currentStepIndex++;
                }
            } else {
                step.setStatus(StepStatus.FAILED);
                step.setFailureReason(outcome);
                failedSteps++;

                // Check if we should retry or fail the objective
                if (step.canRetry()) {
                    step.incrementRetryCount();
                    step.setStatus(StepStatus.ACTIVE);
                } else {
                    // Too many failures, consider failing the objective
                    if (getFailureRate() > 0.7) { // 70% failure rate
                        this.status = ObjectiveStatus.FAILED;
                        this.failureReason = "Too many step failures";
                    }
                }
            }
        }
    }

    public boolean isCompleted() {
        return status == ObjectiveStatus.COMPLETED ||
               (currentStepIndex >= steps.size() &&
                steps.stream().allMatch(step -> step.getStatus() == StepStatus.COMPLETED));
    }

    public boolean hasFailed() {
        return status == ObjectiveStatus.FAILED || getFailureRate() > 0.8;
    }

    public double getFailureRate() {
        int totalSteps = successfulSteps + failedSteps;
        return totalSteps > 0 ? (double) failedSteps / totalSteps : 0.0;
    }

    public double getCompletionPercentage() {
        if (steps.isEmpty()) return 0.0;

        long completedSteps = steps.stream()
                .filter(step -> step.getStatus() == StepStatus.COMPLETED)
                .count();

        return (double) completedSteps / steps.size() * 100.0;
    }

    public long getActualDurationMinutes() {
        if (startTime == null) return 0;

        LocalDateTime endTime = completionTime != null ? completionTime : LocalDateTime.now();
        return ChronoUnit.MINUTES.between(startTime, endTime);
    }

    public boolean isOverdue() {
        if (startTime == null) return false;

        long actualDuration = getActualDurationMinutes();
        return actualDuration > estimatedDurationMinutes * 1.5; // 50% over estimate
    }

    public List<String> getBlockingFactors(GameStateAnalyzer.GameState currentState) {
        List<String> blockingFactors = new ArrayList<>();

        // Check requirements
        for (String requirement : requirements) {
            if (!meetsRequirement(requirement, currentState)) {
                blockingFactors.add("Missing requirement: " + requirement);
            }
        }

        // Check current step requirements
        ObjectiveStep currentStep = getCurrentStep();
        if (currentStep != null && !currentStep.isExecutable(currentState)) {
            blockingFactors.add("Current step not executable: " + currentStep.getDescription());
        }

        return blockingFactors;
    }

    private boolean meetsRequirement(String requirement, GameStateAnalyzer.GameState currentState) {
        // Simple requirement checking - could be expanded
        switch (requirement.toLowerCase()) {
            case "combat_gear":
                return currentState.getInventoryItems().keySet().stream()
                        .anyMatch(item -> item.toLowerCase().contains("sword") ||
                                        item.toLowerCase().contains("bow"));
            case "food":
                return currentState.getInventoryItems().keySet().stream()
                        .anyMatch(item -> item.toLowerCase().contains("food") ||
                                        item.toLowerCase().contains("fish"));
            case "money":
                return currentState.getInventoryItems().keySet().stream()
                        .anyMatch(item -> item.toLowerCase().contains("coin"));
            default:
                return true; // Unknown requirements are assumed to be met
        }
    }

    private int estimateBaseDuration() {
        // Base duration estimates by objective type
        switch (type) {
            case COMBAT:
                return 30; // 30 minutes
            case SKILL_TRAINING:
                return 60; // 1 hour
            case QUEST:
                return 45; // 45 minutes
            case RESOURCE_GATHERING:
                return 20; // 20 minutes
            case EXPLORATION:
                return 15; // 15 minutes
            case EQUIPMENT:
                return 10; // 10 minutes
            case TRADING:
                return 5; // 5 minutes
            default:
                return 30; // Default 30 minutes
        }
    }

    public void addRequirement(String requirement) {
        if (!requirements.contains(requirement)) {
            requirements.add(requirement);
        }
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    public String getSummary() {
        return String.format("%s (%s) - %s [%.1f%% complete, %d/%d steps]",
                name, type, status, getCompletionPercentage(),
                successfulSteps, steps.size());
    }
}

enum ObjectiveType {
    QUEST, SKILL_TRAINING, COMBAT, RESOURCE_GATHERING,
    EXPLORATION, EQUIPMENT, TRADING, OTHER
}

enum ObjectivePriority {
    LOW, MEDIUM, HIGH, URGENT
}

enum ObjectiveStatus {
    PLANNED, ACTIVE, PAUSED, COMPLETED, FAILED, CANCELLED
}