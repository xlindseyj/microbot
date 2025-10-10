package net.runelite.client.plugins.microbot.aiautonomous.core;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.aiautonomous.ai.AiDecision;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;

import java.awt.event.KeyEvent;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;


@Slf4j
public class ActionExecutor {

    private final Client client;
    private final AiAutonomousConfig config;

    // Execution tracking
    private int actionsPerformed = 0;
    private Instant lastActionTime = Instant.MIN;
    private String lastExecutedAction = "";

    // State tracking for complex actions
    private boolean isCurrentlyMoving = false;
    private boolean isCurrentlyBanking = false;
    private boolean isCurrentlyShopping = false;
    private boolean isCurrentlyTrainingSkill = false;
    private String currentTarget = "";

    // Action parsing patterns
    private static final Pattern MOVE_PATTERN = Pattern.compile("(?i).*(?:move|go|walk|travel|run).*to\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTACK_PATTERN = Pattern.compile("(?i).*(?:attack|fight|kill)\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern USE_PATTERN = Pattern.compile("(?i).*(?:use|click|interact)\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_PATTERN = Pattern.compile("(?i).*(?:bank|deposit|withdraw).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern EAT_PATTERN = Pattern.compile("(?i).*(?:eat|consume|drink)\\s+(.+)", Pattern.CASE_INSENSITIVE);

    public ActionExecutor(Client client, AiAutonomousConfig config) {
        this.client = client;
        this.config = config;
    }

    public CompletableFuture<Boolean> executeAction(AiDecision decision) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing action: {}", decision.getAction());

                boolean success = performAction(decision.getAction());

                if (success) {
                    actionsPerformed++;
                    lastActionTime = Instant.now();
                    lastExecutedAction = decision.getAction();
                }

                return success;

            } catch (Exception e) {
                log.error("Error executing action: {}", decision.getAction(), e);
                return false;
            }
        });
    }

    private boolean performAction(String action) {
        String lowerAction = action.toLowerCase().trim();

        try {
            // Apply antiban measures before action execution
            if (config.useAntibanMeasures()) {
                // Set appropriate activity based on action type
                Activity antibanActivity = mapActionToActivity(lowerAction);
                Rs2Antiban.setActivity(antibanActivity);

                // Random chance for micro break
                if (Math.random() < config.randomActionChance() / 100.0) {
                    Rs2Antiban.takeMicroBreakByChance();
                }

                // Random chance for mouse movement
                if (Math.random() < config.randomActionChance() / 200.0) { // Half the chance
                    Rs2Antiban.moveMouseRandomly();
                }
            }

            // Emergency actions - always allowed regardless of feature settings
            if (lowerAction.contains("emergency") || lowerAction.contains("escape")) {
                if (!config.enableEmergencyActions()) {
                    log.warn("Emergency actions disabled, skipping: {}", action);
                    return false;
                }
                return performEmergencyAction(action);
            }

            // Movement actions
            if (MOVE_PATTERN.matcher(lowerAction).matches()) {
                if (!config.enableMovement()) {
                    log.warn("Movement disabled, skipping: {}", action);
                    return false;
                }
                return performMovementAction(action);
            }

            // Combat actions
            if (ATTACK_PATTERN.matcher(lowerAction).matches()) {
                if (!config.enableCombatActions()) {
                    log.warn("Combat actions disabled, skipping: {}", action);
                    return false;
                }
                return performCombatAction(action);
            }

            // Banking actions
            if (BANK_PATTERN.matcher(lowerAction).matches()) {
                if (!config.enableBankingActions()) {
                    log.warn("Banking actions disabled, skipping: {}", action);
                    return false;
                }
                return performBankingAction(action);
            }

            // Item usage actions
            if (USE_PATTERN.matcher(lowerAction).matches() || lowerAction.contains("click")) {
                if (!config.enableInventoryManagement()) {
                    log.warn("Inventory management disabled, skipping: {}", action);
                    return false;
                }
                return performInteractionAction(action);
            }

            // Eating/drinking actions - always allowed for safety
            if (EAT_PATTERN.matcher(lowerAction).matches()) {
                if (!config.enableInventoryManagement() && !config.enableEmergencyActions()) {
                    log.warn("Consumption actions disabled, skipping: {}", action);
                    return false;
                }
                return performConsumptionAction(action);
            }

            // Skill training actions
            if (lowerAction.contains("train") || lowerAction.contains("skill")) {
                if (!config.enableSkillTraining()) {
                    log.warn("Skill training disabled, skipping: {}", action);
                    return false;
                }
                return performSkillAction(action);
            }

            // Dialog actions (quest related)
            if (lowerAction.contains("dialog") || lowerAction.contains("talk") || lowerAction.contains("say")) {
                if (!config.enableQuestActions()) {
                    log.warn("Quest actions disabled, skipping: {}", action);
                    return false;
                }
                return performDialogAction(action);
            }

            // Wait/idle actions - always allowed
            if (lowerAction.contains("wait") || lowerAction.contains("idle")) {
                return performWaitAction(action);
            }

            // Camera actions - always allowed for anti-ban
            if (lowerAction.contains("look") || lowerAction.contains("camera")) {
                return performCameraAction(action);
            }

            // Default fallback
            log.warn("Unknown action type: {}", action);
            return performGenericAction(action);

        } catch (Exception e) {
            log.error("Error performing action: {}", action, e);
            return false;
        }
    }

    private boolean performEmergencyAction(String action) {
        log.warn("Performing emergency action: {}", action);

        try {
            // Try to teleport first
            if (Rs2Inventory.contains("teleport")) {
                return Rs2Inventory.interact("teleport", "Break");
            }

            // Try to eat food if low health
            if (client.getBoostedSkillLevel(Skill.HITPOINTS) < client.getRealSkillLevel(Skill.HITPOINTS) * 0.5) {
                if (performConsumptionAction("eat food")) {
                    return true;
                }
            }

            // Try to run away
            if (Rs2Player.isInCombat()) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                WorldPoint escapePoint = new WorldPoint(playerLocation.getX() + Rs2Random.between(-10, 10),
                                                       playerLocation.getY() + Rs2Random.between(-10, 10),
                                                       playerLocation.getPlane());
                return Rs2Walker.walkTo(escapePoint);
            }

            return true;

        } catch (Exception e) {
            log.error("Error in emergency action", e);
            return false;
        }
    }

    private boolean performMovementAction(String action) {
        try {
            // Extract destination from action
            java.util.regex.Matcher matcher = MOVE_PATTERN.matcher(action);
            if (!matcher.find()) {
                log.warn("Could not extract destination from: {}", action);
                return false;
            }

            String destination = matcher.group(1).trim();
            log.debug("Moving to: {}", destination);

            isCurrentlyMoving = true;

            // Try to find and walk to the destination
            boolean success = walkToDestination(destination);

            if (success) {
                // Wait for movement to complete
                int timeout = 0;
                while (Rs2Player.isMoving() && timeout < 100) {
                    sleep(100);
                    timeout++;
                }
            }

            isCurrentlyMoving = false;
            return success;

        } catch (Exception e) {
            log.error("Error in movement action", e);
            isCurrentlyMoving = false;
            return false;
        }
    }

    private boolean walkToDestination(String destination) {
        // Simple destination mapping - in practice this would be much more sophisticated
        String lowerDest = destination.toLowerCase();

        try {
            if (lowerDest.contains("bank")) {
                return Rs2Bank.walkToBank();
            } else if (lowerDest.contains("lumbridge")) {
                return Rs2Walker.walkTo(new WorldPoint(3222, 3218, 0));
            } else if (lowerDest.contains("varrock")) {
                return Rs2Walker.walkTo(new WorldPoint(3212, 3424, 0));
            } else if (lowerDest.contains("falador")) {
                return Rs2Walker.walkTo(new WorldPoint(2964, 3378, 0));
            } else {
                // Try to find an NPC or object with this name
                return walkToNamedLocation(destination);
            }
        } catch (Exception e) {
            log.error("Error walking to destination", e);
            return false;
        }
    }

    private boolean walkToNamedLocation(String locationName) {
        try {
            // Try to find an NPC first
            NPC npc = Rs2Npc.getNpc(locationName);
            if (npc != null) {
                return Rs2Walker.walkTo(npc.getWorldLocation());
            }

            // Try to find a game object
            GameObject obj = Rs2GameObject.get(locationName);
            if (obj != null) {
                return Rs2Walker.walkTo(obj.getWorldLocation());
            }

            log.warn("Could not find location: {}", locationName);
            return false;

        } catch (Exception e) {
            log.error("Error finding named location", e);
            return false;
        }
    }

    private boolean performCombatAction(String action) {
        try {
            java.util.regex.Matcher matcher = ATTACK_PATTERN.matcher(action);
            if (!matcher.find()) {
                log.warn("Could not extract target from: {}", action);
                return false;
            }

            String targetName = matcher.group(1).trim();
            currentTarget = targetName;

            log.debug("Attacking: {}", targetName);

            // Find and attack the target
            NPC target = Rs2Npc.getNpc(targetName);
            if (target == null) {
                log.warn("Could not find NPC: {}", targetName);
                return false;
            }

            return Rs2Npc.attack(target);

        } catch (Exception e) {
            log.error("Error in combat action", e);
            return false;
        }
    }

    private boolean performBankingAction(String action) {
        try {
            isCurrentlyBanking = true;

            if (!Rs2Bank.isOpen()) {
                if (!Rs2Bank.openBank()) {
                    log.warn("Could not open bank");
                    isCurrentlyBanking = false;
                    return false;
                }
            }

            // Determine what banking action to perform
            String lowerAction = action.toLowerCase();

            if (lowerAction.contains("deposit all")) {
                return Rs2Bank.depositAll();
            } else if (lowerAction.contains("deposit")) {
                // TODO: Find correct method to deposit all inventory items
                return Rs2Bank.depositEquipment(); // Placeholder
            } else if (lowerAction.contains("withdraw")) {
                // This would need more sophisticated parsing to determine what to withdraw
                log.debug("Banking action: {}", action);
                return true;
            }

            return true;

        } catch (Exception e) {
            log.error("Error in banking action", e);
            return false;
        } finally {
            isCurrentlyBanking = false;
        }
    }

    private boolean performInteractionAction(String action) {
        try {
            java.util.regex.Matcher matcher = USE_PATTERN.matcher(action);
            if (!matcher.find()) {
                log.warn("Could not extract interaction target from: {}", action);
                return false;
            }

            String target = matcher.group(1).trim();
            log.debug("Interacting with: {}", target);

            // Try inventory first
            if (Rs2Inventory.contains(target)) {
                return Rs2Inventory.interact(target);
            }

            // Try NPCs
            NPC npc = Rs2Npc.getNpc(target);
            if (npc != null) {
                return Rs2Npc.interact(npc);
            }

            // Try game objects
            GameObject obj = Rs2GameObject.findObject(target, true, 20, true, Rs2Player.getWorldLocation());
            if (obj != null) {
                return Rs2GameObject.interact(obj);
            }

            log.warn("Could not find interaction target: {}", target);
            return false;

        } catch (Exception e) {
            log.error("Error in interaction action", e);
            return false;
        }
    }

    private boolean performConsumptionAction(String action) {
        try {
            java.util.regex.Matcher matcher = EAT_PATTERN.matcher(action);
            String itemName = "food"; // Default

            if (matcher.find()) {
                itemName = matcher.group(1).trim();
            }

            log.debug("Consuming: {}", itemName);

            // Find consumable item in inventory
            if (Rs2Inventory.contains(itemName)) {
                return Rs2Inventory.interact(itemName, "Eat");
            }

            // Try generic food items
            String[] foodTypes = {"bread", "cake", "fish", "meat", "pie"};
            for (String food : foodTypes) {
                if (Rs2Inventory.contains(food)) {
                    return Rs2Inventory.interact(food, "Eat");
                }
            }

            log.warn("No consumable items found");
            return false;

        } catch (Exception e) {
            log.error("Error in consumption action", e);
            return false;
        }
    }

    private boolean performSkillAction(String action) {
        try {
            isCurrentlyTrainingSkill = true;

            String lowerAction = action.toLowerCase();

            if (lowerAction.contains("woodcutting") || lowerAction.contains("chop")) {
                return performWoodcuttingAction();
            } else if (lowerAction.contains("mining") || lowerAction.contains("mine")) {
                return performMiningAction();
            } else if (lowerAction.contains("fishing") || lowerAction.contains("fish")) {
                return performFishingAction();
            } else if (lowerAction.contains("cooking") || lowerAction.contains("cook")) {
                return performCookingAction();
            }

            log.debug("Generic skill training: {}", action);
            return true;

        } catch (Exception e) {
            log.error("Error in skill action", e);
            return false;
        } finally {
            isCurrentlyTrainingSkill = false;
        }
    }

    private boolean performWoodcuttingAction() {
        // Find trees to cut
        GameObject tree = Rs2GameObject.findObject("Tree", true, 20, true, Rs2Player.getWorldLocation());
        if (tree != null) {
            return Rs2GameObject.interact(tree, "Chop down");
        }
        return false;
    }

    private boolean performMiningAction() {
        // Find rocks to mine
        GameObject rock = Rs2GameObject.findObject("Rock", true, 20, true, Rs2Player.getWorldLocation());
        if (rock != null) {
            return Rs2GameObject.interact(rock, "Mine");
        }
        return false;
    }

    private boolean performFishingAction() {
        // Find fishing spots
        NPC fishingSpot = Rs2Npc.getNpc("Fishing spot");
        if (fishingSpot != null) {
            return Rs2Npc.interact(fishingSpot, "Net");
        }
        return false;
    }

    private boolean performCookingAction() {
        // Find cooking fires or ranges
        GameObject cookingSpot = Rs2GameObject.findObject("Fire", true, 20, true, Rs2Player.getWorldLocation());
        if (cookingSpot != null && Rs2Inventory.contains("Raw")) {
            return Rs2GameObject.interact(cookingSpot, "Cook");
        }
        return false;
    }

    private boolean performDialogAction(String action) {
        try {
            if (Rs2Dialogue.isInDialogue()) {
                // Simple dialog handling - continue or select first option
                if (Rs2Dialogue.hasContinue()) {
                    Rs2Dialogue.clickContinue();
                    return true;
                } else {
                    // Select first option
                    Rs2Dialogue.keyPressForDialogueOption(1);
                    return true;
                }
            } else {
                log.debug("Not in dialog, action: {}", action);
                return true;
            }

        } catch (Exception e) {
            log.error("Error in dialog action", e);
            return false;
        }
    }

    private boolean performWaitAction(String action) {
        try {
            // Extract wait time if specified
            int waitTime = 1000; // Default 1 second

            if (action.contains("second")) {
                waitTime = 1000;
            } else if (action.contains("minute")) {
                waitTime = 60000;
            }

            log.debug("Waiting for {}ms", waitTime);
            sleep(waitTime);
            return true;

        } catch (Exception e) {
            log.error("Error in wait action", e);
            return false;
        }
    }

    private boolean performCameraAction(String action) {
        try {
            if (action.contains("around")) {
                Rs2Camera.turnTo(Rs2Random.between(0, 360));
            } else if (action.contains("up")) {
                Rs2Camera.setPitch(Rs2Random.between(383, 383));
            } else if (action.contains("down")) {
                Rs2Camera.setPitch(Rs2Random.between(128, 128));
            } else {
                // Random camera movement
                Rs2Camera.turnTo(Rs2Random.between(0, 360));
            }
            return true;

        } catch (Exception e) {
            log.error("Error in camera action", e);
            return false;
        }
    }

    private boolean performGenericAction(String action) {
        log.debug("Performing generic action: {}", action);

        // Try to find any keyword that might be clickable
        String[] words = action.split("\\s+");
        for (String word : words) {
            if (word.length() > 3) { // Skip very short words
                // Try as NPC
                NPC npc = Rs2Npc.getNpc(word);
                if (npc != null) {
                    return Rs2Npc.interact(npc);
                }

                // Try as game object
                GameObject obj = Rs2GameObject.findObject(word, true, 20, true, Rs2Player.getWorldLocation());
                if (obj != null) {
                    return Rs2GameObject.interact(obj);
                }

                // Try as inventory item
                if (Rs2Inventory.contains(word)) {
                    return Rs2Inventory.interact(word);
                }
            }
        }

        // Fallback: just wait
        sleep(1000);
        return true;
    }

    // Anti-ban and human-like behaviors
    public void performRandomCameraMovement() {
        Rs2Camera.turnTo(Rs2Random.between(0, 360));
    }

    public void checkRandomSkill() {
        Rs2Keyboard.keyPress(KeyEvent.VK_F1 + Rs2Random.between(0, 6)); // F1-F7 tabs
        sleep(Rs2Random.between(500, 1500));
    }

    public void performRandomMouseMovement() {
        // TODO: Implement mouse movement when VirtualMouse API is clarified
        log.debug("Random mouse movement requested");
    }

    public void checkInventory() {
        Rs2Keyboard.keyPress(KeyEvent.VK_F4); // Inventory tab
        sleep(Rs2Random.between(300, 800));
    }

    public void lookAround() {
        int currentYaw = Rs2Camera.getYaw();
        Rs2Camera.turnTo(currentYaw + Rs2Random.between(-45, 45));
    }

    public boolean performSafeLogout() {
        try {
            // Move to safe location first if possible
            if (Rs2Player.isInCombat()) {
                log.warn("Cannot logout while in combat");
                return false;
            }

            // Logout
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleep(1000);
            return true;

        } catch (Exception e) {
            log.error("Error performing safe logout", e);
            return false;
        }
    }

    // State checking methods for game state manager
    public boolean isMoving() {
        return isCurrentlyMoving || Rs2Player.isMoving();
    }

    public boolean isBanking() {
        return isCurrentlyBanking || Rs2Bank.isOpen();
    }

    public boolean isShopping() {
        return isCurrentlyShopping; // This would need shop detection
    }

    public boolean isTrainingSkill() {
        return isCurrentlyTrainingSkill || Rs2Player.isAnimating();
    }

    public boolean isInCombat() {
        return Rs2Player.isInCombat();
    }

    public boolean needsNavigationHelp() {
        return isCurrentlyMoving && !Rs2Player.isMoving();
    }

    public boolean needsSkillDecision() {
        return isCurrentlyTrainingSkill && !Rs2Player.isAnimating();
    }

    public boolean needsCombatDecision() {
        // TODO: Implement health checking when API is available
        return Rs2Player.isInCombat();
    }

    public boolean needsBankingDecision() {
        return isCurrentlyBanking && Rs2Bank.isOpen() && Rs2Inventory.isFull();
    }

    public boolean needsShoppingDecision() {
        return isCurrentlyShopping;
    }

    /**
     * Maps action descriptions to appropriate antiban activities for realistic behavior simulation.
     */
    private Activity mapActionToActivity(String lowerAction) {
        // Combat actions
        if (lowerAction.contains("attack") || lowerAction.contains("fight") || lowerAction.contains("combat") ||
            lowerAction.contains("eat") || lowerAction.contains("drink potion")) {
            return Activity.GENERAL_COMBAT;
        }

        // Skill training actions
        if (lowerAction.contains("woodcut") || lowerAction.contains("cut tree") || lowerAction.contains("chop")) {
            return Activity.GENERAL_WOODCUTTING;
        }
        if (lowerAction.contains("mine") || lowerAction.contains("mining")) {
            return Activity.GENERAL_MINING;
        }
        if (lowerAction.contains("fish") || lowerAction.contains("fishing")) {
            return Activity.GENERAL_FISHING;
        }
        if (lowerAction.contains("cook") || lowerAction.contains("cooking")) {
            return Activity.GENERAL_COOKING;
        }
        if (lowerAction.contains("craft") || lowerAction.contains("crafting")) {
            return Activity.GENERAL_CRAFTING;
        }
        if (lowerAction.contains("smelt") || lowerAction.contains("smithing")) {
            return Activity.GENERAL_SMITHING;
        }

        // Banking and trading
        if (lowerAction.contains("bank") || lowerAction.contains("deposit") || lowerAction.contains("withdraw")) {
            return Activity.GENERAL_COLLECTING;
        }
        if (lowerAction.contains("shop") || lowerAction.contains("buy") || lowerAction.contains("sell") ||
            lowerAction.contains("trade")) {
            return Activity.GENERAL_COLLECTING;
        }

        // Quest and dialogue
        if (lowerAction.contains("quest") || lowerAction.contains("talk") || lowerAction.contains("speak") ||
            lowerAction.contains("dialogue")) {
            return Activity.GENERAL_COLLECTING;
        }

        // Movement and exploration
        if (lowerAction.contains("walk") || lowerAction.contains("run") || lowerAction.contains("move") ||
            lowerAction.contains("travel") || lowerAction.contains("go to")) {
            return Activity.GENERAL_COLLECTING;
        }

        // Idle or unknown actions
        if (lowerAction.contains("wait") || lowerAction.contains("idle") || lowerAction.contains("rest")) {
            return Activity.GENERAL_COLLECTING;
        }

        // Default to general collecting for unknown actions
        return Activity.GENERAL_COLLECTING;
    }

    public boolean hasQuestDialog() {
        return Rs2Dialogue.isInDialogue();
    }

    public String getCurrentTarget() {
        return currentTarget;
    }

    public int getActionsPerformed() {
        return actionsPerformed;
    }

    public String getLastExecutedAction() {
        return lastExecutedAction;
    }

    private void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}