package net.runelite.client.plugins.microbot.aiautonomous.core;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.aiautonomous.ai.OllamaClient.GameContext;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class GameStateAnalyzer {

    private final Client client;

    // Cached state information
    private GameContext lastGameContext;
    private Instant lastAnalysisTime = Instant.now();
    private Map<String, Object> cachedAnalysis = new HashMap<>();

    // Danger detection
    private final Set<String> dangerousAreas = Set.of(
            "Wilderness", "PvP World", "Dangerous Activity"
    );

    private final Set<Integer> dangerousNpcIds = Set.of(
            // Add known dangerous NPC IDs
    );

    public GameStateAnalyzer(Client client) {
        this.client = client;
    }

    public GameContext analyzeCurrentState() {
        try {
            GameContext context = new GameContext();

            // Basic player info
            Player localPlayer = client.getLocalPlayer();
            if (localPlayer == null) {
                return context;
            }

            // Location information
            WorldPoint location = localPlayer.getWorldLocation();
            context.setCurrentLocation(formatLocation(location));

            // Comprehensive player stats
            context.setCurrentHealth(client.getBoostedSkillLevel(Skill.HITPOINTS));
            context.setMaxHealth(client.getRealSkillLevel(Skill.HITPOINTS));
            context.setCombatLevel(client.getLocalPlayer().getCombatLevel());

            // All skill levels for requirement checking
            // Note: GameContext doesn't have setSkillLevels method
            // Skills will be included in detailed state instead

            // Current activity
            context.setCurrentActivity(determineCurrentActivity());

            // Note: GameContext has limited setters, comprehensive analysis
            // will be available through analyzeCurrentGameState() method
            // Commented out unsupported GameContext methods:
            // context.setDetailedGameState(analyzeDetailedState());
            // context.setNearbyNpcs(analyzeNearbyNpcs());
            // context.setNearbyItems(analyzeNearbyItems());
            // context.setNearbyObjects(analyzeNearbyObjects());
            // context.setNearbyPlayers(analyzeNearbyPlayers());
            // context.setInventoryItems(analyzeInventory());
            // context.setEquippedItems(analyzeEquipment());
            // context.setQuestStates(analyzeQuestProgress());
            // context.setAchievements(analyzeAchievements());

            // Available interactions and opportunities
            context.setAvailableActions(analyzeAvailableActions());
            // context.setOpportunities(identifyOpportunities()); // Method doesn't exist in GameContext

            // Combat and danger assessment
            // context.setThreatLevel(assessThreatLevel()); // Method doesn't exist in GameContext
            // context.setInCombat(Rs2Player.isInCombat()); // Method doesn't exist in GameContext

            // Resource and progression status
            // context.setResourceNeeds(analyzeResourceNeeds()); // Method doesn't exist in GameContext
            // context.setProgressionOpportunities(analyzeProgressionOpportunities()); // Method doesn't exist in GameContext

            lastGameContext = context;
            lastAnalysisTime = Instant.now();

            return context;

        } catch (Exception e) {
            log.error("Error analyzing game state", e);
            return new GameContext();
        }
    }

    public boolean isInDangerousState() {
        try {
            // Check if in dangerous area
            String currentArea = getCurrentArea();
            if (dangerousAreas.stream().anyMatch(currentArea::contains)) {
                return true;
            }

            // Check health status
            int currentHealth = client.getBoostedSkillLevel(Skill.HITPOINTS);
            int maxHealth = client.getRealSkillLevel(Skill.HITPOINTS);
            if (currentHealth < maxHealth * 0.3) { // Less than 30% health
                return true;
            }

            // Check for dangerous NPCs nearby
            if (hasNearbyDangerousNpcs()) {
                return true;
            }

            // Check if being attacked
            if (Rs2Player.isInCombat()) {
                Actor interacting = client.getLocalPlayer().getInteracting();
                if (interacting instanceof NPC && ((NPC) interacting).getCombatLevel() > 120) { // Simplified check
                    return true;
                }
            }

            // Check inventory for emergency items
            if (!hasEmergencyItems()) {
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("Error checking dangerous state", e);
            return true; // Err on the side of caution
        }
    }

    public AutonomousGameState determineGameState() {
        try {
            // Check if logged in
            if (client.getGameState() != net.runelite.api.GameState.LOGGED_IN) {
                return AutonomousGameState.LOGGED_OUT;
            }

            // Check for dangerous situations first
            if (isInDangerousState()) {
                return AutonomousGameState.EMERGENCY;
            }

            // Check if in combat
            if (Rs2Player.isInCombat()) {
                return AutonomousGameState.IN_COMBAT;
            }

            // Check if moving
            if (Rs2Player.isMoving()) {
                return AutonomousGameState.TRAVELING;
            }

            // Check if doing a skill activity
            if (Rs2Player.isAnimating()) {
                int animationId = client.getLocalPlayer().getAnimation();
                if (animationId != -1) {
                    SkillActivity skillActivity = determineSkillFromAnimation(animationId);
                    if (skillActivity != SkillActivity.UNKNOWN) {
                        return AutonomousGameState.SKILL_TRAINING;
                    }
                }
            }

            // Check if in a quest dialog
            if (isInQuestDialog()) {
                return AutonomousGameState.QUEST_DIALOG;
            }

            // Check if at a bank
            if (isNearBank()) {
                return AutonomousGameState.BANKING;
            }

            // Check if shopping
            if (isInShop()) {
                return AutonomousGameState.SHOPPING;
            }

            // Default to idle if nothing specific is happening
            return AutonomousGameState.IDLE;

        } catch (Exception e) {
            log.error("Error determining game state", e);
            return AutonomousGameState.UNKNOWN;
        }
    }

    private String formatLocation(WorldPoint location) {
        if (location == null) {
            return "Unknown";
        }

        // Try to get area name from game
        String areaName = getCurrentArea();
        return String.format("%s (%d, %d, %d)", areaName, location.getX(), location.getY(), location.getPlane());
    }

    public String getCurrentArea() {
        // This would need to be implemented based on the game's area detection
        // For now, return a basic implementation
        WorldPoint location = client.getLocalPlayer().getWorldLocation();
        if (location == null) {
            return "Unknown";
        }

        // Basic area detection based on coordinates
        int x = location.getX();
        int y = location.getY();

        if (x >= 3200 && x <= 3300 && y >= 3200 && y <= 3300) {
            return "Varrock";
        } else if (x >= 3000 && x <= 3100 && y >= 3100 && y <= 3200) {
            return "Lumbridge";
        } else if (x >= 2900 && x <= 3000 && y >= 3300 && y <= 3400) {
            return "Falador";
        } else if (y >= 3520) {
            return "Wilderness Level " + ((y - 3520) / 8 + 1);
        }

        return "Unknown Area";
    }

    private String determineCurrentActivity() {
        if (Rs2Player.isInCombat()) {
            return "Combat";
        }

        if (Rs2Player.isMoving()) {
            return "Moving";
        }

        if (Rs2Player.isAnimating()) {
            int animationId = client.getLocalPlayer().getAnimation();
            if (animationId != -1) {
                SkillActivity skill = determineSkillFromAnimation(animationId);
                return skill != SkillActivity.UNKNOWN ? skill.toString() : "Unknown Activity";
            }
        }

        return "Idle";
    }

    private SkillActivity determineSkillFromAnimation(int animationId) {
        // Map animation IDs to skills - this would need to be populated with actual IDs
        Map<Integer, SkillActivity> animationToSkill = Map.of(
                AnimationID.WOODCUTTING_BRONZE, SkillActivity.WOODCUTTING,
                AnimationID.WOODCUTTING_IRON, SkillActivity.WOODCUTTING,
                AnimationID.WOODCUTTING_STEEL, SkillActivity.WOODCUTTING,
                AnimationID.MINING_BRONZE_PICKAXE, SkillActivity.MINING,
                AnimationID.MINING_IRON_PICKAXE, SkillActivity.MINING,
                AnimationID.FISHING_NET, SkillActivity.FISHING,
                AnimationID.COOKING_FIRE, SkillActivity.COOKING
        );

        return animationToSkill.getOrDefault(animationId, SkillActivity.UNKNOWN);
    }

    private String analyzeInventory() {
        try {
            List<String> items = Rs2Inventory.items()
                    .filter(Objects::nonNull)
                    .map(item -> item.getName() + " x" + item.getQuantity())
                    .collect(Collectors.toList());

            return items.isEmpty() ? "Empty" : String.join(", ", items);

        } catch (Exception e) {
            log.error("Error analyzing inventory", e);
            return "Error reading inventory";
        }
    }

    private String analyzeEquipment() {
        try {
            List<String> equipment = new ArrayList<>();

            // Check main equipment slots
            Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            if (weapon != null) {
                equipment.add("Weapon: " + weapon.getName());
            }

            Rs2ItemModel helm = Rs2Equipment.get(EquipmentInventorySlot.HEAD);
            if (helm != null) {
                equipment.add("Helm: " + helm.getName());
            }

            Rs2ItemModel body = Rs2Equipment.get(EquipmentInventorySlot.BODY);
            if (body != null) {
                equipment.add("Body: " + body.getName());
            }

            return equipment.isEmpty() ? "No equipment" : String.join(", ", equipment);

        } catch (Exception e) {
            log.error("Error analyzing equipment", e);
            return "Error reading equipment";
        }
    }

    private String getCurrentQuest() {
        try {
            // Use Rs2Quest to get current quest information
            // This would need to be implemented based on the quest system
            return "No active quest"; // Placeholder

        } catch (Exception e) {
            log.error("Error getting current quest", e);
            return "Unknown";
        }
    }

    private String analyzeAvailableActions() {
        List<String> actions = new ArrayList<>();

        try {
            // Movement actions
            actions.add("Walk/Run");

            // Inventory actions
            if (!Rs2Inventory.isEmpty()) {
                actions.add("Use items");
            }

            // Combat actions
            if (hasWeapon()) {
                actions.add("Attack");
            }

            // Skill actions based on location and items
            if (hasSkillTools()) {
                actions.addAll(getAvailableSkillActions());
            }

            // Bank actions
            if (isNearBank()) {
                actions.add("Bank");
            }

            // Shop actions
            if (isInShop()) {
                actions.add("Shop");
            }

            return String.join(", ", actions);

        } catch (Exception e) {
            log.error("Error analyzing available actions", e);
            return "Unknown actions";
        }
    }

    private boolean hasNearbyDangerousNpcs() {
        try {
            return client.getNpcs().stream()
                    .filter(npc -> npc.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()) <= 10)
                    .anyMatch(npc -> dangerousNpcIds.contains(npc.getId()) ||
                                   (npc.getCombatLevel() > 0 && npc.getCombatLevel() > 130)); // Simplified check
        } catch (Exception e) {
            log.error("Error checking nearby dangerous NPCs", e);
            return false;
        }
    }

    private boolean hasEmergencyItems() {
        try {
            // For now, be more lenient with emergency items requirement
            // TODO: Implement proper food and teleport detection

            // Check if inventory has any items (basic safety check)
            if (Rs2Inventory.isEmpty()) {
                log.debug("No emergency items: inventory is empty");
                return false;
            }

            // Check for common food items
            String[] foodItems = {
                "Lobster", "Swordfish", "Shark", "Monkfish", "Tuna", "Salmon",
                "Bread", "Cake", "Apple pie", "Meat pie", "Cooked chicken",
                "Shrimp", "Anchovies", "Sardine", "Herring", "Mackerel",
                "Cod", "Pike", "Trout", "Bass", "Karambwan"
            };

            boolean hasFood = false;
            for (String food : foodItems) {
                if (Rs2Inventory.contains(food)) {
                    hasFood = true;
                    break;
                }
            }

            // Check for teleport items
            String[] teleportItems = {
                "Varrock teleport", "Lumbridge teleport", "Falador teleport", "Camelot teleport",
                "Ardougne teleport", "Watchtower teleport", "Teleport to house",
                "Ectophial", "Games necklace", "Ring of dueling", "Amulet of glory",
                "Skills necklace", "Combat bracelet", "Dramen staff", "Lunar staff"
            };

            boolean hasTeleport = false;
            for (String teleport : teleportItems) {
                if (Rs2Inventory.contains(teleport)) {
                    hasTeleport = true;
                    break;
                }
            }

            // Be more lenient - only require food OR teleport, not both
            boolean hasEmergencyItems = hasFood || hasTeleport;

            if (!hasEmergencyItems) {
                log.debug("No emergency items found - hasFood: {}, hasTeleport: {}", hasFood, hasTeleport);
            }

            return hasEmergencyItems;

        } catch (Exception e) {
            log.warn("Error checking emergency items, assuming safe", e);
            return true; // Be safe and assume we have emergency items if check fails
        }
    }

    private boolean isInQuestDialog() {
        // Check if quest dialog is open
        // TODO: Fix widget detection when WidgetInfo is available
        return false; // Placeholder
    }

    private boolean isNearBank() {
        // Check if near a bank - this would need to be implemented with actual bank locations
        return false; // Placeholder
    }

    private boolean isInShop() {
        // Check if shop interface is open
        // TODO: Fix widget detection when WidgetInfo is available
        return false; // Placeholder
    }

    private boolean hasWeapon() {
        Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        return weapon != null;
    }

    private boolean hasSkillTools() {
        // Check for common skill tools
        return Rs2Inventory.contains("axe") || Rs2Inventory.contains("pickaxe") ||
               Rs2Inventory.contains("fishing rod") || Rs2Inventory.contains("net");
    }

    private List<String> getAvailableSkillActions() {
        List<String> skillActions = new ArrayList<>();

        if (Rs2Inventory.contains("axe")) {
            skillActions.add("Woodcutting");
        }
        if (Rs2Inventory.contains("pickaxe")) {
            skillActions.add("Mining");
        }
        if (Rs2Inventory.contains("fishing")) {
            skillActions.add("Fishing");
        }

        return skillActions;
    }

    public GameContext getLastGameContext() {
        return lastGameContext;
    }

    // === NEW COMPREHENSIVE ANALYSIS METHODS ===

    private Map<String, Integer> getAllSkillLevels() {
        Map<String, Integer> skillLevels = new HashMap<>();
        try {
            for (Skill skill : Skill.values()) {
                skillLevels.put(skill.name(), client.getRealSkillLevel(skill));
            }
        } catch (Exception e) {
            log.warn("Error getting skill levels", e);
        }
        return skillLevels;
    }

    private Map<String, Object> analyzeDetailedState() {
        Map<String, Object> state = new HashMap<>();
        try {
            state.put("game_state", client.getGameState().name());
            state.put("player_animating", Rs2Player.isAnimating());
            state.put("player_moving", Rs2Player.isMoving());
            state.put("player_in_combat", Rs2Player.isInCombat());
            state.put("current_animation", client.getLocalPlayer().getAnimation());
            state.put("run_energy", client.getEnergy());

            // Prayer and magic info
            state.put("prayer_points", client.getBoostedSkillLevel(Skill.PRAYER));
            state.put("max_prayer", client.getRealSkillLevel(Skill.PRAYER));

        } catch (Exception e) {
            log.debug("Error analyzing detailed state", e);
        }
        return state;
    }

    private List<Map<String, Object>> analyzeNearbyNpcs() {
        List<Map<String, Object>> npcs = new ArrayList<>();
        try {
            client.getNpcs().stream()
                .filter(npc -> npc.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()) <= 15)
                .forEach(npc -> {
                    Map<String, Object> npcInfo = new HashMap<>();
                    npcInfo.put("name", npc.getName());
                    npcInfo.put("id", npc.getId());
                    npcInfo.put("combat_level", npc.getCombatLevel());
                    npcInfo.put("health_ratio", npc.getHealthRatio());
                    npcInfo.put("location", npc.getWorldLocation());
                    npcInfo.put("distance", npc.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()));
                    npcInfo.put("interacting", npc.getInteracting() != null);
                    npcInfo.put("actions", npc.getComposition() != null ? npc.getComposition().getActions() : new String[0]);
                    npcs.add(npcInfo);
                });
        } catch (Exception e) {
            log.debug("Error analyzing nearby NPCs", e);
        }
        return npcs;
    }

    private List<Map<String, Object>> analyzeNearbyItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            // Simplified item detection for now
            log.debug("Analyzing nearby items (simplified implementation)");
        } catch (Exception e) {
            log.debug("Error analyzing nearby items", e);
        }
        return items;
    }

    private List<Map<String, Object>> analyzeNearbyObjects() {
        List<Map<String, Object>> objects = new ArrayList<>();
        try {
            // Simplified object detection for now
            log.debug("Analyzing nearby objects (simplified implementation)");
        } catch (Exception e) {
            log.debug("Error analyzing nearby objects", e);
        }
        return objects;
    }

    private List<Map<String, Object>> analyzeNearbyPlayers() {
        List<Map<String, Object>> players = new ArrayList<>();
        try {
            client.getPlayers().stream()
                .filter(player -> player != client.getLocalPlayer())
                .filter(player -> player.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()) <= 15)
                .forEach(player -> {
                    Map<String, Object> playerInfo = new HashMap<>();
                    playerInfo.put("name", player.getName());
                    playerInfo.put("combat_level", player.getCombatLevel());
                    playerInfo.put("location", player.getWorldLocation());
                    playerInfo.put("distance", player.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()));
                    playerInfo.put("animating", player.getAnimation() != -1);
                    playerInfo.put("interacting", player.getInteracting() != null);
                    players.add(playerInfo);
                });
        } catch (Exception e) {
            log.debug("Error analyzing nearby players", e);
        }
        return players;
    }

    private Map<String, Object> analyzeQuestProgress() {
        Map<String, Object> questInfo = new HashMap<>();
        try {
            // This would need integration with quest tracking systems
            questInfo.put("active_quest", "None"); // Placeholder
            questInfo.put("quest_points", 0); // Would need proper implementation
            questInfo.put("completed_quests", new ArrayList<>());
            questInfo.put("available_quests", new ArrayList<>());
        } catch (Exception e) {
            log.debug("Error analyzing quest progress", e);
        }
        return questInfo;
    }

    private Map<String, Object> analyzeAchievements() {
        Map<String, Object> achievements = new HashMap<>();
        try {
            // Achievement diary progress would go here
            achievements.put("completion_percentage", 0.0);
            achievements.put("completed_diaries", new ArrayList<>());
            achievements.put("available_tasks", new ArrayList<>());
        } catch (Exception e) {
            log.debug("Error analyzing achievements", e);
        }
        return achievements;
    }

    private List<String> identifyOpportunities() {
        List<String> opportunities = new ArrayList<>();
        try {
            // Identify training opportunities
            if (Rs2Player.isAnimating()) {
                opportunities.add("SKILL_TRAINING");
            }

            // Check for nearby quest NPCs or objectives
            // This would be enhanced with quest database integration

        } catch (Exception e) {
            log.debug("Error identifying opportunities", e);
        }
        return opportunities;
    }

    private String assessThreatLevel() {
        try {
            if (Rs2Player.isInCombat()) {
                return "HIGH";
            }

            // Check for aggressive NPCs nearby
            long aggressiveNpcs = client.getNpcs().stream()
                .filter(npc -> npc.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()) <= 5)
                .filter(npc -> npc.getCombatLevel() > 0)
                .count();

            if (aggressiveNpcs > 0) {
                return "MEDIUM";
            }

            return "LOW";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private Map<String, Object> analyzeResourceNeeds() {
        Map<String, Object> needs = new HashMap<>();
        try {
            // Food needs
            boolean needsFood = client.getBoostedSkillLevel(Skill.HITPOINTS) < client.getRealSkillLevel(Skill.HITPOINTS) * 0.7;
            needs.put("food", needsFood);

            // Prayer potion needs
            boolean needsPrayer = client.getBoostedSkillLevel(Skill.PRAYER) < client.getRealSkillLevel(Skill.PRAYER) * 0.3;
            needs.put("prayer_restore", needsPrayer);

            // Inventory space
            needs.put("inventory_space", Rs2Inventory.getEmptySlots());

        } catch (Exception e) {
            log.debug("Error analyzing resource needs", e);
        }
        return needs;
    }

    private Map<String, Object> analyzeProgressionOpportunities() {
        Map<String, Object> progression = new HashMap<>();
        try {
            // Skill training opportunities
            Map<String, Object> skillOpportunities = new HashMap<>();
            for (Skill skill : Skill.values()) {
                int currentLevel = client.getRealSkillLevel(skill);
                if (currentLevel < 99) {
                    skillOpportunities.put(skill.name().toLowerCase(),
                        Map.of("current_level", currentLevel, "next_milestone", getNextMilestone(currentLevel)));
                }
            }
            progression.put("skills", skillOpportunities);

            // Quest opportunities (placeholder)
            progression.put("quests", new ArrayList<>());

            // Achievement opportunities (placeholder)
            progression.put("achievements", new ArrayList<>());

        } catch (Exception e) {
            log.debug("Error analyzing progression opportunities", e);
        }
        return progression;
    }

    private int getNextMilestone(int currentLevel) {
        int[] milestones = {10, 20, 30, 40, 50, 60, 70, 80, 90, 99};
        for (int milestone : milestones) {
            if (currentLevel < milestone) {
                return milestone;
            }
        }
        return 99;
    }

    public GameState analyzeCurrentGameState() {
        GameState gameState = new GameState();

        try {
            Player localPlayer = client.getLocalPlayer();
            if (localPlayer == null) {
                return gameState;
            }

            // Set location
            WorldPoint location = localPlayer.getWorldLocation();
            gameState.setLocation(getLocationName(location));

            // Set nearby players
            List<String> nearbyPlayers = client.getPlayers().stream()
                .filter(p -> p != localPlayer && p.getWorldLocation().distanceTo(location) <= 15)
                .map(Player::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            gameState.setNearbyPlayers(nearbyPlayers);

            // Set nearby NPCs
            List<String> nearbyNpcs = client.getNpcs().stream()
                .filter(npc -> npc.getWorldLocation() != null && npc.getWorldLocation().distanceTo(location) <= 15)
                .map(NPC::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            gameState.setNearbyNpcs(nearbyNpcs);

            // Set skill levels
            Map<String, Integer> skillLevels = new HashMap<>();
            for (Skill skill : Skill.values()) {
                if (skill != Skill.OVERALL) {
                    skillLevels.put(skill.getName(), client.getRealSkillLevel(skill));
                }
            }
            gameState.setSkillLevels(skillLevels);

            // Set inventory items
            Map<String, Integer> inventoryItems = new HashMap<>();
            Rs2Inventory.items().forEach(item -> {
                String itemName = item.getName();
                if (itemName != null) {
                    inventoryItems.put(itemName, inventoryItems.getOrDefault(itemName, 0) + item.getQuantity());
                }
            });
            gameState.setInventoryItems(inventoryItems);

            // Set combat and movement status
            gameState.setInCombat(Rs2Player.isInCombat());
            gameState.setMoving(Rs2Player.isMoving());

            // Set current activity
            gameState.setCurrentActivity(determineCurrentActivity());

            // Set HP and combat level
            gameState.setCurrentHp(client.getBoostedSkillLevel(Skill.HITPOINTS));
            gameState.setMaxHp(client.getRealSkillLevel(Skill.HITPOINTS));
            gameState.setCombatLevel(localPlayer.getCombatLevel());

            // Set quest progress (simplified)
            Map<String, Object> questProgress = new HashMap<>();
            // Add quest data here if available
            gameState.setQuestProgress(questProgress);

            // Set additional data
            Map<String, Object> additionalData = new HashMap<>();
            additionalData.put("health", client.getBoostedSkillLevel(Skill.HITPOINTS));
            additionalData.put("prayer", client.getBoostedSkillLevel(Skill.PRAYER));
            additionalData.put("run_energy", client.getEnergy());
            additionalData.put("combat_level", localPlayer.getCombatLevel());
            gameState.setAdditionalData(additionalData);

        } catch (Exception e) {
            log.error("Error analyzing current game state", e);
        }

        return gameState;
    }

    private String getLocationName(WorldPoint location) {
        if (location == null) {
            return "Unknown";
        }

        // Simple location identification based on coordinates
        // This could be enhanced with more sophisticated location detection
        int x = location.getX();
        int y = location.getY();
        int plane = location.getPlane();

        // Basic location mapping (can be expanded)
        if (x >= 3200 && x <= 3230 && y >= 3200 && y <= 3230) {
            return "Lumbridge";
        } else if (x >= 3180 && x <= 3230 && y >= 3430 && y <= 3460) {
            return "Varrock";
        } else if (x >= 2940 && x <= 2970 && y >= 3350 && y <= 3390) {
            return "Falador";
        } else if (x >= 2600 && x <= 2670 && y >= 3280 && y <= 3320) {
            return "Ardougne";
        } else if (x >= 2440 && x <= 2500 && y >= 3080 && y <= 3120) {
            return "Camelot";
        }

        return String.format("Coordinates(%d,%d,%d)", x, y, plane);
    }

    public static class GameState {
        private String location;
        private List<String> nearbyPlayers;
        private List<String> nearbyNpcs;
        private Map<String, Integer> skillLevels;
        private Map<String, Integer> inventoryItems;
        private boolean inCombat;
        private boolean isMoving;
        private String currentActivity;
        private Map<String, Object> additionalData;
        private int currentHp;
        private int maxHp;
        private int combatLevel;
        private Map<String, Object> questProgress;

        public GameState() {
            this.nearbyPlayers = new ArrayList<>();
            this.nearbyNpcs = new ArrayList<>();
            this.skillLevels = new HashMap<>();
            this.inventoryItems = new HashMap<>();
            this.additionalData = new HashMap<>();
            this.questProgress = new HashMap<>();
        }

        // Getters and setters
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public List<String> getNearbyPlayers() { return nearbyPlayers; }
        public void setNearbyPlayers(List<String> nearbyPlayers) { this.nearbyPlayers = nearbyPlayers; }

        public List<String> getNearbyNpcs() { return nearbyNpcs; }
        public void setNearbyNpcs(List<String> nearbyNpcs) { this.nearbyNpcs = nearbyNpcs; }

        public Map<String, Integer> getSkillLevels() { return skillLevels; }
        public void setSkillLevels(Map<String, Integer> skillLevels) { this.skillLevels = skillLevels; }

        public Map<String, Integer> getAllSkillLevels() { return skillLevels; }

        public Map<String, Integer> getInventoryItems() { return inventoryItems; }
        public void setInventoryItems(Map<String, Integer> inventoryItems) { this.inventoryItems = inventoryItems; }

        public boolean isInCombat() { return inCombat; }
        public void setInCombat(boolean inCombat) { this.inCombat = inCombat; }

        public boolean isMoving() { return isMoving; }
        public void setMoving(boolean moving) { isMoving = moving; }

        public String getCurrentActivity() { return currentActivity; }
        public void setCurrentActivity(String currentActivity) { this.currentActivity = currentActivity; }

        public Map<String, Object> getAdditionalData() { return additionalData; }
        public void setAdditionalData(Map<String, Object> additionalData) { this.additionalData = additionalData; }

        public int getCurrentHp() { return currentHp; }
        public void setCurrentHp(int currentHp) { this.currentHp = currentHp; }

        public int getMaxHp() { return maxHp; }
        public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

        public int getCombatLevel() { return combatLevel; }
        public void setCombatLevel(int combatLevel) { this.combatLevel = combatLevel; }

        public Map<String, Object> getQuestProgress() { return questProgress; }
        public void setQuestProgress(Map<String, Object> questProgress) { this.questProgress = questProgress; }

        // Additional methods needed by other classes
        public List<String> getNearbyObjects() {
            return (List<String>) additionalData.getOrDefault("nearbyObjects", new ArrayList<>());
        }

        public List<String> getGroundItems() {
            return (List<String>) additionalData.getOrDefault("groundItems", new ArrayList<>());
        }
    }

    public enum SkillActivity {
        WOODCUTTING, MINING, FISHING, COOKING, SMITHING, CRAFTING, FLETCHING,
        HERBLORE, FARMING, CONSTRUCTION, HUNTER, RUNECRAFTING, UNKNOWN
    }
}