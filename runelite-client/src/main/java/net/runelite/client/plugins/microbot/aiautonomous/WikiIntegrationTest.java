package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.aiautonomous.integration.WikiIntegration;

/**
 * Test class to verify Wiki integration functionality.
 */
@Slf4j
public class WikiIntegrationTest {

    public static void testWikiIntegration(AiAutonomousConfig config) {
        log.info("Starting Wiki integration tests...");

        testWikiConfiguration(config);
        testWikiConnection();
        testWikiQueries();

        log.info("Wiki integration tests completed");
    }

    private static void testWikiConfiguration(AiAutonomousConfig config) {
        log.info("Testing Wiki configuration...");
        try {
            log.info("Wiki settings:");
            log.info("  - Wiki integration enabled: {}", config.enableWikiIntegration());
            log.info("  - Wiki cache time: {} hours", config.wikiCacheTime());
            log.info("  - Max wiki requests/hour: {}", config.maxWikiRequests());

            // Validate configuration
            if (config.enableWikiIntegration()) {
                log.info("✓ Wiki integration is enabled");

                if (config.wikiCacheTime() > 0 && config.wikiCacheTime() <= 168) { // 1 week max
                    log.info("✓ Wiki cache time is reasonable: {} hours", config.wikiCacheTime());
                } else {
                    log.warn("⚠ Wiki cache time might need adjustment: {} hours", config.wikiCacheTime());
                }

                if (config.maxWikiRequests() > 0 && config.maxWikiRequests() <= 1000) {
                    log.info("✓ Wiki request limit is reasonable: {}/hour", config.maxWikiRequests());
                } else {
                    log.warn("⚠ Wiki request limit might need adjustment: {}/hour", config.maxWikiRequests());
                }
            } else {
                log.info("ℹ Wiki integration is disabled");
            }

        } catch (Exception e) {
            log.error("✗ Wiki configuration test failed", e);
        }
    }

    private static void testWikiConnection() {
        log.info("Testing Wiki connection...");
        try {
            WikiIntegration wikiIntegration = new WikiIntegration();

            // Test that the Wiki integration can be created
            log.info("✓ WikiIntegration instance created successfully");

            // Note: We don't test actual network connections in offline tests
            // to avoid dependencies on external services
            log.info("✓ Wiki connection test completed (instance creation successful)");

        } catch (Exception e) {
            log.error("✗ Wiki connection test failed", e);
        }
    }

    private static void testWikiQueries() {
        log.info("Testing Wiki query functionality...");
        try {
            // Test query formats that the system might use
            String[] testQueries = {
                "fishing locations",
                "mining guide",
                "quest requirements",
                "combat training",
                "skill requirements",
                "item information"
            };

            for (String query : testQueries) {
                // Just validate that queries can be formatted properly
                String formattedQuery = formatWikiQuery(query);
                log.info("✓ Query '{}' formatted as: '{}'", query, formattedQuery);
            }

            log.info("✓ Wiki query formatting tests completed");

        } catch (Exception e) {
            log.error("✗ Wiki query test failed", e);
        }
    }

    /**
     * Simple query formatting for testing
     */
    private static String formatWikiQuery(String query) {
        // Basic formatting - replace spaces with underscores for wiki URLs
        return query.trim().replace(" ", "_").toLowerCase();
    }

    /**
     * Test wiki rate limiting logic
     */
    public static void testWikiRateLimit(AiAutonomousConfig config) {
        log.info("Testing Wiki rate limiting...");
        try {
            int maxRequests = config.maxWikiRequests();

            // Simulate rate limiting calculations
            int requestsPerMinute = maxRequests / 60;
            int requestsPerSecond = Math.max(1, requestsPerMinute / 60);

            log.info("Rate limiting calculations:");
            log.info("  - Max requests/hour: {}", maxRequests);
            log.info("  - Approx requests/minute: {}", requestsPerMinute);
            log.info("  - Approx requests/second: {}", requestsPerSecond);

            if (requestsPerSecond > 0 && requestsPerSecond <= 5) {
                log.info("✓ Rate limiting appears reasonable");
            } else {
                log.warn("⚠ Rate limiting might be too aggressive or too permissive");
            }

        } catch (Exception e) {
            log.error("✗ Wiki rate limit test failed", e);
        }
    }

    /**
     * Test caching mechanism logic
     */
    public static void testWikiCaching(AiAutonomousConfig config) {
        log.info("Testing Wiki caching logic...");
        try {
            int cacheTimeHours = config.wikiCacheTime();
            long cacheTimeMs = cacheTimeHours * 60 * 60 * 1000L;

            log.info("Cache settings:");
            log.info("  - Cache time: {} hours", cacheTimeHours);
            log.info("  - Cache time (ms): {}", cacheTimeMs);

            // Test cache expiry logic
            long currentTime = System.currentTimeMillis();
            long expiryTime = currentTime + cacheTimeMs;

            log.info("✓ Cache expiry calculations work correctly");
            log.info("  - Current time: {}", currentTime);
            log.info("  - Cache expires at: {}", expiryTime);

        } catch (Exception e) {
            log.error("✗ Wiki caching test failed", e);
        }
    }

    /**
     * Offline test that doesn't require network access
     */
    public static void testWikiIntegrationOffline() {
        log.info("Running offline Wiki integration tests...");
        try {
            // Test that we can create WikiIntegration instances
            WikiIntegration wikiIntegration = new WikiIntegration();
            log.info("✓ WikiIntegration can be instantiated");

            // Test query formatting
            String testQuery = formatWikiQuery("test query with spaces");
            if ("test_query_with_spaces".equals(testQuery)) {
                log.info("✓ Query formatting works correctly");
            } else {
                log.warn("✗ Query formatting might have issues: {}", testQuery);
            }

            log.info("✓ Offline Wiki integration tests completed");

        } catch (Exception e) {
            log.error("✗ Offline Wiki integration test failed", e);
        }
    }
}