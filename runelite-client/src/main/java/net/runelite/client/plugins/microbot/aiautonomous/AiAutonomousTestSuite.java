package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.integration.RAGSystemTest;

/**
 * Master test suite for the AI Autonomous Player plugin.
 * Runs comprehensive tests to verify all components work correctly.
 */
@Slf4j
public class AiAutonomousTestSuite {

    /**
     * Run all tests that don't require active game client or external connections
     */
    public static void runOfflineTests() {
        log.info("=".repeat(60));
        log.info("Starting AI Autonomous Player Offline Test Suite");
        log.info("=".repeat(60));

        try {
            // Test RAG systems
            log.info("\n" + "=".repeat(40));
            log.info("RAG SYSTEM TESTS");
            log.info("=".repeat(40));
            RAGSystemTest.testRAGSystemsOffline();

            // Test antiban integration
            log.info("\n" + "=".repeat(40));
            log.info("ANTIBAN INTEGRATION TESTS");
            log.info("=".repeat(40));
            AntibanTest.testAntibanOffline();

            // Test emergency stop mechanisms
            log.info("\n" + "=".repeat(40));
            log.info("EMERGENCY STOP TESTS");
            log.info("=".repeat(40));
            EmergencyStopTest.testEmergencyStopOffline();

            // Test wiki integration
            log.info("\n" + "=".repeat(40));
            log.info("WIKI INTEGRATION TESTS");
            log.info("=".repeat(40));
            WikiIntegrationTest.testWikiIntegrationOffline();

            // Test AI decision making
            log.info("\n" + "=".repeat(40));
            log.info("AI DECISION MAKING TESTS");
            log.info("=".repeat(40));
            AIDecisionTest.testAIDecisionOffline();

            // Test AutoHotkey integration
            log.info("\n" + "=".repeat(40));
            log.info("AUTOHOTKEY INTEGRATION TESTS");
            log.info("=".repeat(40));
            AutoHotkeyTest.testAutoHotkeyOffline();

            // Test network connectivity
            log.info("\n" + "=".repeat(40));
            log.info("NETWORK CONNECTIVITY TESTS");
            log.info("=".repeat(40));
            ConnectionTest.testNetworkConnectivity();

            log.info("\n" + "=".repeat(60));
            log.info("✓ All offline tests completed successfully!");
            log.info("=".repeat(60));

        } catch (Exception e) {
            log.error("✗ Offline test suite failed", e);
        }
    }

    /**
     * Run comprehensive tests with active plugin and configuration
     */
    public static void runFullTests(AiAutonomousPlugin plugin, AiAutonomousScript script, AiAutonomousConfig config) {
        log.info("=".repeat(60));
        log.info("Starting AI Autonomous Player Full Test Suite");
        log.info("=".repeat(60));

        try {
            // Test plugin initialization
            log.info("\n" + "=".repeat(40));
            log.info("PLUGIN INITIALIZATION TESTS");
            log.info("=".repeat(40));
            testPluginInitialization(plugin);

            // Test RAG systems with configuration
            log.info("\n" + "=".repeat(40));
            log.info("RAG SYSTEM TESTS");
            log.info("=".repeat(40));
            RAGSystemTest.testRAGSystems(config);

            // Test antiban integration with configuration
            log.info("\n" + "=".repeat(40));
            log.info("ANTIBAN INTEGRATION TESTS");
            log.info("=".repeat(40));
            AntibanTest.testAntibanIntegration(config);

            // Test emergency stop mechanisms
            log.info("\n" + "=".repeat(40));
            log.info("EMERGENCY STOP TESTS");
            log.info("=".repeat(40));
            EmergencyStopTest.testEmergencyStopMechanisms(script, config);
            EmergencyStopTest.testDecisionTiming(script);
            EmergencyStopTest.testSafetyConfigurations(config);

            // Test wiki integration
            log.info("\n" + "=".repeat(40));
            log.info("WIKI INTEGRATION TESTS");
            log.info("=".repeat(40));
            WikiIntegrationTest.testWikiIntegration(config);
            WikiIntegrationTest.testWikiRateLimit(config);
            WikiIntegrationTest.testWikiCaching(config);

            // Test AI decision making
            log.info("\n" + "=".repeat(40));
            log.info("AI DECISION MAKING TESTS");
            log.info("=".repeat(40));
            AIDecisionTest.testAIDecisionMaking(config);

            // Test AutoHotkey integration
            log.info("\n" + "=".repeat(40));
            log.info("AUTOHOTKEY INTEGRATION TESTS");
            log.info("=".repeat(40));
            AutoHotkeyTest.testAutoHotkeyIntegration(script, config, plugin.getKeyManager());
            AutoHotkeyTest.testAIScriptIntegration(script);

            // Test all system connections
            log.info("\n" + "=".repeat(40));
            log.info("CONNECTION AND INTEGRATION TESTS");
            log.info("=".repeat(40));
            ConnectionTest.runAllTests(config);

            log.info("\n" + "=".repeat(60));
            log.info("✓ All comprehensive tests completed!");
            log.info("=".repeat(60));

        } catch (Exception e) {
            log.error("✗ Full test suite failed", e);
        }
    }

    private static void testPluginInitialization(AiAutonomousPlugin plugin) {
        log.info("Testing plugin initialization...");
        try {
            // Test plugin state
            if (plugin.isInitialized()) {
                log.info("✓ Plugin is initialized");
            } else {
                log.warn("⚠ Plugin is not initialized");
            }

            // Test component availability
            log.info("Testing component availability...");

            if (plugin.getOllamaClient() != null) {
                log.info("✓ Ollama client is available");
            } else {
                log.info("ℹ Ollama client is not initialized (may be disabled)");
            }

            if (plugin.getRagSystem() != null) {
                log.info("✓ RAG system is available: {}", plugin.getRagSystem().getSystemType());
            } else {
                log.info("ℹ RAG system is not initialized (may be disabled)");
            }

            if (plugin.getWikiIntegration() != null) {
                log.info("✓ Wiki integration is available");
            } else {
                log.info("ℹ Wiki integration is not initialized (may be disabled)");
            }

            if (plugin.getGameMemory() != null) {
                log.info("✓ Game memory system is available");
            } else {
                log.info("ℹ Game memory system is not initialized (may be disabled)");
            }

            if (plugin.getDecisionEngine() != null) {
                log.info("✓ Decision engine is available");
            } else {
                log.info("ℹ Decision engine is not initialized (may be disabled)");
            }

            if (plugin.getActionExecutor() != null) {
                log.info("✓ Action executor is available");
                log.info("  - Actions performed: {}", plugin.getActionsPerformed());
            } else {
                log.info("ℹ Action executor is not initialized (may be disabled)");
            }

            if (plugin.getGameStateManager() != null) {
                log.info("✓ Game state manager is available");
                log.info("  - Current status: {}", plugin.getStatus());
            } else {
                log.info("ℹ Game state manager is not initialized");
            }

            // Test knowledge stats
            String knowledgeStats = plugin.getKnowledgeStats();
            log.info("Knowledge system stats: {}", knowledgeStats);

            // Test last decision
            String lastDecision = plugin.getLastDecision();
            log.info("Last AI decision: {}", lastDecision);

        } catch (Exception e) {
            log.error("✗ Plugin initialization test failed", e);
        }
    }

    /**
     * Quick validation that all test classes can be loaded
     */
    public static void validateTestClasses() {
        log.info("Validating test classes...");
        try {
            // Attempt to load all test classes
            Class.forName("net.runelite.client.plugins.microbot.aiautonomous.integration.RAGSystemTest");
            log.info("✓ RAGSystemTest class loaded");

            Class.forName("net.runelite.client.plugins.microbot.aiautonomous.AntibanTest");
            log.info("✓ AntibanTest class loaded");

            Class.forName("net.runelite.client.plugins.microbot.aiautonomous.EmergencyStopTest");
            log.info("✓ EmergencyStopTest class loaded");

            Class.forName("net.runelite.client.plugins.microbot.aiautonomous.WikiIntegrationTest");
            log.info("✓ WikiIntegrationTest class loaded");

            Class.forName("net.runelite.client.plugins.microbot.aiautonomous.AIDecisionTest");
            log.info("✓ AIDecisionTest class loaded");

            Class.forName("net.runelite.client.plugins.microbot.aiautonomous.AutoHotkeyTest");
            log.info("✓ AutoHotkeyTest class loaded");

            Class.forName("net.runelite.client.plugins.microbot.aiautonomous.ConnectionTest");
            log.info("✓ ConnectionTest class loaded");

            log.info("✓ All test classes validated successfully");

        } catch (ClassNotFoundException e) {
            log.error("✗ Test class validation failed", e);
        }
    }

    /**
     * Main entry point for running tests
     */
    public static void main(String[] args) {
        log.info("AI Autonomous Player Test Suite");
        log.info("Running standalone offline tests...");

        validateTestClasses();
        runOfflineTests();

        log.info("Test suite completed. Check logs for results.");
    }
}