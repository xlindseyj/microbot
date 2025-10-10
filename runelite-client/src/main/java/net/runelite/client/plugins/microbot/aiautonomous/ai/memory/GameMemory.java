package net.runelite.client.plugins.microbot.aiautonomous.ai.memory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.aiautonomous.ai.AiDecision;
import net.runelite.client.plugins.microbot.aiautonomous.ai.OllamaClient.GameContext;
import net.runelite.client.plugins.microbot.aiautonomous.core.AutonomousGameState;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class GameMemory {

    private final Map<String, List<MemoryEntry>> episodicMemory = new ConcurrentHashMap<>();
    private final Map<String, SemanticKnowledge> semanticMemory = new ConcurrentHashMap<>();
    private final Map<String, SkillMemory> skillMemories = new ConcurrentHashMap<>();
    private final List<GameSession> sessionHistory = new ArrayList<>();

    private final Gson gson = new Gson();
    private GameSession currentSession;

    private static final String MEMORY_FILE = "microbot_ai_memory.json";
    private static final int MAX_EPISODIC_ENTRIES = 1000;
    private static final int MAX_SESSIONS = 50;
    private static final Duration MEMORY_RETENTION = Duration.ofDays(30);

    public GameMemory() {
        startNewSession();
    }

    public void recordDecision(String situation, AiDecision decision, GameContext context) {
        try {
            MemoryEntry entry = new MemoryEntry();
            entry.setType(MemoryType.DECISION);
            entry.setSituation(situation);
            entry.setDecision(decision.getAction());
            entry.setConfidence(decision.getConfidence());
            entry.setOutcome(null); // Will be set later when outcome is known
            entry.setContext(contextToString(context));
            entry.setTimestamp(Instant.now());
            entry.setSessionId(currentSession.getSessionId());

            addEpisodicMemory(situation, entry);

            // Update current session
            currentSession.incrementDecisions();
            currentSession.setLastActivity(Instant.now());

            log.debug("Recorded decision memory: {}", situation);

        } catch (Exception e) {
            log.error("Error recording decision memory", e);
        }
    }

    public void recordOutcome(String situation, AiDecision decision, boolean successful, String outcome) {
        try {
            // Find the corresponding decision memory and update it
            List<MemoryEntry> memories = episodicMemory.get(situation.toLowerCase());
            if (memories != null) {
                MemoryEntry lastDecision = memories.stream()
                        .filter(m -> m.getType() == MemoryType.DECISION)
                        .filter(m -> Objects.equals(m.getDecision(), decision.getAction()))
                        .max(Comparator.comparing(MemoryEntry::getTimestamp))
                        .orElse(null);

                if (lastDecision != null) {
                    lastDecision.setOutcome(outcome);
                    lastDecision.setSuccessful(successful);
                }
            }

            // Create a new outcome memory entry
            MemoryEntry outcomeEntry = new MemoryEntry();
            outcomeEntry.setType(MemoryType.OUTCOME);
            outcomeEntry.setSituation(situation);
            outcomeEntry.setDecision(decision.getAction());
            outcomeEntry.setOutcome(outcome);
            outcomeEntry.setSuccessful(successful);
            outcomeEntry.setTimestamp(Instant.now());
            outcomeEntry.setSessionId(currentSession.getSessionId());

            addEpisodicMemory(situation, outcomeEntry);

            // Update semantic knowledge
            updateSemanticKnowledge(situation, decision, successful, outcome);

            // Update skill memories
            updateSkillMemory(decision.getCategory().toString(), successful);

            // Update session stats
            if (successful) {
                currentSession.incrementSuccesses();
            } else {
                currentSession.incrementFailures();
            }

            log.debug("Recorded outcome memory: {} -> {}", situation, successful);

        } catch (Exception e) {
            log.error("Error recording outcome memory", e);
        }
    }

    public void recordStateTransition(AutonomousGameState fromState, AutonomousGameState toState) {
        try {
            MemoryEntry entry = new MemoryEntry();
            entry.setType(MemoryType.STATE_TRANSITION);
            entry.setSituation("State transition");
            entry.setDecision(fromState + " -> " + toState);
            entry.setTimestamp(Instant.now());
            entry.setSessionId(currentSession.getSessionId());

            addEpisodicMemory("state_transitions", entry);

            log.debug("Recorded state transition: {} -> {}", fromState, toState);

        } catch (Exception e) {
            log.error("Error recording state transition", e);
        }
    }

    public void recordStateChange(GameState gameState) {
        try {
            MemoryEntry entry = new MemoryEntry();
            entry.setType(MemoryType.GAME_EVENT);
            entry.setSituation("Game state change");
            entry.setDecision("State: " + gameState);
            entry.setTimestamp(Instant.now());
            entry.setSessionId(currentSession.getSessionId());

            addEpisodicMemory("game_events", entry);

        } catch (Exception e) {
            log.error("Error recording game state change", e);
        }
    }

    public List<String> searchRelevantMemories(String situation, int maxResults) {
        List<String> relevantMemories = new ArrayList<>();

        try {
            String lowerSituation = situation.toLowerCase();

            // Search episodic memories
            List<MemoryEntry> allMemories = episodicMemory.entrySet().stream()
                    .filter(entry -> entry.getKey().contains(lowerSituation) ||
                                   lowerSituation.contains(entry.getKey()))
                    .flatMap(entry -> entry.getValue().stream())
                    .filter(memory -> !memory.isExpired(MEMORY_RETENTION))
                    .sorted(Comparator.comparing(MemoryEntry::getRelevanceScore).reversed())
                    .limit(maxResults)
                    .collect(Collectors.toList());

            for (MemoryEntry memory : allMemories) {
                String memoryString = formatMemoryForRetrieval(memory);
                if (!memoryString.isEmpty()) {
                    relevantMemories.add(memoryString);
                }
            }

            // Search semantic knowledge
            semanticMemory.entrySet().stream()
                    .filter(entry -> entry.getKey().contains(lowerSituation) ||
                                   lowerSituation.contains(entry.getKey()))
                    .limit(3)
                    .forEach(entry -> {
                        SemanticKnowledge knowledge = entry.getValue();
                        String knowledgeString = String.format("Knowledge: %s (confidence: %.1f, used %d times)",
                                knowledge.getDescription(), knowledge.getConfidence(), knowledge.getUsageCount());
                        relevantMemories.add(knowledgeString);
                    });

            log.debug("Found {} relevant memories for: {}", relevantMemories.size(), situation);

        } catch (Exception e) {
            log.error("Error searching relevant memories", e);
        }

        return relevantMemories;
    }

    private void addEpisodicMemory(String key, MemoryEntry entry) {
        String lowerKey = key.toLowerCase();
        episodicMemory.computeIfAbsent(lowerKey, k -> new ArrayList<>()).add(entry);

        // Cleanup old memories
        List<MemoryEntry> memories = episodicMemory.get(lowerKey);
        if (memories.size() > MAX_EPISODIC_ENTRIES / episodicMemory.size()) {
            memories.removeIf(memory -> memory.isExpired(MEMORY_RETENTION));
        }
    }

    private void updateSemanticKnowledge(String situation, AiDecision decision, boolean successful, String outcome) {
        String key = situation.toLowerCase();
        SemanticKnowledge knowledge = semanticMemory.computeIfAbsent(key, k -> new SemanticKnowledge());

        knowledge.setSituation(situation);
        knowledge.addOutcome(successful);
        knowledge.setLastUpdated(Instant.now());

        if (successful) {
            knowledge.addSuccessfulAction(decision.getAction());
            knowledge.setDescription("In situation '" + situation + "', action '" + decision.getAction() + "' tends to work well.");
        } else {
            knowledge.addFailedAction(decision.getAction());
            knowledge.setDescription("In situation '" + situation + "', action '" + decision.getAction() + "' tends to fail. Outcome: " + outcome);
        }

        knowledge.incrementUsage();
    }

    private void updateSkillMemory(String skill, boolean successful) {
        SkillMemory skillMemory = skillMemories.computeIfAbsent(skill, k -> new SkillMemory(skill));
        skillMemory.addAttempt(successful);
        skillMemory.setLastUsed(Instant.now());
    }

    private String contextToString(GameContext context) {
        if (context == null) return "";

        return String.format("Location: %s, Health: %d/%d, Activity: %s",
                context.getCurrentLocation(),
                context.getCurrentHealth(),
                context.getMaxHealth(),
                context.getCurrentActivity());
    }

    private String formatMemoryForRetrieval(MemoryEntry memory) {
        StringBuilder formatted = new StringBuilder();

        formatted.append("Memory: ");
        formatted.append(memory.getSituation());

        if (memory.getDecision() != null) {
            formatted.append(" -> ").append(memory.getDecision());
        }

        if (memory.getOutcome() != null) {
            formatted.append(" (").append(memory.isSuccessful() ? "SUCCESS" : "FAILED").append(": ").append(memory.getOutcome()).append(")");
        }

        if (memory.getConfidence() > 0) {
            formatted.append(" [confidence: ").append(memory.getConfidence()).append("%]");
        }

        return formatted.toString();
    }

    public void saveSession() {
        try {
            if (currentSession != null) {
                currentSession.setEndTime(Instant.now());
                sessionHistory.add(currentSession);

                // Limit session history
                if (sessionHistory.size() > MAX_SESSIONS) {
                    sessionHistory.remove(0);
                }
            }

            saveToFile();
            log.info("Session saved");

        } catch (Exception e) {
            log.error("Error saving session", e);
        }
    }

    public void loadSession() {
        try {
            loadFromFile();
            startNewSession();
            log.info("Memory loaded from file");

        } catch (Exception e) {
            log.warn("Could not load memory from file, starting fresh", e);
            startNewSession();
        }
    }

    private void startNewSession() {
        currentSession = new GameSession();
        currentSession.setSessionId(UUID.randomUUID().toString());
        currentSession.setStartTime(Instant.now());
        log.debug("Started new game session: {}", currentSession.getSessionId());
    }

    private void saveToFile() {
        try {
            MemoryData data = new MemoryData();
            data.setEpisodicMemory(episodicMemory);
            data.setSemanticMemory(semanticMemory);
            data.setSkillMemories(skillMemories);
            data.setSessionHistory(sessionHistory);

            String json = gson.toJson(data);

            try (FileWriter writer = new FileWriter(MEMORY_FILE)) {
                writer.write(json);
            }

        } catch (Exception e) {
            log.error("Error saving memory to file", e);
        }
    }

    private void loadFromFile() {
        try {
            File file = new File(MEMORY_FILE);
            if (!file.exists()) {
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<MemoryData>(){}.getType();
                MemoryData data = gson.fromJson(reader, type);

                if (data != null) {
                    if (data.getEpisodicMemory() != null) {
                        episodicMemory.putAll(data.getEpisodicMemory());
                    }
                    if (data.getSemanticMemory() != null) {
                        semanticMemory.putAll(data.getSemanticMemory());
                    }
                    if (data.getSkillMemories() != null) {
                        skillMemories.putAll(data.getSkillMemories());
                    }
                    if (data.getSessionHistory() != null) {
                        sessionHistory.addAll(data.getSessionHistory());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error loading memory from file", e);
        }
    }

    public void clearOldMemories() {
        try {
            // Remove expired episodic memories
            episodicMemory.values().forEach(memories ->
                    memories.removeIf(memory -> memory.isExpired(MEMORY_RETENTION)));

            // Remove empty categories
            episodicMemory.entrySet().removeIf(entry -> entry.getValue().isEmpty());

            log.info("Cleared old memories");

        } catch (Exception e) {
            log.error("Error clearing old memories", e);
        }
    }

    // Getters and stats
    public int getMemoryCount() {
        return episodicMemory.values().stream().mapToInt(List::size).sum();
    }

    public int getSemanticKnowledgeCount() {
        return semanticMemory.size();
    }

    public int getSessionCount() {
        return sessionHistory.size();
    }

    public GameSession getCurrentSession() {
        return currentSession;
    }

    public Map<String, Double> getSkillSuccessRates() {
        return skillMemories.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getSuccessRate()
                ));
    }

    // Data classes for serialization
    private static class MemoryData {
        private Map<String, List<MemoryEntry>> episodicMemory;
        private Map<String, SemanticKnowledge> semanticMemory;
        private Map<String, SkillMemory> skillMemories;
        private List<GameSession> sessionHistory;

        // Getters and setters
        public Map<String, List<MemoryEntry>> getEpisodicMemory() { return episodicMemory; }
        public void setEpisodicMemory(Map<String, List<MemoryEntry>> episodicMemory) { this.episodicMemory = episodicMemory; }
        public Map<String, SemanticKnowledge> getSemanticMemory() { return semanticMemory; }
        public void setSemanticMemory(Map<String, SemanticKnowledge> semanticMemory) { this.semanticMemory = semanticMemory; }
        public Map<String, SkillMemory> getSkillMemories() { return skillMemories; }
        public void setSkillMemories(Map<String, SkillMemory> skillMemories) { this.skillMemories = skillMemories; }
        public List<GameSession> getSessionHistory() { return sessionHistory; }
        public void setSessionHistory(List<GameSession> sessionHistory) { this.sessionHistory = sessionHistory; }
    }
}