package net.runelite.client.plugins.KromitePlugins.kfalconry;

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
                    .text("Kromite's Falconry v" + KFalconryScript.version)
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
                    .right(KFalconryScript.getRuntime())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Hunter level:")
                    .right(String.valueOf(KFalconryScript.getHunterLevel()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP/Hour:")
                    .right(KFalconryScript.getExperiencePerHour())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kebbits/Hour:")
                    .right(String.valueOf(KFalconryScript.getKebbitsPerHour()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kebbits hunted:")
                    .right(String.valueOf(KFalconryScript.getKebbitsHunted()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time to level:")
                    .right(KFalconryScript.getTimeUntilLevel())
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }

        return super.render(graphics);
    }
}