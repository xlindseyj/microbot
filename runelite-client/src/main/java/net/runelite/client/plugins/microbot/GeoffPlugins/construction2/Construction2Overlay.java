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

        // Custom paint using Graphics2D
        graphics.setColor(Color.WHITE);
        graphics.drawString("Construction State: " + plugin.getState(), 10, 50); // Example text
        graphics.setColor(Color.RED);
        graphics.drawRect(5, 5,150, 50); // Example rectangle

        return super.render(graphics);
    }
}
