package net.runelite.client.plugins.KromitePlugins.kpoh;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("KPlayerOwnedHouse")
public interface KPOHConfig extends Config {
    @ConfigItem(
            keyName = "Guide",
            name = "How to use",
            description = "How to use the script",
            position = 0
    )
    default String GUIDE() {
            return "This only supports house advertisements. Use this script in w330";
        }
}
