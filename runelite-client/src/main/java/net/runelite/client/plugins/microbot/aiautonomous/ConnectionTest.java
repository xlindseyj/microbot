package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.integration.RAGSystemInterface;
import net.runelite.client.plugins.microbot.aiautonomous.integration.RAGSystemFactory;
import net.runelite.client.plugins.microbot.aiautonomous.integration.Neo4jRAGSystem;
import net.runelite.client.plugins.microbot.aiautonomous.integration.RAGSystem;
import net.runelite.client.plugins.microbot.aiautonomous.integration.WikiIntegration;
import net.runelite.client.plugins.microbot.aiautonomous.ai.OllamaClient;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive connection test for all AI Autonomous Player systems.
 * Tests Neo4j, Chroma, Ollama, and Wiki connections.
 */
@Slf4j
public class ConnectionTest {

    private static final int CONNECTION_TIMEOUT_MS = 5000; // 5 seconds

    public static void testAllConnections(AiAutonomousConfig config) {
        log.info("=".repeat(60));
        log.info("STARTING COMPREHENSIVE CONNECTION TESTS");
        log.info("=".repeat(60));

        boolean allConnectionsGood = true;

        // Test Neo4j connection
        boolean neo4jOk = testNeo4jConnection(config);
        allConnectionsGood &= neo4jOk;

        // Test Chroma connection
        boolean chromaOk = testChromaConnection(config);
        allConnectionsGood &= chromaOk;

        // Test RAG Factory fallback
        boolean factoryOk = testRAGFactory(config);
        allConnectionsGood &= factoryOk;

        // Test Ollama connection
        boolean ollamaOk = testOllamaConnection(config);
        allConnectionsGood &= ollamaOk;

        // Test Wiki connection
        boolean wikiOk = testWikiConnection(config);
        allConnectionsGood &= wikiOk;

        // Summary
        log.info("\n" + "=".repeat(60));
        if (allConnectionsGood) {
            log.info("✅ ALL SYSTEMS ONLINE AND READY!");
        } else {
            log.warn("⚠️ SOME SYSTEMS OFFLINE - CHECK LOGS ABOVE");
        }
        log.info("=".repeat(60));
    }

    private static boolean testNeo4jConnection(AiAutonomousConfig config) {
        log.info("\n" + "-".repeat(40));
        log.info("TESTING NEO4J CONNECTION");
        log.info("-".repeat(40));

        try {
            String url = config.ragSystemUrl();
            String database = config.neo4jDatabase();
            String username = config.neo4jUsername();
            String password = config.neo4jPassword();

            log.info("Neo4j Configuration:");
            log.info("  URL: {}", url);
            log.info("  Database: {}", database);
            log.info("  Username: {}", username);
            log.info("  Password: {}", maskPassword(password));

            // Test basic HTTP connectivity first
            boolean httpOk = testHttpConnection(url);
            if (!httpOk) {
                log.error("❌ Neo4j HTTP connection failed");
                return false;
            }

            // Test Neo4j RAG system
            Neo4jRAGSystem neo4jSystem = new Neo4jRAGSystem(url, database, username, password);
            neo4jSystem.initialize();

            boolean connected = neo4jSystem.testConnection();
            if (connected) {
                log.info("✅ Neo4j RAG system connected successfully");
                log.info("  System type: {}", neo4jSystem.getSystemType());
                log.info("  Stats: {}", neo4jSystem.getStats());

                neo4jSystem.shutdown();
                return true;
            } else {
                log.error("❌ Neo4j RAG system connection test failed");
                neo4jSystem.shutdown();
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Neo4j connection test failed", e);
            return false;
        }
    }

    private static boolean testChromaConnection(AiAutonomousConfig config) {
        log.info("\n" + "-".repeat(40));
        log.info("TESTING CHROMA CONNECTION");
        log.info("-".repeat(40));

        try {
            String chromaUrl = "http://localhost:8000";
            String collectionName = config.chromaCollectionName();

            log.info("Chroma Configuration:");
            log.info("  URL: {}", chromaUrl);
            log.info("  Collection: {}", collectionName);

            // Test basic HTTP connectivity first
            boolean httpOk = testHttpConnection(chromaUrl);
            if (!httpOk) {
                log.warn("⚠️ Chroma HTTP connection failed (may be offline)");
                return false;
            }

            // Test Chroma RAG system
            RAGSystem chromaSystem = new RAGSystem(chromaUrl, collectionName);
            chromaSystem.initialize();

            boolean connected = chromaSystem.testConnection();
            if (connected) {
                log.info("✅ Chroma RAG system connected successfully");
                log.info("  System type: {}", chromaSystem.getSystemType());
                log.info("  Stats: {}", chromaSystem.getStats());

                chromaSystem.shutdown();
                return true;
            } else {
                log.warn("⚠️ Chroma RAG system connection test failed (may be offline)");
                chromaSystem.shutdown();
                return false;
            }

        } catch (Exception e) {
            log.warn("⚠️ Chroma connection test failed (may be offline)", e);
            return false;
        }
    }

    private static boolean testRAGFactory(AiAutonomousConfig config) {
        log.info("\n" + "-".repeat(40));
        log.info("TESTING RAG FACTORY SYSTEM");
        log.info("-".repeat(40));

        try {
            log.info("Testing RAG factory with fallback mechanism...");

            RAGSystemInterface ragSystem = RAGSystemFactory.createWithFallback(config);

            if (ragSystem != null) {
                log.info("✅ RAG Factory created system successfully");
                log.info("  Active system: {}", ragSystem.getSystemType());
                log.info("  System ready: {}", ragSystem.isReady());

                if (ragSystem.isReady()) {
                    log.info("  System stats: {}", ragSystem.getStats());
                }

                ragSystem.shutdown();
                return true;
            } else {
                log.error("❌ RAG Factory failed to create any system");
                return false;
            }

        } catch (Exception e) {
            log.error("❌ RAG Factory test failed", e);
            return false;
        }
    }

    private static boolean testOllamaConnection(AiAutonomousConfig config) {
        log.info("\n" + "-".repeat(40));
        log.info("TESTING OLLAMA CONNECTION");
        log.info("-".repeat(40));

        try {
            // Use the correct working Ollama configuration
            String ollamaUrl = "http://localhost:11434";
            String model = "llama3";

            log.info("Ollama Configuration:");
            log.info("  URL: {}", ollamaUrl);
            log.info("  Model: {}", model);
            log.info("  Max Tokens: {}", config.maxTokens());
            log.info("  Temperature: {}", config.temperature());

            // Test basic HTTP connectivity first
            boolean httpOk = testHttpConnection(ollamaUrl);
            if (!httpOk) {
                log.warn("⚠️ Ollama HTTP connection failed (may be offline)");
                return false;
            }

            // Test Ollama client
            OllamaClient ollamaClient = new OllamaClient(ollamaUrl, model);

            // Test a simple query with timeout
            CompletableFuture<String> testQuery = ollamaClient.generateText("Test connection");

            try {
                String response = testQuery.get(10, TimeUnit.SECONDS);
                if (response != null && !response.isEmpty()) {
                    log.info("✅ Ollama connection successful");
                    log.info("  Response preview: {}",
                        response.length() > 50 ? response.substring(0, 50) + "..." : response);
                    return true;
                } else {
                    log.warn("⚠️ Ollama responded but with empty response");
                    return false;
                }
            } catch (Exception e) {
                log.warn("⚠️ Ollama connection timeout or failed", e);
                return false;
            }

        } catch (Exception e) {
            log.warn("⚠️ Ollama connection test failed", e);
            return false;
        }
    }

    private static boolean testWikiConnection(AiAutonomousConfig config) {
        log.info("\n" + "-".repeat(40));
        log.info("TESTING WIKI CONNECTION");
        log.info("-".repeat(40));

        try {
            if (!config.enableWikiIntegration()) {
                log.info("ℹ️ Wiki integration disabled in configuration");
                return true;
            }

            log.info("Wiki Configuration:");
            log.info("  Integration enabled: {}", config.enableWikiIntegration());
            log.info("  Cache time: {} hours", config.wikiCacheTime());
            log.info("  Max requests/hour: {}", config.maxWikiRequests());

            // Test basic wiki connectivity
            String wikiUrl = "https://oldschool.runescape.wiki/api.php";
            boolean httpOk = testHttpConnection(wikiUrl);
            if (!httpOk) {
                log.warn("⚠️ Wiki HTTP connection failed");
                return false;
            }

            // Test Wiki integration
            WikiIntegration wikiIntegration = new WikiIntegration();

            log.info("✅ Wiki integration created successfully");
            log.info("  Wiki URL accessible: {}", wikiUrl);

            return true;

        } catch (Exception e) {
            log.warn("⚠️ Wiki connection test failed", e);
            return false;
        }
    }

    private static boolean testHttpConnection(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(CONNECTION_TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            boolean success = responseCode >= 200 && responseCode < 400;

            if (success) {
                log.debug("✅ HTTP connection to {} successful ({})", urlString, responseCode);
            } else {
                log.warn("⚠️ HTTP connection to {} failed with code {}", urlString, responseCode);
            }

            connection.disconnect();
            return success;

        } catch (Exception e) {
            log.warn("⚠️ HTTP connection to {} failed", urlString, e);
            return false;
        }
    }

    private static String maskPassword(String password) {
        if (password == null || password.length() <= 2) {
            return "***";
        }
        return password.substring(0, 2) + "*".repeat(password.length() - 2);
    }

    /**
     * Quick network connectivity test
     */
    public static boolean testNetworkConnectivity() {
        log.info("Testing basic network connectivity...");

        String[] testUrls = {
            "https://www.google.com",
            "https://oldschool.runescape.wiki",
            "http://192.168.5.141:30474"
        };

        boolean hasConnectivity = false;
        for (String url : testUrls) {
            if (testHttpConnection(url)) {
                log.info("✅ Network connectivity confirmed via {}", url);
                hasConnectivity = true;
                break;
            }
        }

        if (!hasConnectivity) {
            log.error("❌ No network connectivity detected");
        }

        return hasConnectivity;
    }

    /**
     * Test individual system configurations
     */
    public static void testConfigurations(AiAutonomousConfig config) {
        log.info("\n" + "=".repeat(50));
        log.info("TESTING SYSTEM CONFIGURATIONS");
        log.info("=".repeat(50));

        // Test RAG configuration
        log.info("RAG System Configuration:");
        log.info("  Type: {}", config.ragSystemType());
        log.info("  URL: {}", config.ragSystemUrl());
        log.info("  Max results: {}", config.maxSearchResults());
        log.info("  Similarity threshold: {}%", config.similarityThreshold());

        // Test AI configuration
        log.info("\nAI Configuration:");
        log.info("  Decision making: {}", config.enableDecisionMaking());
        log.info("  Action execution: {}", config.enableActionExecution());
        log.info("  Memory system: {}", config.enableMemorySystem());
        log.info("  Learning: {}", config.enableLearning());

        // Test safety configuration
        log.info("\nSafety Configuration:");
        log.info("  Antiban measures: {}", config.useAntibanMeasures());
        log.info("  Human-like behavior: {}%", config.humanlikeBehavior());
        log.info("  Max session time: {} hours", config.maxSessionTime());
        log.info("  Emergency stop phrase: '{}'", config.emergencyStop());

        log.info("\n✅ All configurations loaded successfully");
    }

    /**
     * Main test runner
     */
    public static void runAllTests(AiAutonomousConfig config) {
        log.info("Starting comprehensive system tests...");

        // Test network first
        if (!testNetworkConnectivity()) {
            log.error("❌ Network connectivity failed - aborting tests");
            return;
        }

        // Test configurations
        testConfigurations(config);

        // Test all connections
        testAllConnections(config);

        log.info("\n🎉 Comprehensive testing completed!");
    }
}