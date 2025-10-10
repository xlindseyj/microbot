package net.runelite.client.plugins.microbot.aiautonomous.planning;

import lombok.Data;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;

import java.time.LocalDateTime;
import java.util.*;

@Data
public class ObjectiveStep {
    private final String id;
    private final String description;
    private final StepType type;
    private final StepPriority priority;

    // Objective relationship
    private String objectiveId;
    private int stepIndex;

    // Status and timing
    private StepStatus status;
    private LocalDateTime startTime;
    private LocalDateTime completionTime;
    private int estimatedDurationMinutes;

    // Retry and failure handling
    private int retryCount;
    private int maxRetries;
    private String failureReason;

    // Requirements and conditions
    private List<String> prerequisites;
    private List<String> requiredItems;
    private String requiredLocation;
    private Map<String, Integer> requiredSkillLevels;

    // Execution details
    private String actionDetails;
    private Map<String, Object> stepMetadata;

    public ObjectiveStep(String id, String description, StepType type, StepPriority priority) {
        this.id = id;
        this.description = description;
        this.type = type;
        this.priority = priority;
        this.status = StepStatus.PLANNED;
        this.retryCount = 0;
        this.maxRetries = determineMaxRetries();
        this.prerequisites = new ArrayList<>();
        this.requiredItems = new ArrayList<>();
        this.requiredSkillLevels = new HashMap<>();
        this.stepMetadata = new HashMap<>();
        this.estimatedDurationMinutes = estimateStepDuration();
    }

    public boolean isExecutable(GameStateAnalyzer.GameState currentState) {
        // Check if all prerequisites are met
        if (!prerequisitesMet(currentState)) {
            return false;
        }

        // Check required items
        if (!hasRequiredItems(currentState)) {
            return false;
        }

        // Check location requirements
        if (requiredLocation != null && !isAtRequiredLocation(currentState)) {
            return false;
        }

        // Check skill level requirements
        if (!hasRequiredSkillLevels(currentState)) {
            return false;
        }

        // Check step-specific executability
        return isStepSpecificExecutable(currentState);
    }

    private boolean prerequisitesMet(GameStateAnalyzer.GameState currentState) {
        // Check if all prerequisite steps/conditions are met
        for (String prerequisite : prerequisites) {
            if (!checkPrerequisite(prerequisite, currentState)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkPrerequisite(String prerequisite, GameStateAnalyzer.GameState currentState) {
        // Simple prerequisite checking - could be expanded
        switch (prerequisite.toLowerCase()) {
            case "has_weapon":
                return currentState.getInventoryItems().keySet().stream()
                        .anyMatch(item -> item.toLowerCase().contains("sword") ||
                                        item.toLowerCase().contains("bow") ||
                                        item.toLowerCase().contains("staff"));
            case "has_food":
                return currentState.getInventoryItems().keySet().stream()
                        .anyMatch(item -> item.toLowerCase().contains("food") ||
                                        item.toLowerCase().contains("fish") ||
                                        item.toLowerCase().contains("bread"));
            case "healthy":
                return currentState.getCurrentHp() > currentState.getMaxHp() * 0.5;
            case "not_in_combat":
                return !currentState.isInCombat();
            default:
                return true; // Unknown prerequisites are assumed to be met
        }
    }

    private boolean hasRequiredItems(GameStateAnalyzer.GameState currentState) {
        for (String requiredItem : requiredItems) {
            if (!hasItem(requiredItem, currentState)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasItem(String itemName, GameStateAnalyzer.GameState currentState) {
        return currentState.getInventoryItems().keySet().stream()
                .anyMatch(item -> item.toLowerCase().contains(itemName.toLowerCase()));
    }

    private boolean isAtRequiredLocation(GameStateAnalyzer.GameState currentState) {
        if (requiredLocation == null) return true;

        String currentLocation = currentState.getLocation();
        return currentLocation != null &&
               currentLocation.toLowerCase().contains(requiredLocation.toLowerCase());
    }

    private boolean hasRequiredSkillLevels(GameStateAnalyzer.GameState currentState) {
        Map<String, Integer> currentSkills = currentState.getAllSkillLevels();

        for (Map.Entry<String, Integer> requirement : requiredSkillLevels.entrySet()) {
            String skill = requirement.getKey();
            int requiredLevel = requirement.getValue();
            int currentLevel = currentSkills.getOrDefault(skill, 1);

            if (currentLevel < requiredLevel) {
                return false;
            }
        }
        return true;
    }

    private boolean isStepSpecificExecutable(GameStateAnalyzer.GameState currentState) {
        switch (type) {
            case COMBAT:
                return currentState.getCurrentHp() > currentState.getMaxHp() * 0.3 &&
                       hasItem("weapon", currentState);

            case RESOURCE_GATHERING:
                return currentState.getInventoryItems().size() < 28; // Have inventory space

            case TRADING:
                return hasItem("coin", currentState);

            case SKILL_TRAINING:
                return currentState.getCurrentHp() > currentState.getMaxHp() * 0.2;

            case MOVEMENT:
                return !currentState.isInCombat();

            case BANKING:
                return currentState.getInventoryItems().size() > 20; // Need items to bank

            case QUEST_COMPLETION:
                // Check if quest requirements are met
                return prerequisitesMet(currentState);

            default:
                return true;
        }
    }

    public boolean canRetry() {
        return retryCount < maxRetries && status == StepStatus.FAILED;
    }

    public void incrementRetryCount() {
        retryCount++;
    }

    public List<String> getBlockingFactors(GameStateAnalyzer.GameState currentState) {
        List<String> blockingFactors = new ArrayList<>();

        if (!prerequisitesMet(currentState)) {
            blockingFactors.add("Prerequisites not met");
        }

        if (!hasRequiredItems(currentState)) {
            blockingFactors.add("Missing required items: " + String.join(", ", requiredItems));
        }

        if (requiredLocation != null && !isAtRequiredLocation(currentState)) {
            blockingFactors.add("Not at required location: " + requiredLocation);
        }

        if (!hasRequiredSkillLevels(currentState)) {
            for (Map.Entry<String, Integer> req : requiredSkillLevels.entrySet()) {
                int currentLevel = currentState.getAllSkillLevels().getOrDefault(req.getKey(), 1);
                if (currentLevel < req.getValue()) {
                    blockingFactors.add(String.format("Skill %s level %d required (current: %d)",
                            req.getKey(), req.getValue(), currentLevel));
                }
            }
        }

        return blockingFactors;
    }

    private int estimateStepDuration() {
        // Estimate duration based on step type
        switch (type) {
            case MOVEMENT:
                return 2; // 2 minutes
            case COMBAT:
                return 10; // 10 minutes
            case SKILL_TRAINING:
                return 30; // 30 minutes
            case RESOURCE_GATHERING:
                return 15; // 15 minutes
            case TRADING:
                return 3; // 3 minutes
            case BANKING:
                return 2; // 2 minutes
            case QUEST_COMPLETION:
                return 20; // 20 minutes
            case INTERACTION:
                return 1; // 1 minute
            case ASSESSMENT:
                return 1; // 1 minute
            case PLANNING:
                return 2; // 2 minutes
            case EXPLORATION:
                return 10; // 10 minutes
            case RESOURCE_MANAGEMENT:
                return 5; // 5 minutes
            default:
                return 5; // Default 5 minutes
        }
    }

    private int determineMaxRetries() {
        // Different step types have different retry tolerances
        switch (type) {
            case MOVEMENT:
                return 5; // Movement can be retried many times
            case COMBAT:
                return 3; // Combat should be limited retries for safety
            case TRADING:
                return 2; // Trading should be limited
            case SKILL_TRAINING:
                return 1; // Skill training usually doesn't need retries
            case QUEST_COMPLETION:
                return 3; // Quest steps can be retried
            default:
                return 2; // Default 2 retries
        }
    }

    // Builder methods for setting requirements
    public ObjectiveStep withPrerequisite(String prerequisite) {
        prerequisites.add(prerequisite);
        return this;
    }

    public ObjectiveStep withRequiredItem(String item) {
        requiredItems.add(item);
        return this;
    }

    public ObjectiveStep withRequiredLocation(String location) {
        this.requiredLocation = location;
        return this;
    }

    public ObjectiveStep withRequiredSkillLevel(String skill, int level) {
        requiredSkillLevels.put(skill, level);
        return this;
    }

    public ObjectiveStep withActionDetails(String details) {
        this.actionDetails = details;
        return this;
    }

    public void setStepMetadata(String key, Object value) {
        stepMetadata.put(key, value);
    }

    public Object getStepMetadata(String key) {
        return stepMetadata.get(key);
    }

    public String getSummary() {
        return String.format("%s (%s) - %s [%s]",
                description, type, status,
                retryCount > 0 ? "retry " + retryCount : "first attempt");
    }
}

enum StepType {
    MOVEMENT, COMBAT, SKILL_TRAINING, RESOURCE_GATHERING,
    TRADING, BANKING, QUEST_COMPLETION, INTERACTION,
    ASSESSMENT, PLANNING, EXPLORATION, RESOURCE_MANAGEMENT
}

enum StepPriority {
    LOW, MEDIUM, HIGH, URGENT
}

enum StepStatus {
    PLANNED, ACTIVE, COMPLETED, FAILED, SKIPPED, CANCELLED
}