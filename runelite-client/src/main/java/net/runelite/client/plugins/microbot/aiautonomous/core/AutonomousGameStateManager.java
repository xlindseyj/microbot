package net.runelite.client.plugins.microbot.aiautonomous.core;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.aiautonomous.ai.AiDecision;
import net.runelite.client.plugins.microbot.aiautonomous.ai.DecisionEngine;
import net.runelite.client.plugins.microbot.aiautonomous.ai.OllamaClient.GameContext;
import net.runelite.client.plugins.microbot.aiautonomous.ai.memory.GameMemory;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class AutonomousGameStateManager {

    private final GameStateAnalyzer gameStateAnalyzer;
    private final DecisionEngine decisionEngine;
    private final ActionExecutor actionExecutor;
    private final GameMemory gameMemory;
    private final AiAutonomousConfig config;

    private AutonomousGameState currentState = AutonomousGameState.LOGGED_OUT;
    private AutonomousGameState previousState = AutonomousGameState.UNKNOWN;
    private Instant stateStartTime = Instant.now();
    private Instant sessionStartTime = Instant.now();

    // Current decision and execution tracking
    private AiDecision currentDecision;
    private long lastEmergencyLog = 0;
    private CompletableFuture<Boolean> currentExecution;
    private int consecutiveErrors = 0;
    private Instant lastErrorTime = Instant.MIN;

    // State management configuration
    private static final int MAX_CONSECUTIVE_ERRORS = 3;
    private static final Duration ERROR_RECOVERY_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration STATE_TIMEOUT = Duration.ofMinutes(10); // Max time in one state
    private static final Duration DECISION_TIMEOUT = Duration.ofSeconds(30);

    public AutonomousGameStateManager(GameStateAnalyzer gameStateAnalyzer,
                                    DecisionEngine decisionEngine,
                                    ActionExecutor actionExecutor,
                                    GameMemory gameMemory,
                                    AiAutonomousConfig config) {
        this.gameStateAnalyzer = gameStateAnalyzer;
        this.decisionEngine = decisionEngine;
        this.actionExecutor = actionExecutor;
        this.gameMemory = gameMemory;
        this.config = config;
    }

    public void processTick() {
        try {
            // Analyze current game state
            AutonomousGameState detectedState = gameStateAnalyzer.determineGameState();
            GameContext gameContext = gameStateAnalyzer.analyzeCurrentState();

            // Update state if changed
            if (detectedState != currentState) {
                transitionToState(detectedState);
            }

            // Check for state timeout
            if (isStateTimedOut()) {
                log.warn("State {} timed out, forcing transition to IDLE", currentState);
                transitionToState(AutonomousGameState.IDLE);
            }

            // Process current state
            processCurrentState(gameContext);

        } catch (Exception e) {
            log.error("Error processing game tick", e);
            handleError(e);
        }
    }

    private void processCurrentState(GameContext gameContext) {
        switch (currentState) {
            case LOGGED_OUT:
                // Wait for login
                break;

            case EMERGENCY:
                handleEmergencyState(gameContext);
                break;

            case ERROR_RECOVERY:
                handleErrorRecovery();
                break;

            case IDLE:
                handleIdleState(gameContext);
                break;

            case TRAVELING:
                handleTravelingState(gameContext);
                break;

            case SKILL_TRAINING:
                handleSkillTrainingState(gameContext);
                break;

            case IN_COMBAT:
                handleCombatState(gameContext);
                break;

            case BANKING:
                handleBankingState(gameContext);
                break;

            case SHOPPING:
                handleShoppingState(gameContext);
                break;

            case QUEST_DIALOG:
                handleQuestDialogState(gameContext);
                break;

            case BREAK_TIME:
                // AI is taking a break, don't do anything
                break;

            default:
                log.warn("Unknown state: {}", currentState);
                transitionToState(AutonomousGameState.IDLE);
                break;
        }
    }

    private void handleEmergencyState(GameContext gameContext) {
        // Add timeout to prevent endless emergency loops
        if (stateStartTime != null) {
            Duration emergencyDuration = Duration.between(stateStartTime, Instant.now());
            if (emergencyDuration.compareTo(Duration.ofMinutes(2)) > 0) {
                log.warn("Emergency state timeout after {} minutes, forcing recovery", emergencyDuration.toMinutes());
                transitionToState(AutonomousGameState.IDLE);
                return;
            }
        }

        // Reduce log spam - only log every 5 seconds
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastEmergencyLog > 5000) {
            log.warn("In emergency state, attempting to recover ({}s elapsed)",
                    stateStartTime != null ? Duration.between(stateStartTime, Instant.now()).getSeconds() : 0);
            lastEmergencyLog = currentTime;
        }

        if (!gameStateAnalyzer.isInDangerousState()) {
            log.info("Emergency resolved, returning to normal operation");
            transitionToState(AutonomousGameState.IDLE);
            return;
        }

        // Execute emergency procedures
        if (currentDecision == null || currentDecision.isExecuted()) {
            String emergencySituation = "EMERGENCY: " + determineEmergencyType(gameContext);
            makeDecision(emergencySituation, gameContext);
        }
    }

    private void handleErrorRecovery() {
        if (Duration.between(lastErrorTime, Instant.now()).compareTo(ERROR_RECOVERY_TIMEOUT) > 0) {
            log.info("Error recovery timeout reached, returning to normal operation");
            consecutiveErrors = 0;
            transitionToState(AutonomousGameState.IDLE);
        }
    }

    private void handleIdleState(GameContext gameContext) {
        // Make a decision about what to do next
        if (currentDecision == null || currentDecision.isExecuted()) {
            String situation = buildSituationDescription(gameContext);
            makeDecision(situation, gameContext);
        }

        // Execute current decision if we have one
        executeCurrentDecision();
    }

    private void handleTravelingState(GameContext gameContext) {
        // Check if we've arrived at destination
        if (!actionExecutor.isMoving()) {
            log.debug("Finished traveling, transitioning to idle");
            transitionToState(AutonomousGameState.IDLE);
        }

        // Check if we need to make navigation decisions
        if (actionExecutor.needsNavigationHelp()) {
            String situation = "Currently traveling but need navigation assistance: " + gameContext.getCurrentLocation();
            makeDecision(situation, gameContext);
            executeCurrentDecision();
        }
    }

    private void handleSkillTrainingState(GameContext gameContext) {
        // Check if skill training is complete or interrupted
        if (!actionExecutor.isTrainingSkill()) {
            log.debug("Skill training finished, transitioning to idle");
            transitionToState(AutonomousGameState.IDLE);
            return;
        }

        // Check if we need to make skill-related decisions
        if (actionExecutor.needsSkillDecision()) {
            String situation = "Training " + gameContext.getCurrentActivity() + " but need to make a decision";
            makeDecision(situation, gameContext);
            executeCurrentDecision();
        }
    }

    private void handleCombatState(GameContext gameContext) {
        // Check if combat is over
        if (!actionExecutor.isInCombat()) {
            log.debug("Combat finished, transitioning to idle");
            transitionToState(AutonomousGameState.IDLE);
            return;
        }

        // Make combat decisions
        if (actionExecutor.needsCombatDecision()) {
            String situation = "In combat with " + actionExecutor.getCurrentTarget() + ", health: " +
                             gameContext.getCurrentHealth() + "/" + gameContext.getMaxHealth();
            makeDecision(situation, gameContext);
            executeCurrentDecision();
        }
    }

    private void handleBankingState(GameContext gameContext) {
        // Check if banking is complete
        if (!actionExecutor.isBanking()) {
            log.debug("Banking finished, transitioning to idle");
            transitionToState(AutonomousGameState.IDLE);
            return;
        }

        // Make banking decisions
        if (actionExecutor.needsBankingDecision()) {
            String situation = "At bank, inventory: " + gameContext.getInventoryItems();
            makeDecision(situation, gameContext);
            executeCurrentDecision();
        }
    }

    private void handleShoppingState(GameContext gameContext) {
        // Check if shopping is complete
        if (!actionExecutor.isShopping()) {
            log.debug("Shopping finished, transitioning to idle");
            transitionToState(AutonomousGameState.IDLE);
            return;
        }

        // Make shopping decisions
        if (actionExecutor.needsShoppingDecision()) {
            String situation = "In shop, need to decide what to buy/sell";
            makeDecision(situation, gameContext);
            executeCurrentDecision();
        }
    }

    private void handleQuestDialogState(GameContext gameContext) {
        // Handle quest dialog automatically
        if (actionExecutor.hasQuestDialog()) {
            String situation = "In quest dialog, need to choose response";
            makeDecision(situation, gameContext);
            executeCurrentDecision();
        } else {
            log.debug("Quest dialog finished, transitioning to idle");
            transitionToState(AutonomousGameState.IDLE);
        }
    }

    private void makeDecision(String situation, GameContext gameContext) {
        try {
            // Check if decision making is enabled
            if (!config.enableDecisionMaking() || decisionEngine == null) {
                log.debug("Decision making disabled, skipping: {}", situation);
                currentDecision = createFallbackDecision(situation);
                return;
            }

            log.debug("Making decision for situation: {}", situation);

            CompletableFuture<AiDecision> decisionFuture = decisionEngine.makeDecision(situation, gameContext);

            decisionFuture.thenAccept(decision -> {
                currentDecision = decision;
                log.debug("Received AI decision: {}", decision.getAction());
            }).exceptionally(throwable -> {
                log.error("Error making decision", throwable);
                currentDecision = createFallbackDecision(situation);
                return null;
            });

        } catch (Exception e) {
            log.error("Error initiating decision making", e);
            currentDecision = createFallbackDecision(situation);
        }
    }

    private void executeCurrentDecision() {
        if (currentDecision == null || currentDecision.isExecuted()) {
            return;
        }

        if (!currentDecision.shouldExecute()) {
            log.debug("Decision should not be executed: {}", currentDecision);
            currentDecision = null;
            return;
        }

        // Check if action execution is enabled
        if (!config.enableActionExecution() || actionExecutor == null) {
            log.debug("Action execution disabled, marking decision as completed: {}", currentDecision.getAction());
            currentDecision.setExecuted(true);
            currentDecision.setSuccessful(false);
            currentDecision.setExecutionResult("SKIPPED - Action execution disabled");
            return;
        }

        try {
            log.debug("Executing decision: {}", currentDecision.getAction());

            currentExecution = actionExecutor.executeAction(currentDecision);
            currentDecision.setExecuted(true);

            currentExecution.thenAccept(success -> {
                currentDecision.setSuccessful(success);
                currentDecision.setExecutionResult(success ? "SUCCESS" : "FAILED");

                // Learn from the outcome if knowledge sharing is enabled
                if (config.enableKnowledgeSharing() && decisionEngine != null && decisionEngine.getKnowledgeManager() != null) {
                    decisionEngine.getKnowledgeManager().learnFromOutcome(
                        currentDecision.getOriginalSituation(),
                        currentDecision,
                        success,
                        currentDecision.getExecutionResult()
                    );
                }

                if (success) {
                    consecutiveErrors = 0;
                } else {
                    handleExecutionFailure(currentDecision);
                }

            }).exceptionally(throwable -> {
                log.error("Error executing decision", throwable);
                handleExecutionFailure(currentDecision);
                return null;
            });

        } catch (Exception e) {
            log.error("Error starting decision execution", e);
            handleExecutionFailure(currentDecision);
        }
    }

    private void transitionToState(AutonomousGameState newState) {
        if (newState == currentState) {
            return;
        }

        log.debug("State transition: {} -> {}", currentState, newState);

        previousState = currentState;
        currentState = newState;
        stateStartTime = Instant.now();

        // Clear current decision when changing states
        if (newState == AutonomousGameState.IDLE || newState == AutonomousGameState.EMERGENCY) {
            currentDecision = null;
            if (currentExecution != null && !currentExecution.isDone()) {
                currentExecution.cancel(true);
            }
        }

        // Record state change in memory
        gameMemory.recordStateTransition(previousState, currentState);
    }

    private boolean isStateTimedOut() {
        return Duration.between(stateStartTime, Instant.now()).compareTo(STATE_TIMEOUT) > 0;
    }

    private String determineEmergencyType(GameContext gameContext) {
        if (gameContext.getCurrentHealth() < gameContext.getMaxHealth() * 0.3) {
            return "Low health (" + gameContext.getCurrentHealth() + "/" + gameContext.getMaxHealth() + ")";
        }

        if (gameStateAnalyzer.getCurrentArea().contains("Wilderness")) {
            return "In dangerous area (Wilderness)";
        }

        return "Unknown emergency";
    }

    private String buildSituationDescription(GameContext gameContext) {
        StringBuilder situation = new StringBuilder();
        situation.append("I am at ").append(gameContext.getCurrentLocation());
        situation.append(" with ").append(gameContext.getCurrentHealth()).append("/").append(gameContext.getMaxHealth()).append(" health");
        situation.append(". My inventory contains: ").append(gameContext.getInventoryItems());
        situation.append(". Available actions: ").append(gameContext.getAvailableActions());
        situation.append(". What should I do next to progress efficiently?");

        return situation.toString();
    }

    private AiDecision createFallbackDecision(String situation) {
        AiDecision fallback = new AiDecision();
        fallback.setOriginalSituation(situation);
        fallback.setAction("WAIT");
        fallback.setReasoning("Fallback decision due to AI error");
        fallback.setConfidence(10);
        fallback.setPriority(DecisionEngine.DecisionPriority.LOW);
        fallback.setCategory(DecisionEngine.DecisionCategory.IDLE);
        fallback.setValid(true);

        return fallback;
    }

    private void handleExecutionFailure(AiDecision decision) {
        consecutiveErrors++;
        lastErrorTime = Instant.now();

        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            log.error("Too many consecutive errors ({}), entering error recovery", consecutiveErrors);
            transitionToState(AutonomousGameState.ERROR_RECOVERY);
        }
    }

    private void handleError(Exception error) {
        log.error("Error in game state manager", error);
        consecutiveErrors++;
        lastErrorTime = Instant.now();

        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            transitionToState(AutonomousGameState.ERROR_RECOVERY);
        }
    }

    public void onGameStateChanged(GameState newGameState) {
        switch (newGameState) {
            case LOGGED_IN:
                if (currentState == AutonomousGameState.LOGGED_OUT) {
                    transitionToState(AutonomousGameState.IDLE);
                }
                break;

            case LOGIN_SCREEN:
            case LOGGING_IN:
                transitionToState(AutonomousGameState.LOGGED_OUT);
                break;

            case LOADING:
                // Stay in current state while loading
                break;
        }
    }

    // Getters
    public AutonomousGameState getCurrentState() {
        return currentState;
    }

    public AutonomousGameState getPreviousState() {
        return previousState;
    }

    public Duration getTimeInCurrentState() {
        return Duration.between(stateStartTime, Instant.now());
    }

    public Duration getSessionDuration() {
        return Duration.between(sessionStartTime, Instant.now());
    }

    public AiDecision getCurrentDecision() {
        return currentDecision;
    }

    public int getConsecutiveErrors() {
        return consecutiveErrors;
    }

    public boolean isExecutingAction() {
        return currentExecution != null && !currentExecution.isDone();
    }
}