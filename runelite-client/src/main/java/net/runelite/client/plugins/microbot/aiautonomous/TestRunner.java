package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;

/**
 * Simple test runner to execute all connection and system tests.
 */
@Slf4j
public class TestRunner {

    public static void main(String[] args) {
        log.info("=" + "=".repeat(70) + "=");
        log.info("AI AUTONOMOUS PLAYER - SYSTEM TEST RUNNER");
        log.info("=" + "=".repeat(70) + "=");

        try {
            // Create a mock configuration for testing
            AiAutonomousConfig config = createTestConfig();

            // Test all connections
            log.info("\n🔍 RUNNING COMPREHENSIVE CONNECTION TESTS...");
            ConnectionTest.runAllTests(config);

        } catch (Exception e) {
            log.error("❌ Test runner failed", e);
        }

        log.info("=" + "=".repeat(70) + "=");
        log.info("TEST RUNNER COMPLETE");
        log.info("=" + "=".repeat(70) + "=");
    }

    // Create a test configuration that mimics the real one
    private static AiAutonomousConfig createTestConfig() {
        return new AiAutonomousConfig() {
            @Override
            public RagSystemType ragSystemType() {
                return RagSystemType.NEO4J;
            }

            @Override
            public String ragSystemUrl() {
                return "http://100.105.178.55:7474";
            }

            @Override
            public String neo4jDatabase() {
                return "runescapeknowledge";
            }

            @Override
            public String neo4jUsername() {
                return "neo4j";
            }

            @Override
            public String neo4jPassword() {
                return "runescape2025";
            }

            @Override
            public String chromaCollectionName() {
                return "runescape_knowledge";
            }

            @Override
            public String ollamaBaseUrl() {
                return "http://localhost:11434";
            }

            @Override
            public String ollamaModel() {
                return "llama3";
            }

            @Override
            public int maxTokens() {
                return 1000;
            }

            @Override
            public int temperature() {
                return 70;
            }

            @Override
            public boolean enableWikiIntegration() {
                return true;
            }

            @Override
            public int wikiCacheTime() {
                return 24;
            }

            @Override
            public int maxWikiRequests() {
                return 100;
            }

            @Override
            public int maxSearchResults() {
                return 10;
            }

            @Override
            public int similarityThreshold() {
                return 60;
            }
        };
    }
}