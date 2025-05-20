package net.runelite.client.plugins.microbot.KromitePlugins.kfalconry;

import net.runelite.client.plugins.microbot.KromitePlugins.kfalconry.scripts.DefaultScript;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class KFalconryOverlay extends OverlayPanel {
    @Inject
    KFalconryOverlay(KFalconryPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Kromite's Falconry v" + DefaultScript.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(Microbot.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time running:")
                    .right(DefaultScript.getRuntime())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Hunter level:")
                    .right(String.valueOf(DefaultScript.getHunterLevel()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP/Hour:")
                    .right(DefaultScript.getExperiencePerHour())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kebbits/Hour:")
                    .right(String.valueOf(DefaultScript.getKebbitsPerHour()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kebbits hunted:")
                    .right(String.valueOf(DefaultScript.getKebbitsHunted()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time to level:")
                    .right(DefaultScript.getTimeUntilLevel())
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }

        return super.render(graphics);
    }
}