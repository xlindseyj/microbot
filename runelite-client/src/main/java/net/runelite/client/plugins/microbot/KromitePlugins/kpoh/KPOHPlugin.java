package net.runelite.client.plugins.microbot.KromitePlugins.kpoh;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.KromitePlugins.kpoh.scripts.DefaultScript;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "Kromite's Gilded Altar",
        description = "Kromite's Player Owned House - Gilded Altar Plugin",
        tags = {"kromite", "kpoh", "microbot", "poh", "plugin"},
        enabledByDefault = false
)
@Slf4j
public class KPOHPlugin extends Plugin {
    public static String version = "1.0.0";

    @Inject
    private KPOHConfig config;

    @Provides
    KPOHConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KPOHConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private KPOHOverlay gildedAltarOverlay;

    @Inject
    DefaultScript kpohScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(gildedAltarOverlay);
        }
        kpohScript.run(config);
    }


    @Override
    protected void shutDown() {
        overlayManager.remove(gildedAltarOverlay);
        kpohScript.shutdown();
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE && chatMessage.getType() != ChatMessageType.SPAM) return;
        String chatMsg = chatMessage.getMessage().toLowerCase();
        if (
            chatMsg.contains("that player is offline") || chatMsg.contains("haven't visited anyone this session") || chatMsg.contains("house is no longer accessible")
        ) {
            kpohScript.visitedOnce = false;
            kpohScript.usePortal = null;
            kpohScript.altarCoords = null;
            kpohScript.portalCoords = null;
            kpohScript.addNameToBlackList();
        }
    }
}
