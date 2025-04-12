package net.runelite.client.plugins.microbot.klooter;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.klooter.scripts.DefaultScript;
import net.runelite.client.plugins.microbot.klooter.scripts.ForestryScript;
import net.runelite.client.plugins.microbot.klooter.scripts.GrandExchangeScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "KLooter",
        description = "Kromite's Looter Plugin",
        tags = {"kromite", "klooter", "microbot", "looter", "plugin"},
        enabledByDefault = false
)
public class KLooterPlugin extends Plugin {
    public static double version = 1.0;
    @Inject
    DefaultScript defaultScript;
    @Inject
    ForestryScript forestryScript;
    @Inject
    GrandExchangeScript grandExchangeScript;
    @Inject
    private KLooterConfig config;
    @Inject
    private ConfigManager configManager;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private KLooterOverlay kLooterOverlay;

    @Provides
    KLooterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KLooterConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        
        switch (config.looterActivity()) {
            case DEFAULT:
                defaultScript.run(config);
                defaultScript.handleWalk(config);
                break;
            case FORESTRY:
                forestryScript.run(config);
                forestryScript.handleWalk(config);
                break;
            case GRAND_EXCHANGE:
                grandExchangeScript.run(config);
                grandExchangeScript.handleWalk(config);
                break;
        }
        
        if(overlayManager != null){
            overlayManager.add(kLooterOverlay);
        }
    }

    protected void shutDown() throws Exception {
        defaultScript.shutdown();
        overlayManager.remove(kLooterOverlay);
    }
}
