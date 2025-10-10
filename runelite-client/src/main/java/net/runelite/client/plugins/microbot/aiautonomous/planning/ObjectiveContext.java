package net.runelite.client.plugins.microbot.aiautonomous.planning;

import lombok.Data;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;

import java.util.List;
import java.util.Map;

@Data
public class ObjectiveContext {
    private GameStateAnalyzer.GameState currentState;
    private List<GameObjective> activeObjectives;
    private List<String> currentNeeds;
    private Map<String, Double> actionSuccessRates;
    private Map<String, Object> contextMetadata;

    public ObjectiveContext() {
        this.contextMetadata = new java.util.HashMap<>();
    }

    public void setContextMetadata(String key, Object value) {
        contextMetadata.put(key, value);
    }

    public Object getContextMetadata(String key) {
        return contextMetadata.get(key);
    }

    public boolean hasNeed(String need) {
        return currentNeeds != null && currentNeeds.contains(need);
    }

    public double getSuccessRate(String action) {
        return actionSuccessRates != null ? actionSuccessRates.getOrDefault(action, 0.5) : 0.5;
    }

    public boolean hasActiveObjectiveOfType(ObjectiveType type) {
        return activeObjectives != null &&
               activeObjectives.stream()
                       .anyMatch(obj -> obj.getType() == type &&
                                      obj.getStatus() == ObjectiveStatus.ACTIVE);
    }

    public int getActiveObjectiveCount() {
        if (activeObjectives == null) return 0;
        return (int) activeObjectives.stream()
                .filter(obj -> obj.getStatus() == ObjectiveStatus.ACTIVE)
                .count();
    }

    public String getSummary() {
        return String.format("State: %s, Active objectives: %d, Current needs: %d",
                currentState != null ? currentState.getLocation() : "unknown",
                getActiveObjectiveCount(),
                currentNeeds != null ? currentNeeds.size() : 0);
    }
}