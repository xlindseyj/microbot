package net.runelite.client.plugins.microbot.aiautonomous;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiautonomous.ai.DecisionEngine;
import net.runelite.client.plugins.microbot.aiautonomous.ai.KnowledgeManager;
import net.runelite.client.plugins.microbot.aiautonomous.ai.OllamaClient;
import net.runelite.client.plugins.microbot.aiautonomous.ai.memory.GameMemory;
import net.runelite.client.plugins.microbot.aiautonomous.core.AutonomousGameStateManager;
import net.runelite.client.plugins.microbot.aiautonomous.core.ActionExecutor;
import net.runelite.client.plugins.microbot.aiautonomous.core.GameStateAnalyzer;
import net.runelite.client.plugins.microbot.aiautonomous.integration.WikiIntegration;
import net.runelite.client.plugins.microbot.aiautonomous.integration.RAGSystemInterface;
import net.runelite.client.plugins.microbot.aiautonomous.integration.RAGSystemFactory;
import net.runelite.client.plugins.microbot.aiautonomous.training.BaseModeTrainer;
import net.runelite.client.plugins.microbot.aiautonomous.content.ContentManager;
import net.runelite.client.plugins.microbot.aiautonomous.strategy.RealTimeStrategyAdjuster;
import net.runelite.client.plugins.microbot.aiautonomous.ui.AiAutonomousDesktopUI;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.input.KeyManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = "[Kromite] AI Autonomous Player",
        description = "AI-powered autonomous RuneScape player using Ollama and external knowledge sources",
        tags = {"ai", "autonomous", "microbot", "ollama", "rag"},
        authors = {"Kromite"},
        version = "1.0.0",
        enabledByDefault = false,
        priority = true
)
@Slf4j
public class AiAutonomousPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private AiAutonomousConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KeyManager keyManager;

    private AiAutonomousScript script;
    private AiAutonomousOverlay overlay;

    // AI Components
    private OllamaClient ollamaClient;
    private RAGSystemInterface ragSystem;
    private WikiIntegration wikiIntegration;
    private KnowledgeManager knowledgeManager;
    private DecisionEngine decisionEngine;
    private GameMemory gameMemory;

    // Core Components
    private AutonomousGameStateManager gameStateManager;
    private ActionExecutor actionExecutor;
    private GameStateAnalyzer gameStateAnalyzer;

    // Training and Content Components
    private BaseModeTrainer baseModeTrainer;
    private ContentManager contentManager;
    private RealTimeStrategyAdjuster strategyAdjuster;

    // UI Components
    private AiAutonomousDesktopUI desktopUI;

    private boolean initialized = false;

    @Provides
    AiAutonomousConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AiAutonomousConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.info("Starting AI Autonomous Player plugin...");

        if (overlayManager != null) {
            overlay = new AiAutonomousOverlay(this);
            overlayManager.add(overlay);
        }

        initializeComponents();

        if (config.enablePlugin()) {
            script = new AiAutonomousScript(this);
            script.run(config);
        }
    }

    @Override
    protected void shutDown() {
        log.info("Shutting down AI Autonomous Player plugin...");

        if (script != null) {
            script.shutdown();
            script = null;
        }

        if (overlayManager != null && overlay != null) {
            overlayManager.remove(overlay);
        }

        if (gameMemory != null) {
            gameMemory.saveSession();
        }

        shutdownComponents();
    }

    private void initializeComponents() {
        try {
            log.info("Initializing AI components...");

            // Initialize AI clients based on feature toggles
            if (config.enableDecisionMaking()) {
                // Use working local Ollama instance instead of cached configuration
                String ollamaUrl = "http://localhost:11434";
                String ollamaModel = "llama3";

                log.info("Creating Ollama client - URL: {}, Model: {}", ollamaUrl, ollamaModel);
                log.info("Note: Using hardcoded working configuration to override cached settings");
                log.info("To use custom configuration, go to Plugin Configuration -> AI Autonomous Player -> Ollama Settings and clear the cached values");

                ollamaClient = new OllamaClient(ollamaUrl, ollamaModel);
                log.info("Ollama client initialized");
            }

            if (config.enableRAGSystem()) {
                ragSystem = RAGSystemFactory.createWithFallback(config);
                if (ragSystem != null) {
                    log.info("RAG system initialized: {}", ragSystem.getSystemType());
                } else {
                    log.error("Failed to initialize any RAG system");
                }
            }

            if (config.enableWikiIntegration()) {
                wikiIntegration = new WikiIntegration();
                log.info("Wiki integration initialized");
            }

            // Initialize memory and knowledge systems
            if (config.enableMemorySystem()) {
                gameMemory = new GameMemory();
                log.info("Game memory initialized");
            }

            if (config.enableDecisionMaking() && (ragSystem != null || wikiIntegration != null || gameMemory != null)) {
                knowledgeManager = new KnowledgeManager(ragSystem, wikiIntegration, gameMemory, config);
                log.info("Knowledge manager initialized");
            }

            // Initialize decision engine
            if (config.enableDecisionMaking() && ollamaClient != null) {
                decisionEngine = new DecisionEngine(ollamaClient, knowledgeManager);
                log.info("Decision engine initialized");
            }

            // Initialize core game components
            gameStateAnalyzer = new GameStateAnalyzer(client);

            // Initialize content manager for F2P/P2P restrictions
            contentManager = new ContentManager(config);
            log.info("Content manager initialized");

            // Initialize base mode trainer if needed
            if (isBaseModeActive()) {
                baseModeTrainer = new BaseModeTrainer(config, knowledgeManager);
                log.info("Base mode trainer initialized");
            }

            // Initialize real-time strategy adjuster
            if (config.enableDecisionMaking()) {
                strategyAdjuster = new RealTimeStrategyAdjuster(config, knowledgeManager);
                log.info("Real-time strategy adjuster initialized");
            }

            // Initialize desktop UI
            desktopUI = new AiAutonomousDesktopUI(this);
            log.info("Desktop UI initialized");

            if (config.enableActionExecution()) {
                actionExecutor = new ActionExecutor(client, config);
                log.info("Action executor initialized");
            }

            if (gameStateAnalyzer != null && (decisionEngine != null || actionExecutor != null)) {
                gameStateManager = new AutonomousGameStateManager(
                    gameStateAnalyzer,
                    decisionEngine,
                    actionExecutor,
                    gameMemory,
                    config
                );
                log.info("Game state manager initialized");
            }

            // Load existing knowledge and memories if enabled
            if (knowledgeManager != null) {
                knowledgeManager.initialize();
            }
            if (gameMemory != null) {
                gameMemory.loadSession();
            }

            initialized = true;
            log.info("AI Autonomous Player components initialized successfully");

            // Show desktop UI after all components are initialized
            if (desktopUI != null) {
                desktopUI.showUI();
                log.info("Desktop UI launched");
            }

        } catch (Exception e) {
            log.error("Failed to initialize AI components", e);
            Microbot.showMessage("AI Autonomous Player: Failed to initialize - " + e.getMessage());
        }
    }

    private void shutdownComponents() {
        try {
            if (knowledgeManager != null) {
                knowledgeManager.shutdown();
            }
            if (ragSystem != null) {
                ragSystem.shutdown();
            }
            initialized = false;
            log.info("AI components shut down successfully");
        } catch (Exception e) {
            log.error("Error during component shutdown", e);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (!initialized || !config.enablePlugin()) {
            return;
        }

        GameState newState = gameStateChanged.getGameState();
        log.debug("Game state changed to: {}", newState);

        if (gameStateManager != null) {
            gameStateManager.onGameStateChanged(newState);
        }

        // Record state changes in memory
        if (gameMemory != null) {
            gameMemory.recordStateChange(newState);
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        if (!initialized || !config.enablePlugin()) {
            return;
        }

        try {
            if (gameStateManager != null) {
                gameStateManager.processTick();
            }

            // Process real-time strategy adjustments
            if (strategyAdjuster != null && gameStateAnalyzer != null) {
                strategyAdjuster.analyzeAndAdjust(gameStateAnalyzer.analyzeCurrentGameState());
            }
        } catch (Exception e) {
            log.error("Error processing game tick", e);
            if (config.debugMode()) {
                Microbot.showMessage("AI Error: " + e.getMessage());
            }
        }
    }

    // Getters for components
    public OllamaClient getOllamaClient() {
        return ollamaClient;
    }

    public RAGSystemInterface getRagSystem() {
        return ragSystem;
    }

    public WikiIntegration getWikiIntegration() {
        return wikiIntegration;
    }

    public KnowledgeManager getKnowledgeManager() {
        return knowledgeManager;
    }

    public DecisionEngine getDecisionEngine() {
        return decisionEngine;
    }

    public GameMemory getGameMemory() {
        return gameMemory;
    }

    public AutonomousGameStateManager getGameStateManager() {
        return gameStateManager;
    }

    public ActionExecutor getActionExecutor() {
        return actionExecutor;
    }

    public GameStateAnalyzer getGameStateAnalyzer() {
        return gameStateAnalyzer;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public AiAutonomousConfig getConfig() {
        return config;
    }

    public String getStatus() {
        if (!initialized) {
            return "Initializing...";
        }
        if (!config.enablePlugin()) {
            return "Disabled";
        }
        if (gameStateManager != null) {
            return gameStateManager.getCurrentState().toString();
        }
        return "Unknown";
    }

    public String getLastDecision() {
        if (decisionEngine != null) {
            return decisionEngine.getLastDecision();
        }
        return "No decisions yet";
    }

    public int getActionsPerformed() {
        if (actionExecutor != null) {
            return actionExecutor.getActionsPerformed();
        }
        return 0;
    }

    public String getKnowledgeStats() {
        if (knowledgeManager != null) {
            return knowledgeManager.getStats();
        }
        return "Knowledge system not initialized";
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public BaseModeTrainer getBaseModeTrainer() {
        return baseModeTrainer;
    }

    public ContentManager getContentManager() {
        return contentManager;
    }

    public RealTimeStrategyAdjuster getStrategyAdjuster() {
        return strategyAdjuster;
    }

    public AiAutonomousDesktopUI getDesktopUI() {
        return desktopUI;
    }

    private boolean isBaseModeActive() {
        return config.primaryGoal() == AiAutonomousConfig.AiGoal.BASE_MODE ||
               config.trainingMode() == AiAutonomousConfig.TrainingMode.BASE_MODE;
    }
}