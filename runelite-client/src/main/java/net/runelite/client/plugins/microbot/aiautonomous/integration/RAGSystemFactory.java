package net.runelite.client.plugins.microbot.aiautonomous.integration;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig;
import net.runelite.client.plugins.microbot.aiautonomous.AiAutonomousConfig.RagSystemType;

/**
 * Factory for creating RAG system implementations based on configuration.
 */
@Slf4j
public class RAGSystemFactory {

    /**
     * Create a RAG system instance based on configuration.
     *
     * @param config The AI autonomous configuration
     * @return RAG system implementation
     */
    public static RAGSystemInterface createRAGSystem(AiAutonomousConfig config) {
        RagSystemType systemType = config.ragSystemType();

        log.info("Creating RAG system of type: {}", systemType);

        try {
            switch (systemType) {
                case CHROMA:
                    return createChromaRAGSystem(config);

                case NEO4J:
                    return createNeo4jRAGSystem(config);

                default:
                    log.warn("Unknown RAG system type: {}, defaulting to Chroma", systemType);
                    return createChromaRAGSystem(config);
            }

        } catch (Exception e) {
            log.error("Failed to create RAG system of type {}, falling back to Chroma", systemType, e);
            return createChromaRAGSystem(config);
        }
    }

    /**
     * Create a Chroma vector database RAG system.
     */
    private static RAGSystemInterface createChromaRAGSystem(AiAutonomousConfig config) {
        String url = config.ragSystemUrl();
        String collection = config.chromaCollectionName();

        log.info("Creating Chroma RAG system - URL: {}, Collection: {}", url, collection);

        return new RAGSystem(url, collection);
    }

    /**
     * Create a Neo4j graph database RAG system.
     */
    private static RAGSystemInterface createNeo4jRAGSystem(AiAutonomousConfig config) {
        String url = config.ragSystemUrl();
        String database = config.neo4jDatabase();
        String username = config.neo4jUsername();
        String password = config.neo4jPassword();

        log.info("Creating Neo4j RAG system - URL: {}, Database: {}, Username: {}",
                url, database, username);

        return new Neo4jRAGSystem(url, database, username, password);
    }

    /**
     * Test which RAG systems are available and recommend the best one.
     *
     * @param config The configuration to test with
     * @return Recommended RAG system type
     */
    public static RagSystemType testAndRecommendRAGSystem(AiAutonomousConfig config) {
        log.info("Testing available RAG systems...");

        // Test Chroma
        boolean chromaAvailable = false;
        try {
            RAGSystemInterface chroma = createChromaRAGSystem(config);
            chromaAvailable = chroma.testConnection();
            log.info("Chroma availability: {}", chromaAvailable);
        } catch (Exception e) {
            log.warn("Chroma test failed", e);
        }

        // Test Neo4j
        boolean neo4jAvailable = false;
        try {
            RAGSystemInterface neo4j = createNeo4jRAGSystem(config);
            neo4jAvailable = neo4j.testConnection();
            log.info("Neo4j availability: {}", neo4jAvailable);
        } catch (Exception e) {
            log.warn("Neo4j test failed", e);
        }

        // Recommend based on availability and current config
        RagSystemType current = config.ragSystemType();

        if (current == RagSystemType.CHROMA && chromaAvailable) {
            log.info("Recommending Chroma (current config and available)");
            return RagSystemType.CHROMA;
        } else if (current == RagSystemType.NEO4J && neo4jAvailable) {
            log.info("Recommending Neo4j (current config and available)");
            return RagSystemType.NEO4J;
        } else if (neo4jAvailable) {
            log.info("Recommending Neo4j (available, current not available)");
            return RagSystemType.NEO4J;
        } else if (chromaAvailable) {
            log.info("Recommending Chroma (available, current not available)");
            return RagSystemType.CHROMA;
        } else {
            log.warn("No RAG systems available, defaulting to Chroma");
            return RagSystemType.CHROMA;
        }
    }

    /**
     * Create a RAG system with automatic fallback if the primary choice fails.
     *
     * @param config The configuration
     * @return Working RAG system or null if none work
     */
    public static RAGSystemInterface createWithFallback(AiAutonomousConfig config) {
        RagSystemType primary = config.ragSystemType();
        RagSystemType fallback = (primary == RagSystemType.CHROMA) ?
                                RagSystemType.NEO4J : RagSystemType.CHROMA;

        // Try primary system
        try {
            RAGSystemInterface primarySystem = createRAGSystem(config);
            if (primarySystem.testConnection()) {
                log.info("Successfully created primary RAG system: {}", primary);
                return primarySystem;
            }
        } catch (Exception e) {
            log.warn("Primary RAG system {} failed", primary, e);
        }

        // Try fallback system
        try {
            log.info("Trying fallback RAG system: {}", fallback);

            // Temporarily modify config for fallback
            RAGSystemInterface fallbackSystem = (fallback == RagSystemType.CHROMA) ?
                    createChromaRAGSystem(config) : createNeo4jRAGSystem(config);

            if (fallbackSystem.testConnection()) {
                log.info("Successfully created fallback RAG system: {}", fallback);
                return fallbackSystem;
            }
        } catch (Exception e) {
            log.warn("Fallback RAG system {} also failed", fallback, e);
        }

        log.error("All RAG systems failed, returning null");
        return null;
    }
}