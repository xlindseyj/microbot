package net.runelite.client.plugins.microbot.aiautonomous.ai.memory;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.integration.RAGSystemInterface;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class ExperienceStorage {

    private final RAGSystemInterface ragSystem;
    private final AiAutonomousConfig config;
    private final Map<String, GameplayExperience> sessionExperiences;
    private final Map<String, Integer> actionSuccessRates;
    private final Map<String, List<String>> contextualLearning;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ExperienceStorage(RAGSystemInterface ragSystem, AiAutonomousConfig config) {
        this.ragSystem = ragSystem;
        this.config = config;
        this.sessionExperiences = new ConcurrentHashMap<>();
        this.actionSuccessRates = new ConcurrentHashMap<>();
        this.contextualLearning = new ConcurrentHashMap<>();

        log.info("Experience storage initialized with RAG system: {}",
                 ragSystem != null ? ragSystem.getSystemType() : "none");
    }

    public void storeGameplayExperience(GameStateAnalyzer.GameState gameState,
                                      String action,
                                      String outcome,
                                      boolean successful) {
        try {
            String experienceId = generateExperienceId();

            GameplayExperience experience = new GameplayExperience(
                experienceId,
                LocalDateTime.now(),
                gameState,
                action,
                outcome,
                successful
            );

            sessionExperiences.put(experienceId, experience);
            updateSuccessRates(action, successful);

            if (ragSystem != null && ragSystem.isReady()) {
                storeInRAGSystem(experience);
            }

            log.debug("Stored gameplay experience: {} -> {} ({})",
                     action, outcome, successful ? "success" : "failed");

        } catch (Exception e) {
            log.error("Failed to store gameplay experience", e);
        }
    }

    public void storeSkillProgressExperience(String skill, int oldLevel, int newLevel,
                                           String method, long timeSpent) {
        try {
            String context = String.format(
                "Skill training: %s from %d to %d using %s (took %d minutes)",
                skill, oldLevel, newLevel, method, timeSpent / 60000
            );

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "skill_progress");
            metadata.put("skill", skill);
            metadata.put("old_level", oldLevel);
            metadata.put("new_level", newLevel);
            metadata.put("method", method);
            metadata.put("time_spent_ms", timeSpent);
            metadata.put("xp_per_hour", calculateXpPerHour(oldLevel, newLevel, timeSpent));

            if (ragSystem != null && ragSystem.isReady()) {
                ragSystem.storeDocument(generateExperienceId(), context, metadata);
            }

            log.info("Stored skill progress: {} from {} to {} using {}",
                    skill, oldLevel, newLevel, method);

        } catch (Exception e) {
            log.error("Failed to store skill progress experience", e);
        }
    }

    public void storeQuestExperience(String questName, String step, boolean completed,
                                   List<String> requirements, String strategy) {
        try {
            String context = String.format(
                "Quest experience: %s - %s %s. Strategy: %s. Requirements: %s",
                questName, step, completed ? "completed" : "attempted",
                strategy, String.join(", ", requirements)
            );

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "quest_experience");
            metadata.put("quest_name", questName);
            metadata.put("step", step);
            metadata.put("completed", completed);
            metadata.put("requirements", requirements);
            metadata.put("strategy", strategy);

            if (ragSystem != null && ragSystem.isReady()) {
                ragSystem.storeDocument(generateExperienceId(), context, metadata);
            }

            addContextualLearning("quest:" + questName, strategy);

            log.info("Stored quest experience: {} - {} ({})",
                    questName, step, completed ? "completed" : "failed");

        } catch (Exception e) {
            log.error("Failed to store quest experience", e);
        }
    }

    public void storeCombatExperience(String opponent, String strategy, boolean victory,
                                    int damage, long duration) {
        try {
            String context = String.format(
                "Combat experience: Fought %s using %s strategy. Result: %s. " +
                "Damage dealt: %d, Duration: %d seconds",
                opponent, strategy, victory ? "victory" : "defeat", damage, duration / 1000
            );

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "combat_experience");
            metadata.put("opponent", opponent);
            metadata.put("strategy", strategy);
            metadata.put("victory", victory);
            metadata.put("damage_dealt", damage);
            metadata.put("duration_ms", duration);
            metadata.put("dps", damage / (duration / 1000.0));

            if (ragSystem != null && ragSystem.isReady()) {
                ragSystem.storeDocument(generateExperienceId(), context, metadata);
            }

            updateSuccessRates("combat:" + opponent, victory);
            addContextualLearning("combat:" + opponent, strategy);

            log.info("Stored combat experience: {} vs {} using {} ({})",
                    opponent, strategy, victory ? "won" : "lost");

        } catch (Exception e) {
            log.error("Failed to store combat experience", e);
        }
    }

    public void storeResourceGatheringExperience(String resource, String method,
                                               int quantity, long timeSpent) {
        try {
            String context = String.format(
                "Resource gathering: Collected %d %s using %s method in %d minutes",
                quantity, resource, method, timeSpent / 60000
            );

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "resource_gathering");
            metadata.put("resource", resource);
            metadata.put("method", method);
            metadata.put("quantity", quantity);
            metadata.put("time_spent_ms", timeSpent);
            metadata.put("resources_per_hour", (quantity * 3600000.0) / timeSpent);

            if (ragSystem != null && ragSystem.isReady()) {
                ragSystem.storeDocument(generateExperienceId(), context, metadata);
            }

            addContextualLearning("gather:" + resource, method);

            log.info("Stored resource gathering: {} {} using {} ({}/hour)",
                    quantity, resource, method,
                    Math.round((quantity * 3600000.0) / timeSpent));

        } catch (Exception e) {
            log.error("Failed to store resource gathering experience", e);
        }
    }

    public CompletableFuture<List<String>> queryRelevantExperiences(String context) {
        if (ragSystem == null || !ragSystem.isReady()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        try {
            return ragSystem.searchDocuments(context, config.maxSearchResults())
                .thenApply(results -> {
                    log.debug("Found {} relevant experiences for context: {}",
                             results.size(), context);
                    return results.stream()
                            .map(entry -> entry.getContent())
                            .collect(Collectors.toList());
                });
        } catch (Exception e) {
            log.error("Failed to query relevant experiences", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    public Map<String, Double> getActionSuccessRates() {
        Map<String, Double> rates = new HashMap<>();
        for (Map.Entry<String, Integer> entry : actionSuccessRates.entrySet()) {
            String action = entry.getKey();
            if (action.contains(":success:")) {
                String baseAction = action.replace(":success:", "");
                int successes = entry.getValue();
                int total = successes + actionSuccessRates.getOrDefault(baseAction + ":failure:", 0);
                if (total > 0) {
                    rates.put(baseAction, (double) successes / total);
                }
            }
        }
        return rates;
    }

    public List<String> getBestStrategiesFor(String context) {
        return contextualLearning.getOrDefault(context, new ArrayList<>());
    }

    public String getExperienceStats() {
        return String.format(
            "Session experiences: %d, Success rates tracked: %d, Contextual learnings: %d",
            sessionExperiences.size(),
            actionSuccessRates.size() / 2, // Divide by 2 because we track success and failure separately
            contextualLearning.size()
        );
    }

    private void storeInRAGSystem(GameplayExperience experience) {
        try {
            String context = experience.toContextString();
            Map<String, Object> metadata = experience.toMetadata();

            ragSystem.storeDocument(experience.getId(), context, metadata);

        } catch (Exception e) {
            log.error("Failed to store experience in RAG system", e);
        }
    }

    private String generateExperienceId() {
        return "exp_" + System.currentTimeMillis() + "_" +
               UUID.randomUUID().toString().substring(0, 8);
    }

    private void updateSuccessRates(String action, boolean successful) {
        String key = action + (successful ? ":success:" : ":failure:");
        actionSuccessRates.merge(key, 1, Integer::sum);
    }

    private void addContextualLearning(String context, String strategy) {
        contextualLearning.computeIfAbsent(context, k -> new ArrayList<>()).add(strategy);

        // Keep only the most recent strategies (limit to 10)
        List<String> strategies = contextualLearning.get(context);
        if (strategies.size() > 10) {
            strategies.remove(0);
        }
    }

    private double calculateXpPerHour(int oldLevel, int newLevel, long timeSpent) {
        // Simplified XP calculation - in reality this would use proper XP tables
        int levelDiff = newLevel - oldLevel;
        double estimatedXp = levelDiff * oldLevel * 100; // Rough estimate
        return (estimatedXp * 3600000.0) / timeSpent; // XP per hour
    }

    public void saveSession() {
        log.info("Saving session with {} experiences", sessionExperiences.size());
        // Session experiences are already stored in RAG system
        // This could be extended to save to local files as backup
    }

    public void loadSession() {
        log.info("Loading previous session experiences...");
        // Could load from local backup files if needed
        // RAG system already contains persistent storage
    }

    public static class GameplayExperience {
        private final String id;
        private final LocalDateTime timestamp;
        private final GameStateAnalyzer.GameState gameState;
        private final String action;
        private final String outcome;
        private final boolean successful;

        public GameplayExperience(String id, LocalDateTime timestamp,
                                GameStateAnalyzer.GameState gameState,
                                String action, String outcome, boolean successful) {
            this.id = id;
            this.timestamp = timestamp;
            this.gameState = gameState;
            this.action = action;
            this.outcome = outcome;
            this.successful = successful;
        }

        public String getId() { return id; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public GameStateAnalyzer.GameState getGameState() { return gameState; }
        public String getAction() { return action; }
        public String getOutcome() { return outcome; }
        public boolean isSuccessful() { return successful; }

        public String toContextString() {
            return String.format(
                "At %s, in location %s with %d HP, performed action '%s' which resulted in '%s' (%s). " +
                "Context: Player level %d, inventory items: %s, nearby NPCs: %s",
                timestamp.format(TIMESTAMP_FORMAT),
                gameState.getLocation(),
                gameState.getCurrentHp(),
                action,
                outcome,
                successful ? "success" : "failure",
                gameState.getCombatLevel(),
                String.join(", ", gameState.getInventoryItems().keySet()),
                String.join(", ", gameState.getNearbyNpcs())
            );
        }

        public Map<String, Object> toMetadata() {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "gameplay_experience");
            metadata.put("timestamp", timestamp.format(TIMESTAMP_FORMAT));
            metadata.put("action", action);
            metadata.put("outcome", outcome);
            metadata.put("successful", successful);
            metadata.put("location", gameState.getLocation());
            metadata.put("hp", gameState.getCurrentHp());
            metadata.put("combat_level", gameState.getCombatLevel());
            metadata.put("inventory_count", gameState.getInventoryItems().size());
            metadata.put("nearby_npcs_count", gameState.getNearbyNpcs().size());
            return metadata;
        }
    }
}