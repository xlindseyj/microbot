package net.runelite.client.plugins.microbot.GeoffPlugins.construction2;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class Construction2Overlay extends OverlayPanel {

    private final Construction2Plugin plugin;

    @Inject
    public Construction2Overlay(Construction2Plugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        // Add a title to the overlay
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Construction Script")
                .color(Color.YELLOW)
                .build());

        // Display the current state
        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(plugin.getState().toString())
                .build());

        // Display the current action

        // Display EXP gained

        // Display EXP to level up

        // Display EXP per hour

        // Display the current level
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Level:")
                .right(String.valueOf(plugin.getCurrentLevel()))
                .build());

        return super.render(graphics);
    }
}
