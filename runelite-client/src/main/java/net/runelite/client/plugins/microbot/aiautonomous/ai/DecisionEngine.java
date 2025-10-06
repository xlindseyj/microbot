package net.runelite.client.plugins.microbot.aiautonomous.ai;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.ai.OllamaClient.GameContext;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;


@Slf4j
public class DecisionEngine {

    private final OllamaClient ollamaClient;
    private final KnowledgeManager knowledgeManager;

    private String lastDecision = "";
    private Instant lastDecisionTime = Instant.MIN;
    private final Map<String, Integer> decisionHistory = new HashMap<>();
    private final List<String> recentDecisions = new ArrayList<>();

    private static final int MAX_RECENT_DECISIONS = 10;
    private static final int DECISION_TIMEOUT_SECONDS = 30;

    public DecisionEngine(OllamaClient ollamaClient, KnowledgeManager knowledgeManager) {
        this.ollamaClient = ollamaClient;
        this.knowledgeManager = knowledgeManager;
    }

    public CompletableFuture<AiDecision> makeDecision(String situation, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Making AI decision for situation: {}", situation);

                // Gather relevant knowledge
                List<String> relevantKnowledge = knowledgeManager.retrieveRelevantKnowledge(situation);

                // Build enhanced prompt with knowledge
                String enhancedPrompt = buildDecisionPrompt(situation, relevantKnowledge, context);

                // Get AI response
                CompletableFuture<String> aiResponseFuture = ollamaClient.generateDecision(enhancedPrompt, context);
                String aiResponse = aiResponseFuture.get(DECISION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                // Parse and validate decision
                AiDecision decision = parseDecision(aiResponse, situation);

                // Record decision
                recordDecision(decision);

                // Learn from this decision
                if (knowledgeManager != null) {
                    knowledgeManager.recordDecision(situation, decision, context);
                }

                return decision;

            } catch (TimeoutException e) {
                log.warn("AI decision timed out for situation: {}", situation);
                return createFallbackDecision(situation, "Decision timeout");

            } catch (Exception e) {
                log.error("Error making AI decision", e);
                return createFallbackDecision(situation, "Error: " + e.getMessage());
            }
        });
    }

    private String buildDecisionPrompt(String situation, List<String> knowledge, GameContext context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("SITUATION: ").append(situation).append("\n\n");

        if (knowledge != null && !knowledge.isEmpty()) {
            prompt.append("RELEVANT KNOWLEDGE:\n");
            for (String knowledgeItem : knowledge) {
                prompt.append("- ").append(knowledgeItem).append("\n");
            }
            prompt.append("\n");
        }

        if (!recentDecisions.isEmpty()) {
            prompt.append("RECENT DECISIONS:\n");
            for (int i = Math.max(0, recentDecisions.size() - 3); i < recentDecisions.size(); i++) {
                prompt.append("- ").append(recentDecisions.get(i)).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("What should I do? Provide a specific action with reasoning.");

        return prompt.toString();
    }

    private AiDecision parseDecision(String aiResponse, String originalSituation) {
        AiDecision decision = new AiDecision();
        decision.setOriginalSituation(originalSituation);
        decision.setRawResponse(aiResponse);
        decision.setTimestamp(Instant.now());

        try {
            // Extract action from response
            String action = extractAction(aiResponse);
            decision.setAction(action);

            // Extract confidence level
            int confidence = extractConfidence(aiResponse);
            decision.setConfidence(confidence);

            // Extract reasoning
            String reasoning = extractReasoning(aiResponse);
            decision.setReasoning(reasoning);

            // Determine priority
            DecisionPriority priority = determinePriority(action, aiResponse);
            decision.setPriority(priority);

            // Set category
            DecisionCategory category = categorizeDecision(action);
            decision.setCategory(category);

            decision.setValid(validateDecision(decision));

        } catch (Exception e) {
            log.warn("Error parsing AI decision response", e);
            decision.setAction("WAIT");
            decision.setReasoning("Failed to parse AI response: " + e.getMessage());
            decision.setConfidence(10);
            decision.setPriority(DecisionPriority.LOW);
            decision.setCategory(DecisionCategory.IDLE);
            decision.setValid(false);
        }

        return decision;
    }

    private String extractAction(String response) {
        // Look for "ACTION:" prefix
        if (response.contains("ACTION:")) {
            String actionPart = response.substring(response.indexOf("ACTION:") + 7).trim();
            int newlineIndex = actionPart.indexOf('\n');
            if (newlineIndex > 0) {
                actionPart = actionPart.substring(0, newlineIndex);
            }
            return actionPart.trim();
        }

        // Extract first sentence as action if no ACTION: prefix
        String[] sentences = response.split("[.!]");
        if (sentences.length > 0) {
            return sentences[0].trim();
        }

        return response.length() > 100 ? response.substring(0, 100) + "..." : response;
    }

    private int extractConfidence(String response) {
        // Look for confidence indicators
        String lowerResponse = response.toLowerCase();

        if (lowerResponse.contains("confident") || lowerResponse.contains("certain")) {
            return 90;
        } else if (lowerResponse.contains("probably") || lowerResponse.contains("likely")) {
            return 70;
        } else if (lowerResponse.contains("maybe") || lowerResponse.contains("perhaps")) {
            return 50;
        } else if (lowerResponse.contains("unsure") || lowerResponse.contains("uncertain")) {
            return 30;
        }

        return 60; // Default confidence
    }

    private String extractReasoning(String response) {
        // Look for reasoning keywords
        String[] reasoningStarters = {"because", "since", "as", "due to", "reasoning:", "explanation:"};

        for (String starter : reasoningStarters) {
            int index = response.toLowerCase().indexOf(starter);
            if (index >= 0) {
                String reasoning = response.substring(index);
                return reasoning.length() > 200 ? reasoning.substring(0, 200) + "..." : reasoning;
            }
        }

        // Return full response if no specific reasoning found
        return response.length() > 100 ? response.substring(0, 100) + "..." : response;
    }

    private DecisionPriority determinePriority(String action, String response) {
        String lowerAction = action.toLowerCase();
        String lowerResponse = response.toLowerCase();

        // High priority keywords
        if (lowerAction.contains("emergency") || lowerAction.contains("urgent") ||
            lowerResponse.contains("danger") || lowerResponse.contains("critical")) {
            return DecisionPriority.URGENT;
        }

        // Combat and important activities
        if (lowerAction.contains("attack") || lowerAction.contains("flee") ||
            lowerAction.contains("eat") || lowerAction.contains("drink")) {
            return DecisionPriority.HIGH;
        }

        // Regular activities
        if (lowerAction.contains("skill") || lowerAction.contains("quest") ||
            lowerAction.contains("move") || lowerAction.contains("travel")) {
            return DecisionPriority.MEDIUM;
        }

        // Idle or low-priority actions
        return DecisionPriority.LOW;
    }

    private DecisionCategory categorizeDecision(String action) {
        String lowerAction = action.toLowerCase();

        if (lowerAction.contains("attack") || lowerAction.contains("fight") || lowerAction.contains("combat")) {
            return DecisionCategory.COMBAT;
        } else if (lowerAction.contains("move") || lowerAction.contains("walk") || lowerAction.contains("run")) {
            return DecisionCategory.MOVEMENT;
        } else if (lowerAction.contains("skill") || lowerAction.contains("train") || lowerAction.contains("level")) {
            return DecisionCategory.SKILL;
        } else if (lowerAction.contains("quest") || lowerAction.contains("talk") || lowerAction.contains("npc")) {
            return DecisionCategory.QUEST;
        } else if (lowerAction.contains("buy") || lowerAction.contains("sell") || lowerAction.contains("trade")) {
            return DecisionCategory.TRADE;
        } else if (lowerAction.contains("wait") || lowerAction.contains("idle") || lowerAction.contains("rest")) {
            return DecisionCategory.IDLE;
        }

        return DecisionCategory.OTHER;
    }

    private boolean validateDecision(AiDecision decision) {
        // Basic validation
        if (decision.getAction() == null || decision.getAction().trim().isEmpty()) {
            return false;
        }

        // Check if action is reasonable length
        if (decision.getAction().length() > 500) {
            return false;
        }

        // Check confidence is reasonable
        if (decision.getConfidence() < 0 || decision.getConfidence() > 100) {
            return false;
        }

        return true;
    }

    private AiDecision createFallbackDecision(String situation, String reason) {
        AiDecision decision = new AiDecision();
        decision.setOriginalSituation(situation);
        decision.setAction("WAIT");
        decision.setReasoning("Fallback decision: " + reason);
        decision.setConfidence(10);
        decision.setPriority(DecisionPriority.LOW);
        decision.setCategory(DecisionCategory.IDLE);
        decision.setTimestamp(Instant.now());
        decision.setValid(false);
        decision.setRawResponse("FALLBACK: " + reason);

        return decision;
    }

    private void recordDecision(AiDecision decision) {
        lastDecision = decision.getAction();
        lastDecisionTime = decision.getTimestamp();

        // Update decision history
        decisionHistory.merge(decision.getAction(), 1, Integer::sum);

        // Update recent decisions
        recentDecisions.add(decision.getAction());
        if (recentDecisions.size() > MAX_RECENT_DECISIONS) {
            recentDecisions.remove(0);
        }

        log.debug("Recorded decision: {} (confidence: {})", decision.getAction(), decision.getConfidence());
    }

    public String getLastDecision() {
        return lastDecision;
    }

    public Instant getLastDecisionTime() {
        return lastDecisionTime;
    }

    public Map<String, Integer> getDecisionHistory() {
        return new HashMap<>(decisionHistory);
    }

    public List<String> getRecentDecisions() {
        return new ArrayList<>(recentDecisions);
    }

    public KnowledgeManager getKnowledgeManager() {
        return knowledgeManager;
    }

    public enum DecisionPriority {
        LOW, MEDIUM, HIGH, URGENT
    }

    public enum DecisionCategory {
        COMBAT, MOVEMENT, SKILL, QUEST, TRADE, IDLE, OTHER
    }
}