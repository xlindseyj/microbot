package net.runelite.client.plugins.microbot.aiautonomous.core;

public enum AutonomousGameState {
    // Connection states
    LOGGED_OUT("Not logged in"),
    LOGGING_IN("Logging in"),
    LOADING("Loading game"),

    // Normal gameplay states
    IDLE("Idle - deciding next action"),
    TRAVELING("Moving to destination"),
    SKILL_TRAINING("Training a skill"),
    IN_COMBAT("In combat"),
    BANKING("Banking items"),
    SHOPPING("Shopping/Trading"),
    QUEST_DIALOG("In quest dialog"),

    // Special states
    EMERGENCY("Emergency - dangerous situation"),
    BREAK_TIME("Taking a break"),
    ERROR_RECOVERY("Recovering from error"),
    UNKNOWN("Unknown state");

    private final String description;

    AutonomousGameState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }

    public boolean isActive() {
        return this != LOGGED_OUT && this != BREAK_TIME && this != UNKNOWN;
    }

    public boolean isSafe() {
        return this != EMERGENCY && this != ERROR_RECOVERY;
    }

    public boolean requiresAction() {
        return this == IDLE || this == ERROR_RECOVERY;
    }

    public boolean isInActivity() {
        return this == SKILL_TRAINING || this == IN_COMBAT || this == TRAVELING ||
               this == BANKING || this == SHOPPING || this == QUEST_DIALOG;
    }
}