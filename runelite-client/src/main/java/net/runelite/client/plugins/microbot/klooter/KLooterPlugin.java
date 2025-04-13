package net.runelite.client.plugins.microbot.klooter;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
// import net.runelite.client.plugins.microbot.klooter.scripts.BreakingScript;
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

    // @Inject
    // BreakingScript breakingScript;

    @Inject
    DefaultScript defaultScript;

    @Inject
    ForestryScript forestryScript;

    @Inject
    GrandExchangeScript grandExchangeScript;

    @Inject
    private KLooterConfig config;

    @Inject
    private KLooterOverlay kLooterOverlay;

    @Inject
    private OverlayManager overlayManager;

    @Override
    protected void startUp() throws AWTException {
        
        switch (config.looterActivity()) {
            // case BREAKING:
                // breakingScript.run(config);
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
        
        if (overlayManager != null){
            overlayManager.add(kLooterOverlay);
        }
    }

    protected void shutDown() throws Exception {
        defaultScript.shutdown();
        forestryScript.shutdown();
        grandExchangeScript.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(kLooterOverlay);
        }
    }
}
