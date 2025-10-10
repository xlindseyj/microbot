package net.runelite.client.plugins.microbot.aiautonomous.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.List;
import java.util.ArrayList;


@Slf4j
public class OllamaClient {

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final String model;

    private boolean isConnected = false;
    private Instant lastRequestTime = Instant.MIN;
    private int requestCount = 0;

    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 60;

    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.gson = new Gson();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // Test connection on initialization
        testConnection();
    }

    public boolean testConnection() {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);

            Request request = new Request.Builder()
                    .url(baseUrl + "/api/tags")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                isConnected = response.isSuccessful();
                if (isConnected) {
                    log.info("Successfully connected to Ollama at {}", baseUrl);
                } else {
                    log.warn("Failed to connect to Ollama: HTTP {}", response.code());
                }
                return isConnected;
            }
        } catch (Exception e) {
            log.error("Error testing Ollama connection", e);
            isConnected = false;
            return false;
        }
    }

    public CompletableFuture<String> generateDecision(String prompt, GameContext context) {
        return CompletableFuture.supplyAsync((Supplier<String>) () -> {
            try {
                if (!isConnected && !testConnection()) {
                    throw new RuntimeException("Not connected to Ollama server");
                }

                if (!checkRateLimit()) {
                    throw new RuntimeException("Rate limit exceeded");
                }

                String enhancedPrompt = buildEnhancedPrompt(prompt, context);
                return generateTextSync(enhancedPrompt);

            } catch (Exception e) {
                log.error("Error generating AI decision", e);
                return "ERROR: " + e.getMessage();
            }
        });
    }

    public CompletableFuture<String> generateText(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", model);
                requestBody.addProperty("prompt", prompt);
                requestBody.addProperty("stream", false);

                // Add generation parameters
                JsonObject options = new JsonObject();
                options.addProperty("temperature", 0.7);
                options.addProperty("top_p", 0.9);
                options.addProperty("max_tokens", 1000);
                requestBody.add("options", options);

                RequestBody body = RequestBody.create(
                        MediaType.get("application/json"),
                        gson.toJson(requestBody)
                );

                Request request = new Request.Builder()
                        .url(baseUrl + "/api/generate")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected response code: " + response.code());
                    }

                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                    updateRequestTracking();

                    return jsonResponse.get("response").getAsString();
                }

            } catch (Exception e) {
                log.error("Error generating text with Ollama", e);
                throw new RuntimeException("Ollama generation failed", e);
            }
        });
    }

    private String generateTextSync(String prompt) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("prompt", prompt);
            requestBody.addProperty("stream", false);

            JsonObject options = new JsonObject();
            options.addProperty("temperature", 0.7);
            options.addProperty("top_p", 0.9);
            options.addProperty("max_tokens", 1000);
            requestBody.add("options", options);

            RequestBody body = RequestBody.create(
                    MediaType.get("application/json"),
                    gson.toJson(requestBody)
            );

            Request request = new Request.Builder()
                    .url(baseUrl + "/api/generate")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                updateRequestTracking();
                return jsonResponse.get("response").getAsString();
            }
        } catch (Exception e) {
            log.error("Error generating text with Ollama", e);
            throw new RuntimeException("Ollama generation failed", e);
        }
    }

    public CompletableFuture<List<Double>> generateEmbedding(String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "nomic-embed-text"); // Use embedding model
                requestBody.addProperty("prompt", text);

                RequestBody body = RequestBody.create(
                        MediaType.get("application/json"),
                        gson.toJson(requestBody)
                );

                Request request = new Request.Builder()
                        .url(baseUrl + "/api/embeddings")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected response code: " + response.code());
                    }

                    String responseBody = response.body().string();
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                    JsonArray embeddingArray = jsonResponse.getAsJsonArray("embedding");
                    List<Double> embedding = new ArrayList<>();

                    for (int i = 0; i < embeddingArray.size(); i++) {
                        embedding.add(embeddingArray.get(i).getAsDouble());
                    }

                    updateRequestTracking();
                    return embedding;
                }

            } catch (Exception e) {
                log.error("Error generating embedding with Ollama", e);
                throw new RuntimeException("Ollama embedding failed", e);
            }
        });
    }

    private String buildEnhancedPrompt(String basePrompt, GameContext context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an AI playing Old School RuneScape autonomously. ");
        prompt.append("Make decisions based on the current game state and your objectives.\n\n");

        prompt.append("CURRENT GAME STATE:\n");
        if (context != null) {
            prompt.append("Location: ").append(context.getCurrentLocation()).append("\n");
            prompt.append("Health: ").append(context.getCurrentHealth()).append("/").append(context.getMaxHealth()).append("\n");
            prompt.append("Combat Level: ").append(context.getCombatLevel()).append("\n");
            prompt.append("Current Activity: ").append(context.getCurrentActivity()).append("\n");
            prompt.append("Inventory Items: ").append(context.getInventoryItems()).append("\n");
            prompt.append("Equipped Items: ").append(context.getEquippedItems()).append("\n");
            prompt.append("Current Quest: ").append(context.getCurrentQuest()).append("\n");
            prompt.append("Available Actions: ").append(context.getAvailableActions()).append("\n");
        }

        prompt.append("\nQUESTION/TASK:\n");
        prompt.append(basePrompt);

        prompt.append("\n\nINSTRUCTIONS:\n");
        prompt.append("- Respond with a specific action to take\n");
        prompt.append("- Consider efficiency, safety, and progression\n");
        prompt.append("- Format your response as: ACTION: [specific action]\n");
        prompt.append("- If uncertain, choose the safest option\n");
        prompt.append("- Avoid high-risk activities unless specifically needed\n");

        return prompt.toString();
    }

    private boolean checkRateLimit() {
        Instant now = Instant.now();
        Duration timeSinceLastRequest = Duration.between(lastRequestTime, now);

        // Reset counter if it's been more than a minute
        if (timeSinceLastRequest.toMinutes() >= 1) {
            requestCount = 0;
            lastRequestTime = now;
        }

        return requestCount < MAX_REQUESTS_PER_MINUTE;
    }

    private void updateRequestTracking() {
        requestCount++;
        lastRequestTime = Instant.now();
    }

    public boolean isConnected() {
        return isConnected;
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void resetRequestCount() {
        requestCount = 0;
        lastRequestTime = Instant.now();
    }

    public static class GameContext {
        private String currentLocation;
        private int currentHealth;
        private int maxHealth;
        private int combatLevel;
        private String currentActivity;
        private String inventoryItems;
        private String equippedItems;
        private String currentQuest;
        private String availableActions;

        // Getters and setters
        public String getCurrentLocation() { return currentLocation; }
        public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

        public int getCurrentHealth() { return currentHealth; }
        public void setCurrentHealth(int currentHealth) { this.currentHealth = currentHealth; }

        public int getMaxHealth() { return maxHealth; }
        public void setMaxHealth(int maxHealth) { this.maxHealth = maxHealth; }

        public int getCombatLevel() { return combatLevel; }
        public void setCombatLevel(int combatLevel) { this.combatLevel = combatLevel; }

        public String getCurrentActivity() { return currentActivity; }
        public void setCurrentActivity(String currentActivity) { this.currentActivity = currentActivity; }

        public String getInventoryItems() { return inventoryItems; }
        public void setInventoryItems(String inventoryItems) { this.inventoryItems = inventoryItems; }

        public String getEquippedItems() { return equippedItems; }
        public void setEquippedItems(String equippedItems) { this.equippedItems = equippedItems; }

        public String getCurrentQuest() { return currentQuest; }
        public void setCurrentQuest(String currentQuest) { this.currentQuest = currentQuest; }

        public String getAvailableActions() { return availableActions; }
        public void setAvailableActions(String availableActions) { this.availableActions = availableActions; }
    }
}