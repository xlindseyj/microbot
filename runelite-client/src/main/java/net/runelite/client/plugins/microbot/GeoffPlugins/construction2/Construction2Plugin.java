package net.runelite.client.plugins.microbot.GeoffPlugins.construction2;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.GeoffPlugins.construction2.enums.Construction2State;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Kromite + "KConstruction",
        description = "Kromite's construction plugin.",
        tags = {"skilling", "kromite", "microbot", "construction", "poh", "house"},
        enabledByDefault = false
)
@Slf4j
public class Construction2Plugin extends Plugin {

    @Inject
    private Construction2Config config;

    @Provides
    Construction2Config provideConfig(ConfigManager configManager) {
        return configManager.getConfig(Construction2Config.class);
    }

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private Notifier notifier;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private Construction2Overlay construction2Overlay;

    private final Construction2Script construction2Script = new Construction2Script();

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts = false;
        Microbot.setClient(client);
        Microbot.setClientThread(clientThread);
        Microbot.setNotifier(notifier);
        Microbot.setMouse(new VirtualMouse());
        if (overlayManager != null) {
            overlayManager.add(construction2Overlay);
        }
        construction2Script.run(config);
    }

    @Override
    protected void shutDown() {
        construction2Script.shutdown();
        overlayManager.remove(construction2Overlay);
    }

    public Construction2State getState() {
        return construction2Script.getState();
    }

    public String getCurrentLevel() {
        return String.valueOf(client.getRealSkillLevel(Skill.CONSTRUCTION));
    }

//    public String getExperiencePerHour() {
//        return construction2Script.getExperiencePerHour();
//    }

    public String getCurrentExperience() {
        return String.valueOf(client.getSkillExperience(Skill.CONSTRUCTION));
    }

//    public String getNextLevelExperience() {
//        return String.valueOf(Skill.CONSTRUCTION.getExperienceForLevel(client.getRealSkillLevel(Skill.CONSTRUCTION) + 1));
//    }

    public String getNextLevel() {
        return String.valueOf(client.getRealSkillLevel(Skill.CONSTRUCTION) + 1);
    }

    public int getConstructionLevel() {
        return client.getRealSkillLevel(Skill.CONSTRUCTION);
    }

    public int getConstructionExperience() {
        return client.getSkillExperience(Skill.CONSTRUCTION);
    }
}
