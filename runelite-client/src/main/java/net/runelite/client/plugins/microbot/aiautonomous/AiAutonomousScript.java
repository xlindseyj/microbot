package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiautonomous.core.AutonomousGameState;
import net.runelite.client.plugins.microbot.aiautonomous.core.AutoHotkeyIntegration;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.input.KeyManager;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;


@Slf4j
public class AiAutonomousScript extends Script {

    private final AiAutonomousPlugin plugin;
    private AiAutonomousConfig config;
    private AutoHotkeyIntegration autoHotkeyIntegration;

    private Instant sessionStartTime;
    private Instant lastDecisionTime;
    private boolean emergencyStopActivated = false;

    private static final int MAIN_LOOP_DELAY_MS = 1000; // 1 second main loop
    private static final int DECISION_COOLDOWN_MS = 3000; // 3 seconds between decisions
    private static final int EMERGENCY_CHECK_INTERVAL = 5000; // Check for emergency stop every 5 seconds

    public AiAutonomousScript(AiAutonomousPlugin plugin) {
        this.plugin = plugin;
        this.sessionStartTime = Instant.now();
        this.lastDecisionTime = Instant.now();
    }

    public boolean run(AiAutonomousConfig config) {
        this.config = config;

        log.info("Starting AI Autonomous Script...");
        Microbot.showMessage("AI Autonomous Player: Starting up...");

        if (!plugin.isInitialized()) {
            log.error("Plugin not properly initialized, cannot start script");
            Microbot.showMessage("AI Autonomous Player: Initialization failed");
            return false;
        }

        // Initialize antiban settings if enabled
        if (config.useAntibanMeasures()) {
            initializeAntibanSettings();
            log.info("Antiban measures initialized");
        }

        // Initialize AutoHotkey integration
        initializeAutoHotkey();

        mainLoop();
        return true;
    }

    public void onStart() {
        sessionStartTime = Instant.now();
        emergencyStopActivated = false;
        log.info("AI Autonomous Script started");
    }

    public void onStop() {
        log.info("AI Autonomous Script stopped");
        Microbot.showMessage("AI Autonomous Player: Stopped");

        // Shutdown AutoHotkey integration
        if (autoHotkeyIntegration != null) {
            autoHotkeyIntegration.shutdown();
        }

        // Save current session data
        if (plugin.getGameMemory() != null) {
            plugin.getGameMemory().saveSession();
        }
    }

    public void mainLoop() {
        log.info("Entering main AI control loop");

        while (isRunning()) {
            try {
                // Check for emergency stop conditions
                if (checkEmergencyStop()) {
                    log.warn("Emergency stop activated, shutting down");
                    break;
                }

                // Check session time limits
                if (checkSessionTimeLimit()) {
                    log.info("Session time limit reached, taking a break");
                    takeBreak();
                    continue;
                }

                // Check if we're logged in and ready
                if (Microbot.getClient().getGameState() != GameState.LOGGED_IN) {
                    log.debug("Not logged in, waiting...");
                    sleep(MAIN_LOOP_DELAY_MS);
                    continue;
                }

                // Main AI decision making and action execution
                if (shouldMakeDecision()) {
                    executeAiDecisionCycle();
                    lastDecisionTime = Instant.now();
                }

                // Anti-ban measures
                if (config.useAntibanMeasures()) {
                    performAntibanBehaviors();
                }

                // Sleep to prevent excessive CPU usage
                sleep(MAIN_LOOP_DELAY_MS);

            } catch (Exception e) {
                log.error("Error in main AI loop", e);
                if (config.debugMode()) {
                    Microbot.showMessage("AI Error: " + e.getMessage());
                }

                // Brief pause before continuing to prevent error spam
                sleep(5000);
            }
        }

        log.info("Exiting main AI control loop");
    }

    private boolean checkEmergencyStop() {
        // Check for emergency stop phrase in recent chat
        // This would be implemented by monitoring chat events

        // Check if emergency stop was manually activated
        if (emergencyStopActivated) {
            return true;
        }

        // Check for dangerous game states (high-level wilderness, etc.)
        if (plugin.getGameStateAnalyzer() != null) {
            return plugin.getGameStateAnalyzer().isInDangerousState();
        }

        return false;
    }

    private boolean checkSessionTimeLimit() {
        if (config.maxSessionTime() <= 0) {
            return false;
        }

        Duration sessionDuration = Duration.between(sessionStartTime, Instant.now());
        return sessionDuration.toHours() >= config.maxSessionTime();
    }

    private void takeBreak() {
        log.info("Taking mandated break");
        Microbot.showMessage("AI Autonomous Player: Taking a break...");

        // Calculate break time (15-45 minutes)
        int breakMinutes = 15 + (int)(Math.random() * 30);

        try {
            // Logout or move to a safe location
            if (plugin.getActionExecutor() != null) {
                plugin.getActionExecutor().performSafeLogout();
            }

            // Sleep for break duration
            TimeUnit.MINUTES.sleep(breakMinutes);

            // Reset session start time
            sessionStartTime = Instant.now();

        } catch (InterruptedException e) {
            log.warn("Break interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldMakeDecision() {
        Duration timeSinceLastDecision = Duration.between(lastDecisionTime, Instant.now());
        return timeSinceLastDecision.toMillis() >= DECISION_COOLDOWN_MS;
    }

    private void executeAiDecisionCycle() {
        try {
            if (config.debugMode()) {
                log.debug("Executing AI decision cycle");
            }

            // Get current game state
            AutonomousGameState currentState = plugin.getGameStateManager().getCurrentState();

            if (currentState == null) {
                log.warn("No current game state available");
                return;
            }

            // Let the game state manager process the current situation
            plugin.getGameStateManager().processTick();

            if (config.debugMode()) {
                log.debug("Current AI state: {}", currentState);
                log.debug("Last decision: {}", plugin.getDecisionEngine().getLastDecision());
            }

        } catch (Exception e) {
            log.error("Error in AI decision cycle", e);
            throw e; // Re-throw to be handled by main loop
        }
    }

    private void performAntibanBehaviors() {
        try {
            // Official Rs2Antiban system actions
            if (Math.random() * 100 < config.randomActionChance()) {
                // Random chance for action cooldown (creates natural pauses)
                if (Math.random() < 0.3) { // 30% chance
                    Rs2Antiban.actionCooldown();
                    log.debug("Performed antiban action cooldown");
                }

                // Random chance for micro break
                if (Math.random() < 0.1) { // 10% chance
                    Rs2Antiban.takeMicroBreakByChance();
                    log.debug("Attempted antiban micro break");
                }

                // Random mouse movements
                if (Math.random() < 0.2) { // 20% chance
                    Rs2Antiban.moveMouseRandomly();
                    log.debug("Performed antiban random mouse movement");
                }
            }

            // Additional human-like behaviors
            if (Math.random() * 100 < config.randomActionChance() / 2.0) {
                performRandomHumanAction();
            }

        } catch (Exception e) {
            log.warn("Error performing anti-ban behavior", e);
        }
    }

    private void performRandomHumanAction() {
        if (plugin.getActionExecutor() == null) {
            return;
        }

        // Random actions like checking skills, looking around, etc.
        int actionType = (int)(Math.random() * 5);

        switch (actionType) {
            case 0:
                plugin.getActionExecutor().performRandomCameraMovement();
                break;
            case 1:
                plugin.getActionExecutor().checkRandomSkill();
                break;
            case 2:
                plugin.getActionExecutor().performRandomMouseMovement();
                break;
            case 3:
                plugin.getActionExecutor().checkInventory();
                break;
            case 4:
                plugin.getActionExecutor().lookAround();
                break;
        }
    }

    public void activateEmergencyStop() {
        log.warn("Emergency stop activated manually");
        emergencyStopActivated = true;
        Microbot.showMessage("AI Autonomous Player: Emergency stop activated!");
    }

    public boolean isEmergencyStopActivated() {
        return emergencyStopActivated;
    }

    public Duration getSessionDuration() {
        return Duration.between(sessionStartTime, Instant.now());
    }

    public Duration getTimeSinceLastDecision() {
        return Duration.between(lastDecisionTime, Instant.now());
    }

    public void resetSession() {
        sessionStartTime = Instant.now();
        emergencyStopActivated = false;
        log.info("AI session reset");
    }

    private void initializeAntibanSettings() {
        try {
            Rs2AntibanSettings.actionCooldownChance = config.randomActionChance() / 100.0;
            Rs2AntibanSettings.microBreakChance = config.randomActionChance() / 300.0;
            Rs2AntibanSettings.moveMouseRandomlyChance = config.randomActionChance() / 200.0;
            Rs2AntibanSettings.simulateMistakes = config.humanlikeBehavior() > 50;
            Rs2AntibanSettings.simulateFatigue = config.humanlikeBehavior() > 60;
            Rs2AntibanSettings.usePlayStyle = true;

            log.debug("Antiban settings initialized with random action chance: {}%, human-like behavior: {}%",
                     config.randomActionChance(), config.humanlikeBehavior());
        } catch (Exception e) {
            log.warn("Failed to initialize antiban settings", e);
        }
    }

    private void initializeAutoHotkey() {
        try {
            // Get KeyManager from the plugin
            KeyManager keyManager = plugin.getKeyManager();

            if (keyManager != null) {
                autoHotkeyIntegration = new AutoHotkeyIntegration(this, config, keyManager);
                log.info("AutoHotkey integration initialized successfully");

                // Log available hotkeys for user reference
                log.info("AutoHotkey commands available:\n{}", autoHotkeyIntegration.getHotkeyHelp());

                Microbot.showMessage("AutoHotkey integration active - Press Ctrl+Alt+H for help");
            } else {
                log.warn("KeyManager not available, AutoHotkey integration disabled");
            }
        } catch (Exception e) {
            log.error("Failed to initialize AutoHotkey integration", e);
        }
    }

    public AutoHotkeyIntegration getAutoHotkeyIntegration() {
        return autoHotkeyIntegration;
    }
}