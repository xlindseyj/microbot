package net.runelite.client.plugins.microbot.kpoh;

import net.runelite.client.config.*;

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
