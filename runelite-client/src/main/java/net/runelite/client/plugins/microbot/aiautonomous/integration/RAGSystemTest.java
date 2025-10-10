package net.runelite.client.plugins.microbot.aiautonomous.integration;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;

/**
 * Simple test class to verify RAG system implementations work correctly.
 * This helps validate that both Chroma and Neo4j systems can initialize and connect.
 */
@Slf4j
public class RAGSystemTest {

    public static void testRAGSystems(AiAutonomousConfig config) {
        log.info("Starting RAG system tests...");

        // Test Chroma system
        testChromaSystem(config);

        // Test Neo4j system
        testNeo4jSystem(config);

        // Test factory fallback mechanism
        testFactoryFallback(config);

        log.info("RAG system tests completed");
    }

    private static void testChromaSystem(AiAutonomousConfig config) {
        log.info("Testing Chroma RAG system...");
        try {
            RAGSystem chromaSystem = new RAGSystem("http://localhost:8000", config.chromaCollectionName());
            chromaSystem.initialize();

            if (chromaSystem.testConnection()) {
                log.info("✓ Chroma system connected successfully");
            } else {
                log.warn("✗ Chroma system connection failed");
            }

            log.info("Chroma system type: {}", chromaSystem.getSystemType());
            log.info("Chroma system stats: {}", chromaSystem.getStats());

        } catch (Exception e) {
            log.error("✗ Chroma system test failed", e);
        }
    }

    private static void testNeo4jSystem(AiAutonomousConfig config) {
        log.info("Testing Neo4j RAG system...");
        try {
            Neo4jRAGSystem neo4jSystem = new Neo4jRAGSystem(
                config.ragSystemUrl(),
                config.neo4jDatabase(),
                config.neo4jUsername(),
                config.neo4jPassword()
            );
            neo4jSystem.initialize();

            if (neo4jSystem.testConnection()) {
                log.info("✓ Neo4j system connected successfully");
            } else {
                log.warn("✗ Neo4j system connection failed");
            }

            log.info("Neo4j system type: {}", neo4jSystem.getSystemType());
            log.info("Neo4j system stats: {}", neo4jSystem.getStats());

        } catch (Exception e) {
            log.error("✗ Neo4j system test failed", e);
        }
    }

    private static void testFactoryFallback(AiAutonomousConfig config) {
        log.info("Testing RAG factory fallback mechanism...");
        try {
            RAGSystemInterface ragSystem = RAGSystemFactory.createWithFallback(config);

            if (ragSystem != null) {
                log.info("✓ Factory created system: {}", ragSystem.getSystemType());
                if (ragSystem.isReady()) {
                    log.info("✓ Factory system is ready for operations");
                } else {
                    log.warn("✗ Factory system not ready");
                }
            } else {
                log.error("✗ Factory failed to create any RAG system");
            }

        } catch (Exception e) {
            log.error("✗ Factory test failed", e);
        }
    }

    /**
     * Quick test for basic functionality without requiring actual connections
     */
    public static void testRAGSystemsOffline() {
        log.info("Running offline RAG system tests...");

        try {
            // Test Chroma system creation
            RAGSystem chromaSystem = new RAGSystem("http://localhost:8000", "test_collection");
            log.info("✓ Chroma system created: {}", chromaSystem.getSystemType());

            // Test Neo4j system creation
            Neo4jRAGSystem neo4jSystem = new Neo4jRAGSystem(
                "http://localhost:7474",
                "neo4j",
                "neo4j",
                "password"
            );
            log.info("✓ Neo4j system created: {}", neo4jSystem.getSystemType());

            log.info("✓ All RAG systems can be instantiated correctly");

        } catch (Exception e) {
            log.error("✗ Offline RAG system test failed", e);
        }
    }
}