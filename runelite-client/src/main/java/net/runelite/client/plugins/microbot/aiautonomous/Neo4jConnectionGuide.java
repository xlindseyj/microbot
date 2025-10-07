package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Neo4j Connection Guide for WSL/Kubernetes setup.
 * Provides step-by-step instructions for establishing connectivity.
 */
@Slf4j
public class Neo4jConnectionGuide {

    public static void main(String[] args) {
        log.info("=" + "=".repeat(70) + "=");
        log.info("NEO4J WSL/KUBERNETES CONNECTION GUIDE");
        log.info("=" + "=".repeat(70) + "=");

        showConnectionInstructions();
        testCurrentConnection();

        log.info("=" + "=".repeat(70) + "=");
        log.info("CONNECTION GUIDE COMPLETE");
        log.info("=" + "=".repeat(70) + "=");
    }

    private static void showConnectionInstructions() {
        log.info("\n🔧 STEP-BY-STEP SETUP INSTRUCTIONS:");
        log.info("");
        log.info("1. ✅ VERIFY NEO4J IS RUNNING IN KUBERNETES:");
        log.info("   kubectl get pods -A | grep neo4j");
        log.info("   kubectl get services -A | grep neo4j");
        log.info("");
        log.info("2. ✅ SET UP PORT FORWARDING (Required for Windows to access WSL):");
        log.info("   kubectl port-forward service/neo4j 7474:7474 7687:7687");
        log.info("   📝 Keep this command running in a separate terminal");
        log.info("");
        log.info("3. ✅ VERIFY PORT FORWARDING IS ACTIVE:");
        log.info("   netstat -an | findstr :7474");
        log.info("   Should show: TCP    127.0.0.1:7474         0.0.0.0:0              LISTENING");
        log.info("");
        log.info("4. ✅ TEST NEO4J IN BROWSER:");
        log.info("   Open: http://localhost:7474");
        log.info("   Login with: neo4j / runescape2025");
        log.info("");
        log.info("💡 CURRENT AI CONFIGURATION:");
        log.info("   URL: http://localhost:7474");
        log.info("   Database: runescapeknowledge");
        log.info("   Username: neo4j");
        log.info("   Password: runescape2025");
        log.info("");
    }

    private static void testCurrentConnection() {
        log.info("🔍 TESTING CURRENT CONNECTION STATUS:");
        log.info("");

        String neo4jUrl = "http://localhost:7474";

        try {
            log.info("Testing connection to {}...", neo4jUrl);

            URL url = new URL(neo4jUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            int responseCode = connection.getResponseCode();

            if (responseCode > 0) {
                log.info("✅ SUCCESS: Neo4j is reachable!");
                log.info("   Response Code: {}", responseCode);
                log.info("   🎉 Your AI Autonomous Player can now connect to Neo4j");
            } else {
                log.error("❌ Unexpected response code: {}", responseCode);
            }

            connection.disconnect();

        } catch (Exception e) {
            log.error("❌ CONNECTION FAILED: {}", e.getMessage());
            log.error("");
            log.error("🚨 TROUBLESHOOTING STEPS:");
            log.error("   1. Make sure kubectl port-forward is running:");
            log.error("      kubectl port-forward service/neo4j 7474:7474 7687:7687");
            log.error("   2. Check if Neo4j service exists:");
            log.error("      kubectl get services -A | grep neo4j");
            log.error("   3. Check if Neo4j pods are running:");
            log.error("      kubectl get pods -A | grep neo4j");
            log.error("   4. Verify WSL/Kubernetes is running:");
            log.error("      kubectl cluster-info");
            log.error("");
            log.error("📋 ALTERNATIVE SOLUTIONS:");
            log.error("   Option A: Use NodePort service (exposes on WSL IP)");
            log.error("   Option B: Use mirrored networking in WSL config");
            log.error("   Option C: Deploy Neo4j with LoadBalancer service");
        }
    }
}