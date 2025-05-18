package net.runelite.client.plugins.microbot.KromitePlugins.kfalconry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class KFalconryScript extends Script {
    public static double version = 1.0;
    private static final int DARK_KEBBIT_ID = 23094;
    private static final int SPOTTED_KEBBIT_ID = 23097;
    private static final int DASHING_KEBBIT_ID = 23095; // Add other kebbit IDs as needed

    private static long startTime = 0;
    private static int startExperience = 0;
    private static AtomicInteger kebbitsHunted = new AtomicInteger(0);
    private State currentState = State.CATCH;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final WorldPoint FALCONRY_AREA_CENTER = new WorldPoint(2371, 3619, 0);
    private static final WorldPoint KEBBIT_AREA_CENTER = new WorldPoint(2371, 3599, 0);
    private static final int SEARCH_RADIUS = 15;

    // Bank location near falconry
    private static final WorldPoint BANK_LOCATION = new WorldPoint(2383, 3487, 0);

    // Available kebbits to hunt
    private int[] targetKebbits = {DARK_KEBBIT_ID, SPOTTED_KEBBIT_ID, DASHING_KEBBIT_ID};
    private NPC currentTarget = null;

    // Items to keep track of
    private static final List<String> HUNT_ITEMS = Arrays.asList("kebbit fur", "spotted kebbit fur", "dark kebbit fur", "bones");

    private enum State {
        CATCH,
        RETRIEVE,
        BANKING,
        DROPPING,
        IDLE
    }

    public boolean onStart() {
        startTime = System.currentTimeMillis();
        startExperience = Microbot.getClient().getSkillExperience(Skill.HUNTER);
        log.info("Starting Falconry script v" + version);
        applyAntiBanSettings();
        return true;
    }

    public boolean run(KFalconryConfig config) {
        // Use executor service
        executor.submit(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                // if (!isRunning()) return;

                // Check if we're in the falconry area
                if (Rs2Player.distanceTo(FALCONRY_AREA_CENTER) > 50 && currentState != State.BANKING) {
                    log.warn("Not in falconry area. Please teleport to the Piscatoris Hunter area.");
                    Microbot.status = "Not in falconry area";
                    return;
                }

                // Check player has a falcon
                if (Rs2Inventory.hasItem("Falconer's glove") || Rs2Player.hasPlayerEquippedItem(Rs2Player.getLocalPlayer(), "Falconer's glove")) {
                    log.info("Player has a falcon.");
                } else {
                    log.warn("Player does not have a falcon. Please obtain one.");
                    Microbot.status = "No falcon found";
                    return;
                }

                // Check if inventory is full
                if (Rs2Inventory.isFull() && currentState != State.BANKING && currentState != State.DROPPING) {
                    if (config.enableBanking()) {
                        currentState = State.BANKING;
                    } else {
                        currentState = State.DROPPING;
                    }
                }

                // Main script logic
                switch (currentState) {
                    case CATCH:
                        Microbot.status = "Catching kebbits";
                        handleCatchState();
                        break;

                    case RETRIEVE:
                        Microbot.status = "Retrieving falcon";
                        handleRetrieveState();
                        break;

                    case BANKING:
                        Microbot.status = "Banking items";
                        handleBankingState();
                        break;

                    case DROPPING:
                        Microbot.status = "Dropping items";
                        handleDroppingState();
                        break;

                    case IDLE:
                        Microbot.status = "Taking a short break";
                        performIdleBehavior();
                        break;
                }

                // Apply random camera movement occasionally
                if (Rs2Random.between(0, 100) < 5) {
                    // Fix for: Cannot resolve method 'turnTo(int, int)'
                    // Using a nearby location for camera movement instead
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

                // Random chance to take a micro break
                if (Rs2Random.between(0, 100) < 15 && config.enableBreaks()) {
                    currentState = State.IDLE;
                }

            } catch (Exception e) {
                log.error("Error in falconry script", e);
            }
        });
        return true;
    }

    private void handleCatchState() {
        // If player is already animating, wait
        if (Rs2Player.isAnimating()) {
            return;
        }

        // Find closest kebbit to hunt
        NPC kebbit = findClosestKebbit();
        if (kebbit != null) {
            currentTarget = kebbit;
            log.info("Found kebbit to hunt: " + kebbit.getName());

            // Walk to kebbit if needed
            if (Rs2Player.distanceTo(kebbit.getWorldLocation()) > 7) {
                Rs2Walker.walkTo(kebbit.getWorldLocation());
                sleep(Rs2Random.between(600, 1200));
            }

            // Interact with kebbit
            if (Rs2Npc.interact(kebbit.getName(), "Catch")) {
                Rs2Player.waitForAnimation();
                sleep(Rs2Random.between(600, 1200));
                currentState = State.RETRIEVE;
            }
        } else {
            // If no kebbit found, move to a random position in the KEBBIT area
            int newX = KEBBIT_AREA_CENTER.getX() + Rs2Random.between(-SEARCH_RADIUS, SEARCH_RADIUS);
            int newY = KEBBIT_AREA_CENTER.getY() + Rs2Random.between(-SEARCH_RADIUS, SEARCH_RADIUS);
            WorldPoint randomPoint = new WorldPoint(newX, newY, 0);

            // Walking directly to the point
            Microbot.status = "Walking to kebbit area";
            Rs2Walker.walkTo(randomPoint);
            sleep(Rs2Random.between(1500, 3000));
        }
    }

    private void handleRetrieveState() {
        // Wait for falcon to catch the kebbit
        sleep(Rs2Random.between(1200, 2000));

        // Check if falcon is present
        NPC falcon = Rs2Npc.getNpc("Gyr Falcon");
        if (falcon == null) {
            // If falcon is not found, return to catching
            currentState = State.CATCH;
            currentTarget = null;
            log.debug("Falcon not found, returning to catch state");
            return;
        } else {
            if (Rs2Npc.interact(falcon.getName(), "Retrieve")) {
                Rs2Player.waitForAnimation();

                // Increment kebbit counter
                kebbitsHunted.incrementAndGet();

                // Reset state and mark current target as null
                currentState = State.CATCH;
                currentTarget = null;

                // Short pause before looking for next kebbit
                sleep(Rs2Random.between(400, 800));
            } else {
                log.warn("Failed to retrieve kebbit. Trying again...");
                // If interaction fails, try again
                sleep(Rs2Random.between(1000, 2000));
            }
        }
    }

    private void handleBankingState() {
        // Walk to bank if not near bank
        if (Rs2Player.distanceTo(BANK_LOCATION) > 5) {
            Rs2Walker.walkTo(BANK_LOCATION);
            sleep(Rs2Random.between(1000, 2000));
            return;
        }

        // Open bank
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleep(Rs2Random.between(800, 1200));
            return;
        }

        // Deposit all items except falcon and bird snares
        Rs2Bank.depositAllExcept("Gyr Falcon", "Falconer's glove", "Bird snare", "Dramen staff", "Coins", "Graceful gloves", "Graceful hood", "Graceful cape", "Graceful top", "Graceful legs");

        sleep(Rs2Random.between(600, 1000));
        Rs2Bank.closeBank();
        sleep(Rs2Random.between(600, 1000));

        // Return to kebbit area
        Rs2Walker.walkTo(KEBBIT_AREA_CENTER);
        sleep(Rs2Random.between(2000, 3000));

        // Reset state
        currentState = State.CATCH;
    }

    private void handleDroppingState() {
        // Drop all kebbit fur and bones
        for (String itemName : HUNT_ITEMS) {
            Rs2Inventory.dropAll(itemName);
            sleep(Rs2Random.between(300, 600));
        }

        // Reset state
        currentState = State.CATCH;
        currentTarget = null;
    }

    private void performIdleBehavior() {
        // Simulate a player taking a short break
        int idleAction = Rs2Random.between(0, 3);
        int idleDuration = Rs2Random.between(3000, 15000);

        switch (idleAction) {
            case 0: // Move camera slightly
                WorldPoint nearPoint = Rs2Player.getWorldLocation();
                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(), nearPoint);
                if (localPoint != null) {
                    Rs2Camera.turnTo(localPoint);
                }
                sleep(idleDuration);
                break;
            case 1: // Just wait
                sleep(idleDuration);
                break;
            case 2: // Move to a nearby spot
                WorldPoint nearbyPoint = new WorldPoint(
                        Rs2Player.getWorldLocation().getX() + Rs2Random.between(-5, 5),
                        Rs2Player.getWorldLocation().getY() + Rs2Random.between(-5, 5),
                        0
                );
                Rs2Walker.walkTo(nearbyPoint);
                sleep(idleDuration);
                break;
            case 3: // Look at stats
                if (Rs2Random.between(0, 100) < 50) {
                    openSkillsTab();
                    sleep(idleDuration);
                } else {
                    sleep(idleDuration);
                }
                break;
        }

        // Return to catching
        currentState = State.CATCH;
    }

    private void openSkillsTab() {
        // Fix for: Cannot resolve method 'switchToStatsTab' in 'Rs2Tab'
        Rs2Tab.switchToSkillsTab();
        sleep(Rs2Random.between(500, 1500));
    }

    private NPC findClosestKebbit() {
        // find all dark kebbits
        Stream<Rs2NpcModel> darkKebbits = Rs2Npc.getNpcs(DARK_KEBBIT_ID);
        // find all spotted kebbits
        Stream<Rs2NpcModel> spottedKebbits = Rs2Npc.getNpcs(SPOTTED_KEBBIT_ID);
        // find all dashing kebbits
        Stream<Rs2NpcModel> dashingKebbits = Rs2Npc.getNpcs(DASHING_KEBBIT_ID);

        // Combine all kebbits into one list
        List<NPC> allKebbits = Stream.concat(darkKebbits, Stream.concat(spottedKebbits, dashingKebbits))
                .map(Rs2NpcModel::getRuneliteNpc)  // Use the proper getter method
                .filter(Objects::nonNull)      // Ensure no null NPCs
                .collect(Collectors.toList());

        // Find the closest kebbit to the PLAYER (not to the area center)
        if (allKebbits.isEmpty()) {
            return null;
        }

        return allKebbits.stream()
                .min(Comparator.comparingInt(npc ->
                        npc.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);
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
        int kebbitCount = kebbitsHunted.get();

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
        return kebbitsHunted.get();
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
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 15;
        Rs2AntibanSettings.actionCooldownChance = 0.4;
        Rs2AntibanSettings.microBreakChance = 0.15;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.1;
    }

    public void shutdown() {
        log.info("Falconry script stopped. Total kebbits hunted: " + kebbitsHunted.get());
        log.info("Total XP gained: " + (Microbot.getClient().getSkillExperience(Skill.HUNTER) - startExperience));
        executor.shutdown();
    }
}