package net.runelite.client.plugins.microbot.KromitePlugins.kpoh;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.*;

@ConfigGroup("KPlayerOwnedHouse")
public interface KPOHConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General settings for the script",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Bones",
            description = "Bone selection and purchasing options",
            position = 1,
            closedByDefault = false
    )
    String bonesSection = "bones";

    @ConfigSection(
            name = "Behavior",
            description = "Script behavior settings",
            position = 2,
            closedByDefault = false
    )
    String behaviorSection = "behavior";

    @ConfigSection(
            name = "Display",
            description = "Display and debugging settings",
            position = 3,
            closedByDefault = true
    )
    String displaySection = "display";

    // General Section
    @ConfigItem(
            keyName = "instructions",
            name = "Instructions",
            description = "Instructions on how to use the script",
            position = 0,
            section = generalSection
    )
    default String guide() {
        return "This currently only supports house advertisements. Use this script in w329 or w330.";
    }

    @ConfigItem(
            keyName = "hotkey",
            name = "Hotkey",
            description = "The hotkey to start/stop the script.",
            position = 1,
            section = generalSection
    )
    default Keybind hotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "customhouse",
            name = "Custom House",
            description = "The house you want to use. Leave blank for automatic detection.",
            position = 2,
            section = generalSection
    )
    default String customHouse() {
        return "";
    }

    // Bones Section
    @ConfigItem(
            keyName = "boneType",
            name = "Bone Type",
            description = "Select which bones to use at the altar",
            position = 0,
            section = bonesSection
    )
    default int boneType() {
        return ItemID.LAVA_DRAGON_BONES;
    }

    @ConfigItem(
            keyName = "restockGE",
            name = "Restock Bones",
            description = "Restock bones from the Grand Exchange when out",
            position = 1,
            section = bonesSection
    )
    default boolean restockGE() {
        return true;
    }

    @ConfigItem(
            keyName = "maxBonePrice",
            name = "Max Bone Price",
            description = "Maximum price to pay per bone (0 = no limit)",
            position = 2,
            section = bonesSection
    )
    default int maxBonePrice() {
        return 0;
    }

    @ConfigItem(
            keyName = "maxBoneQuantity",
            name = "Max Quantity",
            description = "Maximum number of bones to buy (0 = no limit)",
            position = 3,
            section = bonesSection
    )
    default int maxBoneQuantity() {
        return 0;
    }

    // Behavior Section
    @ConfigItem(
            keyName = "antiban",
            name = "Antiban",
            description = "Use antiban to avoid detection.",
            position = 0,
            section = behaviorSection
    )
    default boolean antiban() {
        return true;
    }

    @ConfigItem(
            keyName = "logoutWhenDone",
            name = "Logout When Done",
            description = "Logout when out of bones",
            position = 1,
            section = behaviorSection
    )
    default boolean logoutWhenDone() {
        return true;
    }

    // Display Section
    @ConfigItem(
            keyName = "showPaint",
            name = "Show Paint",
            description = "Show the paint overlay with status information",
            position = 0,
            section = displaySection
    )
    default boolean showPaint() {
        return true;
    }

    @ConfigItem(
            keyName = "debug",
            name = "Debug",
            description = "Enable debug mode for detailed logging",
            position = 1,
            section = displaySection
    )
    default boolean debug() {
        return false;
    }
}