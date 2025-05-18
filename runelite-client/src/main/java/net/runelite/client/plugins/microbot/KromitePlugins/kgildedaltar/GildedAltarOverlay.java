package net.runelite.client.plugins.microbot.KromitePlugins.kgildedaltar;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class GildedAltarOverlay extends OverlayPanel {

    @Inject
    GildedAltarOverlay()
    {
        super();
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));

            panelComponent.getChildren().add(
                TitleComponent.builder()
                    .text("Gilded Altar")
                    .color(Color.GREEN)
                    .build()
            );

            panelComponent.getChildren().add(
                LineComponent.builder()
                    .left("Current Prayer Level: ")
                    .right(String.valueOf(GildedAltarScript.getPrayerLevel()))
                    .build()
            );

            panelComponent.getChildren().add(
                LineComponent.builder()
                    .left("Experience per hour: ")
                    .right(String.valueOf(GildedAltarScript.getExperiencePerHour(GildedAltarScript.startingExp, GildedAltarScript.currentExp)))
                    .build()
            );

            panelComponent.getChildren().add(
                LineComponent.builder()
                    .left("Bones per hour: ")
                    .right(String.valueOf(GildedAltarScript.getBonesPerHour(GildedAltarScript.startingBones, GildedAltarScript.currentBones)))
                    .build()
            );

            panelComponent.getChildren().add(
                LineComponent.builder()
                    .left("Time until level: ")
                    .right(String.valueOf(GildedAltarScript.getTimeUntilLevel(GildedAltarScript.startingLevel, GildedAltarScript.currentLevel)))
                    .build()
            );

            panelComponent.getChildren().add(
                LineComponent.builder()
                    .left("Runtime: ")
                    .right(String.valueOf(GildedAltarScript.getRuntime(GildedAltarScript.startTime)))
                    .build()
            );

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status: ")
                    .right(GildedAltarScript.state.toString())
                    .build());

        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
