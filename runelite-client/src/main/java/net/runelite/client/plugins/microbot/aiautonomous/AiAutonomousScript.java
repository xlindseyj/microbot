package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
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

        // Initialize the parent Script class running state
        super.run();

        log.info("================== AI AUTONOMOUS SCRIPT STARTUP ==================");
        log.info("Starting AI Autonomous Script with configuration:");
        log.info("  - Primary Goal: {}", config.primaryGoal());
        log.info("  - Training Mode: {}", config.trainingMode());
        log.info("  - Account Type: {}", config.accountType());
        log.info("  - Enable Combat: {}", config.enableCombatActions());
        log.info("  - Enable Trading: {}", config.enableTradingActions());
        log.info("  - Enable Quests: {}", config.enableQuestActions());
        log.info("  - Use Antiban: {}", config.useAntibanMeasures());
        log.info("  - Random Action Chance: {}%", config.randomActionChance());
        log.info("  - Human-like Behavior: {}%", config.humanlikeBehavior());
        log.info("  - Max Session Time: {} hours", config.maxSessionTime());
        log.info("  - Debug Mode: {}", config.debugMode());
        Microbot.showMessage("AI Autonomous Player: Starting up...");

        // Verify plugin initialization
        log.info("Checking plugin initialization status...");
        if (!plugin.isInitialized()) {
            log.error("STARTUP FAILED: Plugin not properly initialized");
            log.error("Plugin components status:");
            log.error("  - Ollama Client: {}", plugin.getOllamaClient() != null ? "Available" : "Missing");
            log.error("  - RAG System: {}", plugin.getRagSystem() != null ? "Available" : "Missing");
            log.error("  - Knowledge Manager: {}", plugin.getKnowledgeManager() != null ? "Available" : "Missing");
            log.error("  - Decision Engine: {}", plugin.getDecisionEngine() != null ? "Available" : "Missing");
            log.error("  - Game State Manager: {}", plugin.getGameStateManager() != null ? "Available" : "Missing");
            log.error("  - Action Executor: {}", plugin.getActionExecutor() != null ? "Available" : "Missing");
            Microbot.showMessage("AI Autonomous Player: Initialization failed");
            return false;
        }
        log.info("✓ Plugin initialization verified successfully");

        // Initialize antiban settings if enabled
        if (config.useAntibanMeasures()) {
            log.info("Initializing antiban measures...");
            initializeAntibanSettings();
            log.info("✓ Antiban measures initialized successfully");
        } else {
            log.info("Antiban measures disabled by configuration");
        }

        // Initialize AutoHotkey integration
        log.info("Initializing AutoHotkey integration...");
        initializeAutoHotkey();

        log.info("Starting main control loop...");
        mainLoop();
        log.info("Main control loop ended");
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

        // Close desktop UI when script stops
        if (plugin.getDesktopUI() != null) {
            plugin.getDesktopUI().hideUI();
            log.info("Desktop UI closed due to script stop");
        }

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
        log.info("================== MAIN CONTROL LOOP STARTED ==================");
        log.info("Loop settings: Decision cooldown={}ms, Main delay={}ms, Emergency check={}ms",
                DECISION_COOLDOWN_MS, MAIN_LOOP_DELAY_MS, EMERGENCY_CHECK_INTERVAL);

        int loopIteration = 0;
        long lastStatusReport = System.currentTimeMillis();
        long lastEmergencyCheck = System.currentTimeMillis();

        while (isRunning()) {
            try {
                loopIteration++;
                long currentTime = System.currentTimeMillis();

                // Periodic status reporting (every 30 seconds)
                if (currentTime - lastStatusReport > 30000) {
                    Duration sessionDuration = getSessionDuration();
                    Duration timeSinceDecision = getTimeSinceLastDecision();
                    log.info("=== STATUS REPORT (Loop #{}) ===", loopIteration);
                    log.info("Session time: {}h {}m {}s",
                            sessionDuration.toHours(),
                            sessionDuration.toMinutesPart(),
                            sessionDuration.toSecondsPart());
                    log.info("Time since last decision: {}ms", timeSinceDecision.toMillis());
                    log.info("Game state: {}", Microbot.getClient().getGameState());
                    if (plugin.getGameStateManager() != null) {
                        log.info("AI state: {}", plugin.getGameStateManager().getCurrentState());
                    }
                    lastStatusReport = currentTime;
                }

                // Check for emergency stop conditions
                if (currentTime - lastEmergencyCheck > EMERGENCY_CHECK_INTERVAL) {
                    log.debug("Performing emergency check (iteration {})", loopIteration);
                    if (checkEmergencyStop()) {
                        log.warn("EMERGENCY STOP ACTIVATED - Shutting down AI loop");
                        log.warn("Emergency reason: Manual activation={}, danger detected={}",
                                emergencyStopActivated,
                                plugin.getGameStateAnalyzer() != null && plugin.getGameStateAnalyzer().isInDangerousState());
                        break;
                    }
                    lastEmergencyCheck = currentTime;
                }

                // Check session time limits
                if (checkSessionTimeLimit()) {
                    Duration sessionTime = getSessionDuration();
                    log.info("SESSION TIME LIMIT REACHED - Taking mandatory break");
                    log.info("Session duration: {}h {}m, Max allowed: {}h",
                            sessionTime.toHours(), sessionTime.toMinutesPart(), config.maxSessionTime());
                    takeBreak();
                    continue;
                }

                // Check if we're logged in and ready
                GameState gameState = Microbot.getClient().getGameState();
                if (gameState != GameState.LOGGED_IN) {
                    if (loopIteration % 30 == 0) { // Log every 30 iterations when not logged in
                        log.info("Waiting for login - Current game state: {}", gameState);
                    }
                    sleep(MAIN_LOOP_DELAY_MS);
                    continue;
                }

                // Main AI decision making and action execution
                if (shouldMakeDecision()) {
                    log.debug("Decision cooldown elapsed, executing AI decision cycle (iteration {})", loopIteration);
                    long decisionStart = System.currentTimeMillis();
                    executeAiDecisionCycle();
                    long decisionTime = System.currentTimeMillis() - decisionStart;
                    log.debug("AI decision cycle completed in {}ms", decisionTime);
                    lastDecisionTime = Instant.now();
                } else {
                    Duration timeLeft = Duration.ofMillis(DECISION_COOLDOWN_MS).minus(getTimeSinceLastDecision());
                    if (loopIteration % 10 == 0) { // Log every 10 iterations during cooldown
                        log.debug("Decision on cooldown, {}ms remaining", timeLeft.toMillis());
                    }
                }

                // Anti-ban measures
                if (config.useAntibanMeasures()) {
                    if (loopIteration % 20 == 0) { // Log antiban attempts every 20 iterations
                        log.debug("Performing anti-ban behaviors (iteration {})", loopIteration);
                    }
                    performAntibanBehaviors();
                }

                // Sleep to prevent excessive CPU usage
                sleep(MAIN_LOOP_DELAY_MS);

            } catch (Exception e) {
                log.error("CRITICAL ERROR in main AI loop (iteration {})", loopIteration, e);
                log.error("Error details: Type={}, Message={}", e.getClass().getSimpleName(), e.getMessage());
                if (e.getStackTrace().length > 0) {
                    log.error("Stack trace: {}", e.getStackTrace()[0]);
                }

                if (config.debugMode()) {
                    Microbot.showMessage("AI Error: " + e.getMessage());
                }

                // Log system state for debugging
                try {
                    log.error("System state at error:");
                    log.error("  - Game State: {}", Microbot.getClient().getGameState());
                    log.error("  - Session Duration: {}", getSessionDuration());
                    log.error("  - Emergency Stop: {}", emergencyStopActivated);
                    if (plugin.getGameStateManager() != null) {
                        log.error("  - AI State: {}", plugin.getGameStateManager().getCurrentState());
                    }
                    if (Microbot.isLoggedIn()) {
                        log.error("  - Player Location: {}", Rs2Player.getWorldLocation());
                        log.error("  - Player Health: {}", Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS));
                    }
                } catch (Exception debugE) {
                    log.error("Error while logging system state", debugE);
                }

                // Try to recover from the error
                log.info("Attempting error recovery, sleeping for 5000ms");
                sleep(5000);
            }
        }

        log.info("================== MAIN CONTROL LOOP ENDED ==================");
        log.info("Final session statistics:");
        log.info("  - Total iterations: {}", loopIteration);
        log.info("  - Session duration: {}", getSessionDuration());
        log.info("  - Emergency stop activated: {}", emergencyStopActivated);
    }

    private boolean checkEmergencyStop() {
        log.debug("Checking emergency stop conditions...");

        // Check if emergency stop was manually activated
        if (emergencyStopActivated) {
            log.warn("Emergency stop manually activated");
            return true;
        }

        // Check for dangerous game states (high-level wilderness, etc.)
        if (plugin.getGameStateAnalyzer() != null) {
            boolean inDanger = plugin.getGameStateAnalyzer().isInDangerousState();
            if (inDanger) {
                log.warn("Emergency stop triggered by dangerous game state");
                try {
                    if (Microbot.isLoggedIn()) {
                        log.warn("Danger details: Location={}, Health={}, Combat={}",
                                Rs2Player.getWorldLocation(),
                                Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS),
                                Rs2Player.isInCombat());
                    }
                } catch (Exception e) {
                    log.error("Error logging danger details", e);
                }
            }
            return inDanger;
        }

        log.debug("No emergency conditions detected");
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
            log.debug("=== EXECUTING AI DECISION CYCLE ===");

            // Verify all required components are available
            if (plugin.getGameStateManager() == null) {
                log.error("Game State Manager not available for decision cycle");
                return;
            }

            if (plugin.getDecisionEngine() == null) {
                log.error("Decision Engine not available for decision cycle");
                return;
            }

            // Get current game state
            AutonomousGameState currentState = plugin.getGameStateManager().getCurrentState();
            log.debug("Current AI state: {}", currentState);

            if (currentState == null) {
                log.warn("No current game state available, cannot make decisions");
                return;
            }

            // Log player state information
            if (Microbot.isLoggedIn()) {
                log.debug("Player state: Location={}, Health={}/{}, Combat={}, Animation={}",
                        Rs2Player.getWorldLocation(),
                        Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS),
                        Rs2Player.getRealSkillLevel(Skill.HITPOINTS),
                        Rs2Player.isInCombat(),
                        Rs2Player.isAnimating());
            }

            // Let the game state manager process the current situation
            log.debug("Processing game state tick...");
            plugin.getGameStateManager().processTick();

            // Log decision engine status
            String lastDecision = plugin.getDecisionEngine().getLastDecision();
            log.debug("Last AI decision: {}", lastDecision != null ? lastDecision : "None");

            // Log any knowledge manager statistics
            if (plugin.getKnowledgeManager() != null) {
                log.debug("Knowledge stats: {}", plugin.getKnowledgeManager().getStats());
            }

            log.debug("=== AI DECISION CYCLE COMPLETED ===");

        } catch (Exception e) {
            log.error("CRITICAL ERROR in AI decision cycle", e);
            log.error("Decision cycle error details:");
            log.error("  - Error type: {}", e.getClass().getSimpleName());
            log.error("  - Error message: {}", e.getMessage());

            // Log component availability
            log.error("  - Game State Manager: {}", plugin.getGameStateManager() != null);
            log.error("  - Decision Engine: {}", plugin.getDecisionEngine() != null);
            log.error("  - Action Executor: {}", plugin.getActionExecutor() != null);
            log.error("  - Knowledge Manager: {}", plugin.getKnowledgeManager() != null);

            throw e; // Re-throw to be handled by main loop
        }
    }

    private void performAntibanBehaviors() {
        try {
            double randomRoll = Math.random() * 100;
            double actionChance = config.randomActionChance();

            log.debug("Antiban check: roll={:.2f}, threshold={:.2f}", randomRoll, actionChance);

            // Official Rs2Antiban system actions
            if (randomRoll < actionChance) {
                log.debug("Executing antiban behaviors...");

                // Random chance for action cooldown (creates natural pauses)
                if (Math.random() < 0.3) { // 30% chance
                    log.debug("Performing antiban action cooldown");
                    Rs2Antiban.actionCooldown();
                }

                // Random chance for micro break
                if (Math.random() < 0.1) { // 10% chance
                    log.debug("Attempting antiban micro break");
                    Rs2Antiban.takeMicroBreakByChance();
                }

                // Random mouse movements
                if (Math.random() < 0.2) { // 20% chance
                    log.debug("Performing antiban random mouse movement");
                    Rs2Antiban.moveMouseRandomly();
                }
            }

            // Additional human-like behaviors
            double humanRoll = Math.random() * 100;
            double humanThreshold = actionChance / 2.0;
            if (humanRoll < humanThreshold) {
                log.debug("Performing human-like behavior (roll={:.2f}, threshold={:.2f})", humanRoll, humanThreshold);
                performRandomHumanAction();
            }

        } catch (Exception e) {
            log.error("Error performing anti-ban behavior", e);
            log.error("Antiban error details: {}", e.getMessage());
        }
    }

    private void performRandomHumanAction() {
        if (plugin.getActionExecutor() == null) {
            log.debug("Action executor not available for human action");
            return;
        }

        // Random actions like checking skills, looking around, etc.
        int actionType = (int)(Math.random() * 5);
        String actionName;

        try {
            switch (actionType) {
                case 0:
                    actionName = "random camera movement";
                    log.debug("Performing human action: {}", actionName);
                    plugin.getActionExecutor().performRandomCameraMovement();
                    break;
                case 1:
                    actionName = "check random skill";
                    log.debug("Performing human action: {}", actionName);
                    plugin.getActionExecutor().checkRandomSkill();
                    break;
                case 2:
                    actionName = "random mouse movement";
                    log.debug("Performing human action: {}", actionName);
                    plugin.getActionExecutor().performRandomMouseMovement();
                    break;
                case 3:
                    actionName = "check inventory";
                    log.debug("Performing human action: {}", actionName);
                    plugin.getActionExecutor().checkInventory();
                    break;
                case 4:
                    actionName = "look around";
                    log.debug("Performing human action: {}", actionName);
                    plugin.getActionExecutor().lookAround();
                    break;
                default:
                    log.debug("Invalid action type: {}", actionType);
                    return;
            }
            log.debug("Completed human action: {}", actionName);
        } catch (Exception e) {
            log.error("Error performing human action (type {})", actionType, e);
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