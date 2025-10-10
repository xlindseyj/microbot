package net.runelite.client.plugins.microbot.aiautonomous.ai;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.ai.memory.GameMemory;
import net.runelite.client.plugins.microbot.aiautonomous.ai.memory.ExperienceStorage;
import net.runelite.client.plugins.microbot.aiautonomous.ai.OllamaClient.GameContext;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;
import net.runelite.client.plugins.microbot.aiautonomous.integration.RAGSystemInterface;
import net.runelite.client.plugins.microbot.aiautonomous.integration.RAGSystemInterface.KnowledgeEntry;
import net.runelite.client.plugins.microbot.aiautonomous.integration.WikiIntegration;
import net.runelite.client.plugins.microbot.aiautonomous.integration.WikiIntegration.WikiSearchResult;
import net.runelite.client.plugins.microbot.aiautonomous.integration.WikiIntegration.WikiPageContent;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Slf4j
public class KnowledgeManager {

    private final RAGSystemInterface ragSystem;
    private final WikiIntegration wikiIntegration;
    private final GameMemory gameMemory;
    private final ExperienceStorage experienceStorage;

    // Knowledge quality tracking
    private final Map<String, KnowledgeQuality> knowledgeQuality = new HashMap<>();
    private final Set<String> verifiedKnowledge = new HashSet<>();
    private final Map<String, Integer> knowledgeUsageCount = new HashMap<>();

    private static final int MAX_KNOWLEDGE_ENTRIES = 10;
    private static final double MIN_SIMILARITY_THRESHOLD = 0.6;
    private static final int MAX_WIKI_RESULTS = 3;

    public KnowledgeManager(RAGSystemInterface ragSystem, WikiIntegration wikiIntegration, GameMemory gameMemory, AiAutonomousConfig config) {
        this.ragSystem = ragSystem;
        this.wikiIntegration = wikiIntegration;
        this.gameMemory = gameMemory;
        this.experienceStorage = new ExperienceStorage(ragSystem, config);
    }

    public void initialize() {
        log.info("Initializing Knowledge Manager...");

        // Load existing knowledge quality data
        loadKnowledgeQuality();

        // Populate initial knowledge if needed
        populateInitialKnowledge();

        log.info("Knowledge Manager initialized");
    }

    public List<String> retrieveRelevantKnowledge(String situation) {
        List<String> relevantKnowledge = new ArrayList<>();

        try {
            // 1. Search RAG system for stored knowledge (if available)
            CompletableFuture<List<KnowledgeEntry>> ragFuture = null;
            if (ragSystem != null) {
                ragFuture = ragSystem.searchKnowledge(situation, MAX_KNOWLEDGE_ENTRIES, MIN_SIMILARITY_THRESHOLD);
            }

            // 2. Search game memory for relevant experiences
            List<String> memoryKnowledge = gameMemory.searchRelevantMemories(situation, 3);

            // 3. Search experience storage for similar gameplay experiences
            CompletableFuture<List<String>> experienceFuture =
                experienceStorage.queryRelevantExperiences(situation);

            // 4. Search wiki if needed (for new or complex topics)
            CompletableFuture<WikiSearchResult> wikiFuture = null;
            if (shouldSearchWiki(situation)) {
                wikiFuture = wikiIntegration.searchWiki(extractWikiSearchTerm(situation));
            }

            // Wait for RAG results (if RAG system is available)
            if (ragFuture != null) {
                List<KnowledgeEntry> ragResults = ragFuture.get();
                for (KnowledgeEntry entry : ragResults) {
                    String knowledge = formatKnowledgeEntry(entry);
                    relevantKnowledge.add(knowledge);
                    updateKnowledgeUsage(entry.getId());
                }
            }

            // Add memory knowledge
            relevantKnowledge.addAll(memoryKnowledge);

            // Add experience storage results
            List<String> experienceResults = experienceFuture.get();
            relevantKnowledge.addAll(experienceResults);

            // Wait for wiki results if searching
            if (wikiFuture != null) {
                WikiSearchResult wikiResult = wikiFuture.get();
                List<String> wikiKnowledge = processWikiResults(wikiResult);
                relevantKnowledge.addAll(wikiKnowledge);
            }

            // Sort by relevance and quality
            relevantKnowledge = prioritizeKnowledge(relevantKnowledge, situation);

            // Limit to most relevant entries
            if (relevantKnowledge.size() > MAX_KNOWLEDGE_ENTRIES) {
                relevantKnowledge = relevantKnowledge.subList(0, MAX_KNOWLEDGE_ENTRIES);
            }

            log.debug("Retrieved {} knowledge entries for situation: {}", relevantKnowledge.size(), situation);

        } catch (Exception e) {
            log.error("Error retrieving relevant knowledge", e);
        }

        return relevantKnowledge;
    }

    public void recordDecision(String situation, AiDecision decision, GameContext context) {
        try {
            // Create knowledge entry from this decision
            String knowledgeContent = formatDecisionAsKnowledge(situation, decision, context);

            // Add to RAG system
            KnowledgeEntry entry = new KnowledgeEntry();
            entry.setId("decision_" + System.currentTimeMillis());
            entry.setContent(knowledgeContent);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("type", "decision");
            metadata.put("category", decision.getCategory().toString());
            metadata.put("confidence", String.valueOf(decision.getConfidence()));
            metadata.put("timestamp", decision.getTimestamp().toString());
            entry.setMetadata(metadata);

            if (ragSystem != null) {
                ragSystem.addKnowledge(entry);
            }

            // Record in game memory
            gameMemory.recordDecision(situation, decision, context);

            log.debug("Recorded decision knowledge: {}", decision.getAction());

        } catch (Exception e) {
            log.error("Error recording decision knowledge", e);
        }
    }

    public void learnFromOutcome(String situation, AiDecision decision, boolean successful, String outcome) {
        try {
            // Update knowledge quality based on outcome
            String knowledgeId = findRelatedKnowledgeId(situation, decision);
            if (knowledgeId != null) {
                updateKnowledgeQuality(knowledgeId, successful);
            }

            // Create learning entry
            String learningContent = formatLearningEntry(situation, decision, successful, outcome);

            KnowledgeEntry learningEntry = new KnowledgeEntry();
            learningEntry.setId("learning_" + System.currentTimeMillis());
            learningEntry.setContent(learningContent);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("type", "learning");
            metadata.put("original_decision", decision.getAction());
            metadata.put("successful", String.valueOf(successful));
            metadata.put("confidence_adjustment", calculateConfidenceAdjustment(decision, successful));
            learningEntry.setMetadata(metadata);

            ragSystem.addKnowledge(learningEntry);

            // Update game memory
            gameMemory.recordOutcome(situation, decision, successful, outcome);

            log.debug("Recorded learning from outcome: {} -> {}", situation, successful ? "SUCCESS" : "FAILURE");

        } catch (Exception e) {
            log.error("Error learning from outcome", e);
        }
    }

    public void addExternalKnowledge(String source, String content, Map<String, String> metadata) {
        try {
            KnowledgeEntry entry = new KnowledgeEntry();
            entry.setId("external_" + source + "_" + System.currentTimeMillis());
            entry.setContent(content);

            Map<String, String> fullMetadata = new HashMap<>(metadata);
            fullMetadata.put("source", source);
            fullMetadata.put("type", "external");
            fullMetadata.put("added_timestamp", Instant.now().toString());
            entry.setMetadata(fullMetadata);

            if (ragSystem != null) {
                ragSystem.addKnowledge(entry);
            }

            log.debug("Added external knowledge from source: {}", source);

        } catch (Exception e) {
            log.error("Error adding external knowledge", e);
        }
    }

    private boolean shouldSearchWiki(String situation) {
        // Search wiki for new or complex situations
        String[] wikiTriggers = {"quest", "monster", "item", "skill", "location", "how to", "where is", "what is"};
        String lowerSituation = situation.toLowerCase();

        return Arrays.stream(wikiTriggers).anyMatch(lowerSituation::contains);
    }

    private String extractWikiSearchTerm(String situation) {
        // Extract key terms for wiki search
        String[] words = situation.toLowerCase().split("\\s+");
        List<String> importantWords = new ArrayList<>();

        // Filter out common words
        Set<String> stopWords = Set.of("the", "is", "at", "which", "on", "and", "or", "but", "in", "with", "a", "an");

        for (String word : words) {
            if (!stopWords.contains(word) && word.length() > 2) {
                importantWords.add(word);
            }
        }

        return String.join(" ", importantWords.subList(0, Math.min(3, importantWords.size())));
    }

    private List<String> processWikiResults(WikiSearchResult wikiResult) {
        List<String> wikiKnowledge = new ArrayList<>();

        for (int i = 0; i < Math.min(MAX_WIKI_RESULTS, wikiResult.getEntries().size()); i++) {
            WikiIntegration.WikiSearchEntry entry = wikiResult.getEntries().get(i);
            String knowledge = String.format("Wiki: %s - %s", entry.getTitle(), entry.getSnippet());
            wikiKnowledge.add(knowledge);

            // Optionally add to RAG system for future use
            addWikiKnowledgeToRAG(entry);
        }

        return wikiKnowledge;
    }

    private void addWikiKnowledgeToRAG(WikiIntegration.WikiSearchEntry wikiEntry) {
        try {
            KnowledgeEntry ragEntry = new KnowledgeEntry();
            ragEntry.setId("wiki_" + wikiEntry.getTitle().replaceAll("\\s+", "_").toLowerCase());
            ragEntry.setContent(wikiEntry.getTitle() + ": " + wikiEntry.getSnippet());

            Map<String, String> metadata = new HashMap<>();
            metadata.put("source", "wiki");
            metadata.put("type", "reference");
            metadata.put("wiki_title", wikiEntry.getTitle());
            metadata.put("wiki_size", String.valueOf(wikiEntry.getSize()));
            ragEntry.setMetadata(metadata);

            ragSystem.addKnowledge(ragEntry);
        } catch (Exception e) {
            log.warn("Failed to add wiki knowledge to RAG", e);
        }
    }

    private String formatKnowledgeEntry(KnowledgeEntry entry) {
        String source = entry.getMetadata() != null ? entry.getMetadata().getOrDefault("source", "unknown") : "unknown";
        return String.format("[%s] %s", source.toUpperCase(), entry.getContent());
    }

    private String formatDecisionAsKnowledge(String situation, AiDecision decision, GameContext context) {
        StringBuilder knowledge = new StringBuilder();
        knowledge.append("Situation: ").append(situation).append("\n");
        knowledge.append("Action taken: ").append(decision.getAction()).append("\n");
        knowledge.append("Reasoning: ").append(decision.getReasoning()).append("\n");
        knowledge.append("Confidence: ").append(decision.getConfidence()).append("%\n");

        if (context != null) {
            knowledge.append("Context: Location=").append(context.getCurrentLocation())
                    .append(", Health=").append(context.getCurrentHealth())
                    .append(", Activity=").append(context.getCurrentActivity()).append("\n");
        }

        return knowledge.toString();
    }

    private String formatLearningEntry(String situation, AiDecision decision, boolean successful, String outcome) {
        StringBuilder learning = new StringBuilder();
        learning.append("Learning: In situation '").append(situation).append("', ");
        learning.append("the action '").append(decision.getAction()).append("' ");
        learning.append(successful ? "was successful" : "failed").append(".\n");
        learning.append("Outcome: ").append(outcome).append("\n");
        learning.append("Original confidence: ").append(decision.getConfidence()).append("%\n");
        learning.append("Lesson: ").append(generateLesson(situation, decision, successful));

        return learning.toString();
    }

    private String generateLesson(String situation, AiDecision decision, boolean successful) {
        if (successful) {
            return "This action type (" + decision.getCategory() + ") works well in similar situations.";
        } else {
            return "Avoid this action type (" + decision.getCategory() + ") in similar situations. Consider alternatives.";
        }
    }

    private List<String> prioritizeKnowledge(List<String> knowledge, String situation) {
        // Sort knowledge by relevance, quality, and recency
        return knowledge.stream()
                .sorted((k1, k2) -> compareKnowledgeRelevance(k1, k2, situation))
                .collect(Collectors.toList());
    }

    private int compareKnowledgeRelevance(String k1, String k2, String situation) {
        // Simple relevance scoring - in practice this would be more sophisticated
        int score1 = calculateRelevanceScore(k1, situation);
        int score2 = calculateRelevanceScore(k2, situation);
        return Integer.compare(score2, score1); // Descending order
    }

    private int calculateRelevanceScore(String knowledge, String situation) {
        int score = 0;
        String[] situationWords = situation.toLowerCase().split("\\s+");
        String lowerKnowledge = knowledge.toLowerCase();

        for (String word : situationWords) {
            if (lowerKnowledge.contains(word)) {
                score += 10;
            }
        }

        // Boost for verified knowledge
        if (verifiedKnowledge.contains(knowledge)) {
            score += 50;
        }

        return score;
    }

    private void updateKnowledgeUsage(String knowledgeId) {
        knowledgeUsageCount.merge(knowledgeId, 1, Integer::sum);
    }

    private void updateKnowledgeQuality(String knowledgeId, boolean successful) {
        KnowledgeQuality quality = knowledgeQuality.computeIfAbsent(knowledgeId, k -> new KnowledgeQuality());
        quality.addOutcome(successful);

        if (quality.getSuccessRate() > 0.8 && quality.getTotalUsages() >= 5) {
            verifiedKnowledge.add(knowledgeId);
        }
    }

    private String findRelatedKnowledgeId(String situation, AiDecision decision) {
        // Simple implementation - in practice this would use similarity matching
        return "decision_" + situation.hashCode();
    }

    private String calculateConfidenceAdjustment(AiDecision decision, boolean successful) {
        int adjustment = successful ? +10 : -20;
        return String.valueOf(adjustment);
    }

    private void loadKnowledgeQuality() {
        // In a real implementation, this would load from persistent storage
        log.debug("Loading knowledge quality data");
    }

    private void populateInitialKnowledge() {
        // Add some initial game knowledge
        if (ragSystem == null) {
            log.warn("RAG system not available, skipping initial knowledge population");
            return;
        }

        List<KnowledgeEntry> initialKnowledge = createInitialGameKnowledge();

        if (!initialKnowledge.isEmpty()) {
            ragSystem.addKnowledgeBatch(initialKnowledge).thenAccept(success -> {
                if (success) {
                    log.info("Populated initial game knowledge");
                } else {
                    log.warn("Failed to populate initial knowledge");
                }
            });
        }
    }

    private List<KnowledgeEntry> createInitialGameKnowledge() {
        List<KnowledgeEntry> entries = new ArrayList<>();

        // Add common gameplay knowledge
        entries.add(createKnowledgeEntry("safety_first",
                "Always prioritize safety. Keep food in inventory and teleport runes for escape.",
                "category", "safety", "priority", "high"));

        entries.add(createKnowledgeEntry("efficient_training",
                "Train skills efficiently by doing the highest experience rate activity you can afford and access.",
                "category", "skills", "priority", "medium"));

        entries.add(createKnowledgeEntry("quest_progression",
                "Do quests that unlock new areas, skills, or provide good experience rewards first.",
                "category", "quests", "priority", "high"));

        return entries;
    }

    private KnowledgeEntry createKnowledgeEntry(String id, String content, String... metadata) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(id);
        entry.setContent(content);

        Map<String, String> metaMap = new HashMap<>();
        for (int i = 0; i < metadata.length; i += 2) {
            if (i + 1 < metadata.length) {
                metaMap.put(metadata[i], metadata[i + 1]);
            }
        }
        entry.setMetadata(metaMap);

        return entry;
    }

    public void shutdown() {
        log.info("Knowledge Manager shutting down");
        // Save knowledge quality data
        saveKnowledgeQuality();
    }

    private void saveKnowledgeQuality() {
        // In a real implementation, this would save to persistent storage
        log.debug("Saving knowledge quality data");
    }

    // Experience storage methods
    public void storeGameplayExperience(GameStateAnalyzer.GameState gameState, String action,
                                      String outcome, boolean successful) {
        experienceStorage.storeGameplayExperience(gameState, action, outcome, successful);
    }

    public void storeSkillProgressExperience(String skill, int oldLevel, int newLevel,
                                           String method, long timeSpent) {
        experienceStorage.storeSkillProgressExperience(skill, oldLevel, newLevel, method, timeSpent);
    }

    public void storeQuestExperience(String questName, String step, boolean completed,
                                   List<String> requirements, String strategy) {
        experienceStorage.storeQuestExperience(questName, step, completed, requirements, strategy);
    }

    public void storeCombatExperience(String opponent, String strategy, boolean victory,
                                    int damage, long duration) {
        experienceStorage.storeCombatExperience(opponent, strategy, victory, damage, duration);
    }

    public void storeResourceGatheringExperience(String resource, String method,
                                               int quantity, long timeSpent) {
        experienceStorage.storeResourceGatheringExperience(resource, method, quantity, timeSpent);
    }

    public Map<String, Double> getActionSuccessRates() {
        return experienceStorage.getActionSuccessRates();
    }

    public List<String> getBestStrategiesFor(String context) {
        return experienceStorage.getBestStrategiesFor(context);
    }

    public String getStats() {
        String baseStats = String.format("Knowledge entries used: %d, Verified: %d, Quality tracked: %d",
                knowledgeUsageCount.size(), verifiedKnowledge.size(), knowledgeQuality.size());
        String experienceStats = experienceStorage.getExperienceStats();
        return baseStats + " | " + experienceStats;
    }

    private static class KnowledgeQuality {
        private int successCount = 0;
        private int totalUsages = 0;

        public void addOutcome(boolean successful) {
            totalUsages++;
            if (successful) {
                successCount++;
            }
        }

        public double getSuccessRate() {
            return totalUsages > 0 ? (double) successCount / totalUsages : 0.0;
        }

        public int getTotalUsages() {
            return totalUsages;
        }
    }
}