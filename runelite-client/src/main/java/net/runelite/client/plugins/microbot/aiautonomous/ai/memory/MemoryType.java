package net.runelite.client.plugins.microbot.aiautonomous.ai.memory;

public enum MemoryType {
    DECISION("AI Decision"),
    OUTCOME("Action Outcome"),
    STATE_TRANSITION("Game State Change"),
    GAME_EVENT("Game Event"),
    SKILL_TRAINING("Skill Training"),
    COMBAT_EVENT("Combat Event"),
    QUEST_PROGRESS("Quest Progress"),
    LEARNING("Learning Experience"),
    ERROR("Error Event");

    private final String description;

    MemoryType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}