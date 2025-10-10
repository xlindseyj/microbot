package net.runelite.client.plugins.microbot.aiautonomous.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


@Slf4j
public class RAGSystem implements RAGSystemInterface {

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final String collectionName;

    private boolean isConnected = false;
    private boolean collectionExists = false;

    public RAGSystem(String baseUrl, String collectionName) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.collectionName = collectionName;
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
            testConnection();

            if (isConnected) {
                // Create or verify collection exists
                ensureCollectionExists();

                // Initialize with basic RuneScape knowledge if collection is empty
                if (isCollectionEmpty()) {
                    populateBasicKnowledge();
                }
            }

        } catch (Exception e) {
            log.error("Failed to initialize RAG system", e);
        }
    }

    @Override
    public boolean testConnection() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/heartbeat")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                isConnected = response.isSuccessful();
                if (isConnected) {
                    log.info("Successfully connected to Chroma RAG system at {}", baseUrl);
                } else {
                    log.warn("Failed to connect to Chroma: HTTP {}", response.code());
                }
                return isConnected;
            }
        } catch (Exception e) {
            log.error("Error testing RAG connection", e);
            isConnected = false;
            return false;
        }
    }

    private void ensureCollectionExists() {
        try {
            // Check if collection exists
            if (checkCollectionExists()) {
                collectionExists = true;
                log.info("Collection '{}' exists", collectionName);
                return;
            }

            // Create collection
            JsonObject createRequest = new JsonObject();
            createRequest.addProperty("name", collectionName);

            // Add metadata
            JsonObject metadata = new JsonObject();
            metadata.addProperty("description", "RuneScape knowledge base for AI autonomous player");
            metadata.addProperty("created_by", "microbot_ai");
            createRequest.add("metadata", metadata);

            RequestBody body = RequestBody.create(
                MediaType.get("application/json"),
                gson.toJson(createRequest)
            );

            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/collections")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    collectionExists = true;
                    log.info("Created collection '{}'", collectionName);
                } else {
                    log.error("Failed to create collection: HTTP {}", response.code());
                }
            }

        } catch (Exception e) {
            log.error("Error ensuring collection exists", e);
        }
    }

    private boolean checkCollectionExists() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/collections/" + collectionName)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.error("Error checking if collection exists", e);
            return false;
        }
    }

    private boolean isCollectionEmpty() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/collections/" + collectionName + "/count")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    int count = gson.fromJson(responseBody, Integer.class);
                    return count == 0;
                }
            }
        } catch (Exception e) {
            log.error("Error checking collection count", e);
        }
        return true;
    }

    @Override
    public CompletableFuture<List<RAGSystemInterface.KnowledgeEntry>> searchKnowledge(String query, int maxResults, double minSimilarity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected || !collectionExists) {
                    log.warn("RAG system not available for search");
                    return new ArrayList<>();
                }

                JsonObject searchRequest = new JsonObject();

                JsonArray queryTexts = new JsonArray();
                queryTexts.add(query);
                searchRequest.add("query_texts", queryTexts);

                searchRequest.addProperty("n_results", maxResults);

                RequestBody body = RequestBody.create(
                    MediaType.get("application/json"),
                    gson.toJson(searchRequest)
                );

                Request request = new Request.Builder()
                        .url(baseUrl + "/api/v1/collections/" + collectionName + "/query")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.error("Search failed: HTTP {}", response.code());
                        return new ArrayList<>();
                    }

                    String responseBody = response.body().string();
                    return parseSearchResults(responseBody, minSimilarity);
                }

            } catch (Exception e) {
                log.error("Error searching knowledge", e);
                return new ArrayList<>();
            }
        });
    }

    private List<RAGSystemInterface.KnowledgeEntry> parseSearchResults(String responseBody, double minSimilarity) {
        List<RAGSystemInterface.KnowledgeEntry> results = new ArrayList<>();

        try {
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);

            JsonArray ids = response.getAsJsonArray("ids").get(0).getAsJsonArray();
            JsonArray documents = response.getAsJsonArray("documents").get(0).getAsJsonArray();
            JsonArray distances = response.getAsJsonArray("distances").get(0).getAsJsonArray();
            JsonArray metadatas = response.getAsJsonArray("metadatas").get(0).getAsJsonArray();

            for (int i = 0; i < ids.size(); i++) {
                double distance = distances.get(i).getAsDouble();
                double similarity = 1.0 - distance; // Convert distance to similarity

                if (similarity >= minSimilarity) {
                    RAGSystemInterface.KnowledgeEntry entry = new RAGSystemInterface.KnowledgeEntry();
                    entry.setId(ids.get(i).getAsString());
                    entry.setContent(documents.get(i).getAsString());
                    entry.setSimilarity(similarity);

                    if (!metadatas.get(i).isJsonNull()) {
                        JsonObject metadata = metadatas.get(i).getAsJsonObject();
                        entry.setMetadata(parseMetadata(metadata));
                    }

                    results.add(entry);
                }
            }

        } catch (Exception e) {
            log.error("Error parsing search results", e);
        }

        return results;
    }

    private Map<String, String> parseMetadata(JsonObject metadata) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : metadata.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    @Override
    public CompletableFuture<Boolean> addKnowledge(RAGSystemInterface.KnowledgeEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected || !collectionExists) {
                    log.warn("RAG system not available for adding knowledge");
                    return false;
                }

                JsonObject addRequest = new JsonObject();

                JsonArray ids = new JsonArray();
                ids.add(entry.getId());
                addRequest.add("ids", ids);

                JsonArray documents = new JsonArray();
                documents.add(entry.getContent());
                addRequest.add("documents", documents);

                if (entry.getMetadata() != null && !entry.getMetadata().isEmpty()) {
                    JsonArray metadatas = new JsonArray();
                    JsonObject metadata = new JsonObject();
                    for (Map.Entry<String, String> metaEntry : entry.getMetadata().entrySet()) {
                        metadata.addProperty(metaEntry.getKey(), metaEntry.getValue());
                    }
                    metadatas.add(metadata);
                    addRequest.add("metadatas", metadatas);
                }

                RequestBody body = RequestBody.create(
                    MediaType.get("application/json"),
                    gson.toJson(addRequest)
                );

                Request request = new Request.Builder()
                        .url(baseUrl + "/api/v1/collections/" + collectionName + "/add")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    boolean success = response.isSuccessful();
                    if (success) {
                        log.debug("Added knowledge entry: {}", entry.getId());
                    } else {
                        log.error("Failed to add knowledge: HTTP {}", response.code());
                    }
                    return success;
                }

            } catch (Exception e) {
                log.error("Error adding knowledge", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> addKnowledgeBatch(List<RAGSystemInterface.KnowledgeEntry> entries) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected || !collectionExists || entries.isEmpty()) {
                    return false;
                }

                JsonObject addRequest = new JsonObject();

                JsonArray ids = new JsonArray();
                JsonArray documents = new JsonArray();
                JsonArray metadatas = new JsonArray();

                for (KnowledgeEntry entry : entries) {
                    ids.add(entry.getId());
                    documents.add(entry.getContent());

                    JsonObject metadata = new JsonObject();
                    if (entry.getMetadata() != null) {
                        for (Map.Entry<String, String> metaEntry : entry.getMetadata().entrySet()) {
                            metadata.addProperty(metaEntry.getKey(), metaEntry.getValue());
                        }
                    }
                    metadatas.add(metadata);
                }

                addRequest.add("ids", ids);
                addRequest.add("documents", documents);
                addRequest.add("metadatas", metadatas);

                RequestBody body = RequestBody.create(
                    MediaType.get("application/json"),
                    gson.toJson(addRequest)
                );

                Request request = new Request.Builder()
                        .url(baseUrl + "/api/v1/collections/" + collectionName + "/add")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    boolean success = response.isSuccessful();
                    if (success) {
                        log.info("Added {} knowledge entries", entries.size());
                    } else {
                        log.error("Failed to add knowledge batch: HTTP {}", response.code());
                    }
                    return success;
                }

            } catch (Exception e) {
                log.error("Error adding knowledge batch", e);
                return false;
            }
        });
    }

    private void populateBasicKnowledge() {
        log.info("Populating basic RuneScape knowledge");

        List<KnowledgeEntry> basicKnowledge = createBasicRuneScapeKnowledge();
        addKnowledgeBatch(basicKnowledge).thenAccept(success -> {
            if (success) {
                log.info("Successfully populated basic knowledge");
            } else {
                log.error("Failed to populate basic knowledge");
            }
        });
    }

    private List<RAGSystemInterface.KnowledgeEntry> createBasicRuneScapeKnowledge() {
        List<RAGSystemInterface.KnowledgeEntry> entries = new ArrayList<>();

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

    private RAGSystemInterface.KnowledgeEntry createKnowledgeEntry(String id, String content, String... metadata) {
        RAGSystemInterface.KnowledgeEntry entry = new RAGSystemInterface.KnowledgeEntry();
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

    @Override
    public CompletableFuture<Boolean> updateKnowledge(RAGSystemInterface.KnowledgeEntry entry) {
        // For Chroma, we delete and re-add (no direct update API)
        return deleteKnowledge(entry.getId()).thenCompose(deleted -> {
            if (deleted) {
                return addKnowledge(entry);
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteKnowledge(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected || !collectionExists) {
                    log.warn("RAG system not available for deleting knowledge");
                    return false;
                }

                JsonObject deleteRequest = new JsonObject();
                JsonArray ids = new JsonArray();
                ids.add(id);
                deleteRequest.add("ids", ids);

                RequestBody body = RequestBody.create(
                    MediaType.get("application/json"),
                    gson.toJson(deleteRequest)
                );

                Request request = new Request.Builder()
                        .url(baseUrl + "/api/v1/collections/" + collectionName + "/delete")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    boolean success = response.isSuccessful();
                    if (success) {
                        log.debug("Deleted knowledge entry: {}", id);
                    } else {
                        log.error("Failed to delete knowledge: HTTP {}", response.code());
                    }
                    return success;
                }

            } catch (Exception e) {
                log.error("Error deleting knowledge", e);
                return false;
            }
        });
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("system_type", "Chroma");
        stats.put("connected", isConnected);
        stats.put("collection_exists", collectionExists);
        stats.put("collection_name", collectionName);
        stats.put("base_url", baseUrl);
        return stats;
    }

    @Override
    public boolean isReady() {
        return isConnected && collectionExists;
    }

    @Override
    public String getSystemType() {
        return "Chroma";
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isCollectionExists() {
        return collectionExists;
    }

    public String getCollectionName() {
        return collectionName;
    }

    @Override
    public CompletableFuture<Boolean> storeDocument(String id, String content, Map<String, Object> metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected || !collectionExists) {
                    log.warn("RAG system not available for storing document");
                    return false;
                }

                JsonObject addRequest = new JsonObject();

                JsonArray ids = new JsonArray();
                ids.add(id);
                addRequest.add("ids", ids);

                JsonArray documents = new JsonArray();
                documents.add(content);
                addRequest.add("documents", documents);

                if (metadata != null && !metadata.isEmpty()) {
                    JsonArray metadatas = new JsonArray();
                    JsonObject metadataObj = new JsonObject();
                    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                        metadataObj.addProperty(entry.getKey(), entry.getValue().toString());
                    }
                    metadatas.add(metadataObj);
                    addRequest.add("metadatas", metadatas);
                }

                RequestBody body = RequestBody.create(
                    MediaType.get("application/json"),
                    gson.toJson(addRequest)
                );

                Request request = new Request.Builder()
                        .url(baseUrl + "/api/v1/collections/" + collectionName + "/add")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    boolean success = response.isSuccessful();
                    if (success) {
                        log.debug("Stored document: {}", id);
                    } else {
                        log.error("Failed to store document: HTTP {}", response.code());
                    }
                    return success;
                }

            } catch (Exception e) {
                log.error("Error storing document", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<KnowledgeEntry>> searchDocuments(String query, int maxResults) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected || !collectionExists) {
                    log.warn("RAG system not available for search");
                    return new ArrayList<>();
                }

                JsonObject searchRequest = new JsonObject();

                JsonArray queryTexts = new JsonArray();
                queryTexts.add(query);
                searchRequest.add("query_texts", queryTexts);

                searchRequest.addProperty("n_results", maxResults);

                RequestBody body = RequestBody.create(
                    MediaType.get("application/json"),
                    gson.toJson(searchRequest)
                );

                Request request = new Request.Builder()
                        .url(baseUrl + "/api/v1/collections/" + collectionName + "/query")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.error("Document search failed: HTTP {}", response.code());
                        return new ArrayList<>();
                    }

                    String responseBody = response.body().string();
                    return parseSearchResults(responseBody, 0.0); // No minimum similarity for document search
                }

            } catch (Exception e) {
                log.error("Error searching documents", e);
                return new ArrayList<>();
            }
        });
    }

    @Override
    public void shutdown() {
        // Close HTTP client resources if needed
        log.info("RAG system shutdown");
    }

}