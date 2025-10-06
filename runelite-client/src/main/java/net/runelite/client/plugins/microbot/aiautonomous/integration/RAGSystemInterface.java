package net.runelite.client.plugins.microbot.aiautonomous.integration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for RAG (Retrieval Augmented Generation) systems.
 * Allows switching between different RAG implementations like Chroma and Neo4j.
 */
public interface RAGSystemInterface {

    /**
     * Initialize the RAG system connection and setup.
     */
    void initialize();

    /**
     * Test if the RAG system is connected and available.
     * @return true if connected, false otherwise
     */
    boolean testConnection();

    /**
     * Search for relevant knowledge entries.
     * @param query The search query
     * @param maxResults Maximum number of results to return
     * @param minSimilarity Minimum similarity threshold (0.0-1.0)
     * @return Future containing list of matching knowledge entries
     */
    CompletableFuture<List<KnowledgeEntry>> searchKnowledge(String query, int maxResults, double minSimilarity);

    /**
     * Add a single knowledge entry.
     * @param entry The knowledge entry to add
     * @return Future indicating success/failure
     */
    CompletableFuture<Boolean> addKnowledge(KnowledgeEntry entry);

    /**
     * Add multiple knowledge entries in batch.
     * @param entries List of knowledge entries to add
     * @return Future indicating success/failure
     */
    CompletableFuture<Boolean> addKnowledgeBatch(List<KnowledgeEntry> entries);

    /**
     * Update an existing knowledge entry.
     * @param entry The updated knowledge entry
     * @return Future indicating success/failure
     */
    CompletableFuture<Boolean> updateKnowledge(KnowledgeEntry entry);

    /**
     * Delete a knowledge entry by ID.
     * @param id The ID of the entry to delete
     * @return Future indicating success/failure
     */
    CompletableFuture<Boolean> deleteKnowledge(String id);

    /**
     * Get statistics about the knowledge base.
     * @return Map containing statistics
     */
    Map<String, Object> getStats();

    /**
     * Check if the system is properly connected and initialized.
     * @return true if ready for operations
     */
    boolean isReady();

    /**
     * Check if the system is connected.
     * @return true if connected
     */
    default boolean isConnected() {
        return isReady();
    }

    /**
     * Get the type of RAG system.
     * @return System type identifier
     */
    String getSystemType();

    /**
     * Shutdown the RAG system and cleanup resources.
     */
    void shutdown();

    /**
     * Knowledge entry structure used by all RAG implementations.
     */
    class KnowledgeEntry {
        private String id;
        private String content;
        private double similarity;
        private Map<String, String> metadata;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public double getSimilarity() { return similarity; }
        public void setSimilarity(double similarity) { this.similarity = similarity; }

        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

        @Override
        public String toString() {
            return String.format("KnowledgeEntry{id='%s', similarity=%.2f, content='%s'}",
                    id, similarity, content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content);
        }
    }
}