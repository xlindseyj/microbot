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
            String query = "RETURN 1 as test";
            JsonObject result = executeCypher(query);

            isConnected = result != null && !result.has("errors");
            if (isConnected) {
                log.info("Successfully connected to Neo4j at {}", baseUrl);
            } else {
                log.warn("Failed to connect to Neo4j at {}", baseUrl);
            }
            return isConnected;

        } catch (Exception e) {
            log.error("Error testing Neo4j connection", e);
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
                return parseSearchResults(result, minSimilarity);

            } catch (Exception e) {
                log.error("Error searching Neo4j knowledge", e);
                return new ArrayList<>();
            }
        });
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
                boolean success = result != null && !result.has("errors");

                if (success) {
                    log.info("Added {} knowledge entries to Neo4j", entries.size());
                } else {
                    log.error("Failed to add knowledge batch to Neo4j");
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
    public void shutdown() {
        log.info("Neo4j RAG system shutdown");
    }

    private JsonObject executeCypher(String cypher) {
        return executeCypherWithParams(cypher, new HashMap<>());
    }

    private JsonObject executeCypherWithParams(String cypher, Map<String, Object> parameters) {
        try {
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
            Request request = new Request.Builder()
                    .url(baseUrl + "/db/" + database + "/tx/commit")
                    .header("Authorization", credentials)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    return gson.fromJson(responseBody, JsonObject.class);
                } else {
                    log.error("Neo4j query failed: HTTP {}", response.code());
                    return null;
                }
            }

        } catch (Exception e) {
            log.error("Error executing Neo4j query", e);
            return null;
        }
    }

    private void createSchema() {
        try {
            // Create fulltext index for content search
            String createIndex =
                "CALL db.index.fulltext.createNodeIndex('knowledge_content', ['Knowledge'], ['content'])";
            executeCypher(createIndex);

            // Create constraint for unique knowledge IDs
            String createConstraint =
                "CREATE CONSTRAINT knowledge_id_unique IF NOT EXISTS FOR (k:Knowledge) REQUIRE k.id IS UNIQUE";
            executeCypher(createConstraint);

            log.info("Created Neo4j schema for knowledge base");

        } catch (Exception e) {
            log.warn("Failed to create Neo4j schema (may already exist)", e);
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
}