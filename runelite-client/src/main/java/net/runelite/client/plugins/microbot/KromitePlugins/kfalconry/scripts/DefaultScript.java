package net.runelite.client.plugins.microbot.KromitePlugins.kfalconry.scripts;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.KromitePlugins.kfalconry.KFalconryConfig;
import net.runelite.client.plugins.microbot.KromitePlugins.kfalconry.enums.KFalconryState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class DefaultScript extends Script {

    public static double version = 1.2;
    private static final int DARK_KEBBIT_ID = 5867;
    private static final int SPOTTED_KEBBIT_ID = 5868;
    private static final int DASHING_KEBBIT_ID = 5869;

    private static long startTime = 0;
    private static int startExperience = 0;
    private static int kebbitsHunted = 0;
    private static long startXp = 0;
    private static int hunterLevel = 0;

    // State management
    private KFalconryState currentState = KFalconryState.IDLE;
    private long lastStateChange = 0;
    private int stateStuckCount = 0;
    private boolean isActive = false;

    // Updated coordinates for the Falconry area
    private static final WorldPoint FALCONRY_AREA_CENTER = new WorldPoint(2371, 3619, 0);
    private static final WorldPoint KEBBIT_AREA_CENTER = new WorldPoint(2371, 3599, 0);
    private static final int SEARCH_RADIUS = 15;
    private static final WorldPoint BANK_LOCATION = new WorldPoint(2383, 3487, 0);

    // Catch tracking
    private NPC currentTarget = null;
    private long catchAttemptTime = 0;
    private static final long CATCH_TIMEOUT = 10000; // 10 seconds timeout
    private static final List<String> HUNT_ITEMS = Arrays.asList("kebbit fur", "spotted kebbit fur", "dark kebbit fur", "bones");

    public boolean onStart() {
        startTime = System.currentTimeMillis();
        startExperience = Microbot.getClient().getSkillExperience(Skill.HUNTER);
        kebbitsHunted = 0;
        currentState = KFalconryState.STARTING;
        lastStateChange = System.currentTimeMillis();
        Microbot.log("Starting Falconry script v" + version);
        applyAntiBanSettings();
        isActive = true;
        return true;
    }

    @Override
    public void shutdown() {
        Microbot.log("Falconry script stopped. Total kebbits hunted: " + kebbitsHunted);
        Microbot.log("Total XP gained: " + (Microbot.getClient().getSkillExperience(Skill.HUNTER) - startExperience));
        isActive = false;
        currentState = KFalconryState.STOPPING;
    }

    public boolean run(KFalconryConfig config) {
        if (!isActive || !Microbot.isLoggedIn()) return false;

        try {
            // Check for stuck states
            // checkForStuckState();

            // Set state if needed
            if (currentState == KFalconryState.STARTING) {
                validateEnvironment();
                return true;
            }

            // Execute current state
            executeCurrentState(config);

            // Apply random camera movement occasionally
            if (Rs2Random.between(0, 100) < 5) {
                moveRandomCamera();
            }

        } catch (Exception e) {
            Microbot.log("Error in falconry script: {}", e.getMessage(), e);
            Microbot.status = "Script error - retrying";
            sleep(2000);
        }

        return true;
    }

    private void executeCurrentState(KFalconryConfig config) {
        Microbot.status = "State: " + currentState;

        switch (currentState) {
            case IDLE:
                // Direct transition to hunting state
                currentState = KFalconryState.STARTING;
                updateStateChangeTime();
                break;

            case ANTIBAN:
                performAntibanAction();
                break;

            case LOGGING_OUT:
                handleLogout();
                break;

            case WAITING:
                // We're waiting for something to happen
                if (System.currentTimeMillis() - lastStateChange > 5000) {
                    currentState = KFalconryState.STARTING;
                    updateStateChangeTime();
                }
                break;

            case STARTING:
                // Actual hunting logic
                if (Rs2Inventory.isFull()) {
                    if (config.enableBanking()) {
                        currentState = KFalconryState.LOGGING_OUT;
                        updateStateChangeTime();
                    } else {
                        handleDroppingItems();
                    }
                } else {
                    // Check if we're in position
                    if (Rs2Player.distanceTo(KEBBIT_AREA_CENTER) > SEARCH_RADIUS) {
                        moveToKebbitsArea();
                    } else {
                        // Try to catch kebbits
                        catchKebbits(config);
                    }
                }
                break;

            default:
                currentState = KFalconryState.STARTING;
                updateStateChangeTime();
                break;
        }
    }

    private void validateEnvironment() {
        // Check if we're in the falconry area
        if (Rs2Player.distanceTo(FALCONRY_AREA_CENTER) > 50) {
            Microbot.log("Not in falconry area. Please teleport to the Piscatoris Hunter area.");
            Microbot.status = "Not in falconry area";
            currentState = KFalconryState.STOPPING;
            return;
        }

        // Check player has falcon equipment
        if (!Rs2Inventory.hasItem("Falconer's glove") && !Rs2Player.hasPlayerEquippedItem(Rs2Player.getLocalPlayer(), "Falconer's glove")) {
            Microbot.log("Player does not have a falcon. Please obtain one.");
            Microbot.status = "No falcon found";
            currentState = KFalconryState.STOPPING;
            return;
        }

        currentState = KFalconryState.IDLE;
    }

    private void setNextState() {
        // Add small chance of antiban
        if (Rs2Random.between(0, 100) < 5) {
            currentState = KFalconryState.ANTIBAN;
        } else {
            // Go to STARTING state to do actual work instead of IDLE
            currentState = KFalconryState.STARTING;
        }
        updateStateChangeTime();
    }

    private void catchKebbits(KFalconryConfig config) {
        // If player is already animating, wait and return
        if (Rs2Player.isAnimating()) {
            Microbot.status = "Animating...";
            return;
        }

        // First check if there's a falcon to retrieve
        NPC falcon = Rs2Npc.getNpc("Gyr Falcon");
        if (falcon != null) {
            Microbot.status = "Retrieving falcon";
            // Try to interact with the falcon
            if (Rs2Npc.interact(falcon.getName(), "Retrieve")) {
                Rs2Player.waitForAnimation();
                sleep(Rs2Random.between(800, 1200));
                kebbitsHunted++;
            }
            return;
        }

        // Find closest kebbit to hunt
        NPC kebbit = findClosestKebbit(config);
        if (kebbit != null) {
            currentTarget = kebbit;
            Microbot.status = "Catching " + kebbit.getName();

            // Walk to kebbit if needed
            if (Rs2Player.distanceTo(kebbit.getWorldLocation()) > 7) {
                Microbot.status = "Walking to kebbit";
                Rs2Walker.walkTo(kebbit.getWorldLocation());
                sleep(Rs2Random.between(600, 1200));
                return;
            }

            // Interact with kebbit
            if (Rs2Npc.interact(kebbit.getName(), "Catch")) {
                catchAttemptTime = System.currentTimeMillis();
                Microbot.status = "Waiting for animation";
                Rs2Player.waitForAnimation();
                sleep(Rs2Random.between(1000, 2000));
            } else {
                // If interaction fails, move a bit and try again
                Microbot.status = "Failed to interact, adjusting position";
                moveSlightly();
            }
        } else {
            // If no kebbit found, move to the kebbit area
            Microbot.status = "No kebbits found, repositioning";
            moveToKebbitsArea();
        }
    }

    private void checkAndRetrieveFalcon() {
        // Check if falcon is present
        NPC falcon = Rs2Npc.getNpc("Gyr Falcon");
        if (falcon == null) {
            // No falcon found, return to catching
            return;
        }

        // Try to interact with the falcon
        if (Rs2Npc.interact(falcon.getName(), "Retrieve")) {
            Rs2Player.waitForAnimation(3000);

            // Check if we got an item (successful catch)
            boolean inventoryChanged = Rs2Inventory.hasItem("kebbit fur") ||
                    Rs2Inventory.hasItem("spotted kebbit fur") ||
                    Rs2Inventory.hasItem("dark kebbit fur");

            if (inventoryChanged) {
                // Increment kebbit counter
                kebbitsHunted++;
            }

            // Short pause before looking for next kebbit
            sleep(Rs2Random.between(400, 800));
        }
    }

    private void moveSlightly() {
        WorldPoint movePoint = new WorldPoint(
                Rs2Player.getWorldLocation().getX() + Rs2Random.between(-3, 3),
                Rs2Player.getWorldLocation().getY() + Rs2Random.between(-3, 3),
                0
        );
        Rs2Walker.walkTo(movePoint);
        sleep(Rs2Random.between(800, 1500));
    }

    private void moveToKebbitsArea() {
        Microbot.status = "Moving to kebbit area";

        // Walk directly to the kebbit area center
        if (Rs2Walker.walkTo(KEBBIT_AREA_CENTER)) {
            sleep(Rs2Random.between(1000, 2000));
        } else {
            // If path finding fails, try a more direct approach
            Rs2Walker.walkTo(KEBBIT_AREA_CENTER);
            sleep(Rs2Random.between(2000, 3000));
        }
    }

    private void handleDroppingItems() {
        Microbot.status = "Dropping items";

        // Drop all kebbit fur and bones
        for (String itemName : HUNT_ITEMS) {
            if (Rs2Inventory.hasItem(itemName)) {
                Rs2Inventory.dropAll(itemName);
                sleep(Rs2Random.between(300, 600));
            }
        }

        currentState = KFalconryState.IDLE;
        currentTarget = null;
        updateStateChangeTime();
    }

    private void moveRandomCamera() {
        WorldPoint nearbyPoint = new WorldPoint(
                Rs2Player.getWorldLocation().getX() + Rs2Random.between(-10, 10),
                Rs2Player.getWorldLocation().getY() + Rs2Random.between(-10, 10),
                0
        );
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(), nearbyPoint);
        if (localPoint != null) {
            Rs2Camera.turnTo(localPoint);
        }
        sleep(Rs2Random.between(200, 800));
    }

    private void performAntibanAction() {
        // Simulate a player taking a short break
        int idleAction = Rs2Random.between(0, 3);
        int idleDuration = Rs2Random.between(2000, 5000);

        switch (idleAction) {
            case 0: // Move camera slightly
                moveRandomCamera();
                break;

            case 1: // Just wait
                // Do nothing, just sleep
                break;

            case 2: // Move to a nearby spot
                moveSlightly();
                break;

            case 3: // Look at stats
                Rs2Tab.switchToSkillsTab();
                break;
        }

        sleep(idleDuration);
        currentState = KFalconryState.IDLE;
        updateStateChangeTime();
    }

    public static List<Rs2NpcModel> findNearbyKebbits(KFalconryConfig config) {
        List<Rs2NpcModel> foundKebbits = new ArrayList<>();

        // Parse the config setting
        String targetKebbitsSetting = config.targetKebbits();

        // Check if we should prefer the closest kebbit regardless of type
        if (config.preferClosestKebbit()) {
            // Get all kebbit types
            Stream<Rs2NpcModel> allKebbits = Rs2Npc.getNpcs(npc ->
                    npc.getId() == SPOTTED_KEBBIT_ID ||
                            npc.getId() == DARK_KEBBIT_ID);

            // Return the closest kebbit if found
            return allKebbits
                    .sorted(Comparator.comparingInt(value ->
                            value.getLocalLocation().distanceTo(
                                    Microbot.getClient().getLocalPlayer().getLocalLocation())))
                    .collect(Collectors.toList());
        }

        // Filter kebbit types based on config
        if (targetKebbitsSetting.equals("All") || targetKebbitsSetting.contains("Spotted")) {
            Rs2Npc.getNpcs(npc -> npc.getId() == SPOTTED_KEBBIT_ID)
                    .forEach(foundKebbits::add);
        }

        if (targetKebbitsSetting.equals("All") || targetKebbitsSetting.contains("Dark")) {
            Rs2Npc.getNpcs(npc -> npc.getId() == DARK_KEBBIT_ID)
                    .forEach(foundKebbits::add);
        }

        return foundKebbits;
    }

    private NPC findClosestKebbit(KFalconryConfig config) {
        List<NPC> kebbits = null;

        // Based on config, get appropriate kebbits
        if ("All".equals(config.targetKebbits())) {
            kebbits = Rs2Npc.findNearbyNpc(new int[]{
                    DARK_KEBBIT_ID, SPOTTED_KEBBIT_ID, DASHING_KEBBIT_ID
            });
        } else if ("Dark".equals(config.targetKebbits())) {
            kebbits = Rs2Npc.findNearbyNpc(DARK_KEBBIT_ID);
        } else if ("Spotted".equals(config.targetKebbits())) {
            kebbits = Rs2Npc.findNearbyNpc(SPOTTED_KEBBIT_ID);
        } else {
            kebbits = Rs2Npc.findNearbyNpc(DASHING_KEBBIT_ID);
        }

        if (kebbits == null || kebbits.isEmpty()) {
            return null;
        }

        // Find closest kebbit
        NPC closest = null;
        int minDistance = Integer.MAX_VALUE;

        for (NPC kebbit : kebbits) {
            int distance = Rs2Player.distanceTo(kebbit.getWorldLocation());
            if (distance < minDistance) {
                minDistance = distance;
                closest = kebbit;
            }
        }

        return closest;
    }

    private void checkForStuckState() {
        long currentTime = System.currentTimeMillis();
        // If we've been in the same state for more than 30 seconds
        if (currentTime - lastStateChange > 30000) {
            stateStuckCount++;
            Microbot.status = "Potential stuck state detected: " + currentState;

            // After 3 stuck occurrences, try to recover
            if (stateStuckCount >= 3) {
                Microbot.log("Script appears stuck. Attempting recovery by resetting state.");
                currentState = KFalconryState.STARTING;
                currentTarget = null;
                stateStuckCount = 0;
                updateStateChangeTime();
            }
        }
    }

    private void handleLogout() {
        Microbot.status = "Logging out";

        // Click the logout button
        if (Rs2Tab.switchToLogout()) {
            sleep(1000);
            Rs2Widget.clickWidget("Logout");
            sleep(1000);
        }

        // Stop the script
        isActive = false;
    }

    private void updateStateChangeTime() {
        lastStateChange = System.currentTimeMillis();
        stateStuckCount = 0;
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.microBreakDurationLow = 1;
        Rs2AntibanSettings.microBreakDurationHigh = 3;
        Rs2AntibanSettings.actionCooldownChance = 0.4;
        Rs2AntibanSettings.microBreakChance = 0.15;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.1;
    }

    // Utility methods for overlay
    public static String getRuntime() {
        long elapsed = System.currentTimeMillis() - startTime;
        long hours = elapsed / 3600000;
        long minutes = (elapsed % 3600000) / 60000;
        long seconds = (elapsed % 60000) / 1000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static int getHunterLevel() {
        return Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
    }

    public static String getExperiencePerHour() {
        long timeElapsed = System.currentTimeMillis() - startTime;
        if (timeElapsed <= 0) return "0 xp/h";

        double hoursElapsed = timeElapsed / 3600000.0;
        int expGained = Microbot.getClient().getSkillExperience(Skill.HUNTER) - startExperience;
        int expPerHour = (int)(expGained / hoursElapsed);

        return expPerHour + " xp/h";
    }

    public static int getKebbitsPerHour() {
        long timeElapsed = System.currentTimeMillis() - startTime;
        if (timeElapsed <= 0) return 0;

        double hoursElapsed = timeElapsed / 3600000.0;
        int kebbitCount = kebbitsHunted;

        return (int)(kebbitCount / hoursElapsed);
    }

    public static String getTimeUntilLevel() {
        int currentExp = Microbot.getClient().getSkillExperience(Skill.HUNTER);
        int currentLevel = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
        int nextLevelExp = Experience.getXpForLevel(currentLevel + 1);
        int expNeeded = nextLevelExp - currentExp;

        long timeElapsed = System.currentTimeMillis() - startTime;
        if (timeElapsed <= 0) return "Unknown";

        int expGained = currentExp - startExperience;
        if (expGained <= 0) return "Unknown";

        double expPerMillis = expGained / (double)timeElapsed;
        long millisRemaining = (long)(expNeeded / expPerMillis);

        long hours = millisRemaining / 3600000;
        long minutes = (millisRemaining % 3600000) / 60000;

        return hours + "h " + minutes + "m";
    }

    public static int getKebbitsHunted() {
        return kebbitsHunted;
    }
}