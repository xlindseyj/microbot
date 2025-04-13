package net.runelite.client.plugins.microbot.klooter;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.klooter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.klooter.enums.LooterActivity;

import java.util.ArrayList;

@ConfigGroup("KLooter")
public interface KLooterConfig extends Config {
    // ArrayList<String> listOfItemsForLooting = new ArrayList<>();

    // Configuration Sections

    @ConfigSection(
            name = "General Settings",
            description = "Configure global plugin settings",
            position = 0
    )
    String generalSection = "Configure global plugin settings";

    @ConfigSection(
            name = "Looting Style",
            description = "Configure looting activity",
            position = 1,
            closedByDefault = true
    )
    String defaultSection = "Configure Looting Activity Settings";

    // Default - Configuration Items

    @ConfigItem(
            name = "Activity Type",
            keyName = "lootingActivity",
            position = 0,
            description = "Choose Looting Activity",
            section = generalSection
    )
    default LooterActivity looterActivity() {
        return LooterActivity.DEFAULT;
    }

    @Range(max = 28)
    @ConfigItem(
            name = "Min. free slots",
            keyName = "minFreeSlots",
            position = 1,
            description = "Minimum amount of slots",
            section = generalSection
    )
    default int minFreeSlots() {
        return 2;
    }

    @ConfigItem(
            keyName = "Hop",
            name = "Auto World Hop",
            description = "Auto Hop when no loot is found within 5 attempts or when player is detected",
            position = 2,
            section = generalSection
    )
    default boolean worldHop() {
        return false;
    }

    @ConfigItem(
            keyName = "useNextWorld",
            name = "Use Next World",
            description = "When enabled, it will hop to the next world, instead of using a random world",
            position = 3,
            section = generalSection
    )
    default boolean useNextWorld() {
        return false;
    }

    @ConfigItem(
            keyName = "takeBreaks",
            name = "Take Breaks",
            description = "When enabled, it will automatically take breaks",
            position = 4,
            section = generalSection
    )
    default boolean takeBreaks() { return false; }

    @ConfigItem(
            keyName = "breakTime",
            name = "Break Time",
            description = "Time to take a break in seconds",
            position = 5,
            section = generalSection
    )
    @Range(min = 1)
    default int breakTime() {
        return 5;
    }

    // Looting Style - Configuration Items

    @ConfigItem(
            name = "Loot Style",
            keyName = "lootStyle",
            position = 0,
            description = "Choose Looting Style",
            section = defaultSection
    )
    default DefaultLooterStyle looterStyle() {
        return DefaultLooterStyle.ITEM_LIST;
    }

    @ConfigItem(
            name = "Distance to Stray",
            keyName = "distanceToStray",
            position = 1,
            description = "Radius of tiles to stray/look for items",
            section = defaultSection
    )
    default int distanceToStray() {
        return 20;
    }

    @ConfigItem(
            name = "List of Items",
            keyName = "listOfItemsToLoot",
            position = 2,
            description = "List of items to loot",
            section = defaultSection
    )
    default String listOfItemsToLoot() {
        return "Logs,Oak logs,Willow logs,Maple logs,Magic logs,Willow seed,Oak seed,Maple seed,Magic seed,Red chinchompa,Black chinchompa,Red chinchompa bait,Black chinchompa bait,Ashes";
    }

    @ConfigItem(
            name = "Min. GE price of Item",
            keyName = "minGEPriceOfItem",
            position = 3,
            description = "Minimum GE price of item to loot",
            section = defaultSection
    )
    default int minPriceOfItem() {
        return 1000;
    }

    @ConfigItem(
            name = "Max GE price of Item",
            keyName = "maxGEPriceOfItem",
            position = 4,
            description = "Maximum GE price of item to loot",
            section = defaultSection
    )
    default int maxPriceOfItem() {
        return Integer.MAX_VALUE;
    }

    @ConfigItem(
            name = "Loot My Items Only",
            keyName = "lootMyItemsOnly",
            position = 5,
            description = "Toggles check for ownership of grounditem",
            section = defaultSection
    )
    default boolean toggleLootMyItemsOnly() {
        return false;
    }

    @ConfigItem(
            name = "Delayed Looting",
            keyName = "delayedLooting",
            position = 6,
            description = "Toggles Delayed Looting",
            section = defaultSection
    )
    default boolean toggleDelayedLooting() {
        return false;
    }
}
