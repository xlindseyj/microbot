package net.runelite.client.plugins.microbot.aiautonomous.planning;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.ai.KnowledgeManager;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class ObjectivePlanner {

    private final KnowledgeManager knowledgeManager;
    private final AiAutonomousConfig config;
    private final Map<String, GameObjective> activeObjectives;
    private final Queue<GameObjective> objectiveQueue;
    private final Map<String, ObjectiveTemplate> objectiveTemplates;

    // Objective priorities and constraints
    private static final int MAX_ACTIVE_OBJECTIVES = 3;
    private static final int MAX_QUEUED_OBJECTIVES = 10;

    public ObjectivePlanner(KnowledgeManager knowledgeManager, AiAutonomousConfig config) {
        this.knowledgeManager = knowledgeManager;
        this.config = config;
        this.activeObjectives = new HashMap<>();
        this.objectiveQueue = new LinkedList<>();
        this.objectiveTemplates = new HashMap<>();

        initializeObjectiveTemplates();
        log.info("Objective planner initialized with {} templates", objectiveTemplates.size());
    }

    public CompletableFuture<List<GameObjective>> planObjectives(GameStateAnalyzer.GameState currentState) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Planning objectives for current game state");

                // Analyze current situation and needs
                ObjectiveContext context = analyzeCurrentContext(currentState);

                // Generate potential objectives
                List<GameObjective> potentialObjectives = generatePotentialObjectives(context);

                // Prioritize objectives based on current needs and past success
                List<GameObjective> prioritizedObjectives = prioritizeObjectives(potentialObjectives, context);

                // Select objectives to pursue
                List<GameObjective> selectedObjectives = selectObjectivesToPursue(prioritizedObjectives, context);

                // Update active objectives
                updateActiveObjectives(selectedObjectives);

                log.info("Planned {} objectives: {}",
                        selectedObjectives.size(),
                        selectedObjectives.stream()
                                .map(GameObjective::getName)
                                .collect(Collectors.joining(", ")));

                return selectedObjectives;

            } catch (Exception e) {
                log.error("Error planning objectives", e);
                return new ArrayList<>();
            }
        });
    }

    public List<ObjectiveStep> getNextSteps(GameStateAnalyzer.GameState currentState) {
        List<ObjectiveStep> nextSteps = new ArrayList<>();

        for (GameObjective objective : activeObjectives.values()) {
            if (objective.getStatus() == ObjectiveStatus.ACTIVE) {
                ObjectiveStep nextStep = objective.getNextStep(currentState);
                if (nextStep != null && nextStep.isExecutable(currentState)) {
                    nextSteps.add(nextStep);
                }
            }
        }

        // Sort by priority
        nextSteps.sort(Comparator.comparing(ObjectiveStep::getPriority).reversed());

        return nextSteps;
    }

    public void updateObjectiveProgress(String objectiveId, String stepId, boolean successful, String outcome) {
        GameObjective objective = activeObjectives.get(objectiveId);
        if (objective != null) {
            objective.updateStepProgress(stepId, successful, outcome);

            // Store experience for this objective progress
            if (knowledgeManager != null) {
                String experience = String.format("Objective: %s, Step: %s, Success: %s, Outcome: %s",
                        objective.getName(), stepId, successful, outcome);

                // This would be stored as quest experience if quest-related, skill experience if skill-related, etc.
                storeObjectiveExperience(objective, stepId, successful, outcome);
            }

            // Check if objective is completed
            if (objective.isCompleted()) {
                completeObjective(objectiveId);
            } else if (objective.hasFailed()) {
                failObjective(objectiveId, "Step failures exceeded threshold");
            }

            log.debug("Updated objective {} progress: {} ({})",
                    objective.getName(), stepId, successful ? "success" : "failure");
        }
    }

    private ObjectiveContext analyzeCurrentContext(GameStateAnalyzer.GameState currentState) {
        ObjectiveContext context = new ObjectiveContext();
        context.setCurrentState(currentState);
        context.setActiveObjectives(new ArrayList<>(activeObjectives.values()));

        // Analyze current needs
        List<String> currentNeeds = identifyCurrentNeeds(currentState);
        context.setCurrentNeeds(currentNeeds);

        // Get success rates for different objective types
        if (knowledgeManager != null) {
            context.setActionSuccessRates(knowledgeManager.getActionSuccessRates());
        }

        return context;
    }

    private List<String> identifyCurrentNeeds(GameStateAnalyzer.GameState currentState) {
        List<String> needs = new ArrayList<>();

        // Resource needs
        if (currentState.getCurrentHp() < currentState.getMaxHp() * 0.5) {
            needs.add("healing");
        }

        if (currentState.getInventoryItems().size() < 5) {
            needs.add("resources");
        }

        // Skill progression needs
        Map<String, Integer> skillLevels = currentState.getAllSkillLevels();
        for (Map.Entry<String, Integer> skill : skillLevels.entrySet()) {
            if (skill.getValue() < 20) {
                needs.add("skill_training_" + skill.getKey());
            }
        }

        // Equipment needs
        if (!hasAdequateEquipment(currentState)) {
            needs.add("equipment_upgrade");
        }

        // Quest progression
        if (currentState.getQuestProgress().isEmpty()) {
            needs.add("quest_progression");
        }

        return needs;
    }

    private boolean hasAdequateEquipment(GameStateAnalyzer.GameState currentState) {
        // Simple check - could be more sophisticated
        return currentState.getInventoryItems().keySet().stream()
                .anyMatch(item -> item.toLowerCase().contains("sword") ||
                                item.toLowerCase().contains("bow"));
    }

    private List<GameObjective> generatePotentialObjectives(ObjectiveContext context) {
        List<GameObjective> potentialObjectives = new ArrayList<>();

        // Generate objectives based on current needs
        for (String need : context.getCurrentNeeds()) {
            potentialObjectives.addAll(generateObjectivesForNeed(need, context));
        }

        // Generate progression objectives
        potentialObjectives.addAll(generateProgressionObjectives(context));

        // Generate exploration objectives
        potentialObjectives.addAll(generateExplorationObjectives(context));

        return potentialObjectives;
    }

    private List<GameObjective> generateObjectivesForNeed(String need, ObjectiveContext context) {
        List<GameObjective> objectives = new ArrayList<>();

        switch (need) {
            case "healing":
                objectives.add(createObjectiveFromTemplate("obtain_food", context));
                break;
            case "resources":
                objectives.add(createObjectiveFromTemplate("gather_basic_resources", context));
                break;
            case "equipment_upgrade":
                objectives.add(createObjectiveFromTemplate("obtain_better_equipment", context));
                break;
            case "quest_progression":
                objectives.add(createObjectiveFromTemplate("start_beginner_quest", context));
                break;
            default:
                if (need.startsWith("skill_training_")) {
                    String skill = need.replace("skill_training_", "");
                    objectives.add(createSkillTrainingObjective(skill, context));
                }
                break;
        }

        return objectives.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private List<GameObjective> generateProgressionObjectives(ObjectiveContext context) {
        List<GameObjective> objectives = new ArrayList<>();

        // Combat level progression
        if (context.getCurrentState().getCombatLevel() < 50) {
            objectives.add(createObjectiveFromTemplate("improve_combat_level", context));
        }

        // Total level progression
        int totalLevel = context.getCurrentState().getAllSkillLevels().values()
                .stream().mapToInt(Integer::intValue).sum();
        if (totalLevel < 500) {
            objectives.add(createObjectiveFromTemplate("reach_total_level_milestone", context));
        }

        return objectives;
    }

    private List<GameObjective> generateExplorationObjectives(ObjectiveContext context) {
        List<GameObjective> objectives = new ArrayList<>();

        // Explore new areas
        objectives.add(createObjectiveFromTemplate("explore_new_area", context));

        // Visit important locations
        objectives.add(createObjectiveFromTemplate("visit_major_cities", context));

        return objectives;
    }

    private GameObjective createObjectiveFromTemplate(String templateId, ObjectiveContext context) {
        ObjectiveTemplate template = objectiveTemplates.get(templateId);
        if (template == null) {
            log.warn("No template found for objective: {}", templateId);
            return null;
        }

        return template.createObjective(context);
    }

    private GameObjective createSkillTrainingObjective(String skill, ObjectiveContext context) {
        int currentLevel = context.getCurrentState().getAllSkillLevels()
                .getOrDefault(skill, 1);
        int targetLevel = Math.min(currentLevel + 10, 99); // Train 10 levels or to 99

        GameObjective objective = new GameObjective(
                "train_" + skill + "_to_" + targetLevel,
                "Train " + skill + " to level " + targetLevel,
                ObjectiveType.SKILL_TRAINING,
                ObjectivePriority.MEDIUM
        );

        // Add steps for skill training
        objective.addStep(new ObjectiveStep(
                "step_find_training_location",
                "Find " + skill + " training location",
                StepType.MOVEMENT,
                StepPriority.HIGH
        ));

        objective.addStep(new ObjectiveStep(
                "step_obtain_tools",
                "Obtain " + skill + " training tools",
                StepType.RESOURCE_GATHERING,
                StepPriority.HIGH
        ));

        objective.addStep(new ObjectiveStep(
                "step_train_skill",
                "Train " + skill + " from " + currentLevel + " to " + targetLevel,
                StepType.SKILL_TRAINING,
                StepPriority.MEDIUM
        ));

        return objective;
    }

    private List<GameObjective> prioritizeObjectives(List<GameObjective> objectives, ObjectiveContext context) {
        // Sort by priority, success rate, and estimated time
        return objectives.stream()
                .sorted((o1, o2) -> {
                    // Primary: Priority
                    int priorityCompare = o2.getPriority().ordinal() - o1.getPriority().ordinal();
                    if (priorityCompare != 0) return priorityCompare;

                    // Secondary: Success rate from past experience
                    double successRate1 = getObjectiveSuccessRate(o1, context);
                    double successRate2 = getObjectiveSuccessRate(o2, context);
                    int successCompare = Double.compare(successRate2, successRate1);
                    if (successCompare != 0) return successCompare;

                    // Tertiary: Estimated completion time (shorter first)
                    return Integer.compare(o1.getEstimatedDurationMinutes(), o2.getEstimatedDurationMinutes());
                })
                .collect(Collectors.toList());
    }

    private double getObjectiveSuccessRate(GameObjective objective, ObjectiveContext context) {
        if (context.getActionSuccessRates() == null) return 0.5; // Default 50%

        // Get success rate for this type of objective
        String objectiveKey = objective.getType().toString().toLowerCase();
        return context.getActionSuccessRates().getOrDefault(objectiveKey, 0.5);
    }

    private List<GameObjective> selectObjectivesToPursue(List<GameObjective> prioritizedObjectives, ObjectiveContext context) {
        List<GameObjective> selected = new ArrayList<>();

        // Don't exceed max active objectives
        int currentActiveCount = (int) activeObjectives.values().stream()
                .filter(obj -> obj.getStatus() == ObjectiveStatus.ACTIVE)
                .count();

        int availableSlots = MAX_ACTIVE_OBJECTIVES - currentActiveCount;

        for (GameObjective objective : prioritizedObjectives) {
            if (selected.size() >= availableSlots) break;

            // Check if we're not already pursuing this type of objective
            if (!hasConflictingObjective(objective, context)) {
                selected.add(objective);
            }
        }

        return selected;
    }

    private boolean hasConflictingObjective(GameObjective newObjective, ObjectiveContext context) {
        // Check if we already have an objective of the same type
        return context.getActiveObjectives().stream()
                .anyMatch(existing -> existing.getType() == newObjective.getType() &&
                                    existing.getStatus() == ObjectiveStatus.ACTIVE);
    }

    private void updateActiveObjectives(List<GameObjective> newObjectives) {
        for (GameObjective objective : newObjectives) {
            objective.setStatus(ObjectiveStatus.ACTIVE);
            objective.setStartTime(LocalDateTime.now());
            activeObjectives.put(objective.getId(), objective);
        }
    }

    private void completeObjective(String objectiveId) {
        GameObjective objective = activeObjectives.get(objectiveId);
        if (objective != null) {
            objective.setStatus(ObjectiveStatus.COMPLETED);
            objective.setCompletionTime(LocalDateTime.now());

            log.info("Completed objective: {} (took {} minutes)",
                    objective.getName(), objective.getActualDurationMinutes());

            // Store completion experience
            if (knowledgeManager != null) {
                storeObjectiveCompletion(objective);
            }

            // Remove from active objectives
            activeObjectives.remove(objectiveId);
        }
    }

    private void failObjective(String objectiveId, String reason) {
        GameObjective objective = activeObjectives.get(objectiveId);
        if (objective != null) {
            objective.setStatus(ObjectiveStatus.FAILED);
            objective.setFailureReason(reason);

            log.warn("Failed objective: {} - {}", objective.getName(), reason);

            // Store failure experience for learning
            if (knowledgeManager != null) {
                storeObjectiveFailure(objective, reason);
            }

            // Remove from active objectives
            activeObjectives.remove(objectiveId);
        }
    }

    private void storeObjectiveExperience(GameObjective objective, String stepId, boolean successful, String outcome) {
        try {
            switch (objective.getType()) {
                case QUEST:
                    knowledgeManager.storeQuestExperience(
                            objective.getName(),
                            stepId,
                            successful,
                            objective.getRequirements(),
                            objective.getStrategy()
                    );
                    break;
                case SKILL_TRAINING:
                    // Extract skill info from objective name
                    String[] parts = objective.getName().split("_");
                    if (parts.length >= 4) {
                        String skill = parts[1];
                        try {
                            int targetLevel = Integer.parseInt(parts[3]);
                            knowledgeManager.storeSkillProgressExperience(
                                    skill, targetLevel - 1, targetLevel,
                                    objective.getStrategy(),
                                    System.currentTimeMillis() - objective.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            );
                        } catch (NumberFormatException e) {
                            // Fallback to general experience
                            storeGeneralObjectiveExperience(objective, stepId, successful, outcome);
                        }
                    }
                    break;
                case COMBAT:
                    knowledgeManager.storeCombatExperience(
                            objective.getTarget(),
                            objective.getStrategy(),
                            successful,
                            0, // damage - would need to track this
                            System.currentTimeMillis() - objective.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    );
                    break;
                default:
                    storeGeneralObjectiveExperience(objective, stepId, successful, outcome);
                    break;
            }
        } catch (Exception e) {
            log.warn("Failed to store objective experience", e);
        }
    }

    private void storeGeneralObjectiveExperience(GameObjective objective, String stepId, boolean successful, String outcome) {
        // Store as general gameplay experience
        GameStateAnalyzer.GameState dummyState = new GameStateAnalyzer.GameState();
        dummyState.setLocation("unknown");
        knowledgeManager.storeGameplayExperience(dummyState, stepId, outcome, successful);
    }

    private void storeObjectiveCompletion(GameObjective objective) {
        String context = String.format("Completed objective: %s (type: %s, duration: %d minutes)",
                objective.getName(), objective.getType(), objective.getActualDurationMinutes());
        knowledgeManager.addExternalKnowledge("objective_completion", context, Map.of(
                "objective_type", objective.getType().toString(),
                "success", "true",
                "duration_minutes", String.valueOf(objective.getActualDurationMinutes())
        ));
    }

    private void storeObjectiveFailure(GameObjective objective, String reason) {
        String context = String.format("Failed objective: %s (type: %s, reason: %s)",
                objective.getName(), objective.getType(), reason);
        knowledgeManager.addExternalKnowledge("objective_failure", context, Map.of(
                "objective_type", objective.getType().toString(),
                "success", "false",
                "failure_reason", reason
        ));
    }

    private void initializeObjectiveTemplates() {
        // Basic resource gathering
        objectiveTemplates.put("obtain_food", createResourceGatheringTemplate("food", "Obtain food for healing"));
        objectiveTemplates.put("gather_basic_resources", createResourceGatheringTemplate("basic_resources", "Gather basic resources"));
        objectiveTemplates.put("obtain_better_equipment", createEquipmentTemplate());

        // Quest templates
        objectiveTemplates.put("start_beginner_quest", createBeginnerQuestTemplate());

        // Combat templates
        objectiveTemplates.put("improve_combat_level", createCombatTrainingTemplate());

        // Exploration templates
        objectiveTemplates.put("explore_new_area", createExplorationTemplate());
        objectiveTemplates.put("visit_major_cities", createCityVisitTemplate());

        // Progression templates
        objectiveTemplates.put("reach_total_level_milestone", createTotalLevelTemplate());
    }

    private ObjectiveTemplate createResourceGatheringTemplate(String resourceType, String description) {
        return context -> {
            GameObjective objective = new GameObjective(
                    "gather_" + resourceType,
                    description,
                    ObjectiveType.RESOURCE_GATHERING,
                    ObjectivePriority.MEDIUM
            );

            objective.addStep(new ObjectiveStep("find_resource_location", "Find " + resourceType + " location", StepType.MOVEMENT, StepPriority.HIGH));
            objective.addStep(new ObjectiveStep("gather_resources", "Gather " + resourceType, StepType.RESOURCE_GATHERING, StepPriority.MEDIUM));
            objective.addStep(new ObjectiveStep("bank_resources", "Bank gathered " + resourceType, StepType.BANKING, StepPriority.LOW));

            return objective;
        };
    }

    private ObjectiveTemplate createEquipmentTemplate() {
        return context -> {
            GameObjective objective = new GameObjective(
                    "obtain_better_equipment",
                    "Obtain better equipment",
                    ObjectiveType.EQUIPMENT,
                    ObjectivePriority.HIGH
            );

            objective.addStep(new ObjectiveStep("assess_current_equipment", "Assess current equipment", StepType.ASSESSMENT, StepPriority.HIGH));
            objective.addStep(new ObjectiveStep("find_equipment_vendor", "Find equipment vendor", StepType.MOVEMENT, StepPriority.HIGH));
            objective.addStep(new ObjectiveStep("purchase_equipment", "Purchase better equipment", StepType.TRADING, StepPriority.MEDIUM));

            return objective;
        };
    }

    private ObjectiveTemplate createBeginnerQuestTemplate() {
        return context -> {
            GameObjective objective = new GameObjective(
                    "start_beginner_quest",
                    "Start a beginner quest",
                    ObjectiveType.QUEST,
                    ObjectivePriority.MEDIUM
            );

            objective.addStep(new ObjectiveStep("find_quest_giver", "Find quest giver", StepType.MOVEMENT, StepPriority.HIGH));
            objective.addStep(new ObjectiveStep("accept_quest", "Accept quest", StepType.INTERACTION, StepPriority.MEDIUM));
            objective.addStep(new ObjectiveStep("complete_quest_objectives", "Complete quest objectives", StepType.QUEST_COMPLETION, StepPriority.MEDIUM));

            return objective;
        };
    }

    private ObjectiveTemplate createCombatTrainingTemplate() {
        return context -> {
            GameObjective objective = new GameObjective(
                    "improve_combat_level",
                    "Improve combat level",
                    ObjectiveType.COMBAT,
                    ObjectivePriority.MEDIUM
            );

            objective.addStep(new ObjectiveStep("find_training_area", "Find combat training area", StepType.MOVEMENT, StepPriority.HIGH));
            objective.addStep(new ObjectiveStep("engage_combat", "Engage in combat training", StepType.COMBAT, StepPriority.MEDIUM));
            objective.addStep(new ObjectiveStep("manage_resources", "Manage health and resources", StepType.RESOURCE_MANAGEMENT, StepPriority.HIGH));

            return objective;
        };
    }

    private ObjectiveTemplate createExplorationTemplate() {
        return context -> {
            GameObjective objective = new GameObjective(
                    "explore_new_area",
                    "Explore new area",
                    ObjectiveType.EXPLORATION,
                    ObjectivePriority.LOW
            );

            objective.addStep(new ObjectiveStep("choose_unexplored_area", "Choose unexplored area", StepType.PLANNING, StepPriority.MEDIUM));
            objective.addStep(new ObjectiveStep("travel_to_area", "Travel to area", StepType.MOVEMENT, StepPriority.MEDIUM));
            objective.addStep(new ObjectiveStep("explore_area", "Explore the area", StepType.EXPLORATION, StepPriority.LOW));

            return objective;
        };
    }

    private ObjectiveTemplate createCityVisitTemplate() {
        return context -> {
            GameObjective objective = new GameObjective(
                    "visit_major_cities",
                    "Visit major cities",
                    ObjectiveType.EXPLORATION,
                    ObjectivePriority.LOW
            );

            objective.addStep(new ObjectiveStep("plan_city_route", "Plan route to major cities", StepType.PLANNING, StepPriority.MEDIUM));
            objective.addStep(new ObjectiveStep("visit_cities", "Visit each major city", StepType.MOVEMENT, StepPriority.LOW));

            return objective;
        };
    }

    private ObjectiveTemplate createTotalLevelTemplate() {
        return context -> {
            int currentTotal = context.getCurrentState().getAllSkillLevels().values()
                    .stream().mapToInt(Integer::intValue).sum();
            int targetTotal = ((currentTotal / 100) + 1) * 100; // Next 100 level milestone

            GameObjective objective = new GameObjective(
                    "reach_total_level_" + targetTotal,
                    "Reach total level " + targetTotal,
                    ObjectiveType.SKILL_TRAINING,
                    ObjectivePriority.LOW
            );

            objective.addStep(new ObjectiveStep("identify_skills_to_train", "Identify skills to train", StepType.PLANNING, StepPriority.MEDIUM));
            objective.addStep(new ObjectiveStep("train_multiple_skills", "Train multiple skills", StepType.SKILL_TRAINING, StepPriority.LOW));

            return objective;
        };
    }

    // Getters and status methods
    public Map<String, GameObjective> getActiveObjectives() {
        return new HashMap<>(activeObjectives);
    }

    public Queue<GameObjective> getObjectiveQueue() {
        return new LinkedList<>(objectiveQueue);
    }

    public String getStatus() {
        long activeCount = activeObjectives.values().stream()
                .filter(obj -> obj.getStatus() == ObjectiveStatus.ACTIVE)
                .count();

        return String.format("Active objectives: %d/%d, Queue: %d",
                activeCount, MAX_ACTIVE_OBJECTIVES, objectiveQueue.size());
    }

    @FunctionalInterface
    public interface ObjectiveTemplate {
        GameObjective createObjective(ObjectiveContext context);
    }
}