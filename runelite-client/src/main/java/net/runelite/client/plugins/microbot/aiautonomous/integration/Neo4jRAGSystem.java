package net.runelite.client.plugins.microbot.aiautonomous.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Neo4j implementation of the RAG system using Cypher queries over HTTP.
 * Stores knowledge as nodes with relationships for semantic search.
 */
@Slf4j
public class Neo4jRAGSystem implements RAGSystemInterface {

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final String database;
    private final String username;
    private final String password;

    private boolean isConnected = false;
    private boolean isInitialized = false;

    public Neo4jRAGSystem(String baseUrl, String database, String username, String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.database = database;
        this.username = username;
        this.password = password;
        this.gson = new Gson();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        initialize();
    }

    @Override
    public void initialize() {
        try {
            // Test connection
            if (testConnection()) {
                // Create indexes and constraints
                createSchema();

                // Populate initial knowledge if database is empty
                if (isDatabaseEmpty()) {
                    populateBasicKnowledge();
                }

                isInitialized = true;
                log.info("Neo4j RAG system initialized successfully");
            }

        } catch (Exception e) {
            log.error("Failed to initialize Neo4j RAG system", e);
        }
    }

    @Override
    public boolean testConnection() {
        try {
            log.info("Testing Neo4j connection to: {}", baseUrl);
            String query = "RETURN 1 as test";
            JsonObject result = executeCypher(query);

            if (result == null) {
                log.warn("Neo4j connection test returned null result");
                isConnected = false;
                return false;
            }

            if (result.has("errors")) {
                JsonArray errors = result.getAsJsonArray("errors");
                if (errors.size() > 0) {
                    log.error("Neo4j connection test returned errors: {}", errors);
                    isConnected = false;
                    return false;
                }
            }

            // Check if we have results
            if (result.has("results")) {
                JsonArray results = result.getAsJsonArray("results");
                if (results.size() > 0) {
                    log.info("Successfully connected to Neo4j at {}", baseUrl);
                    isConnected = true;
                    return true;
                }
            }

            log.warn("Neo4j connection test - unexpected response format: {}", result);
            isConnected = false;
            return false;

        } catch (Exception e) {
            log.error("Error testing Neo4j connection to {}", baseUrl, e);
            isConnected = false;
            return false;
        }
    }

    @Override
    public CompletableFuture<List<KnowledgeEntry>> searchKnowledge(String query, int maxResults, double minSimilarity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isReady()) {
                    log.warn("Neo4j RAG system not ready for search");
                    return new ArrayList<>();
                }

                // Use full-text search and semantic similarity
                String cypher = String.format(
                    "CALL db.index.fulltext.queryNodes('knowledge_content', $query) " +
                    "YIELD node, score " +
                    "WHERE score >= $minSimilarity " +
                    "RETURN node.id as id, node.content as content, node.category as category, " +
                    "       node.type as type, node.priority as priority, score " +
                    "ORDER BY score DESC " +
                    "LIMIT $maxResults"
                );

                Map<String, Object> parameters = new HashMap<>();
                parameters.put("query", query);
                parameters.put("minSimilarity", minSimilarity);
                parameters.put("maxResults", maxResults);

                JsonObject result = executeCypherWithParams(cypher, parameters);

                // Check if the result contains an error about missing fulltext index
                if (result != null && result.has("errors")) {
                    JsonArray errors = result.getAsJsonArray("errors");
                    boolean hasIndexError = false;
                    for (int i = 0; i < errors.size(); i++) {
                        JsonObject error = errors.get(i).getAsJsonObject();
                        if (error.has("message") &&
                            error.get("message").getAsString().contains("knowledge_content")) {
                            hasIndexError = true;
                            break;
                        }
                    }

                    if (hasIndexError) {
                        log.warn("Fulltext index missing, falling back to basic text search");
                        return performFallbackSearch(query, maxResults, minSimilarity);
                    }
                }

                return parseSearchResults(result, minSimilarity);

            } catch (Exception e) {
                log.error("Error searching Neo4j knowledge", e);
                // Try fallback search as last resort
                try {
                    return performFallbackSearch(query, maxResults, minSimilarity);
                } catch (Exception fallbackError) {
                    log.error("Fallback search also failed", fallbackError);
                    return new ArrayList<>();
                }
            }
        });
    }

    /**
     * Fallback search method that doesn't rely on fulltext index
     */
    private List<KnowledgeEntry> performFallbackSearch(String query, int maxResults, double minSimilarity) {
        try {
            // Basic text search using CONTAINS instead of fulltext index
            String cypher =
                "MATCH (k:Knowledge) " +
                "WHERE toLower(k.content) CONTAINS toLower($query) " +
                "   OR toLower(k.category) CONTAINS toLower($query) " +
                "   OR toLower(k.type) CONTAINS toLower($query) " +
                "RETURN k.id as id, k.content as content, k.category as category, " +
                "       k.type as type, k.priority as priority, 0.5 as score " +
                "ORDER BY k.priority DESC, size(k.content) ASC " +
                "LIMIT $maxResults";

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("query", query);
            parameters.put("maxResults", maxResults);

            JsonObject result = executeCypherWithParams(cypher, parameters);
            return parseSearchResults(result, 0.0); // Lower threshold for fallback

        } catch (Exception e) {
            log.error("Error in fallback search", e);
            return new ArrayList<>();
        }
    }

    @Override
    public CompletableFuture<Boolean> addKnowledge(KnowledgeEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isReady()) {
                    log.warn("Neo4j RAG system not ready for adding knowledge");
                    return false;
                }

                String cypher =
                    "MERGE (k:Knowledge {id: $id}) " +
                    "SET k.content = $content, " +
                    "    k.category = $category, " +
                    "    k.type = $type, " +
                    "    k.priority = $priority, " +
                    "    k.created_timestamp = timestamp(), " +
                    "    k.updated_timestamp = timestamp() " +
                    "RETURN k.id as id";

                Map<String, Object> parameters = new HashMap<>();
                parameters.put("id", entry.getId());
                parameters.put("content", entry.getContent());

                Map<String, String> metadata = entry.getMetadata();
                if (metadata != null) {
                    parameters.put("category", metadata.getOrDefault("category", "general"));
                    parameters.put("type", metadata.getOrDefault("type", "knowledge"));
                    parameters.put("priority", metadata.getOrDefault("priority", "medium"));
                } else {
                    parameters.put("category", "general");
                    parameters.put("type", "knowledge");
                    parameters.put("priority", "medium");
                }

                JsonObject result = executeCypherWithParams(cypher, parameters);
                boolean success = result != null && !result.has("errors");

                if (success) {
                    log.debug("Added knowledge entry to Neo4j: {}", entry.getId());
                    // Create relationships based on category
                    createKnowledgeRelationships(entry);
                } else {
                    log.error("Failed to add knowledge to Neo4j");
                }

                return success;

            } catch (Exception e) {
                log.error("Error adding knowledge to Neo4j", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> addKnowledgeBatch(List<KnowledgeEntry> entries) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isReady() || entries.isEmpty()) {
                    return false;
                }

                // Use UNWIND for batch insert
                StringBuilder cypherBuilder = new StringBuilder();
                cypherBuilder.append("UNWIND $entries as entry ");
                cypherBuilder.append("MERGE (k:Knowledge {id: entry.id}) ");
                cypherBuilder.append("SET k.content = entry.content, ");
                cypherBuilder.append("    k.category = entry.category, ");
                cypherBuilder.append("    k.type = entry.type, ");
                cypherBuilder.append("    k.priority = entry.priority, ");
                cypherBuilder.append("    k.created_timestamp = timestamp(), ");
                cypherBuilder.append("    k.updated_timestamp = timestamp() ");
                cypherBuilder.append("RETURN count(k) as created");

                List<Map<String, Object>> entryMaps = new ArrayList<>();
                for (KnowledgeEntry entry : entries) {
                    Map<String, Object> entryMap = new HashMap<>();
                    entryMap.put("id", entry.getId());
                    entryMap.put("content", entry.getContent());

                    Map<String, String> metadata = entry.getMetadata();
                    if (metadata != null) {
                        entryMap.put("category", metadata.getOrDefault("category", "general"));
                        entryMap.put("type", metadata.getOrDefault("type", "knowledge"));
                        entryMap.put("priority", metadata.getOrDefault("priority", "medium"));
                    } else {
                        entryMap.put("category", "general");
                        entryMap.put("type", "knowledge");
                        entryMap.put("priority", "medium");
                    }
                    entryMaps.add(entryMap);
                }

                Map<String, Object> parameters = new HashMap<>();
                parameters.put("entries", entryMaps);

                JsonObject result = executeCypherWithParams(cypherBuilder.toString(), parameters);
                boolean success = false;

                if (result != null) {
                    // Check if there are errors
                    if (result.has("errors")) {
                        JsonArray errors = result.getAsJsonArray("errors");
                        if (errors.size() > 0) {
                            log.error("Failed to add knowledge batch to Neo4j - errors: {}", errors);
                            success = false;
                        } else {
                            // No errors, check if we have data indicating success
                            success = result.has("data");
                            if (success) {
                                log.info("Added {} knowledge entries to Neo4j", entries.size());
                            } else {
                                log.warn("Neo4j batch operation completed but no data returned");
                                success = true; // Assume success if no errors and query executed
                            }
                        }
                    } else {
                        // No errors property, check for data
                        success = result.has("data");
                        if (success) {
                            log.info("Added {} knowledge entries to Neo4j", entries.size());
                        } else {
                            log.warn("Neo4j batch operation completed but no data or errors returned");
                            success = true; // Assume success if query executed without errors
                        }
                    }
                } else {
                    log.error("Failed to add knowledge batch to Neo4j - null result");
                    success = false;
                }

                return success;

            } catch (Exception e) {
                log.error("Error adding knowledge batch to Neo4j", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> updateKnowledge(KnowledgeEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isReady()) {
                    return false;
                }

                String cypher =
                    "MATCH (k:Knowledge {id: $id}) " +
                    "SET k.content = $content, " +
                    "    k.category = $category, " +
                    "    k.type = $type, " +
                    "    k.priority = $priority, " +
                    "    k.updated_timestamp = timestamp() " +
                    "RETURN k.id as id";

                Map<String, Object> parameters = new HashMap<>();
                parameters.put("id", entry.getId());
                parameters.put("content", entry.getContent());

                Map<String, String> metadata = entry.getMetadata();
                if (metadata != null) {
                    parameters.put("category", metadata.getOrDefault("category", "general"));
                    parameters.put("type", metadata.getOrDefault("type", "knowledge"));
                    parameters.put("priority", metadata.getOrDefault("priority", "medium"));
                } else {
                    parameters.put("category", "general");
                    parameters.put("type", "knowledge");
                    parameters.put("priority", "medium");
                }

                JsonObject result = executeCypherWithParams(cypher, parameters);
                boolean success = result != null && !result.has("errors");

                if (success) {
                    log.debug("Updated knowledge entry in Neo4j: {}", entry.getId());
                } else {
                    log.error("Failed to update knowledge in Neo4j");
                }

                return success;

            } catch (Exception e) {
                log.error("Error updating knowledge in Neo4j", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteKnowledge(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isReady()) {
                    return false;
                }

                String cypher =
                    "MATCH (k:Knowledge {id: $id}) " +
                    "DETACH DELETE k " +
                    "RETURN count(k) as deleted";

                Map<String, Object> parameters = new HashMap<>();
                parameters.put("id", id);

                JsonObject result = executeCypherWithParams(cypher, parameters);
                boolean success = result != null && !result.has("errors");

                if (success) {
                    log.debug("Deleted knowledge entry from Neo4j: {}", id);
                } else {
                    log.error("Failed to delete knowledge from Neo4j");
                }

                return success;

            } catch (Exception e) {
                log.error("Error deleting knowledge from Neo4j", e);
                return false;
            }
        });
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("system_type", "Neo4j");
        stats.put("connected", isConnected);
        stats.put("initialized", isInitialized);
        stats.put("database", database);
        stats.put("base_url", baseUrl);

        try {
            if (isReady()) {
                String cypher = "MATCH (k:Knowledge) RETURN count(k) as total_knowledge";
                JsonObject result = executeCypher(cypher);

                if (result != null && !result.has("errors")) {
                    JsonArray data = result.getAsJsonArray("results")
                            .get(0).getAsJsonObject()
                            .getAsJsonArray("data");
                    if (data.size() > 0) {
                        int total = data.get(0).getAsJsonObject()
                                .getAsJsonArray("row")
                                .get(0).getAsInt();
                        stats.put("total_knowledge_entries", total);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get Neo4j stats", e);
        }

        return stats;
    }

    @Override
    public boolean isReady() {
        return isConnected && isInitialized;
    }

    @Override
    public String getSystemType() {
        return "Neo4j";
    }

    @Override
    public CompletableFuture<Boolean> storeDocument(String id, String content, Map<String, Object> metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isReady()) {
                    log.warn("Neo4j RAG system not ready for storing document");
                    return false;
                }

                String cypher =
                    "MERGE (d:Document {id: $id}) " +
                    "SET d.content = $content, " +
                    "    d.type = $type, " +
                    "    d.created_timestamp = timestamp(), " +
                    "    d.updated_timestamp = timestamp() " +
                    "RETURN d.id as id";

                Map<String, Object> parameters = new HashMap<>();
                parameters.put("id", id);
                parameters.put("content", content);
                parameters.put("type", metadata != null ? metadata.getOrDefault("type", "document") : "document");

                // Add any additional metadata as properties
                if (metadata != null) {
                    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                        if (!entry.getKey().equals("type")) {
                            parameters.put("d_" + entry.getKey(), entry.getValue());
                            cypher = cypher.replace("SET d.content = $content, ",
                                "SET d.content = $content, d." + entry.getKey() + " = $d_" + entry.getKey() + ", ");
                        }
                    }
                }

                JsonObject result = executeCypherWithParams(cypher, parameters);
                boolean success = result != null && !result.has("errors");

                if (success) {
                    log.debug("Stored document in Neo4j: {}", id);
                } else {
                    log.error("Failed to store document in Neo4j");
                }

                return success;

            } catch (Exception e) {
                log.error("Error storing document in Neo4j", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<KnowledgeEntry>> searchDocuments(String query, int maxResults) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isReady()) {
                    log.warn("Neo4j RAG system not ready for search");
                    return new ArrayList<>();
                }

                // Search both Knowledge and Document nodes
                String cypher =
                    "CALL { " +
                    "  CALL db.index.fulltext.queryNodes('knowledge_content', $query) " +
                    "  YIELD node, score " +
                    "  WHERE node:Knowledge " +
                    "  RETURN node.id as id, node.content as content, 'knowledge' as nodeType, score " +
                    "  UNION " +
                    "  MATCH (d:Document) " +
                    "  WHERE d.content CONTAINS $query " +
                    "  RETURN d.id as id, d.content as content, 'document' as nodeType, 1.0 as score " +
                    "} " +
                    "RETURN id, content, nodeType, score " +
                    "ORDER BY score DESC " +
                    "LIMIT $maxResults";

                Map<String, Object> parameters = new HashMap<>();
                parameters.put("query", query);
                parameters.put("maxResults", maxResults);

                JsonObject result = executeCypherWithParams(cypher, parameters);

                // Check if the result contains an error about missing fulltext index
                if (result != null && result.has("errors")) {
                    JsonArray errors = result.getAsJsonArray("errors");
                    boolean hasIndexError = false;
                    for (int i = 0; i < errors.size(); i++) {
                        JsonObject error = errors.get(i).getAsJsonObject();
                        if (error.has("message") &&
                            error.get("message").getAsString().contains("knowledge_content")) {
                            hasIndexError = true;
                            break;
                        }
                    }

                    if (hasIndexError) {
                        log.warn("Fulltext index missing in searchDocuments, falling back to basic text search");
                        return performFallbackDocumentSearch(query, maxResults);
                    }
                }

                return parseDocumentSearchResults(result);

            } catch (Exception e) {
                log.error("Error searching documents in Neo4j", e);
                // Try fallback search as last resort
                try {
                    return performFallbackDocumentSearch(query, maxResults);
                } catch (Exception fallbackError) {
                    log.error("Fallback document search also failed", fallbackError);
                    return new ArrayList<>();
                }
            }
        });
    }

    /**
     * Fallback document search method that doesn't rely on fulltext index
     */
    private List<KnowledgeEntry> performFallbackDocumentSearch(String query, int maxResults) {
        try {
            // Basic text search using CONTAINS instead of fulltext index
            String cypher =
                "CALL { " +
                "  MATCH (k:Knowledge) " +
                "  WHERE toLower(k.content) CONTAINS toLower($query) " +
                "     OR toLower(k.category) CONTAINS toLower($query) " +
                "     OR toLower(k.type) CONTAINS toLower($query) " +
                "  RETURN k.id as id, k.content as content, 'knowledge' as nodeType, 0.5 as score " +
                "  UNION " +
                "  MATCH (d:Document) " +
                "  WHERE toLower(d.content) CONTAINS toLower($query) " +
                "  RETURN d.id as id, d.content as content, 'document' as nodeType, 0.5 as score " +
                "} " +
                "RETURN id, content, nodeType, score " +
                "ORDER BY score DESC, size(content) ASC " +
                "LIMIT $maxResults";

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("query", query);
            parameters.put("maxResults", maxResults);

            JsonObject result = executeCypherWithParams(cypher, parameters);
            return parseDocumentSearchResults(result);

        } catch (Exception e) {
            log.error("Error in fallback document search", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void shutdown() {
        log.info("Neo4j RAG system shutdown");
    }

    private JsonObject executeCypher(String cypher) {
        return executeCypherWithParams(cypher, new HashMap<>());
    }

    private JsonObject executeCypherWithParams(String cypher, Map<String, Object> parameters) {
        try {
            log.debug("Executing Neo4j query: {}", cypher);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("statement", cypher);

            if (!parameters.isEmpty()) {
                JsonObject params = gson.toJsonTree(parameters).getAsJsonObject();
                requestBody.add("parameters", params);
            }

            JsonArray statements = new JsonArray();
            statements.add(requestBody);

            JsonObject fullRequest = new JsonObject();
            fullRequest.add("statements", statements);

            RequestBody body = RequestBody.create(
                MediaType.get("application/json"),
                gson.toJson(fullRequest)
            );

            String credentials = Credentials.basic(username, password);
            String url = baseUrl + "/db/" + database + "/tx/commit";

            log.debug("Making Neo4j request to: {}", url);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", credentials)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                log.debug("Neo4j response: HTTP {}", response.code());

                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    log.debug("Neo4j response body: {}", responseBody);
                    return gson.fromJson(responseBody, JsonObject.class);
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "no body";
                    log.error("Neo4j query failed: HTTP {} - {}", response.code(), errorBody);
                    return null;
                }
            }

        } catch (Exception e) {
            log.error("Error executing Neo4j query: {}", cypher, e);
            return null;
        }
    }

    private void createSchema() {
        try {
            // First check if the fulltext index already exists
            String checkIndex = "SHOW INDEXES YIELD name WHERE name = 'knowledge_content'";
            JsonObject indexResult = executeCypher(checkIndex);

            boolean indexExists = false;
            if (indexResult != null && indexResult.has("data")) {
                JsonArray data = indexResult.getAsJsonArray("data");
                indexExists = data.size() > 0;
            }

            if (!indexExists) {
                log.info("Creating fulltext index 'knowledge_content'...");
                // Create fulltext index for content search
                String createIndex =
                    "CALL db.index.fulltext.createNodeIndex('knowledge_content', ['Knowledge'], ['content'])";
                JsonObject result = executeCypher(createIndex);

                if (result != null) {
                    log.info("Successfully created fulltext index 'knowledge_content'");

                    // Wait for index to be ready
                    Thread.sleep(2000);

                    // Verify index was created
                    JsonObject verifyResult = executeCypher(checkIndex);
                    if (verifyResult != null && verifyResult.has("data")) {
                        JsonArray verifyData = verifyResult.getAsJsonArray("data");
                        if (verifyData.size() > 0) {
                            log.info("Verified fulltext index 'knowledge_content' is ready");
                        } else {
                            log.error("Fulltext index 'knowledge_content' was not created successfully");
                        }
                    }
                } else {
                    log.error("Failed to create fulltext index 'knowledge_content' - null result");
                }
            } else {
                log.info("Fulltext index 'knowledge_content' already exists");
            }

            // Create constraint for unique knowledge IDs
            String createConstraint =
                "CREATE CONSTRAINT knowledge_id_unique IF NOT EXISTS FOR (k:Knowledge) REQUIRE k.id IS UNIQUE";
            JsonObject constraintResult = executeCypher(createConstraint);

            if (constraintResult != null) {
                log.info("Created or verified unique constraint for knowledge IDs");
            } else {
                log.warn("Failed to create constraint for knowledge IDs");
            }

            log.info("Created Neo4j schema for knowledge base");

        } catch (Exception e) {
            log.error("Failed to create Neo4j schema", e);
        }
    }

    private boolean isDatabaseEmpty() {
        try {
            String cypher = "MATCH (k:Knowledge) RETURN count(k) as count";
            JsonObject result = executeCypher(cypher);

            if (result != null && !result.has("errors")) {
                JsonArray data = result.getAsJsonArray("results")
                        .get(0).getAsJsonObject()
                        .getAsJsonArray("data");
                if (data.size() > 0) {
                    int count = data.get(0).getAsJsonObject()
                            .getAsJsonArray("row")
                            .get(0).getAsInt();
                    return count == 0;
                }
            }
        } catch (Exception e) {
            log.error("Error checking if Neo4j database is empty", e);
        }
        return true;
    }

    private void populateBasicKnowledge() {
        log.info("Populating basic RuneScape knowledge in Neo4j");

        List<KnowledgeEntry> basicKnowledge = createBasicRuneScapeKnowledge();
        addKnowledgeBatch(basicKnowledge).thenAccept(success -> {
            if (success) {
                log.info("Successfully populated basic knowledge in Neo4j");
            } else {
                log.error("Failed to populate basic knowledge in Neo4j");
            }
        });
    }

    private List<KnowledgeEntry> createBasicRuneScapeKnowledge() {
        List<KnowledgeEntry> entries = new ArrayList<>();

        // Basic game mechanics
        entries.add(createKnowledgeEntry("combat_basics",
                "Combat in RuneScape uses a combat triangle: Melee beats Ranged, Ranged beats Magic, Magic beats Melee. Higher combat levels indicate stronger opponents.",
                "category", "combat", "type", "basic"));

        entries.add(createKnowledgeEntry("food_healing",
                "Food heals health points. Higher level foods heal more. Always carry food when fighting monsters. Eat when health gets low.",
                "category", "combat", "type", "safety"));

        entries.add(createKnowledgeEntry("skill_training",
                "Skills are trained by performing related activities. Higher level activities give more experience but may require better equipment or stats.",
                "category", "skills", "type", "basic"));

        entries.add(createKnowledgeEntry("quest_benefits",
                "Quests provide experience rewards, unlock new areas, and give access to new content. Complete quests that match your current skill levels.",
                "category", "quests", "type", "progression"));

        entries.add(createKnowledgeEntry("banking",
                "Banks store items safely. Use banks to store valuable items and supplies. Bank locations are in major cities and some other areas.",
                "category", "items", "type", "basic"));

        entries.add(createKnowledgeEntry("wilderness_danger",
                "The Wilderness is a dangerous PvP area north of Varrock. Other players can attack you there. Avoid unless specifically needed.",
                "category", "areas", "type", "safety"));

        entries.add(createKnowledgeEntry("death_mechanics",
                "When you die, you lose items unless they're protected. Protect valuable items with Protect Item prayer or by keeping them as your 3 most valuable items.",
                "category", "combat", "type", "safety"));

        entries.add(createKnowledgeEntry("grand_exchange",
                "The Grand Exchange in Varrock is the main trading hub. You can buy and sell items with other players through the GE system.",
                "category", "trading", "type", "basic"));

        return entries;
    }

    private KnowledgeEntry createKnowledgeEntry(String id, String content, String... metadata) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(id);
        entry.setContent(content);

        Map<String, String> metaMap = new HashMap<>();
        for (int i = 0; i < metadata.length; i += 2) {
            if (i + 1 < metadata.length) {
                metaMap.put(metadata[i], metadata[i + 1]);
            }
        }
        entry.setMetadata(metaMap);

        return entry;
    }

    private void createKnowledgeRelationships(KnowledgeEntry entry) {
        try {
            Map<String, String> metadata = entry.getMetadata();
            if (metadata == null) return;

            String category = metadata.get("category");
            if (category != null) {
                // Create relationships between knowledge entries in the same category
                String cypher =
                    "MATCH (k1:Knowledge {id: $id}) " +
                    "MATCH (k2:Knowledge) " +
                    "WHERE k2.category = $category AND k1.id <> k2.id " +
                    "MERGE (k1)-[r:RELATED_TO]-(k2) " +
                    "SET r.relationship_type = 'same_category'";

                Map<String, Object> parameters = new HashMap<>();
                parameters.put("id", entry.getId());
                parameters.put("category", category);

                executeCypherWithParams(cypher, parameters);
            }

        } catch (Exception e) {
            log.warn("Failed to create knowledge relationships", e);
        }
    }

    private List<KnowledgeEntry> parseSearchResults(JsonObject result, double minSimilarity) {
        List<KnowledgeEntry> results = new ArrayList<>();

        try {
            if (result.has("errors") && result.getAsJsonArray("errors").size() > 0) {
                log.error("Neo4j search returned errors: {}", result.getAsJsonArray("errors"));
                return results;
            }

            JsonArray data = result.getAsJsonArray("results")
                    .get(0).getAsJsonObject()
                    .getAsJsonArray("data");

            for (int i = 0; i < data.size(); i++) {
                JsonArray row = data.get(i).getAsJsonObject().getAsJsonArray("row");

                String id = row.get(0).getAsString();
                String content = row.get(1).getAsString();
                String category = row.get(2).isJsonNull() ? "general" : row.get(2).getAsString();
                String type = row.get(3).isJsonNull() ? "knowledge" : row.get(3).getAsString();
                String priority = row.get(4).isJsonNull() ? "medium" : row.get(4).getAsString();
                double score = row.get(5).getAsDouble();

                if (score >= minSimilarity) {
                    KnowledgeEntry entry = new KnowledgeEntry();
                    entry.setId(id);
                    entry.setContent(content);
                    entry.setSimilarity(score);

                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("category", category);
                    metadata.put("type", type);
                    metadata.put("priority", priority);
                    entry.setMetadata(metadata);

                    results.add(entry);
                }
            }

        } catch (Exception e) {
            log.error("Error parsing Neo4j search results", e);
        }

        return results;
    }

    private List<KnowledgeEntry> parseDocumentSearchResults(JsonObject result) {
        List<KnowledgeEntry> results = new ArrayList<>();

        try {
            if (result.has("errors") && result.getAsJsonArray("errors").size() > 0) {
                log.error("Neo4j document search returned errors: {}", result.getAsJsonArray("errors"));
                return results;
            }

            JsonArray data = result.getAsJsonArray("results")
                    .get(0).getAsJsonObject()
                    .getAsJsonArray("data");

            for (int i = 0; i < data.size(); i++) {
                JsonArray row = data.get(i).getAsJsonObject().getAsJsonArray("row");

                String id = row.get(0).getAsString();
                String content = row.get(1).getAsString();
                String nodeType = row.get(2).getAsString();
                double score = row.get(3).getAsDouble();

                KnowledgeEntry entry = new KnowledgeEntry();
                entry.setId(id);
                entry.setContent(content);
                entry.setSimilarity(score);

                Map<String, String> metadata = new HashMap<>();
                metadata.put("type", nodeType);
                entry.setMetadata(metadata);

                results.add(entry);
            }

        } catch (Exception e) {
            log.error("Error parsing Neo4j document search results", e);
        }

        return results;
    }
}