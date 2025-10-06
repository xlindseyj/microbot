package net.runelite.client.plugins.microbot.aiautonomous.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
public class WikiIntegration {

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Map<String, WikiCacheEntry> cache;
    private final RateLimiter rateLimiter;

    private static final String WIKI_API_BASE = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "MicrobotAI/1.0 (RuneLite Plugin; https://github.com/runelite/runelite)";
    private static final int CACHE_HOURS = 24;
    private static final int MAX_CACHE_SIZE = 1000;

    public WikiIntegration() {
        this.gson = new Gson();
        this.cache = new LinkedHashMap<String, WikiCacheEntry>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, WikiCacheEntry> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
        this.rateLimiter = new RateLimiter(100, Duration.ofHours(1)); // 100 requests per hour

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder()
                            .header("User-Agent", USER_AGENT);
                    return chain.proceed(requestBuilder.build());
                })
                .build();
    }

    public CompletableFuture<WikiSearchResult> searchWiki(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!rateLimiter.tryAcquire()) {
                    log.warn("Wiki API rate limit exceeded");
                    return new WikiSearchResult(query, "Rate limit exceeded", Collections.emptyList());
                }

                // Check cache first
                String cacheKey = "search:" + query.toLowerCase();
                WikiCacheEntry cached = cache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    log.debug("Using cached wiki search result for: {}", query);
                    return (WikiSearchResult) cached.getData();
                }

                // Search the wiki
                String searchUrl = buildSearchUrl(query);
                Request request = new Request.Builder().url(searchUrl).build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.error("Wiki search failed: HTTP {}", response.code());
                        return new WikiSearchResult(query, "Search failed", Collections.emptyList());
                    }

                    String responseBody = response.body().string();
                    WikiSearchResult result = parseSearchResponse(query, responseBody);

                    // Cache the result
                    cache.put(cacheKey, new WikiCacheEntry(result));

                    return result;
                }

            } catch (Exception e) {
                log.error("Error searching wiki", e);
                return new WikiSearchResult(query, "Error: " + e.getMessage(), Collections.emptyList());
            }
        });
    }

    public CompletableFuture<WikiPageContent> getPageContent(String pageTitle) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!rateLimiter.tryAcquire()) {
                    log.warn("Wiki API rate limit exceeded");
                    return new WikiPageContent(pageTitle, "Rate limit exceeded", "", Collections.emptyMap());
                }

                // Check cache first
                String cacheKey = "page:" + pageTitle.toLowerCase();
                WikiCacheEntry cached = cache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    log.debug("Using cached wiki page for: {}", pageTitle);
                    return (WikiPageContent) cached.getData();
                }

                // Get page content
                String pageUrl = buildPageContentUrl(pageTitle);
                Request request = new Request.Builder().url(pageUrl).build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.error("Wiki page fetch failed: HTTP {}", response.code());
                        return new WikiPageContent(pageTitle, "Page fetch failed", "", Collections.emptyMap());
                    }

                    String responseBody = response.body().string();
                    WikiPageContent content = parsePageContent(pageTitle, responseBody);

                    // Cache the result
                    cache.put(cacheKey, new WikiCacheEntry(content));

                    return content;
                }

            } catch (Exception e) {
                log.error("Error fetching wiki page", e);
                return new WikiPageContent(pageTitle, "Error: " + e.getMessage(), "", Collections.emptyMap());
            }
        });
    }

    public CompletableFuture<List<WikiInfoboxData>> getInfoboxData(String pageTitle) {
        return getPageContent(pageTitle).thenApply(content -> {
            List<WikiInfoboxData> infoboxes = new ArrayList<>();

            try {
                // Extract infobox data from the page content
                // This is a simplified implementation - in practice you'd need more sophisticated parsing
                String wikitext = content.getWikitext();
                if (wikitext.contains("{{Infobox")) {
                    WikiInfoboxData infobox = parseInfoboxFromWikitext(wikitext);
                    if (infobox != null) {
                        infoboxes.add(infobox);
                    }
                }

            } catch (Exception e) {
                log.error("Error parsing infobox data", e);
            }

            return infoboxes;
        });
    }

    private String buildSearchUrl(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            return WIKI_API_BASE + "?action=query&format=json&list=search&srsearch=" + encodedQuery + "&srlimit=10";
        } catch (Exception e) {
            throw new RuntimeException("Error building search URL", e);
        }
    }

    private String buildPageContentUrl(String pageTitle) {
        try {
            String encodedTitle = URLEncoder.encode(pageTitle, StandardCharsets.UTF_8.toString());
            return WIKI_API_BASE + "?action=query&format=json&prop=extracts|revisions&exintro=&explaintext=&rvprop=content&titles=" + encodedTitle;
        } catch (Exception e) {
            throw new RuntimeException("Error building page URL", e);
        }
    }

    private WikiSearchResult parseSearchResponse(String query, String responseBody) {
        try {
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);
            JsonArray searchResults = response.getAsJsonObject("query")
                    .getAsJsonArray("search");

            List<WikiSearchEntry> entries = new ArrayList<>();
            for (JsonElement element : searchResults) {
                JsonObject result = element.getAsJsonObject();

                WikiSearchEntry entry = new WikiSearchEntry();
                entry.setTitle(result.get("title").getAsString());
                entry.setSnippet(cleanWikitext(result.get("snippet").getAsString()));
                entry.setSize(result.get("size").getAsInt());
                entry.setTimestamp(result.get("timestamp").getAsString());

                entries.add(entry);
            }

            return new WikiSearchResult(query, "Success", entries);

        } catch (Exception e) {
            log.error("Error parsing search response", e);
            return new WikiSearchResult(query, "Parse error", Collections.emptyList());
        }
    }

    private WikiPageContent parsePageContent(String pageTitle, String responseBody) {
        try {
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);
            JsonObject pages = response.getAsJsonObject("query").getAsJsonObject("pages");

            // Get the first (and should be only) page
            JsonObject page = pages.entrySet().iterator().next().getValue().getAsJsonObject();

            String extract = "";
            String wikitext = "";
            Map<String, String> metadata = new HashMap<>();

            if (page.has("extract")) {
                extract = page.get("extract").getAsString();
            }

            if (page.has("revisions")) {
                JsonArray revisions = page.getAsJsonArray("revisions");
                if (revisions.size() > 0) {
                    JsonObject revision = revisions.get(0).getAsJsonObject();
                    if (revision.has("*")) {
                        wikitext = revision.get("*").getAsString();
                    }
                }
            }

            // Extract basic metadata
            metadata.put("title", pageTitle);
            if (page.has("pageid")) {
                metadata.put("pageid", page.get("pageid").getAsString());
            }

            return new WikiPageContent(pageTitle, extract, wikitext, metadata);

        } catch (Exception e) {
            log.error("Error parsing page content", e);
            return new WikiPageContent(pageTitle, "Parse error", "", Collections.emptyMap());
        }
    }

    private WikiInfoboxData parseInfoboxFromWikitext(String wikitext) {
        try {
            // Simple infobox parser - in practice this would be much more sophisticated
            Pattern infoboxPattern = Pattern.compile("\\{\\{Infobox[^}]*\\}\\}", Pattern.DOTALL);
            java.util.regex.Matcher matcher = infoboxPattern.matcher(wikitext);

            if (matcher.find()) {
                String infoboxText = matcher.group();
                Map<String, String> data = new HashMap<>();

                // Extract key-value pairs from infobox
                String[] lines = infoboxText.split("\n");
                for (String line : lines) {
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim().replaceAll("[|\\s]", "");
                            String value = cleanWikitext(parts[1].trim());
                            if (!key.isEmpty() && !value.isEmpty()) {
                                data.put(key, value);
                            }
                        }
                    }
                }

                return new WikiInfoboxData("unknown", data);
            }

        } catch (Exception e) {
            log.error("Error parsing infobox", e);
        }

        return null;
    }

    private String cleanWikitext(String text) {
        if (text == null) return "";

        // Remove wiki markup
        return text
                .replaceAll("\\[\\[[^|\\]]*\\|([^\\]]*)\\]\\]", "$1") // [[link|text]] -> text
                .replaceAll("\\[\\[([^\\]]*)\\]\\]", "$1") // [[link]] -> link
                .replaceAll("'''([^']*?)'''", "$1") // '''bold''' -> bold
                .replaceAll("''([^']*?)''", "$1") // ''italic'' -> italic
                .replaceAll("\\{\\{[^}]*\\}\\}", "") // Remove templates
                .replaceAll("<[^>]*>", "") // Remove HTML tags
                .replaceAll("&[^;]*;", "") // Remove HTML entities
                .trim();
    }

    public void clearCache() {
        cache.clear();
        log.info("Wiki cache cleared");
    }

    public int getCacheSize() {
        return cache.size();
    }

    public String getStats() {
        return String.format("Cache: %d entries, Rate limit: %d/%d",
                cache.size(), rateLimiter.getUsedRequests(), rateLimiter.getMaxRequests());
    }

    // Data classes
    public static class WikiSearchResult {
        private final String query;
        private final String status;
        private final List<WikiSearchEntry> entries;

        public WikiSearchResult(String query, String status, List<WikiSearchEntry> entries) {
            this.query = query;
            this.status = status;
            this.entries = entries;
        }

        public String getQuery() { return query; }
        public String getStatus() { return status; }
        public List<WikiSearchEntry> getEntries() { return entries; }
    }

    public static class WikiSearchEntry {
        private String title;
        private String snippet;
        private int size;
        private String timestamp;

        // Getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSnippet() { return snippet; }
        public void setSnippet(String snippet) { this.snippet = snippet; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    public static class WikiPageContent {
        private final String title;
        private final String extract;
        private final String wikitext;
        private final Map<String, String> metadata;

        public WikiPageContent(String title, String extract, String wikitext, Map<String, String> metadata) {
            this.title = title;
            this.extract = extract;
            this.wikitext = wikitext;
            this.metadata = metadata;
        }

        public String getTitle() { return title; }
        public String getExtract() { return extract; }
        public String getWikitext() { return wikitext; }
        public Map<String, String> getMetadata() { return metadata; }
    }

    public static class WikiInfoboxData {
        private final String type;
        private final Map<String, String> data;

        public WikiInfoboxData(String type, Map<String, String> data) {
            this.type = type;
            this.data = data;
        }

        public String getType() { return type; }
        public Map<String, String> getData() { return data; }
    }

    private static class WikiCacheEntry {
        private final Object data;
        private final Instant timestamp;

        public WikiCacheEntry(Object data) {
            this.data = data;
            this.timestamp = Instant.now();
        }

        public Object getData() { return data; }

        public boolean isExpired() {
            return Duration.between(timestamp, Instant.now()).toHours() >= CACHE_HOURS;
        }
    }

    private static class RateLimiter {
        private final int maxRequests;
        private final Duration window;
        private final Queue<Instant> requests;

        public RateLimiter(int maxRequests, Duration window) {
            this.maxRequests = maxRequests;
            this.window = window;
            this.requests = new LinkedList<>();
        }

        public synchronized boolean tryAcquire() {
            Instant now = Instant.now();

            // Remove old requests outside the window
            while (!requests.isEmpty() && Duration.between(requests.peek(), now).compareTo(window) > 0) {
                requests.poll();
            }

            if (requests.size() < maxRequests) {
                requests.offer(now);
                return true;
            }

            return false;
        }

        public int getUsedRequests() {
            Instant now = Instant.now();
            // Clean up old requests
            while (!requests.isEmpty() && Duration.between(requests.peek(), now).compareTo(window) > 0) {
                requests.poll();
            }
            return requests.size();
        }

        public int getMaxRequests() {
            return maxRequests;
        }
    }
}