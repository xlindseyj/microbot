package net.runelite.client.plugins.microbot.aiautonomous;

import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

public class AiAutonomousOverlay extends OverlayPanel {

    private final AiAutonomousPlugin plugin;

    @Inject
    public AiAutonomousOverlay(AiAutonomousPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "AI Autonomous overlay"));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.getConfig().enablePlugin()) {
            return null;
        }

        panelComponent.getChildren().clear();

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("AI Autonomous Player")
                .color(plugin.isInitialized() ? Color.GREEN : Color.RED)
                .build());

        // Status
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(plugin.getStatus())
                .rightColor(getStatusColor())
                .build());

        // Initialization status
        if (!plugin.isInitialized()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Initializing...")
                    .rightColor(Color.ORANGE)
                    .build());
            return super.render(graphics);
        }

        // Current decision
        String lastDecision = plugin.getLastDecision();
        if (lastDecision != null && !lastDecision.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Last Decision:")
                    .right(truncateText(lastDecision, 30))
                    .build());
        }

        // Actions performed
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Actions:")
                .right(String.valueOf(plugin.getActionsPerformed()))
                .build());

        // Knowledge stats
        if (plugin.getConfig().debugMode()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Knowledge:")
                    .right(plugin.getKnowledgeStats())
                    .build());
        }

        // Memory usage
        if (plugin.getGameMemory() != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Memories:")
                    .right(String.valueOf(plugin.getGameMemory().getMemoryCount()))
                    .build());
        }

        // Session time
        if (plugin.getGameStateManager() != null) {
            Duration sessionTime = plugin.getGameStateManager().getSessionDuration();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Session:")
                    .right(formatDuration(sessionTime))
                    .build());
        }

        // Current goal
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Goal:")
                .right(plugin.getConfig().primaryGoal().toString())
                .build());

        // AI Model info
        if (plugin.getConfig().debugMode()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Model:")
                    .right(plugin.getConfig().ollamaModel())
                    .build());
        }

        // Connection status
        Color connectionColor = Color.GREEN;
        String connectionStatus = "Connected";

        if (plugin.getOllamaClient() != null && !plugin.getOllamaClient().isConnected()) {
            connectionColor = Color.RED;
            connectionStatus = "Disconnected";
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Ollama:")
                .right(connectionStatus)
                .rightColor(connectionColor)
                .build());

        // RAG status
        if (plugin.getRagSystem() != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("RAG:")
                    .right(plugin.getRagSystem().isConnected() ? "Online" : "Offline")
                    .rightColor(plugin.getRagSystem().isConnected() ? Color.GREEN : Color.RED)
                    .build());
        }

        // Emergency stop indicator
        if (plugin.getConfig().debugMode()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Emergency:")
                    .right(plugin.getConfig().emergencyStop())
                    .rightColor(Color.YELLOW)
                    .build());
        }

        return super.render(graphics);
    }

    private Color getStatusColor() {
        String status = plugin.getStatus();
        switch (status.toLowerCase()) {
            case "running":
            case "active":
                return Color.GREEN;
            case "disabled":
            case "stopped":
                return Color.RED;
            case "initializing...":
            case "waiting":
                return Color.ORANGE;
            default:
                return Color.WHITE;
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "00:00:00";
        }

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}