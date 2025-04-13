package net.runelite.client.plugins.microbot.klooter.scripts;

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
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BreakingScript extends Script {
    LooterState state = LooterState.LOOTING;
    boolean lootExists;
    int failedLootAttempts = 0;

    public boolean run(KLooterConfig config) {
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run(config)) return;
                if (!Microbot.isLoggedIn() || Rs2Combat.inCombat()) return;
                if (Microbot.pauseAllScripts) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                long startTime = System.currentTimeMillis();

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                if (state == LooterState.BREAKING) {
                    if (Microbot.isLoggedIn() && !Rs2Combat.inCombat()) {
                        // break for the config break time
                        if (System.currentTimeMillis() - startTime > config.breakTime()) {
                            Rs2AntibanSettings.actionCooldownActive = false;
                            Rs2AntibanSettings.actionCooldownChance = 0.4;
                            Rs2AntibanSettings.microBreakDurationLow = 3;
                            Rs2AntibanSettings.microBreakDurationHigh = 15;
                            Rs2AntibanSettings.microBreakChance = 0.15;
                            Rs2AntibanSettings.moveMouseRandomlyChance = 0.1;
                            state = LooterState.LOOTING;
                        }
                    }
                    return;
                }
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

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
                    if (Rs2Player.getWorldLocation().distanceTo(initialPlayerLocation) > config.distanceToStray()) {
                        Rs2Walker.walkTo(initialPlayerLocation);
                    }
                    return;
                }

                if (state == LooterState.BANKING) {
                    if (config.looterStyle() == DefaultLooterStyle.ITEM_LIST) {
                        Rs2Bank.bankItemsAndWalkBackToOriginalPosition(Arrays.stream(config.listOfItemsToLoot().trim().split(",")).collect(Collectors.toList()), initialPlayerLocation, config.minFreeSlots());
                    } else {
                        Rs2Bank.bankItemsAndWalkBackToOriginalPosition(Rs2Inventory.all().stream().map(Rs2ItemModel::getName).collect(Collectors.toList()), initialPlayerLocation, config.minFreeSlots());
                    }
                    return;
                }
            } catch (Exception ex) {
                Microbot.log("Error in handleWalk: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
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
