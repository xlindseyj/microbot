package net.runelite.client.plugins.microbot.klooter.scripts;

import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.klooter.KLooterConfig;
import net.runelite.client.plugins.microbot.klooter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.klooter.enums.LooterState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GrandExchangeScript extends Script {

    LooterState state = LooterState.LOOTING;
    boolean lootExists;
    int failedLootAttempts = 0;
    // tracks if currently walking to an item location to loot
    boolean isWalkingToLoot = false;
    // Constant for the local loot scan radius
    private static final int LOCAL_LOOT_RADIUS = 3;

    public boolean run(KLooterConfig config) {
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn() || Rs2Combat.inCombat()) return;
                if (Microbot.pauseAllScripts) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                // Check if currently walking to loot an item; if so, wait until area is cleared before scanning for new loot.
                if (isWalkingToLoot) {
                    // Use the helper method with a fixed small radius
                    if (checkLootExists(config, LOCAL_LOOT_RADIUS)) {
                        // Still waiting on the original loot area to be cleared, skip scanning.
                        return;
                    } else {
                        // Loot area cleared, reset walking flag.
                        isWalkingToLoot = false;
                    }
                }

                long startTime = System.currentTimeMillis();

                // Cache current player location
                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                // check if player is at grand exchange, if so, set loot radius to 50
                // else, navigate to grand exchange, and set loot radius to 50
                if (Rs2Player.getWorldLocation().getRegionID() == 10284) {
                    // config.setDistanceToStray(50);
                } else {
                    // Rs2Walker.walkTo();
                    // config.setDistanceToStray(50);
                }

                switch (state) {
                    case LOOTING:
                        if (config.worldHop()) {
                            // Use the helper method to check for loot using the configured distance
                            lootExists = checkLootExists(config, config.distanceToStray());
                        } else {
                            lootExists = true;
                        }

                        if (lootExists) {
                            failedLootAttempts = 0;
                            // Set the walking flag to true as a loot action is now in progress.
                            isWalkingToLoot = true;

                            // Perform the actual looting using a dedicated method
                            performLooting(config);

                            Microbot.pauseAllScripts = false;
                            Rs2Antiban.actionCooldown();
                            Rs2Antiban.takeMicroBreakByChance();
                        } else {
                            failedLootAttempts++; // No items found, increment failure count

                            if (failedLootAttempts >= 5) { // Hop worlds after 5 failed attempts
                                Microbot.log("Failed to find loot 5 times, hopping worlds...");
                                int worldNumber = config.useNextWorld() ? Login.getNextWorld(Rs2Player.isMember()) : Login.getRandomWorld(Rs2Player.isMember());
                                Microbot.hopToWorld(worldNumber);
                                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
                                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
                                failedLootAttempts = 0; // Reset failure count after hopping
                                return;
                            }
                        }

                        if (Rs2Inventory.getEmptySlots() <= config.minFreeSlots()) {
                            state = LooterState.BANKING;
                            return;
                        }
                        break;
                    case BANKING:
                        if (Rs2Inventory.getEmptySlots() <= config.minFreeSlots()) return;
                        state = LooterState.LOOTING;
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                Microbot.log("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.log("Error in DefaultScript: " + ex.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown(){
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    public boolean handleWalk(KLooterConfig config) {
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (Microbot.pauseAllScripts) return;
                if (initialPlayerLocation == null) return;

                if (state == LooterState.LOOTING) {
                    // Cache the current location to avoid multiple calls
                    if (Rs2Player.getWorldLocation().distanceTo(initialPlayerLocation) > config.distanceToStray()) {
                        Rs2Walker.walkTo(initialPlayerLocation);
                    }
                    return;
                }

                if (state == LooterState.BANKING) {
                    // Perform banking and then walk back to original position using a helper method
                    performBanking(config);
                    return;
                }
            } catch (Exception ex) {
                Microbot.log("Error in handleWalk: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Checks if loot exists based on the looter style using a provided scan radius.
     *
     * @param config the configuration object
     * @param scanRadius the radius within which to check for loot
     * @return true if loot is detected, false otherwise
     */
    private boolean checkLootExists(KLooterConfig config, int scanRadius) {
        boolean exists = false;
        if (config.looterStyle() == DefaultLooterStyle.ITEM_LIST) {
            exists = Arrays.stream(config.listOfItemsToLoot().trim().split(","))
                    .anyMatch(itemName -> Rs2GroundItem.exists(itemName, scanRadius));
        } else if (config.looterStyle() == DefaultLooterStyle.GE_PRICE_RANGE) {
            exists = Rs2GroundItem.isItemBasedOnValueOnGround(config.minPriceOfItem(), scanRadius);
        } else if (config.looterStyle() == DefaultLooterStyle.MIXED) {
            exists = Arrays.stream(config.listOfItemsToLoot().trim().split(","))
                    .anyMatch(itemName -> Rs2GroundItem.exists(itemName, scanRadius)) ||
                    Rs2GroundItem.isItemBasedOnValueOnGround(config.minPriceOfItem(), scanRadius);
        }
        return exists;
    }

    /**
     * Performs looting based on the configured looter style.
     *
     * This method encapsulates both item list and GE price range looting logic.
     *
     * @param config the configuration object
     */
    private void performLooting(KLooterConfig config) {
        if (config.looterStyle() == DefaultLooterStyle.ITEM_LIST || config.looterStyle() == DefaultLooterStyle.MIXED) {
            LootingParameters itemLootParams = new LootingParameters(
                    config.distanceToStray(),
                    1,
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleLootMyItemsOnly(),
                    config.listOfItemsToLoot().split(",")
            );
            Rs2GroundItem.lootItemsBasedOnNames(itemLootParams);
        }
        if (config.looterStyle() == DefaultLooterStyle.GE_PRICE_RANGE || config.looterStyle() == DefaultLooterStyle.MIXED) {
            LootingParameters valueParams = new LootingParameters(
                    config.minPriceOfItem(),
                    config.maxPriceOfItem(),
                    config.distanceToStray(),
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleLootMyItemsOnly()
            );
            Rs2GroundItem.lootItemBasedOnValue(valueParams);
        }
    }

    /**
     * Performs banking and walks back to the original player location.
     *
     * @param config the configuration object
     */
    private void performBanking(KLooterConfig config) {
        if (config.looterStyle() == DefaultLooterStyle.ITEM_LIST) {
            Rs2Bank.bankItemsAndWalkBackToOriginalPosition(
                    Arrays.stream(config.listOfItemsToLoot().trim().split(","))
                            .collect(Collectors.toList()),
                    initialPlayerLocation,
                    config.minFreeSlots()
            );
        } else {
            Rs2Bank.bankItemsAndWalkBackToOriginalPosition(
                    Rs2Inventory.all().stream().map(Rs2ItemModel::getName)
                            .collect(Collectors.toList()),
                    initialPlayerLocation,
                    config.minFreeSlots()
            );
        }
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
}
