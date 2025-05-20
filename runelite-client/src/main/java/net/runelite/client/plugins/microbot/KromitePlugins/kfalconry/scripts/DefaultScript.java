package net.runelite.client.plugins.microbot.KromitePlugins.kfalconry.scripts;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.KromitePlugins.kfalconry.KFalconryConfig;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class DefaultScript extends Script {

        public static double version = 1.1;
        private static final int DARK_KEBBIT_ID = 23094;
        private static final int SPOTTED_KEBBIT_ID = 23097;
        private static final int DASHING_KEBBIT_ID = 23095;

        private static long startTime = 0;
        private static int startExperience = 0;
        private static AtomicInteger kebbitsHunted = new AtomicInteger(0);
        private State currentState = State.CATCH;
        private Logger log = Logger.getLogger(DefaultScript.class.getName());
        private ExecutorService executor = Executors.newSingleThreadExecutor();
        private AtomicBoolean isRunning = new AtomicBoolean(false);
        private boolean scriptStopped = false;
        private long lastStateChange = 0;
        private int stateStuckCount = 0;

        // Updated coordinates for the Falconry area
        private static final WorldPoint FALCONRY_AREA_CENTER = new WorldPoint(2371, 3619, 0);

        // Updated coordinates for the Kebbit hunting area
        private static final WorldPoint KEBBIT_AREA_CENTER = new WorldPoint(2371, 3599, 0);
        private static final int SEARCH_RADIUS = 15;

        // Bank location near falconry
        private static final WorldPoint BANK_LOCATION = new WorldPoint(2383, 3487, 0);

        private NPC currentTarget = null;
        private long catchAttemptTime = 0;
        private static final long CATCH_TIMEOUT = 15000; // 15 seconds timeout for catch attempts

        // Items to keep track of
        private static final List<String> HUNT_ITEMS = Arrays.asList("kebbit fur", "spotted kebbit fur", "dark kebbit fur", "bones");

        private enum State {
            CATCH,
            RETRIEVE,
            BANKING,
            DROPPING,
            IDLE,
            MOVE_TO_AREA
        }

        public boolean onStart() {
            startTime = System.currentTimeMillis();
            startExperience = Microbot.getClient().getSkillExperience(Skill.HUNTER);
            kebbitsHunted.set(0);
            currentState = State.CATCH;
            lastStateChange = System.currentTimeMillis();
            log.info("Starting Falconry script v" + version);
            applyAntiBanSettings();
            isRunning.set(true);
            scriptStopped = false;
            return true;
        }

        public boolean run(KFalconryConfig config) {
            scriptStopped = false;

            if (startTime == 0) {
                startTime = System.currentTimeMillis();
            }

            if (startExperience == 0) {
                startExperience = Microbot.getClient().getSkillExperience(Skill.HUNTER);
            }

            if (kebbitsHunted.get() == 0) {
                kebbitsHunted.set(0);
            }

            if (currentState == null) {
                currentState = State.CATCH;
            }

            if (catchAttemptTime == 0) {
                catchAttemptTime = System.currentTimeMillis();
            }

            if (!isRunning.get()) {
                return false;
            }

            executor.submit(() -> {
                try {
                    if (!Microbot.isLoggedIn() || !isRunning.get()) return;

                    // Check for stuck states
                    checkForStuckState();

                    // Check if we're in the falconry area
                    if (Rs2Player.distanceTo(FALCONRY_AREA_CENTER) > 50 && currentState != State.BANKING) {
                        log.info("Not in falconry area. Please teleport to the Piscatoris Hunter area.");
                        Microbot.status = "Not in falconry area";
                        sleep(2000);
                        return;
                    }

                    // Check player has falcon equipment
                    if (!Rs2Inventory.hasItem("Falconer's glove") && !Rs2Player.hasPlayerEquippedItem(Rs2Player.getLocalPlayer(), "Falconer's glove")) {
                        log.info("Player does not have a falcon. Please obtain one.");
                        Microbot.status = "No falcon found";
                        sleep(2000);
                        return;
                    }

                    // Check if catch attempt is taking too long
                    if (currentState == State.RETRIEVE && System.currentTimeMillis() - catchAttemptTime > CATCH_TIMEOUT) {
                        log.info("Catch attempt timed out. Resetting state.");
                        currentTarget = null;
                        currentState = State.CATCH;
                        updateStateChangeTime();
                    }

                    // Check if inventory is full
                    if (Rs2Inventory.isFull() && currentState != State.BANKING && currentState != State.DROPPING) {
                        if (config.enableBanking()) {
                            currentState = State.BANKING;
                        } else {
                            currentState = State.DROPPING;
                        }
                        updateStateChangeTime();
                    }

                    // Main script logic
                    // In your run() method where the switch statement is
                    switch (currentState) {
                        case CATCH:
                            handleCatchState(config);
                            break;
                        case RETRIEVE:
                            handleRetrieveState();
                            break;
                        case BANKING:
                            handleBankingState();
                            break;
                        case DROPPING:
                            handleDroppingState();
                            break;
                        case IDLE:
                            performIdleBehavior();
                            break;
                        case MOVE_TO_AREA:
                            moveToKebbitsArea();
                            break;
                    }

                    // Apply random camera movement occasionally
                    if (Rs2Random.between(0, 100) < 5) {
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
                    if (Rs2Random.between(0, 1000) < 15 && config.enableBreaks() && currentState == State.CATCH) {
                        currentState = State.IDLE;
                        updateStateChangeTime();
                    }

                    // Add a sleep to prevent too many CPU cycles
                    sleep(300 + Rs2Random.between(0, 300));

                } catch (Exception e) {
                    log.info("Error in falconry script" + e.toString());
                    sleep(1000);
                }
            });
            return true;
        }

        private void handleCatchState(KFalconryConfig config) {
            // If player is already animating, wait
            if (Rs2Player.isAnimating()) {
                return;
            }

            // Check if we're in the kebbit area
            if (Rs2Player.distanceTo(KEBBIT_AREA_CENTER) > SEARCH_RADIUS) {
                log.info("Moving closer to kebbit area");
                currentState = State.MOVE_TO_AREA;
                updateStateChangeTime();
                return;
            }

            // Find closest kebbit to hunt
            NPC kebbit = findClosestKebbit(config);
            if (kebbit != null) {
                currentTarget = kebbit;

                // Walk to kebbit if needed
                if (Rs2Player.distanceTo(kebbit.getWorldLocation()) > 7) {
                    Rs2Walker.walkTo(kebbit.getWorldLocation());
                    sleep(Rs2Random.between(600, 1200));
                    return;
                }

                // Interact with kebbit
                if (Rs2Npc.interact(kebbit.getName(), "Catch")) {
                    catchAttemptTime = System.currentTimeMillis();
                    Rs2Player.waitForAnimation(3000);
                    sleep(Rs2Random.between(600, 1200));
                    // If we catch the kebbit, move to retrieve state
                    currentTarget = kebbit;
                    Rs2Camera.turnTo(kebbit);
                    Rs2Npc.interact(kebbit.getName(), "Catch");
                    sleep(Rs2Random.between(600, 1200));

                    // Check if we caught the kebbit
                    // check the message box for success
                    if (Rs2Player.getAnimation() == -1) {
                        kebbitsHunted.incrementAndGet();
                        currentState = State.RETRIEVE;
                        updateStateChangeTime();
                    } else {
                        // If we failed to catch, reset target
                        currentTarget = null;
                        currentState = State.CATCH;
                        updateStateChangeTime();
                    }
                } else {
                    // If interaction fails, move a bit and try again
                    WorldPoint movePoint = new WorldPoint(
                            Rs2Player.getWorldLocation().getX() + Rs2Random.between(-3, 3),
                            Rs2Player.getWorldLocation().getY() + Rs2Random.between(-3, 3),
                            0
                    );
                    Rs2Walker.walkTo(movePoint);
                    sleep(Rs2Random.between(800, 1500));
                }
            } else {
                // If no kebbit found, move to a random position in the KEBBIT area
                currentState = State.MOVE_TO_AREA;
                updateStateChangeTime();
            }
        }

        private void moveToKebbitsArea() {
            // Log the current position
            log.info("Moving to kebbit area from: " + Rs2Player.getWorldLocation());

            // Walk directly to the kebbit area center
            if (Rs2Walker.walkTo(KEBBIT_AREA_CENTER)) {
                log.info("Walking to kebbit area center: " + KEBBIT_AREA_CENTER);
                sleep(Rs2Random.between(1000, 2000));
            } else {
                // If path finding fails, try a more direct approach
                log.info("Path finding failed, trying direct movement");
                Rs2Walker.walkTo(KEBBIT_AREA_CENTER);
                sleep(Rs2Random.between(2000, 3000));
            }

            // Once we're close enough to the kebbit area, switch back to CATCH state
            if (Rs2Player.distanceTo(KEBBIT_AREA_CENTER) <= SEARCH_RADIUS) {
                log.info("Reached kebbit area, switching to CATCH state");
                currentState = State.CATCH;
                updateStateChangeTime();
            } else {
                // If we're still not close enough, stay in MOVE_TO_AREA state
                log.info("Still moving to kebbit area, distance: " + Rs2Player.distanceTo(KEBBIT_AREA_CENTER));
            }
        }

        private void handleRetrieveState() {
            // Wait for falcon to catch the kebbit
            sleep(Rs2Random.between(1000, 1500));

            // Check if falcon is present
            NPC falcon = Rs2Npc.getNpc("Gyr Falcon");
            if (falcon == null) {
                // If falcon is not found after a short wait, return to catching
                currentState = State.CATCH;
                currentTarget = null;
                updateStateChangeTime();
                return;
            }

            // Try to interact with the falcon
            if (Rs2Npc.interact(falcon.getName(), "Retrieve")) {
                Rs2Player.waitForAnimation(3000);

                // If inventory full, check if we got an item (successful catch)
                boolean inventoryChanged = Rs2Inventory.isFull() || Rs2Inventory.hasItem("kebbit fur") ||
                        Rs2Inventory.hasItem("spotted kebbit fur") ||
                        Rs2Inventory.hasItem("dark kebbit fur");

                if (inventoryChanged) {
                    // Increment kebbit counter
                    kebbitsHunted.incrementAndGet();
                }

                // Reset state and mark current target as null
                currentState = State.CATCH;
                currentTarget = null;
                updateStateChangeTime();

                // Short pause before looking for next kebbit
                sleep(Rs2Random.between(400, 800));
            } else {
                log.info("Failed to retrieve kebbit. Trying again...");
                // If interaction fails, try again
                sleep(Rs2Random.between(1000, 2000));

                // After a few tries, reset to catch state
                if (System.currentTimeMillis() - catchAttemptTime > 8000) {
                    currentState = State.CATCH;
                    currentTarget = null;
                    updateStateChangeTime();
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

            // Deposit all items except falcon and essential items
            log.info("Depositing items");
            Rs2Bank.depositAllExcept("Gyr Falcon", "Falconer's glove", "Bird snare", "Dramen staff",
                    "Coins", "Graceful gloves", "Graceful hood", "Graceful cape",
                    "Graceful top", "Graceful legs");

            sleep(Rs2Random.between(600, 1000));
            Rs2Bank.closeBank();
            sleep(Rs2Random.between(600, 1000));

            // Return to kebbit area
            log.info("Returning to kebbit area");
            Rs2Walker.walkTo(KEBBIT_AREA_CENTER);
            sleep(Rs2Random.between(2000, 3000));

            // Reset state
            currentState = State.CATCH;
            updateStateChangeTime();
        }

        private void handleDroppingState() {
            log.info("Dropping items");

            // Drop all kebbit fur and bones
            for (String itemName : HUNT_ITEMS) {
                if (Rs2Inventory.hasItem(itemName)) {
                    Rs2Inventory.dropAll(itemName);
                    sleep(Rs2Random.between(300, 600));
                }
            }

            // Reset state
            currentState = State.CATCH;
            currentTarget = null;
            updateStateChangeTime();
        }

        private void performIdleBehavior() {
            // Simulate a player taking a short break
            int idleAction = Rs2Random.between(0, 3);
            int idleDuration = Rs2Random.between(3000, 8000);  // Shorter break duration

            switch (idleAction) {
                case 0: // Move camera slightly
                    WorldPoint nearPoint = Rs2Player.getWorldLocation();
                    LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(), nearPoint);
                    if (localPoint != null) {
                        Rs2Camera.turnTo(localPoint);
                    }
                    break;

                case 1: // Just wait
                    // Do nothing, just sleep
                    break;

                case 2: // Move to a nearby spot
                    WorldPoint nearbyPoint = new WorldPoint(
                            Rs2Player.getWorldLocation().getX() + Rs2Random.between(-3, 3),
                            Rs2Player.getWorldLocation().getY() + Rs2Random.between(-3, 3),
                            0
                    );
                    Rs2Walker.walkTo(nearbyPoint);
                    break;

                case 3: // Look at stats
                    Rs2Tab.switchToSkillsTab();
                    break;
            }

            sleep(idleDuration);

            // Return to catching
            currentState = State.CATCH;
            updateStateChangeTime();
        }

        private NPC findClosestKebbit(KFalconryConfig config) {
            // Get all kebbits
            Stream<Rs2NpcModel> darkKebbits = Rs2Npc.getNpcs(DARK_KEBBIT_ID);
            Stream<Rs2NpcModel> spottedKebbits = Rs2Npc.getNpcs(SPOTTED_KEBBIT_ID);
            Stream<Rs2NpcModel> dashingKebbits = Rs2Npc.getNpcs(DASHING_KEBBIT_ID);

            // Combine all kebbits into one list
            List<NPC> allKebbits = Stream.concat(darkKebbits, Stream.concat(spottedKebbits, dashingKebbits))
                    .map(Rs2NpcModel::getNpc)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (allKebbits.isEmpty()) {
                return null;
            }

            // Find closest kebbit
            NPC closestKebbit = allKebbits.stream()
                    .min(Comparator.comparingInt(npc ->
                            npc.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                    .orElse(null);

            return closestKebbit;
        }

        private void checkForStuckState() {
            long currentTime = System.currentTimeMillis();
            // If we've been in the same state for more than 30 seconds
            if (currentTime - lastStateChange > 30000) {
                stateStuckCount++;

                // After 3 stuck occurrences, try to recover
                if (stateStuckCount >= 3) {
                    log.info("Script appears stuck. Attempting recovery by resetting state.");
                    currentState = State.CATCH;
                    currentTarget = null;
                    stateStuckCount = 0;
                    updateStateChangeTime();
                }
            }
        }

        private void updateStateChangeTime() {
            lastStateChange = System.currentTimeMillis();
            stateStuckCount = 0;
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
            Rs2AntibanSettings.actionCooldownChance = 0.3;
            Rs2AntibanSettings.microBreakChance = 0.05;
            Rs2AntibanSettings.moveMouseRandomlyChance = 0.05;
        }

        public void shutdown() {
            log.info("Falconry script stopped. Total kebbits hunted: " + kebbitsHunted.get());
            log.info("Total XP gained: " + (Microbot.getClient().getSkillExperience(Skill.HUNTER) - startExperience));
            isRunning.set(false);
            scriptStopped = true;

            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
            }
        }
}
