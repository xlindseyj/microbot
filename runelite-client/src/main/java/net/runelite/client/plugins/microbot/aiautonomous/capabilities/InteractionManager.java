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
public class InteractionManager {

    private final Client client;
    private final KnowledgeManager knowledgeManager;
    private final AiAutonomousConfig config;

    // Interaction tracking
    private final Map<String, NpcInteractionData> npcInteractions;
    private final Map<String, ObjectInteractionData> objectInteractions;
    private final Map<String, ItemInteractionData> itemInteractions;
    private final List<String> interactionHistory;

    // Quest and dialogue state
    private String currentQuestDialogue;
    private Map<String, QuestProgress> questStates;
    private Set<String> completedQuests;

    public InteractionManager(Client client, KnowledgeManager knowledgeManager, AiAutonomousConfig config) {
        this.client = client;
        this.knowledgeManager = knowledgeManager;
        this.config = config;
        this.npcInteractions = new HashMap<>();
        this.objectInteractions = new HashMap<>();
        this.itemInteractions = new HashMap<>();
        this.interactionHistory = new ArrayList<>();
        this.questStates = new HashMap<>();
        this.completedQuests = new HashSet<>();

        initializeKnownInteractions();
        log.info("Interaction manager initialized");
    }

    public CompletableFuture<InteractionDecision> analyzeInteractionOpportunities(GameStateAnalyzer.GameState currentState) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Analyzing interaction opportunities");

                // Check for NPCs to interact with
                List<String> nearbyNpcs = currentState.getNearbyNpcs();
                for (String npc : nearbyNpcs) {
                    InteractionDecision npcDecision = evaluateNpcInteraction(npc, currentState);
                    if (npcDecision.getPriority() > 50) {
                        return npcDecision;
                    }
                }

                // Check for objects to interact with
                List<String> nearbyObjects = currentState.getNearbyObjects();
                for (String object : nearbyObjects) {
                    InteractionDecision objDecision = evaluateObjectInteraction(object, currentState);
                    if (objDecision.getPriority() > 50) {
                        return objDecision;
                    }
                }

                // Check for items to interact with
                List<String> groundItems = currentState.getGroundItems();
                for (String item : groundItems) {
                    InteractionDecision itemDecision = evaluateItemInteraction(item, currentState);
                    if (itemDecision.getPriority() > 50) {
                        return itemDecision;
                    }
                }

                return new InteractionDecision(InteractionType.NONE, "No valuable interactions found", 0);

            } catch (Exception e) {
                log.error("Error analyzing interaction opportunities", e);
                return new InteractionDecision(InteractionType.NONE, "Error in interaction analysis", 0);
            }
        });
    }

    private InteractionDecision evaluateNpcInteraction(String npc, GameStateAnalyzer.GameState currentState) {
        NpcInteractionData data = npcInteractions.get(npc);
        if (data == null) {
            data = new NpcInteractionData(npc);
            npcInteractions.put(npc, data);
        }

        int priority = 0;
        String reasoning = "";

        // Check if this NPC offers quests
        if (isQuestGiver(npc)) {
            priority += 60;
            reasoning = "Quest giver NPC";

            // Higher priority if we haven't talked to them before
            if (!data.hasInteracted()) {
                priority += 20;
                reasoning += " (new quest opportunity)";
            }
        }

        // Check if this NPC is a trainer
        if (isSkillTrainer(npc)) {
            priority += 40;
            reasoning = "Skill trainer NPC";
        }

        // Check if this NPC is a merchant
        if (isMerchant(npc)) {
            priority += 30;
            reasoning = "Merchant NPC";

            // Higher priority if we need supplies
            if (needsSupplies(currentState)) {
                priority += 20;
                reasoning += " (need supplies)";
            }
        }

        // Check if this NPC provides services
        if (isServiceProvider(npc)) {
            priority += 25;
            reasoning = "Service provider";
        }

        // Reduce priority if recently interacted
        if (data.getLastInteraction() != null &&
            data.getLastInteraction().isAfter(LocalDateTime.now().minusMinutes(5))) {
            priority = Math.max(0, priority - 30);
            reasoning += " (recently interacted)";
        }

        return new InteractionDecision(InteractionType.NPC, reasoning, priority, npc);
    }

    private InteractionDecision evaluateObjectInteraction(String object, GameStateAnalyzer.GameState currentState) {
        ObjectInteractionData data = objectInteractions.get(object);
        if (data == null) {
            data = new ObjectInteractionData(object);
            objectInteractions.put(object, data);
        }

        int priority = 0;
        String reasoning = "";

        // Check if this is a resource gathering object
        if (isResourceObject(object)) {
            priority += 50;
            reasoning = "Resource gathering object";

            // Higher priority if we have appropriate tools
            if (hasAppropriateTools(object, currentState)) {
                priority += 20;
                reasoning += " (have tools)";
            } else {
                priority -= 20;
                reasoning += " (missing tools)";
            }

            // Higher priority if we have inventory space
            if (hasInventorySpace(currentState)) {
                priority += 10;
            } else {
                priority -= 30;
                reasoning += " (inventory full)";
            }
        }

        // Check if this is a quest object
        if (isQuestObject(object)) {
            priority += 70;
            reasoning = "Quest object";
        }

        // Check if this is a bank or chest
        if (isBankObject(object)) {
            priority += 35;
            reasoning = "Banking object";

            // Higher priority if inventory is full
            if (!hasInventorySpace(currentState)) {
                priority += 30;
                reasoning += " (need to bank)";
            }
        }

        // Check if this is a teleportation object
        if (isTeleportObject(object)) {
            priority += 20;
            reasoning = "Teleportation object";
        }

        return new InteractionDecision(InteractionType.OBJECT, reasoning, priority, object);
    }

    private InteractionDecision evaluateItemInteraction(String item, GameStateAnalyzer.GameState currentState) {
        ItemInteractionData data = itemInteractions.get(item);
        if (data == null) {
            data = new ItemInteractionData(item);
            itemInteractions.put(item, data);
        }

        int priority = 0;
        String reasoning = "";

        // Check if this is a valuable item
        if (isValuableItem(item)) {
            priority += 60;
            reasoning = "Valuable item";
        }

        // Check if this item is useful for current objectives
        if (isUsefulForObjectives(item, currentState)) {
            priority += 40;
            reasoning = "Useful for current objectives";
        }

        // Check if this is equipment
        if (isEquipment(item)) {
            priority += 30;
            reasoning = "Equipment item";

            // Higher priority if it's better than current equipment
            if (isBetterEquipment(item, currentState)) {
                priority += 30;
                reasoning += " (upgrade)";
            }
        }

        // Check if this is food or consumable
        if (isConsumable(item)) {
            priority += 20;
            reasoning = "Consumable item";

            // Higher priority if we're low on supplies
            if (isLowOnSupplies(currentState)) {
                priority += 25;
                reasoning += " (low on supplies)";
            }
        }

        // Don't pick up if inventory is full
        if (!hasInventorySpace(currentState)) {
            priority = Math.max(0, priority - 50);
            reasoning += " (inventory full)";
        }

        return new InteractionDecision(InteractionType.ITEM, reasoning, priority, item);
    }

    public void recordInteraction(String target, InteractionType type, boolean successful, String outcome) {
        try {
            // Record in appropriate data structure
            switch (type) {
                case NPC:
                    NpcInteractionData npcData = npcInteractions.computeIfAbsent(target, NpcInteractionData::new);
                    npcData.recordInteraction(successful, outcome);
                    break;
                case OBJECT:
                    ObjectInteractionData objData = objectInteractions.computeIfAbsent(target, ObjectInteractionData::new);
                    objData.recordInteraction(successful, outcome);
                    break;
                case ITEM:
                    ItemInteractionData itemData = itemInteractions.computeIfAbsent(target, ItemInteractionData::new);
                    itemData.recordInteraction(successful, outcome);
                    break;
            }

            // Add to history
            String historyEntry = String.format("%s: %s %s - %s (%s)",
                    LocalDateTime.now(), type, target, successful ? "SUCCESS" : "FAILED", outcome);
            interactionHistory.add(historyEntry);

            // Keep history limited
            if (interactionHistory.size() > 200) {
                interactionHistory.remove(0);
            }

            // Store in knowledge manager
            if (knowledgeManager != null) {
                storeInteractionExperience(target, type, successful, outcome);
            }

            log.debug("Recorded {} interaction with {}: {}", type, target, successful ? "success" : "failure");

        } catch (Exception e) {
            log.error("Error recording interaction", e);
        }
    }

    public void updateQuestProgress(String questName, String step, boolean completed) {
        QuestProgress progress = questStates.computeIfAbsent(questName, QuestProgress::new);
        progress.updateStep(step, completed);

        if (progress.isCompleted()) {
            completedQuests.add(questName);
            log.info("Quest completed: {}", questName);
        }

        // Store quest experience
        if (knowledgeManager != null) {
            knowledgeManager.storeQuestExperience(questName, step, completed,
                    progress.getRequirements(), progress.getStrategy());
        }
    }

    private void storeInteractionExperience(String target, InteractionType type, boolean successful, String outcome) {
        try {
            switch (type) {
                case NPC:
                    // Store as quest experience if it's quest-related
                    if (isQuestGiver(target)) {
                        knowledgeManager.storeQuestExperience(target, "interaction", successful,
                                Arrays.asList(), "dialogue");
                    }
                    break;
                case OBJECT:
                    // Store as resource gathering if it's a resource object
                    if (isResourceObject(target)) {
                        knowledgeManager.storeResourceGatheringExperience(target, "interaction",
                                successful ? 1 : 0, 1000);
                    }
                    break;
                case ITEM:
                    // Store as general gameplay experience
                    GameStateAnalyzer.GameState dummyState = new GameStateAnalyzer.GameState();
                    dummyState.setLocation("unknown");
                    knowledgeManager.storeGameplayExperience(dummyState, "pickup_" + target,
                            outcome, successful);
                    break;
            }

            // Store general interaction knowledge
            Map<String, String> metadata = new HashMap<>();
            metadata.put("interaction_type", type.toString());
            metadata.put("target", target);
            metadata.put("success", String.valueOf(successful));

            String content = String.format("Interaction with %s (%s): %s. Outcome: %s",
                    target, type, successful ? "successful" : "failed", outcome);

            knowledgeManager.addExternalKnowledge("interaction", content, metadata);

        } catch (Exception e) {
            log.warn("Failed to store interaction experience", e);
        }
    }

    // Helper methods for interaction evaluation
    private boolean isQuestGiver(String npc) {
        String lower = npc.toLowerCase();
        return lower.contains("quest") || lower.contains("giver") ||
               Arrays.asList("duke", "cook", "wizard", "lumbridge guide", "hans").stream()
                       .anyMatch(lower::contains);
    }

    private boolean isSkillTrainer(String npc) {
        String lower = npc.toLowerCase();
        return lower.contains("trainer") || lower.contains("master") ||
               lower.contains("tutor") || lower.contains("instructor");
    }

    private boolean isMerchant(String npc) {
        String lower = npc.toLowerCase();
        return lower.contains("shop") || lower.contains("store") ||
               lower.contains("merchant") || lower.contains("trader") ||
               lower.contains("seller");
    }

    private boolean isServiceProvider(String npc) {
        String lower = npc.toLowerCase();
        return lower.contains("banker") || lower.contains("healer") ||
               lower.contains("guard") || lower.contains("priest");
    }

    private boolean needsSupplies(GameStateAnalyzer.GameState currentState) {
        Set<String> inventory = currentState.getInventoryItems().keySet();

        // Check for food
        boolean hasFood = inventory.stream().anyMatch(item ->
                item.toLowerCase().contains("food") || item.toLowerCase().contains("fish"));

        // Check for basic supplies
        boolean hasBasicSupplies = inventory.size() > 10;

        return !hasFood || !hasBasicSupplies;
    }

    private boolean isResourceObject(String object) {
        String lower = object.toLowerCase();
        return lower.contains("tree") || lower.contains("rock") ||
               lower.contains("ore") || lower.contains("fish") ||
               lower.contains("mine") || lower.contains("quarry");
    }

    private boolean hasAppropriateTools(String object, GameStateAnalyzer.GameState currentState) {
        String lower = object.toLowerCase();
        Set<String> inventory = currentState.getInventoryItems().keySet();

        if (lower.contains("tree")) {
            return inventory.stream().anyMatch(item -> item.toLowerCase().contains("axe"));
        } else if (lower.contains("rock") || lower.contains("ore")) {
            return inventory.stream().anyMatch(item -> item.toLowerCase().contains("pickaxe"));
        } else if (lower.contains("fish")) {
            return inventory.stream().anyMatch(item -> item.toLowerCase().contains("net") ||
                    item.toLowerCase().contains("rod"));
        }

        return true; // Assume we have tools for unknown objects
    }

    private boolean hasInventorySpace(GameStateAnalyzer.GameState currentState) {
        return currentState.getInventoryItems().size() < 28;
    }

    private boolean isQuestObject(String object) {
        String lower = object.toLowerCase();
        return lower.contains("quest") || lower.contains("altar") ||
               lower.contains("chest") || lower.contains("door") ||
               lower.contains("lever");
    }

    private boolean isBankObject(String object) {
        String lower = object.toLowerCase();
        return lower.contains("bank") || lower.contains("chest") ||
               lower.contains("deposit");
    }

    private boolean isTeleportObject(String object) {
        String lower = object.toLowerCase();
        return lower.contains("portal") || lower.contains("teleport") ||
               lower.contains("gate") || lower.contains("spirit");
    }

    private boolean isValuableItem(String item) {
        String lower = item.toLowerCase();
        return lower.contains("gold") || lower.contains("gem") ||
               lower.contains("coin") || lower.contains("rare") ||
               lower.contains("rune") || lower.contains("dragon");
    }

    private boolean isUsefulForObjectives(String item, GameStateAnalyzer.GameState currentState) {
        // Check if item relates to current quests or training
        // This would be more sophisticated in practice
        return isConsumable(item) || isEquipment(item);
    }

    private boolean isEquipment(String item) {
        String lower = item.toLowerCase();
        return lower.contains("sword") || lower.contains("shield") ||
               lower.contains("armour") || lower.contains("helmet") ||
               lower.contains("bow") || lower.contains("staff");
    }

    private boolean isBetterEquipment(String item, GameStateAnalyzer.GameState currentState) {
        // Simple check - in practice would compare item stats
        String lower = item.toLowerCase();
        return lower.contains("mithril") || lower.contains("adamant") ||
               lower.contains("rune") || lower.contains("dragon");
    }

    private boolean isConsumable(String item) {
        String lower = item.toLowerCase();
        return lower.contains("food") || lower.contains("potion") ||
               lower.contains("fish") || lower.contains("bread") ||
               lower.contains("cake");
    }

    private boolean isLowOnSupplies(GameStateAnalyzer.GameState currentState) {
        Set<String> inventory = currentState.getInventoryItems().keySet();
        long foodCount = inventory.stream().filter(this::isConsumable).count();
        return foodCount < 3;
    }

    private void initializeKnownInteractions() {
        // Initialize with some common NPCs, objects, and items
        // This would be expanded based on game knowledge
        log.debug("Initialized known interactions database");
    }

    public Map<String, NpcInteractionData> getNpcInteractions() {
        return new HashMap<>(npcInteractions);
    }

    public Map<String, QuestProgress> getQuestStates() {
        return new HashMap<>(questStates);
    }

    public Set<String> getCompletedQuests() {
        return new HashSet<>(completedQuests);
    }

    public String getStatus() {
        return String.format("NPCs tracked: %d, Quests: %d (completed: %d), Interactions: %d",
                npcInteractions.size(), questStates.size(), completedQuests.size(),
                interactionHistory.size());
    }

    // Helper classes
    public static class InteractionDecision {
        private final InteractionType type;
        private final String reasoning;
        private final int priority;
        private final String target;

        public InteractionDecision(InteractionType type, String reasoning, int priority) {
            this(type, reasoning, priority, null);
        }

        public InteractionDecision(InteractionType type, String reasoning, int priority, String target) {
            this.type = type;
            this.reasoning = reasoning;
            this.priority = priority;
            this.target = target;
        }

        public InteractionType getType() { return type; }
        public String getReasoning() { return reasoning; }
        public int getPriority() { return priority; }
        public String getTarget() { return target; }
    }

    public enum InteractionType {
        NPC, OBJECT, ITEM, NONE
    }

    private static class InteractionData {
        protected int interactionCount;
        protected int successCount;
        protected LocalDateTime lastInteraction;
        protected String lastOutcome;

        public void recordInteraction(boolean successful, String outcome) {
            interactionCount++;
            if (successful) successCount++;
            lastInteraction = LocalDateTime.now();
            lastOutcome = outcome;
        }

        public boolean hasInteracted() {
            return interactionCount > 0;
        }

        public double getSuccessRate() {
            return interactionCount > 0 ? (double) successCount / interactionCount : 0.0;
        }

        public LocalDateTime getLastInteraction() { return lastInteraction; }
    }

    public static class NpcInteractionData extends InteractionData {
        private final String npcName;
        private final Set<String> dialogueOptions;

        public NpcInteractionData(String npcName) {
            this.npcName = npcName;
            this.dialogueOptions = new HashSet<>();
        }

        public String getNpcName() { return npcName; }
        public Set<String> getDialogueOptions() { return new HashSet<>(dialogueOptions); }
    }

    public static class ObjectInteractionData extends InteractionData {
        private final String objectName;
        private final Set<String> actionOptions;

        public ObjectInteractionData(String objectName) {
            this.objectName = objectName;
            this.actionOptions = new HashSet<>();
        }

        public String getObjectName() { return objectName; }
        public Set<String> getActionOptions() { return new HashSet<>(actionOptions); }
    }

    public static class ItemInteractionData extends InteractionData {
        private final String itemName;
        private int totalPickedUp;

        public ItemInteractionData(String itemName) {
            this.itemName = itemName;
            this.totalPickedUp = 0;
        }

        @Override
        public void recordInteraction(boolean successful, String outcome) {
            super.recordInteraction(successful, outcome);
            if (successful) totalPickedUp++;
        }

        public String getItemName() { return itemName; }
        public int getTotalPickedUp() { return totalPickedUp; }
    }

    public static class QuestProgress {
        private final String questName;
        private final Map<String, Boolean> steps;
        private final List<String> requirements;
        private String strategy;
        private boolean completed;

        public QuestProgress(String questName) {
            this.questName = questName;
            this.steps = new HashMap<>();
            this.requirements = new ArrayList<>();
            this.strategy = "standard";
            this.completed = false;
        }

        public void updateStep(String step, boolean completed) {
            steps.put(step, completed);
            checkCompletion();
        }

        private void checkCompletion() {
            // Simple completion check - all steps must be true
            completed = !steps.isEmpty() && steps.values().stream().allMatch(Boolean::booleanValue);
        }

        public String getQuestName() { return questName; }
        public Map<String, Boolean> getSteps() { return new HashMap<>(steps); }
        public List<String> getRequirements() { return new ArrayList<>(requirements); }
        public String getStrategy() { return strategy; }
        public boolean isCompleted() { return completed; }
    }
}