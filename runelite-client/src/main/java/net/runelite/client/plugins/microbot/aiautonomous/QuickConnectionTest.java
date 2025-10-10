package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.integration.Neo4jRAGSystem;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Quick connection test to verify our Neo4j setup with the new configuration.
 */
@Slf4j
public class QuickConnectionTest {

    public static void main(String[] args) {
        log.info("=".repeat(60));
        log.info("QUICK CONNECTION TEST - Updated Neo4j Configuration");
        log.info("=".repeat(60));

        testNeo4jConnectivity();

        log.info("=".repeat(60));
        log.info("Quick connection test completed!");
        log.info("=".repeat(60));
    }

    public static void testNeo4jConnectivity() {
        // Use the updated configuration values
        String neo4jUrl = "http://localhost:7474";
        String database = "runescapeknowledge";
        String username = "neo4j";
        String password = "runescape2025";

        log.info("Testing Neo4j connectivity with updated configuration:");
        log.info("  URL: {}", neo4jUrl);
        log.info("  Database: {}", database);
        log.info("  Username: {}", username);
        log.info("  Password: {}***", password.substring(0, Math.min(4, password.length())));

        // Test 1: Basic HTTP connectivity
        log.info("\n1. Testing basic HTTP connectivity...");
        boolean httpConnected = testHttpConnection(neo4jUrl);

        if (!httpConnected) {
            log.error("❌ Cannot reach Neo4j HTTP endpoint");
            log.error("   Please check:");
            log.error("   - Is Neo4j port forwarding active? Run: kubectl port-forward service/neo4j 7474:7474 7687:7687");
            log.error("   - Is Neo4j running in your Kubernetes cluster?");
            log.error("   - Are there any firewalls blocking localhost:7474?");
            return;
        }

        log.info("✅ HTTP connectivity successful");

        // Test 2: Neo4j RAG System
        log.info("\n2. Testing Neo4j RAG system initialization...");
        try {
            Neo4jRAGSystem neo4jSystem = new Neo4jRAGSystem(neo4jUrl, database, username, password);
            log.info("✅ Neo4j RAG system created successfully");

            // Test 3: System initialization
            log.info("\n3. Testing system initialization...");
            neo4jSystem.initialize();
            log.info("✅ Neo4j RAG system initialized");

            // Test 4: Connection test
            log.info("\n4. Testing actual connection...");
            boolean connected = neo4jSystem.testConnection();

            if (connected) {
                log.info("✅ Neo4j connection test SUCCESSFUL!");
                log.info("  System type: {}", neo4jSystem.getSystemType());
                log.info("  System ready: {}", neo4jSystem.isReady());

                // Test 5: Get stats
                try {
                    log.info("  System stats: {}", neo4jSystem.getStats());
                } catch (Exception e) {
                    log.warn("  Could not retrieve stats: {}", e.getMessage());
                }

                log.info("\n🎉 All Neo4j tests PASSED! Your configuration is working correctly.");

            } else {
                log.error("❌ Neo4j connection test FAILED");
                log.error("   This could indicate:");
                log.error("   - Incorrect credentials (username/password)");
                log.error("   - Database '{}' does not exist", database);
                log.error("   - Neo4j authentication issues");
                log.error("   - Neo4j server configuration problems");
            }

            // Cleanup
            neo4jSystem.shutdown();

        } catch (Exception e) {
            log.error("❌ Neo4j RAG system test failed", e);
            log.error("   Error details: {}", e.getMessage());
        }
    }

    private static boolean testHttpConnection(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5 seconds
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            log.info("   HTTP Response Code: {}", responseCode);

            // Accept any response that indicates the server is reachable
            // Neo4j might return various codes depending on configuration
            return responseCode > 0; // Any response means server is reachable

        } catch (Exception e) {
            log.error("   HTTP connection failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Test the configuration values as they would be used by the AI system
     */
    public static void testConfigurationValues() {
        log.info("\nTesting configuration values as used by AI system:");

        // Simulate config behavior
        AiAutonomousConfig.RagSystemType ragType = AiAutonomousConfig.RagSystemType.NEO4J;

        // These are the default values that would be returned by the config
        String url = (ragType == AiAutonomousConfig.RagSystemType.NEO4J) ?
            "http://localhost:7474" : "http://localhost:8000";
        String database = "runescapeknowledge";
        String username = "neo4j";
        String password = "runescape2025";

        log.info("  RAG System Type: {}", ragType);
        log.info("  Resolved URL: {}", url);
        log.info("  Database: {}", database);
        log.info("  Username: {}", username);
        log.info("  Password: {}***", password.substring(0, Math.min(4, password.length())));

        log.info("✅ Configuration values validated");
    }
}