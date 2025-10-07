package net.runelite.client.plugins.microbot.aiautonomous.capabilities;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.aiautonomous.ai.KnowledgeManager;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class ExplorationManager {

    private final Client client;
    private final KnowledgeManager knowledgeManager;
    private final AiAutonomousConfig config;

    // Exploration state
    private final Set<String> visitedLocations;
    private final Map<String, LocationInfo> locationDatabase;
    private final Queue<String> explorationQueue;
    private String currentExplorationTarget;

    // Path finding and navigation
    private final Map<String, List<String>> pathCache;
    private final Set<String> safeRoutes;
    private final Set<String> dangerousAreas;

    public ExplorationManager(Client client, KnowledgeManager knowledgeManager, AiAutonomousConfig config) {
        this.client = client;
        this.knowledgeManager = knowledgeManager;
        this.config = config;
        this.visitedLocations = new HashSet<>();
        this.locationDatabase = new HashMap<>();
        this.explorationQueue = new LinkedList<>();
        this.pathCache = new HashMap<>();
        this.safeRoutes = new HashSet<>();
        this.dangerousAreas = new HashSet<>();

        initializeKnownLocations();
        log.info("Exploration manager initialized with {} known locations", locationDatabase.size());
    }

    public CompletableFuture<ExplorationDecision> planExploration(GameStateAnalyzer.GameState currentState) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Planning exploration from location: {}", currentState.getLocation());

                // Record current location
                recordLocationVisit(currentState.getLocation(), currentState);

                // Check if we have an active exploration target
                if (currentExplorationTarget != null && !hasReachedTarget(currentState)) {
                    return continueCurrentExploration(currentState);
                }

                // Find new exploration opportunities
                List<String> unexploredAreas = findUnexploredAreas(currentState);
                if (unexploredAreas.isEmpty()) {
                    return new ExplorationDecision(ExplorationAction.NO_EXPLORATION, "All nearby areas explored", 0);
                }

                // Select best exploration target
                String bestTarget = selectExplorationTarget(unexploredAreas, currentState);
                currentExplorationTarget = bestTarget;

                return new ExplorationDecision(ExplorationAction.EXPLORE_AREA,
                        "Exploring: " + bestTarget, 70);

            } catch (Exception e) {
                log.error("Error planning exploration", e);
                return new ExplorationDecision(ExplorationAction.STAY_PUT, "Error in exploration planning", 0);
            }
        });
    }

    public CompletableFuture<NavigationDecision> planNavigation(String destination, GameStateAnalyzer.GameState currentState) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Planning navigation from {} to {}", currentState.getLocation(), destination);

                // Check if already at destination
                if (isAtLocation(destination, currentState)) {
                    return new NavigationDecision(NavigationAction.ARRIVED, "Already at destination", 100);
                }

                // Find path to destination
                List<String> path = findPath(currentState.getLocation(), destination);
                if (path.isEmpty()) {
                    return new NavigationDecision(NavigationAction.PATH_NOT_FOUND, "No path found", 0);
                }

                // Check path safety
                PathSafetyLevel safetyLevel = assessPathSafety(path, currentState);
                String nextStep = path.get(0);

                return new NavigationDecision(NavigationAction.MOVE_TO_NEXT_STEP,
                        String.format("Next: %s (Safety: %s)", nextStep, safetyLevel), 80);

            } catch (Exception e) {
                log.error("Error planning navigation", e);
                return new NavigationDecision(NavigationAction.STAY_PUT, "Navigation error", 0);
            }
        });
    }

    private void recordLocationVisit(String location, GameStateAnalyzer.GameState currentState) {
        if (location == null || location.trim().isEmpty()) return;

        visitedLocations.add(location);

        // Update or create location info
        LocationInfo info = locationDatabase.computeIfAbsent(location, k -> new LocationInfo(location));
        info.recordVisit(currentState);

        // Store exploration experience
        if (knowledgeManager != null) {
            storeExplorationExperience(location, currentState);
        }

        log.debug("Recorded visit to location: {}", location);
    }

    private List<String> findUnexploredAreas(GameStateAnalyzer.GameState currentState) {
        List<String> unexplored = new ArrayList<>();

        // Get potentially reachable locations from current position
        String currentLocation = currentState.getLocation();
        LocationInfo currentInfo = locationDatabase.get(currentLocation);

        if (currentInfo != null) {
            for (String connectedArea : currentInfo.getConnectedAreas()) {
                if (!visitedLocations.contains(connectedArea)) {
                    unexplored.add(connectedArea);
                }
            }
        }

        // Add known but unvisited locations
        for (String knownLocation : getKnownLocations()) {
            if (!visitedLocations.contains(knownLocation) && !unexplored.contains(knownLocation)) {
                // Check if it's reasonably accessible
                if (isAccessibleFrom(knownLocation, currentLocation, currentState)) {
                    unexplored.add(knownLocation);
                }
            }
        }

        return unexplored;
    }

    private String selectExplorationTarget(List<String> candidates, GameStateAnalyzer.GameState currentState) {
        String bestTarget = null;
        double bestScore = 0;

        for (String candidate : candidates) {
            double score = calculateExplorationScore(candidate, currentState);
            if (score > bestScore) {
                bestScore = score;
                bestTarget = candidate;
            }
        }

        return bestTarget != null ? bestTarget : candidates.get(0); // Fallback to first candidate
    }

    private double calculateExplorationScore(String location, GameStateAnalyzer.GameState currentState) {
        double score = 0.5; // Base score

        // Factor in location type priority
        LocationType type = getLocationTypeFromName(location);
        switch (type) {
            case CITY:
                score += 0.3; // Cities are high priority
                break;
            case DUNGEON:
                score += 0.2; // Dungeons are interesting
                break;
            case RESOURCE_AREA:
                score += 0.15; // Resource areas are useful
                break;
            case WILDERNESS:
                score -= 0.2; // Wilderness is dangerous
                break;
            case QUEST_AREA:
                score += 0.25; // Quest areas are valuable
                break;
        }

        // Factor in safety
        if (dangerousAreas.contains(location)) {
            score -= 0.3;
        }

        // Factor in distance (closer is better)
        int estimatedDistance = estimateDistance(currentState.getLocation(), location);
        if (estimatedDistance < 5) {
            score += 0.1; // Close areas
        } else if (estimatedDistance > 20) {
            score -= 0.2; // Far areas
        }

        // Factor in player level
        int combatLevel = currentState.getCombatLevel();
        if (isHighLevelArea(location) && combatLevel < 50) {
            score -= 0.4; // Too dangerous for low level
        }

        return Math.max(0, Math.min(1, score));
    }

    private List<String> findPath(String from, String to) {
        // Check cache first
        String pathKey = from + "->" + to;
        if (pathCache.containsKey(pathKey)) {
            return pathCache.get(pathKey);
        }

        // Simple pathfinding - in practice would use A* or similar
        List<String> path = new ArrayList<>();

        // Direct connection check
        LocationInfo fromInfo = locationDatabase.get(from);
        if (fromInfo != null && fromInfo.getConnectedAreas().contains(to)) {
            path.add(to);
        } else {
            // Multi-step path (simplified)
            path = findMultiStepPath(from, to);
        }

        // Cache the result
        pathCache.put(pathKey, path);
        return path;
    }

    private List<String> findMultiStepPath(String from, String to) {
        // Simplified breadth-first search
        Queue<List<String>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.offer(Arrays.asList(from));
        visited.add(from);

        while (!queue.isEmpty() && queue.size() < 100) { // Limit search
            List<String> currentPath = queue.poll();
            String currentLocation = currentPath.get(currentPath.size() - 1);

            LocationInfo currentInfo = locationDatabase.get(currentLocation);
            if (currentInfo == null) continue;

            for (String neighbor : currentInfo.getConnectedAreas()) {
                if (neighbor.equals(to)) {
                    // Found destination
                    List<String> completePath = new ArrayList<>(currentPath);
                    completePath.add(to);
                    return completePath.subList(1, completePath.size()); // Exclude starting location
                }

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<String> newPath = new ArrayList<>(currentPath);
                    newPath.add(neighbor);
                    queue.offer(newPath);
                }
            }
        }

        return new ArrayList<>(); // No path found
    }

    private PathSafetyLevel assessPathSafety(List<String> path, GameStateAnalyzer.GameState currentState) {
        if (path.stream().anyMatch(dangerousAreas::contains)) {
            return PathSafetyLevel.DANGEROUS;
        }

        if (path.stream().allMatch(safeRoutes::contains)) {
            return PathSafetyLevel.SAFE;
        }

        // Check player level vs area requirements
        int combatLevel = currentState.getCombatLevel();
        boolean hasHighLevelAreas = path.stream().anyMatch(this::isHighLevelArea);

        if (hasHighLevelAreas && combatLevel < 40) {
            return PathSafetyLevel.RISKY;
        }

        return PathSafetyLevel.MODERATE;
    }

    private void storeExplorationExperience(String location, GameStateAnalyzer.GameState currentState) {
        try {
            // Store as resource gathering experience (exploration = "discovery")
            knowledgeManager.storeResourceGatheringExperience(
                    "location_discovery",
                    "exploration",
                    1, // One location discovered
                    1000 // Assume 1 second per discovery
            );

            // Store location-specific knowledge
            Map<String, String> metadata = new HashMap<>();
            metadata.put("location", location);
            metadata.put("safety_level", getSafetyLevel(location).toString());
            metadata.put("location_type", getLocationTypeFromName(location).toString());

            String content = String.format("Explored location: %s. Type: %s, Safety: %s",
                    location, getLocationTypeFromName(location), getSafetyLevel(location));

            knowledgeManager.addExternalKnowledge("exploration", content, metadata);

        } catch (Exception e) {
            log.warn("Failed to store exploration experience", e);
        }
    }

    private ExplorationDecision continueCurrentExploration(GameStateAnalyzer.GameState currentState) {
        return new ExplorationDecision(ExplorationAction.CONTINUE_EXPLORATION,
                "Continuing to " + currentExplorationTarget, 60);
    }

    private boolean hasReachedTarget(GameStateAnalyzer.GameState currentState) {
        return currentExplorationTarget != null &&
               isAtLocation(currentExplorationTarget, currentState);
    }

    private boolean isAtLocation(String targetLocation, GameStateAnalyzer.GameState currentState) {
        String currentLocation = currentState.getLocation();
        return currentLocation != null &&
               currentLocation.toLowerCase().contains(targetLocation.toLowerCase());
    }

    private boolean isAccessibleFrom(String destination, String origin, GameStateAnalyzer.GameState currentState) {
        // Check if destination is accessible given current player state
        int combatLevel = currentState.getCombatLevel();

        // High level areas require sufficient combat level
        if (isHighLevelArea(destination) && combatLevel < 30) {
            return false;
        }

        // Members areas require membership (assume all accessible for now)
        return true;
    }

    private void initializeKnownLocations() {
        // Major cities
        addLocation("Lumbridge", LocationType.CITY, PathSafetyLevel.SAFE)
                .addConnection("Al Kharid")
                .addConnection("Draynor Village")
                .addConnection("Varrock");

        addLocation("Varrock", LocationType.CITY, PathSafetyLevel.SAFE)
                .addConnection("Lumbridge")
                .addConnection("Edgeville")
                .addConnection("Grand Exchange");

        addLocation("Falador", LocationType.CITY, PathSafetyLevel.SAFE)
                .addConnection("Barbarian Village")
                .addConnection("Rimmington");

        addLocation("Al Kharid", LocationType.CITY, PathSafetyLevel.SAFE)
                .addConnection("Lumbridge");

        // Resource areas
        addLocation("Lumbridge Swamp", LocationType.RESOURCE_AREA, PathSafetyLevel.MODERATE)
                .addConnection("Lumbridge");

        addLocation("Varrock Mine", LocationType.RESOURCE_AREA, PathSafetyLevel.SAFE)
                .addConnection("Varrock");

        // Dungeons and dangerous areas
        addLocation("Wilderness", LocationType.WILDERNESS, PathSafetyLevel.DANGEROUS)
                .addConnection("Edgeville");

        addLocation("Karamja", LocationType.QUEST_AREA, PathSafetyLevel.MODERATE)
                .addConnection("Port Sarim");

        // Mark known safe routes
        safeRoutes.add("Lumbridge");
        safeRoutes.add("Varrock");
        safeRoutes.add("Falador");

        // Mark known dangerous areas
        dangerousAreas.add("Wilderness");
        dangerousAreas.add("Karamja Volcano");
    }

    private LocationInfo addLocation(String name, LocationType type, PathSafetyLevel safety) {
        LocationInfo info = new LocationInfo(name);
        info.setType(type);
        info.setSafetyLevel(safety);
        locationDatabase.put(name, info);
        return info;
    }

    // Helper methods
    private LocationType getLocationTypeFromName(String location) {
        String lower = location.toLowerCase();
        if (lower.contains("city") || lower.contains("varrock") || lower.contains("lumbridge") ||
            lower.contains("falador") || lower.contains("al kharid")) {
            return LocationType.CITY;
        } else if (lower.contains("wilderness") || lower.contains("wild")) {
            return LocationType.WILDERNESS;
        } else if (lower.contains("mine") || lower.contains("forest") || lower.contains("swamp")) {
            return LocationType.RESOURCE_AREA;
        } else if (lower.contains("dungeon") || lower.contains("cave")) {
            return LocationType.DUNGEON;
        } else if (lower.contains("quest") || lower.contains("karamja")) {
            return LocationType.QUEST_AREA;
        }
        return LocationType.UNKNOWN;
    }

    private PathSafetyLevel getSafetyLevel(String location) {
        if (dangerousAreas.contains(location)) return PathSafetyLevel.DANGEROUS;
        if (safeRoutes.contains(location)) return PathSafetyLevel.SAFE;
        if (isHighLevelArea(location)) return PathSafetyLevel.RISKY;
        return PathSafetyLevel.MODERATE;
    }

    private boolean isHighLevelArea(String location) {
        String lower = location.toLowerCase();
        return lower.contains("wilderness") || lower.contains("dragon") ||
               lower.contains("demon") || lower.contains("boss");
    }

    private int estimateDistance(String from, String to) {
        // Simple distance estimation - in practice would use actual coordinates
        if (from.equals(to)) return 0;

        LocationInfo fromInfo = locationDatabase.get(from);
        if (fromInfo != null && fromInfo.getConnectedAreas().contains(to)) {
            return 1; // Direct connection
        }

        // Estimate based on path length
        List<String> path = findPath(from, to);
        return path.size();
    }

    private Set<String> getKnownLocations() {
        return locationDatabase.keySet();
    }

    public Set<String> getVisitedLocations() {
        return new HashSet<>(visitedLocations);
    }

    public Map<String, LocationInfo> getLocationDatabase() {
        return new HashMap<>(locationDatabase);
    }

    public String getStatus() {
        return String.format("Visited: %d locations, Known: %d locations, Current target: %s",
                visitedLocations.size(), locationDatabase.size(),
                currentExplorationTarget != null ? currentExplorationTarget : "none");
    }

    // Helper classes and enums
    public static class ExplorationDecision {
        private final ExplorationAction action;
        private final String reasoning;
        private final int confidence;

        public ExplorationDecision(ExplorationAction action, String reasoning, int confidence) {
            this.action = action;
            this.reasoning = reasoning;
            this.confidence = confidence;
        }

        public ExplorationAction getAction() { return action; }
        public String getReasoning() { return reasoning; }
        public int getConfidence() { return confidence; }
    }

    public static class NavigationDecision {
        private final NavigationAction action;
        private final String reasoning;
        private final int confidence;

        public NavigationDecision(NavigationAction action, String reasoning, int confidence) {
            this.action = action;
            this.reasoning = reasoning;
            this.confidence = confidence;
        }

        public NavigationAction getAction() { return action; }
        public String getReasoning() { return reasoning; }
        public int getConfidence() { return confidence; }
    }

    public enum ExplorationAction {
        EXPLORE_AREA, CONTINUE_EXPLORATION, NO_EXPLORATION, STAY_PUT
    }

    public enum NavigationAction {
        MOVE_TO_NEXT_STEP, ARRIVED, PATH_NOT_FOUND, STAY_PUT
    }

    public enum LocationType {
        CITY, WILDERNESS, RESOURCE_AREA, DUNGEON, QUEST_AREA, UNKNOWN
    }

    public enum PathSafetyLevel {
        SAFE, MODERATE, RISKY, DANGEROUS
    }

    public static class LocationInfo {
        private final String name;
        private LocationType type;
        private PathSafetyLevel safetyLevel;
        private final Set<String> connectedAreas;
        private int visitCount;
        private LocalDateTime lastVisit;
        private final Map<String, Object> metadata;

        public LocationInfo(String name) {
            this.name = name;
            this.connectedAreas = new HashSet<>();
            this.visitCount = 0;
            this.metadata = new HashMap<>();
            this.type = LocationType.UNKNOWN;
            this.safetyLevel = PathSafetyLevel.MODERATE;
        }

        public void recordVisit(GameStateAnalyzer.GameState state) {
            visitCount++;
            lastVisit = LocalDateTime.now();
        }

        public LocationInfo addConnection(String location) {
            connectedAreas.add(location);
            return this;
        }

        // Getters and setters
        public String getName() { return name; }
        public LocationType getType() { return type; }
        public void setType(LocationType type) { this.type = type; }
        public PathSafetyLevel getSafetyLevel() { return safetyLevel; }
        public void setSafetyLevel(PathSafetyLevel safetyLevel) { this.safetyLevel = safetyLevel; }
        public Set<String> getConnectedAreas() { return new HashSet<>(connectedAreas); }
        public int getVisitCount() { return visitCount; }
        public LocalDateTime getLastVisit() { return lastVisit; }
    }
}