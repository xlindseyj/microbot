package net.runelite.client.plugins.microbot.KromitePlugins.kpoh;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("KPlayerOwnedHouse")
public interface KPOHConfig extends Config {
    @ConfigItem(
        keyName = "instructions",
        name = "Instructions",
        description = "Instructions on how to use the script",
        position = 0
    )
    default String guide() {
        return "This currently only supports house advertisements. Use this script in w329 or w330.";
    }

    @ConfigItem(
        keyName = "hotkey",
        name = "Hotkey",
        description = "The hotkey to start/stop the script.",
        position = 1
    )
    default Keybind hotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "customhouse",
        name = "Custom House",
        description = "The house you want to use. Leave blank for automatic detection.",
        position = 2
    )
    default String customHouse() {
        return "";
    }
    
    @ConfigItem(
        keyName = "antiban",
        name = "Antiban",
        description = "Use antiban to avoid detection.",
        position = 3
    )
    default boolean antiban() {
        return true;
    }

    @ConfigItem(
        keyName = "debug",
        name = "Debug",
        description = "Enable debug mode.",
        position = 4
    )
    default boolean debug() {
        return false;
    }
}
