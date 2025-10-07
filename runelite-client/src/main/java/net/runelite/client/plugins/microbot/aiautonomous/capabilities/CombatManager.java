package net.runelite.client.plugins.microbot.aiautonomous.capabilities;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.aiautonomous.ai.KnowledgeManager;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class CombatManager {

    private final Client client;
    private final KnowledgeManager knowledgeManager;
    private final AiAutonomousConfig config;

    // Combat state tracking
    private String currentTarget;
    private long combatStartTime;
    private Map<String, CombatStats> opponentStats;
    private List<String> combatHistory;

    // Combat strategies
    private final Map<String, CombatStrategy> strategies;

    public CombatManager(Client client, KnowledgeManager knowledgeManager, AiAutonomousConfig config) {
        this.client = client;
        this.knowledgeManager = knowledgeManager;
        this.config = config;
        this.opponentStats = new HashMap<>();
        this.combatHistory = new ArrayList<>();
        this.strategies = new HashMap<>();

        initializeCombatStrategies();
        log.info("Combat manager initialized with {} strategies", strategies.size());
    }

    public CompletableFuture<CombatDecision> analyzeCombatSituation(GameStateAnalyzer.GameState currentState) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Analyzing combat situation");

                // Check if already in combat
                if (currentState.isInCombat()) {
                    return handleActiveCombat(currentState);
                }

                // Analyze potential combat opportunities
                List<String> nearbyNpcs = currentState.getNearbyNpcs();
                if (nearbyNpcs.isEmpty()) {
                    return new CombatDecision(CombatAction.NO_ACTION, "No NPCs nearby", 0);
                }

                // Evaluate each potential target
                return evaluateCombatTargets(nearbyNpcs, currentState);

            } catch (Exception e) {
                log.error("Error analyzing combat situation", e);
                return new CombatDecision(CombatAction.RETREAT, "Error in combat analysis", 0);
            }
        });
    }

    private CombatDecision handleActiveCombat(GameStateAnalyzer.GameState currentState) {
        // Assess current combat situation
        double healthPercentage = (double) currentState.getCurrentHp() / currentState.getMaxHp();

        // Emergency retreat if health is too low
        if (healthPercentage < 0.2) {
            log.warn("Health critically low, recommending retreat");
            return new CombatDecision(CombatAction.RETREAT, "Health below 20%", 90);
        }

        // Check if we should eat food
        if (healthPercentage < 0.5 && hasFood(currentState)) {
            return new CombatDecision(CombatAction.EAT_FOOD, "Health below 50%", 80);
        }

        // Check if we should use potions
        if (healthPercentage < 0.7 && hasPotion(currentState)) {
            return new CombatDecision(CombatAction.USE_POTION, "Health below 70%", 70);
        }

        // Continue fighting with optimal strategy
        String strategy = selectOptimalStrategy(currentTarget, currentState);
        return new CombatDecision(CombatAction.CONTINUE_COMBAT, "Continue with " + strategy, 60);
    }

    private CombatDecision evaluateCombatTargets(List<String> nearbyNpcs, GameStateAnalyzer.GameState currentState) {
        String bestTarget = null;
        double bestScore = 0;
        String reasoning = "";

        for (String npc : nearbyNpcs) {
            double targetScore = evaluateTargetSuitability(npc, currentState);
            if (targetScore > bestScore) {
                bestScore = targetScore;
                bestTarget = npc;
                reasoning = String.format("Best target: %s (score: %.2f)", npc, targetScore);
            }
        }

        if (bestTarget != null && bestScore > 0.5) {
            // Check if we're ready for combat
            if (isReadyForCombat(currentState)) {
                return new CombatDecision(CombatAction.ENGAGE_TARGET, reasoning, (int) (bestScore * 100));
            } else {
                return new CombatDecision(CombatAction.PREPARE_FOR_COMBAT, "Need to prepare first", 70);
            }
        }

        return new CombatDecision(CombatAction.NO_ACTION, "No suitable targets", 0);
    }

    private double evaluateTargetSuitability(String npc, GameStateAnalyzer.GameState currentState) {
        double score = 0.5; // Base score

        // Get historical data about this NPC type
        CombatStats stats = opponentStats.get(npc);
        if (stats != null) {
            // Factor in historical success rate
            score += stats.getSuccessRate() * 0.3;

            // Factor in average damage taken
            if (stats.getAverageDamageTaken() < currentState.getCurrentHp() * 0.5) {
                score += 0.2; // Safe target
            } else {
                score -= 0.3; // Dangerous target
            }
        }

        // Factor in current health
        double healthPercentage = (double) currentState.getCurrentHp() / currentState.getMaxHp();
        if (healthPercentage > 0.8) {
            score += 0.2; // Healthy, can take risks
        } else if (healthPercentage < 0.5) {
            score -= 0.4; // Low health, avoid combat
        }

        // Factor in combat level relative to NPC (if known)
        int combatLevel = currentState.getCombatLevel();
        if (isWeakNpc(npc) && combatLevel > 20) {
            score += 0.2; // Easy target
        } else if (isStrongNpc(npc) && combatLevel < 50) {
            score -= 0.5; // Too dangerous
        }

        // Factor in inventory (food, potions)
        if (hasFood(currentState)) score += 0.1;
        if (hasPotion(currentState)) score += 0.1;
        if (!hasWeapon(currentState)) score -= 0.8; // Critical: no weapon

        return Math.max(0, Math.min(1, score));
    }

    private boolean isReadyForCombat(GameStateAnalyzer.GameState currentState) {
        // Must have weapon
        if (!hasWeapon(currentState)) {
            log.debug("Not ready for combat: no weapon");
            return false;
        }

        // Must have reasonable health
        double healthPercentage = (double) currentState.getCurrentHp() / currentState.getMaxHp();
        if (healthPercentage < 0.6) {
            log.debug("Not ready for combat: health too low ({}%)", healthPercentage * 100);
            return false;
        }

        // Should have some food for emergencies
        if (!hasFood(currentState)) {
            log.debug("Not ready for combat: no food");
            return false;
        }

        return true;
    }

    private String selectOptimalStrategy(String target, GameStateAnalyzer.GameState currentState) {
        // Get best strategy from knowledge manager
        if (knowledgeManager != null) {
            List<String> strategies = knowledgeManager.getBestStrategiesFor("combat:" + target);
            if (!strategies.isEmpty()) {
                return strategies.get(0); // Return best strategy
            }
        }

        // Fallback to default strategies
        if (isWeakNpc(target)) {
            return "aggressive"; // Quick kills
        } else if (isStrongNpc(target)) {
            return "defensive"; // Careful approach
        } else {
            return "balanced"; // Standard approach
        }
    }

    public void recordCombatOutcome(String target, String strategy, boolean victory,
                                  int damageTaken, long duration) {
        try {
            // Update opponent stats
            CombatStats stats = opponentStats.computeIfAbsent(target, k -> new CombatStats());
            stats.addCombatResult(victory, damageTaken, duration);

            // Record in knowledge manager
            if (knowledgeManager != null) {
                knowledgeManager.storeCombatExperience(target, strategy, victory, damageTaken, duration);
            }

            // Add to combat history
            String historyEntry = String.format("%s vs %s: %s (%s strategy)",
                    new Date(), target, victory ? "victory" : "defeat", strategy);
            combatHistory.add(historyEntry);

            // Keep history limited
            if (combatHistory.size() > 100) {
                combatHistory.remove(0);
            }

            log.info("Recorded combat outcome: {} vs {} - {} using {} strategy",
                    victory ? "Victory" : "Defeat", target, strategy);

        } catch (Exception e) {
            log.error("Error recording combat outcome", e);
        }
    }

    private boolean hasWeapon(GameStateAnalyzer.GameState currentState) {
        return currentState.getInventoryItems().keySet().stream()
                .anyMatch(item -> item.toLowerCase().contains("sword") ||
                                item.toLowerCase().contains("bow") ||
                                item.toLowerCase().contains("staff") ||
                                item.toLowerCase().contains("axe") ||
                                item.toLowerCase().contains("dagger"));
    }

    private boolean hasFood(GameStateAnalyzer.GameState currentState) {
        return currentState.getInventoryItems().keySet().stream()
                .anyMatch(item -> item.toLowerCase().contains("food") ||
                                item.toLowerCase().contains("fish") ||
                                item.toLowerCase().contains("bread") ||
                                item.toLowerCase().contains("cake") ||
                                item.toLowerCase().contains("lobster"));
    }

    private boolean hasPotion(GameStateAnalyzer.GameState currentState) {
        return currentState.getInventoryItems().keySet().stream()
                .anyMatch(item -> item.toLowerCase().contains("potion") ||
                                item.toLowerCase().contains("brew"));
    }

    private boolean isWeakNpc(String npc) {
        String lowerNpc = npc.toLowerCase();
        return lowerNpc.contains("rat") || lowerNpc.contains("chicken") ||
               lowerNpc.contains("cow") || lowerNpc.contains("goblin") ||
               lowerNpc.contains("spider") || lowerNpc.contains("frog");
    }

    private boolean isStrongNpc(String npc) {
        String lowerNpc = npc.toLowerCase();
        return lowerNpc.contains("dragon") || lowerNpc.contains("demon") ||
               lowerNpc.contains("boss") || lowerNpc.contains("king") ||
               lowerNpc.contains("giant") || lowerNpc.contains("troll");
    }

    private void initializeCombatStrategies() {
        strategies.put("aggressive", new CombatStrategy("aggressive", "High damage, quick fights"));
        strategies.put("defensive", new CombatStrategy("defensive", "Prioritize safety and healing"));
        strategies.put("balanced", new CombatStrategy("balanced", "Balanced approach"));
        strategies.put("ranged", new CombatStrategy("ranged", "Keep distance, use ranged attacks"));
        strategies.put("magic", new CombatStrategy("magic", "Use magic attacks"));
    }

    public Map<String, CombatStats> getOpponentStats() {
        return new HashMap<>(opponentStats);
    }

    public List<String> getCombatHistory() {
        return new ArrayList<>(combatHistory);
    }

    public String getStatus() {
        return String.format("Opponents tracked: %d, Combat history: %d entries",
                opponentStats.size(), combatHistory.size());
    }

    // Helper classes
    public static class CombatDecision {
        private final CombatAction action;
        private final String reasoning;
        private final int confidence;

        public CombatDecision(CombatAction action, String reasoning, int confidence) {
            this.action = action;
            this.reasoning = reasoning;
            this.confidence = confidence;
        }

        public CombatAction getAction() { return action; }
        public String getReasoning() { return reasoning; }
        public int getConfidence() { return confidence; }
    }

    public enum CombatAction {
        ENGAGE_TARGET, CONTINUE_COMBAT, RETREAT, EAT_FOOD,
        USE_POTION, PREPARE_FOR_COMBAT, NO_ACTION
    }

    private static class CombatStats {
        private int totalFights;
        private int victories;
        private long totalDamageTaken;
        private long totalDuration;

        public void addCombatResult(boolean victory, int damage, long duration) {
            totalFights++;
            if (victory) victories++;
            totalDamageTaken += damage;
            totalDuration += duration;
        }

        public double getSuccessRate() {
            return totalFights > 0 ? (double) victories / totalFights : 0.0;
        }

        public double getAverageDamageTaken() {
            return totalFights > 0 ? (double) totalDamageTaken / totalFights : 0.0;
        }

        public double getAverageDuration() {
            return totalFights > 0 ? (double) totalDuration / totalFights : 0.0;
        }
    }

    private static class CombatStrategy {
        private final String name;
        private final String description;

        public CombatStrategy(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}