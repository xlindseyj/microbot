package net.runelite.client.plugins.microbot.aiautonomous.strategy;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;
import net.runelite.client.plugins.microbot.aiautonomous.ai.KnowledgeManager;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RealTimeStrategyAdjuster {

    private final AiAutonomousConfig config;
    private final KnowledgeManager knowledgeManager;

    // Strategy monitoring
    private final Map<String, StrategyMetrics> strategyPerformance;
    private final Map<String, Integer> playerDensity;
    private final Queue<ServerEvent> recentEvents;
    private final Map<String, Double> skillEfficiencyRates;

    // Real-time adjustments
    private String currentStrategy;
    private LocalDateTime lastAdjustment;
    private final Map<String, Object> dynamicParameters;

    // Event detection
    private int lastPlayerCount;
    private double lastXpRate;
    private LocalDateTime sessionStart;

    private static final int MAX_EVENTS = 100;
    private static final int MIN_ADJUSTMENT_INTERVAL_MINUTES = 5;

    public RealTimeStrategyAdjuster(AiAutonomousConfig config, KnowledgeManager knowledgeManager) {
        this.config = config;
        this.knowledgeManager = knowledgeManager;
        this.strategyPerformance = new ConcurrentHashMap<>();
        this.playerDensity = new ConcurrentHashMap<>();
        this.recentEvents = new LinkedList<>();
        this.skillEfficiencyRates = new ConcurrentHashMap<>();
        this.dynamicParameters = new ConcurrentHashMap<>();
        this.currentStrategy = "balanced";
        this.lastAdjustment = LocalDateTime.now();
        this.sessionStart = LocalDateTime.now();

        initializeStrategies();
        log.info("Real-time strategy adjuster initialized");
    }

    public StrategyAdjustment analyzeAndAdjust(GameStateAnalyzer.GameState currentState) {
        try {
            // Collect current metrics
            collectMetrics(currentState);

            // Detect server events
            detectServerEvents(currentState);

            // Check if adjustment is needed
            if (shouldAdjustStrategy()) {
                return performStrategyAdjustment(currentState);
            }

            return new StrategyAdjustment(
                AdjustmentType.NO_CHANGE,
                currentStrategy,
                "No adjustment needed",
                getCurrentParameters()
            );

        } catch (Exception e) {
            log.error("Error in real-time strategy adjustment", e);
            return new StrategyAdjustment(
                AdjustmentType.ERROR,
                currentStrategy,
                "Error in strategy analysis",
                getCurrentParameters()
            );
        }
    }

    private void collectMetrics(GameStateAnalyzer.GameState currentState) {
        // Update player density
        String location = currentState.getLocation();
        int nearbyPlayers = currentState.getNearbyPlayers().size();
        playerDensity.put(location, nearbyPlayers);

        // Track XP efficiency
        updateSkillEfficiencyRates(currentState);

        // Update strategy performance
        updateStrategyMetrics(currentState);
    }

    private void detectServerEvents(GameStateAnalyzer.GameState currentState) {
        // Detect player count changes
        int currentPlayerCount = currentState.getNearbyPlayers().size();
        if (Math.abs(currentPlayerCount - lastPlayerCount) > 5) {
            addEvent(new ServerEvent(
                ServerEventType.PLAYER_DENSITY_CHANGE,
                String.format("Player count changed from %d to %d", lastPlayerCount, currentPlayerCount),
                LocalDateTime.now()
            ));
        }
        lastPlayerCount = currentPlayerCount;

        // Detect competition for resources
        if (hasHighCompetition(currentState)) {
            addEvent(new ServerEvent(
                ServerEventType.HIGH_COMPETITION,
                "High competition detected at " + currentState.getLocation(),
                LocalDateTime.now()
            ));
        }

        // Detect XP rate changes
        double currentXpRate = calculateCurrentXpRate(currentState);
        if (Math.abs(currentXpRate - lastXpRate) > lastXpRate * 0.3) { // 30% change
            addEvent(new ServerEvent(
                ServerEventType.XP_RATE_CHANGE,
                String.format("XP rate changed from %.1f to %.1f", lastXpRate, currentXpRate),
                LocalDateTime.now()
            ));
        }
        lastXpRate = currentXpRate;

        // Detect time-based events
        detectTimeBasedEvents();
    }

    private boolean shouldAdjustStrategy() {
        // Don't adjust too frequently
        if (ChronoUnit.MINUTES.between(lastAdjustment, LocalDateTime.now()) < MIN_ADJUSTMENT_INTERVAL_MINUTES) {
            return false;
        }

        // Check for triggering events
        return hasRecentHighImpactEvents() ||
               hasPerformanceDegraded() ||
               hasEnvironmentChanged() ||
               shouldOptimizeForTimeOfDay();
    }

    private StrategyAdjustment performStrategyAdjustment(GameStateAnalyzer.GameState currentState) {
        String newStrategy = determineOptimalStrategy(currentState);
        Map<String, Object> newParameters = calculateOptimalParameters(currentState, newStrategy);

        if (!newStrategy.equals(currentStrategy) || parametersChanged(newParameters)) {
            String reasoning = buildAdjustmentReasoning(currentState, newStrategy);

            // Store adjustment in knowledge base
            if (knowledgeManager != null) {
                storeStrategyAdjustment(newStrategy, reasoning);
            }

            currentStrategy = newStrategy;
            dynamicParameters.clear();
            dynamicParameters.putAll(newParameters);
            lastAdjustment = LocalDateTime.now();

            log.info("Strategy adjusted to: {} - {}", newStrategy, reasoning);

            return new StrategyAdjustment(
                AdjustmentType.STRATEGY_CHANGE,
                newStrategy,
                reasoning,
                newParameters
            );
        }

        return new StrategyAdjustment(
            AdjustmentType.NO_CHANGE,
            currentStrategy,
            "Current strategy remains optimal",
            getCurrentParameters()
        );
    }

    private String determineOptimalStrategy(GameStateAnalyzer.GameState currentState) {
        Map<String, Double> strategyScores = new HashMap<>();

        // Score each available strategy
        for (String strategy : getAvailableStrategies()) {
            double score = calculateStrategyScore(strategy, currentState);
            strategyScores.put(strategy, score);
        }

        // Return highest scoring strategy
        return strategyScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("balanced");
    }

    private double calculateStrategyScore(String strategy, GameStateAnalyzer.GameState currentState) {
        double score = 0.5; // Base score

        // Performance history
        StrategyMetrics metrics = strategyPerformance.get(strategy);
        if (metrics != null) {
            score += metrics.getSuccessRate() * 0.4;
            score += Math.min(metrics.getEfficiency() / 100.0, 0.3);
        }

        // Environmental factors
        score += calculateEnvironmentalScore(strategy, currentState);

        // Time-based factors
        score += calculateTimeBasedScore(strategy);

        // Player competition
        score += calculateCompetitionScore(strategy, currentState);

        return Math.max(0, Math.min(1, score));
    }

    private double calculateEnvironmentalScore(String strategy, GameStateAnalyzer.GameState currentState) {
        double score = 0;

        switch (strategy) {
            case "aggressive":
                // Better when fewer players around
                if (currentState.getNearbyPlayers().size() < 3) score += 0.2;
                // Better with high HP
                if (currentState.getCurrentHp() > currentState.getMaxHp() * 0.8) score += 0.1;
                break;

            case "conservative":
                // Better when many players around
                if (currentState.getNearbyPlayers().size() > 10) score += 0.2;
                // Better with low HP
                if (currentState.getCurrentHp() < currentState.getMaxHp() * 0.5) score += 0.1;
                break;

            case "efficient":
                // Better during peak hours
                if (isPeakHours()) score += 0.15;
                // Better with good resources
                if (hasGoodResources(currentState)) score += 0.1;
                break;

            case "social":
                // Better when many players around
                if (currentState.getNearbyPlayers().size() > 5) score += 0.2;
                break;
        }

        return score;
    }

    private Map<String, Object> calculateOptimalParameters(GameStateAnalyzer.GameState currentState, String strategy) {
        Map<String, Object> params = new HashMap<>();

        // Base parameters for strategy
        switch (strategy) {
            case "aggressive":
                params.put("risk_tolerance", 80);
                params.put("efficiency_focus", 90);
                params.put("social_interaction", 20);
                params.put("break_frequency", 0.5);
                break;

            case "conservative":
                params.put("risk_tolerance", 20);
                params.put("efficiency_focus", 60);
                params.put("social_interaction", 40);
                params.put("break_frequency", 1.5);
                break;

            case "efficient":
                params.put("risk_tolerance", 50);
                params.put("efficiency_focus", 95);
                params.put("social_interaction", 10);
                params.put("break_frequency", 0.8);
                break;

            case "social":
                params.put("risk_tolerance", 40);
                params.put("efficiency_focus", 70);
                params.put("social_interaction", 80);
                params.put("break_frequency", 1.2);
                break;

            default: // balanced
                params.put("risk_tolerance", 50);
                params.put("efficiency_focus", 75);
                params.put("social_interaction", 50);
                params.put("break_frequency", 1.0);
                break;
        }

        // Adjust parameters based on current conditions
        adjustParametersForConditions(params, currentState);

        return params;
    }

    private void adjustParametersForConditions(Map<String, Object> params, GameStateAnalyzer.GameState currentState) {
        // Adjust for player density
        int playerCount = currentState.getNearbyPlayers().size();
        if (playerCount > 10) {
            params.put("risk_tolerance", (Integer) params.get("risk_tolerance") - 20);
            params.put("social_interaction", (Integer) params.get("social_interaction") + 10);
        }

        // Adjust for time of day
        if (isPeakHours()) {
            params.put("efficiency_focus", (Integer) params.get("efficiency_focus") + 10);
            params.put("break_frequency", (Double) params.get("break_frequency") * 0.8);
        }

        // Adjust for competition
        if (hasHighCompetition(currentState)) {
            params.put("efficiency_focus", (Integer) params.get("efficiency_focus") - 15);
            params.put("break_frequency", (Double) params.get("break_frequency") * 1.3);
        }

        // Ensure parameters stay within bounds
        ensureParameterBounds(params);
    }

    // Helper methods
    private void initializeStrategies() {
        String[] strategies = {"aggressive", "conservative", "efficient", "social", "balanced"};
        for (String strategy : strategies) {
            strategyPerformance.put(strategy, new StrategyMetrics());
        }
    }

    private void addEvent(ServerEvent event) {
        recentEvents.offer(event);
        if (recentEvents.size() > MAX_EVENTS) {
            recentEvents.poll();
        }
        log.debug("Detected server event: {}", event.getDescription());
    }

    private boolean hasHighCompetition(GameStateAnalyzer.GameState currentState) {
        // Simple heuristic: many players at resource locations
        String location = currentState.getLocation();
        int playerCount = currentState.getNearbyPlayers().size();

        return (location.toLowerCase().contains("mine") ||
                location.toLowerCase().contains("fish") ||
                location.toLowerCase().contains("tree")) && playerCount > 8;
    }

    private double calculateCurrentXpRate(GameStateAnalyzer.GameState currentState) {
        // This would calculate based on recent XP gains
        // For now, return a placeholder
        return 50000.0; // XP per hour
    }

    private void updateSkillEfficiencyRates(GameStateAnalyzer.GameState currentState) {
        // Track efficiency for each skill being trained
        // This would be more complex in practice
        for (Map.Entry<String, Integer> skill : currentState.getAllSkillLevels().entrySet()) {
            skillEfficiencyRates.put(skill.getKey(), calculateSkillEfficiency(skill.getKey()));
        }
    }

    private double calculateSkillEfficiency(String skill) {
        // Placeholder efficiency calculation
        return 75.0 + (Math.random() * 20 - 10); // 65-85% efficiency
    }

    private void updateStrategyMetrics(GameStateAnalyzer.GameState currentState) {
        StrategyMetrics metrics = strategyPerformance.get(currentStrategy);
        if (metrics != null) {
            metrics.updateMetrics(true, calculateCurrentXpRate(currentState));
        }
    }

    private void detectTimeBasedEvents() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();

        // Detect peak hours
        if ((hour >= 18 && hour <= 23) || (hour >= 7 && hour <= 9)) {
            addEvent(new ServerEvent(
                ServerEventType.PEAK_HOURS,
                "Peak hours detected",
                now
            ));
        }

        // Detect late night
        if (hour >= 0 && hour <= 5) {
            addEvent(new ServerEvent(
                ServerEventType.OFF_PEAK,
                "Off-peak hours detected",
                now
            ));
        }
    }

    private boolean hasRecentHighImpactEvents() {
        return recentEvents.stream()
                .filter(event -> ChronoUnit.MINUTES.between(event.getTimestamp(), LocalDateTime.now()) <= 10)
                .anyMatch(event -> event.getType() == ServerEventType.HIGH_COMPETITION ||
                                 event.getType() == ServerEventType.PLAYER_DENSITY_CHANGE);
    }

    private boolean hasPerformanceDegraded() {
        StrategyMetrics current = strategyPerformance.get(currentStrategy);
        return current != null && current.getEfficiency() < 60;
    }

    private boolean hasEnvironmentChanged() {
        return recentEvents.stream()
                .anyMatch(event -> ChronoUnit.MINUTES.between(event.getTimestamp(), LocalDateTime.now()) <= 15 &&
                                 event.getType() == ServerEventType.XP_RATE_CHANGE);
    }

    private boolean shouldOptimizeForTimeOfDay() {
        // Check if strategy should change based on time
        return ChronoUnit.HOURS.between(lastAdjustment, LocalDateTime.now()) >= 2;
    }

    private void storeStrategyAdjustment(String strategy, String reasoning) {
        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("strategy", strategy);
            metadata.put("adjustment_type", "real_time");
            metadata.put("session_duration", String.valueOf(ChronoUnit.MINUTES.between(sessionStart, LocalDateTime.now())));

            knowledgeManager.addExternalKnowledge("strategy_adjustment",
                    "Strategy adjusted to " + strategy + ": " + reasoning, metadata);
        } catch (Exception e) {
            log.warn("Failed to store strategy adjustment", e);
        }
    }

    private List<String> getAvailableStrategies() {
        return Arrays.asList("aggressive", "conservative", "efficient", "social", "balanced");
    }

    private boolean isPeakHours() {
        int hour = LocalDateTime.now().getHour();
        return (hour >= 18 && hour <= 23) || (hour >= 7 && hour <= 9);
    }

    private boolean hasGoodResources(GameStateAnalyzer.GameState currentState) {
        return currentState.getInventoryItems().size() > 20; // Well-stocked inventory
    }

    private double calculateTimeBasedScore(String strategy) {
        if (isPeakHours() && strategy.equals("efficient")) {
            return 0.1;
        }
        return 0.0;
    }

    private double calculateCompetitionScore(String strategy, GameStateAnalyzer.GameState currentState) {
        boolean highCompetition = hasHighCompetition(currentState);
        if (highCompetition && strategy.equals("conservative")) {
            return 0.15;
        } else if (!highCompetition && strategy.equals("aggressive")) {
            return 0.1;
        }
        return 0.0;
    }

    private String buildAdjustmentReasoning(GameStateAnalyzer.GameState currentState, String newStrategy) {
        StringBuilder reasoning = new StringBuilder();

        if (hasHighCompetition(currentState)) {
            reasoning.append("High competition detected. ");
        }

        if (isPeakHours()) {
            reasoning.append("Peak hours - optimizing efficiency. ");
        }

        int playerCount = currentState.getNearbyPlayers().size();
        if (playerCount > 10) {
            reasoning.append("Crowded area - adjusting for social dynamics. ");
        } else if (playerCount < 3) {
            reasoning.append("Low population - can be more aggressive. ");
        }

        reasoning.append("Switching to ").append(newStrategy).append(" strategy.");

        return reasoning.toString();
    }

    private boolean parametersChanged(Map<String, Object> newParameters) {
        return !newParameters.equals(dynamicParameters);
    }

    private Map<String, Object> getCurrentParameters() {
        return new HashMap<>(dynamicParameters);
    }

    private void ensureParameterBounds(Map<String, Object> params) {
        // Ensure risk tolerance stays between 0-100
        Integer risk = (Integer) params.get("risk_tolerance");
        params.put("risk_tolerance", Math.max(0, Math.min(100, risk)));

        // Ensure efficiency focus stays between 0-100
        Integer efficiency = (Integer) params.get("efficiency_focus");
        params.put("efficiency_focus", Math.max(0, Math.min(100, efficiency)));

        // Ensure social interaction stays between 0-100
        Integer social = (Integer) params.get("social_interaction");
        params.put("social_interaction", Math.max(0, Math.min(100, social)));

        // Ensure break frequency stays reasonable
        Double breakFreq = (Double) params.get("break_frequency");
        params.put("break_frequency", Math.max(0.1, Math.min(3.0, breakFreq)));
    }

    // Getters for UI
    public String getCurrentStrategy() { return currentStrategy; }
    public Map<String, StrategyMetrics> getStrategyPerformance() { return new HashMap<>(strategyPerformance); }
    public Queue<ServerEvent> getRecentEvents() { return new LinkedList<>(recentEvents); }
    public Map<String, Double> getSkillEfficiencyRates() { return new HashMap<>(skillEfficiencyRates); }
    public Map<String, Object> getDynamicParameters() { return new HashMap<>(dynamicParameters); }

    // Helper classes
    public static class StrategyAdjustment {
        private final AdjustmentType type;
        private final String strategy;
        private final String reasoning;
        private final Map<String, Object> parameters;

        public StrategyAdjustment(AdjustmentType type, String strategy, String reasoning, Map<String, Object> parameters) {
            this.type = type;
            this.strategy = strategy;
            this.reasoning = reasoning;
            this.parameters = parameters;
        }

        public AdjustmentType getType() { return type; }
        public String getStrategy() { return strategy; }
        public String getReasoning() { return reasoning; }
        public Map<String, Object> getParameters() { return parameters; }
    }

    public enum AdjustmentType {
        NO_CHANGE, STRATEGY_CHANGE, PARAMETER_ADJUSTMENT, ERROR
    }

    public static class ServerEvent {
        private final ServerEventType type;
        private final String description;
        private final LocalDateTime timestamp;

        public ServerEvent(ServerEventType type, String description, LocalDateTime timestamp) {
            this.type = type;
            this.description = description;
            this.timestamp = timestamp;
        }

        public ServerEventType getType() { return type; }
        public String getDescription() { return description; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public enum ServerEventType {
        PLAYER_DENSITY_CHANGE, HIGH_COMPETITION, XP_RATE_CHANGE, PEAK_HOURS, OFF_PEAK, LAG_DETECTED
    }

    public static class StrategyMetrics {
        private int usageCount = 0;
        private int successCount = 0;
        private double totalEfficiency = 0;

        public void updateMetrics(boolean success, double efficiency) {
            usageCount++;
            if (success) successCount++;
            totalEfficiency += efficiency;
        }

        public double getSuccessRate() {
            return usageCount > 0 ? (double) successCount / usageCount : 0.0;
        }

        public double getEfficiency() {
            return usageCount > 0 ? totalEfficiency / usageCount : 0.0;
        }

        public int getUsageCount() { return usageCount; }
    }
}