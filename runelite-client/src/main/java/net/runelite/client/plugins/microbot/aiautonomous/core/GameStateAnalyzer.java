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

            // Health and combat stats
            context.setCurrentHealth(client.getBoostedSkillLevel(Skill.HITPOINTS));
            context.setMaxHealth(client.getRealSkillLevel(Skill.HITPOINTS));
            // TODO: Find correct way to get combat level
            context.setCombatLevel(100); // Placeholder

            // Current activity
            context.setCurrentActivity(determineCurrentActivity());

            // Inventory analysis
            context.setInventoryItems(analyzeInventory());

            // Equipment analysis
            context.setEquippedItems(analyzeEquipment());

            // Quest information
            context.setCurrentQuest(getCurrentQuest());

            // Available actions
            context.setAvailableActions(analyzeAvailableActions());

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
            if (client.getGameState() != GameState.LOGGED_IN) {
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
        // Check for food and teleports
        boolean hasFood = Rs2Inventory.hasItemAmount("food", 1, false); // This would need proper food detection
        boolean hasTeleport = Rs2Inventory.contains("Teleport"); // This would need proper teleport detection
        return hasFood && hasTeleport;
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

    public enum SkillActivity {
        WOODCUTTING, MINING, FISHING, COOKING, SMITHING, CRAFTING, FLETCHING,
        HERBLORE, FARMING, CONSTRUCTION, HUNTER, RUNECRAFTING, UNKNOWN
    }
}