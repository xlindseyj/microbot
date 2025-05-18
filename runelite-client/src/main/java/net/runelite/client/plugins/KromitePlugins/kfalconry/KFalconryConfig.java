package net.runelite.client.plugins.KromitePlugins.kfalconry;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

import java.awt.event.KeyEvent;

@ConfigGroup("kfalconry")
public interface KFalconryConfig extends Config {

    @ConfigItem(
            keyName = "hotkey",
            name = "Toggle Hotkey",
            description = "Hotkey to toggle the script on and off",
            position = 0
    )
    default Keybind hotkey() {
        return new Keybind(KeyEvent.VK_F1, 0);
    }

    @ConfigItem(
            keyName = "enableBreaks",
            name = "Enable Random Breaks",
            description = "Enable random breaks during the script execution",
            position = 1
    )
    default boolean enableBreaks() {
        return true;
    }

    @ConfigItem(
            keyName = "enableBanking",
            name = "Enable Banking",
            description = "If enabled, will bank items when inventory is full. If disabled, drops items.",
            position = 2
    )
    default boolean enableBanking() {
        return false;
    }

    @ConfigItem(
            keyName = "targetKebbits",
            name = "Target Kebbits",
            description = "Choose which kebbits to hunt (Dark, Spotted, Dashing)",
            position = 3
    )
    default String targetKebbits() {
        return "All";
    }

    @ConfigItem(
            keyName = "preferClosestKebbit",
            name = "Prefer Closest Kebbit",
            description = "Always target the closest kebbit regardless of type",
            position = 4
    )
    default boolean preferClosestKebbit() {
        return true;
    }
}