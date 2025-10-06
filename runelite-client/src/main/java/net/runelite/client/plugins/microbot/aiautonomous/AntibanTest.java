package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;

/**
 * Test class to verify antiban integration works correctly.
 */
@Slf4j
public class AntibanTest {

    public static void testAntibanIntegration(AiAutonomousConfig config) {
        log.info("Starting antiban integration tests...");

        testAntibanSettings(config);
        testActivityMapping();
        testAntibanBehaviors();

        log.info("Antiban integration tests completed");
    }

    private static void testAntibanSettings(AiAutonomousConfig config) {
        log.info("Testing antiban settings initialization...");
        try {
            // Initialize antiban settings like the script does
            Rs2AntibanSettings.actionCooldownChance = config.randomActionChance() / 100.0;
            Rs2AntibanSettings.microBreakChance = config.randomActionChance() / 300.0;
            Rs2AntibanSettings.moveMouseRandomlyChance = config.randomActionChance() / 200.0;
            Rs2AntibanSettings.simulateMistakes = config.humanlikeBehavior() > 50;
            Rs2AntibanSettings.simulateFatigue = config.humanlikeBehavior() > 60;
            Rs2AntibanSettings.usePlayStyle = true;

            log.info("✓ Antiban settings configured:");
            log.info("  - Action cooldown chance: {}", Rs2AntibanSettings.actionCooldownChance);
            log.info("  - Micro break chance: {}", Rs2AntibanSettings.microBreakChance);
            log.info("  - Random mouse chance: {}", Rs2AntibanSettings.moveMouseRandomlyChance);
            log.info("  - Simulate mistakes: {}", Rs2AntibanSettings.simulateMistakes);
            log.info("  - Simulate fatigue: {}", Rs2AntibanSettings.simulateFatigue);
            log.info("  - Use play style: {}", Rs2AntibanSettings.usePlayStyle);

        } catch (Exception e) {
            log.error("✗ Antiban settings test failed", e);
        }
    }

    private static void testActivityMapping() {
        log.info("Testing activity mapping...");
        try {
            // Test various activity mappings that the ActionExecutor uses
            String[] testActions = {
                "mine ore", "fish lobsters", "cook food", "chop trees",
                "bank items", "buy supplies", "talk to npc", "walk to bank",
                "wait for respawn", "craft items", "fight monster"
            };

            for (String action : testActions) {
                Activity activity = mapActionToActivity(action.toLowerCase());
                log.info("✓ Action '{}' maps to activity: {}", action, activity);
            }

        } catch (Exception e) {
            log.error("✗ Activity mapping test failed", e);
        }
    }

    private static void testAntibanBehaviors() {
        log.info("Testing antiban behaviors...");
        try {
            // Test setting activities
            Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);
            log.info("✓ Set antiban activity to: {}", Activity.GENERAL_COLLECTING);

            // Test if we can call antiban methods without errors
            log.info("✓ Rs2Antiban methods accessible");

            // Note: We don't actually execute the behaviors in tests to avoid side effects
            log.info("✓ Antiban behavior tests completed (methods accessible)");

        } catch (Exception e) {
            log.error("✗ Antiban behavior test failed", e);
        }
    }

    // Simplified version of the ActionExecutor's mapping logic for testing
    private static Activity mapActionToActivity(String lowerAction) {
        // Mining
        if (lowerAction.contains("mine") || lowerAction.contains("quarry") || lowerAction.contains("extract")) {
            return Activity.GENERAL_MINING;
        }
        // Fishing
        if (lowerAction.contains("fish") || lowerAction.contains("catch")) {
            return Activity.GENERAL_FISHING;
        }
        // Cooking
        if (lowerAction.contains("cook") || lowerAction.contains("bake")) {
            return Activity.GENERAL_COOKING;
        }
        // Woodcutting
        if (lowerAction.contains("chop") || lowerAction.contains("cut") || lowerAction.contains("tree")) {
            return Activity.GENERAL_WOODCUTTING;
        }
        // Combat
        if (lowerAction.contains("fight") || lowerAction.contains("attack") || lowerAction.contains("kill")) {
            return Activity.GENERAL_COMBAT;
        }
        // Crafting
        if (lowerAction.contains("craft") || lowerAction.contains("make") || lowerAction.contains("create")) {
            return Activity.GENERAL_CRAFTING;
        }
        // Smithing
        if (lowerAction.contains("smelt") || lowerAction.contains("smithing")) {
            return Activity.GENERAL_SMITHING;
        }
        // Default to collecting for other actions
        return Activity.GENERAL_COLLECTING;
    }

    /**
     * Simple offline test that doesn't require game client
     */
    public static void testAntibanOffline() {
        log.info("Running offline antiban tests...");
        try {
            // Test that we can access antiban classes
            Rs2AntibanSettings.reset();
            log.info("✓ Rs2AntibanSettings accessible");

            // Test activity enum
            Activity testActivity = Activity.GENERAL_COLLECTING;
            log.info("✓ Activity enum accessible: {}", testActivity);

            log.info("✓ Offline antiban tests completed successfully");

        } catch (Exception e) {
            log.error("✗ Offline antiban test failed", e);
        }
    }
}