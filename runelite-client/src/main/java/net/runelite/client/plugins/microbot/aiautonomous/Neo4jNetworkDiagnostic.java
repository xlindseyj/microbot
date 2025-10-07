package net.runelite.client.plugins.microbot.aiautonomous;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Comprehensive diagnostic tool for Neo4j connectivity issues between Windows and WSL/Kubernetes.
 */
@Slf4j
public class Neo4jNetworkDiagnostic {

    public static void main(String[] args) {
        log.info("=".repeat(70));
        log.info("NEO4J WSL/KUBERNETES NETWORK DIAGNOSTIC TOOL");
        log.info("=".repeat(70));

        runComprehensiveDiagnostic();

        log.info("=".repeat(70));
        log.info("DIAGNOSTIC COMPLETE - Check results above for connectivity issues");
        log.info("=".repeat(70));
    }

    public static void runComprehensiveDiagnostic() {
        // Test various potential Neo4j endpoints
        String[] potentialEndpoints = {
            "http://192.168.5.141:7474",     // Current configuration
            "http://localhost:7474",         // Direct localhost
            "http://127.0.0.1:7474",        // IPv4 localhost
            "http://0.0.0.0:7474",          // All interfaces
            "http://192.168.5.141:7687",    // Neo4j Bolt protocol port
            "http://localhost:7687",         // Bolt on localhost
        };

        log.info("1. TESTING BASIC NETWORK CONNECTIVITY");
        log.info("-".repeat(50));

        for (String endpoint : potentialEndpoints) {
            testEndpointConnectivity(endpoint);
        }

        log.info("\n2. TESTING WSL NETWORK CONFIGURATION");
        log.info("-".repeat(50));
        testWSLNetworking();

        log.info("\n3. TESTING KUBERNETES PORT FORWARDING");
        log.info("-".repeat(50));
        testKubernetesPortForwarding();

        log.info("\n4. TESTING NEO4J SPECIFIC ENDPOINTS");
        log.info("-".repeat(50));
        testNeo4jSpecificEndpoints();

        log.info("\n5. NETWORK TROUBLESHOOTING SUGGESTIONS");
        log.info("-".repeat(50));
        provideTroubleshootingSuggestions();
    }

    private static void testEndpointConnectivity(String endpoint) {
        log.info("Testing endpoint: {}", endpoint);

        try {
            URL url = new URL(endpoint);
            String host = url.getHost();
            int port = url.getPort() != -1 ? url.getPort() : url.getDefaultPort();

            // Test 1: DNS Resolution
            try {
                InetAddress address = InetAddress.getByName(host);
                log.info("  ✅ DNS Resolution: {} -> {}", host, address.getHostAddress());
            } catch (Exception e) {
                log.error("  ❌ DNS Resolution failed: {}", e.getMessage());
                return;
            }

            // Test 2: Port Connectivity
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 3000);
                log.info("  ✅ Port {} is reachable", port);
            } catch (Exception e) {
                log.error("  ❌ Port {} unreachable: {}", port, e.getMessage());
                return;
            }

            // Test 3: HTTP Response
            try {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                log.info("  ✅ HTTP Response: {}", responseCode);

                connection.disconnect();
            } catch (Exception e) {
                log.error("  ❌ HTTP request failed: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("  ❌ Endpoint test failed: {}", e.getMessage());
        }

        log.info("");
    }

    private static void testWSLNetworking() {
        log.info("Testing WSL network configuration...");

        // Test WSL IP discovery
        try {
            String wslHostname = getWSLHostname();
            if (wslHostname != null) {
                log.info("  WSL Hostname: {}", wslHostname);

                // Try to resolve WSL hostname
                try {
                    InetAddress wslAddress = InetAddress.getByName(wslHostname);
                    log.info("  ✅ WSL IP Address: {}", wslAddress.getHostAddress());

                    // Test Neo4j ports on WSL IP
                    testPortOnHost(wslAddress.getHostAddress(), 7474, "Neo4j HTTP");
                    testPortOnHost(wslAddress.getHostAddress(), 7687, "Neo4j Bolt");

                } catch (Exception e) {
                    log.error("  ❌ Cannot resolve WSL hostname: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("  ⚠️ WSL hostname detection failed: {}", e.getMessage());
        }

        // Test common WSL IP ranges
        String[] commonWSLIPs = {
            "172.17.0.1",     // Docker default bridge
            "172.18.0.1",     // Docker custom bridge
            "172.19.0.1",     // Docker custom bridge
            "172.20.0.1",     // Docker custom bridge
            "192.168.49.1",   // Minikube default
            "192.168.65.2",   // Docker Desktop
        };

        log.info("  Testing common WSL/Docker IP ranges...");
        for (String ip : commonWSLIPs) {
            testPortOnHost(ip, 7474, "Neo4j HTTP on " + ip);
        }
    }

    private static void testKubernetesPortForwarding() {
        log.info("Testing Kubernetes port forwarding...");

        // Check if kubectl is available and can list services
        try {
            Process process = Runtime.getRuntime().exec("kubectl get services -A");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            boolean found = false;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("neo4j")) {
                    log.info("  ✅ Found Neo4j service: {}", line);
                    found = true;
                }
            }

            if (!found) {
                log.warn("  ⚠️ No Neo4j services found in Kubernetes");
            }

        } catch (Exception e) {
            log.warn("  ⚠️ Cannot access kubectl: {}", e.getMessage());
            log.info("  💡 Try running: kubectl get services -A | grep neo4j");
        }

        // Check for active port forwards
        try {
            Process process = Runtime.getRuntime().exec("netstat -an | findstr :7474");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            boolean found = false;
            while ((line = reader.readLine()) != null) {
                log.info("  Port 7474 binding: {}", line.trim());
                found = true;
            }

            if (!found) {
                log.warn("  ⚠️ No active port forwarding detected for port 7474");
                log.info("  💡 Try: kubectl port-forward service/neo4j 7474:7474");
            }

        } catch (Exception e) {
            log.warn("  ⚠️ Cannot check port bindings: {}", e.getMessage());
        }
    }

    private static void testNeo4jSpecificEndpoints() {
        String[] neo4jEndpoints = {
            "http://192.168.5.141:7474/",
            "http://192.168.5.141:7474/db/data/",
            "http://192.168.5.141:7474/db/neo4j/tx/commit",
            "http://localhost:7474/",
            "http://localhost:7474/browser/",
        };

        for (String endpoint : neo4jEndpoints) {
            log.info("Testing Neo4j endpoint: {}", endpoint);
            try {
                URL url = new URL(endpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();

                if (responseCode >= 200 && responseCode < 400) {
                    log.info("  ✅ Endpoint accessible: {} {}", responseCode, responseMessage);
                } else if (responseCode == 401) {
                    log.info("  ✅ Endpoint accessible but requires auth: {} {}", responseCode, responseMessage);
                } else {
                    log.warn("  ⚠️ Unexpected response: {} {}", responseCode, responseMessage);
                }

                connection.disconnect();

            } catch (Exception e) {
                log.error("  ❌ Endpoint failed: {}", e.getMessage());
            }
        }
    }

    private static void testPortOnHost(String host, int port, String description) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 2000);
            log.info("  ✅ {}: {}:{} is reachable", description, host, port);
        } catch (Exception e) {
            log.debug("  ❌ {}: {}:{} unreachable", description, host, port);
        }
    }

    private static String getWSLHostname() {
        try {
            // Try to get WSL hostname from environment or registry
            Process process = Runtime.getRuntime().exec("hostname");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String hostname = reader.readLine();

            if (hostname != null && !hostname.trim().isEmpty()) {
                return hostname.trim() + ".local";
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }

    private static void provideTroubleshootingSuggestions() {
        log.info("TROUBLESHOOTING SUGGESTIONS:");
        log.info("");

        log.info("🔧 WSL/Kubernetes Neo4j Connection Issues:");
        log.info("   1. Check if Neo4j is exposed outside the cluster:");
        log.info("      kubectl get services -A | grep neo4j");
        log.info("");

        log.info("   2. Set up port forwarding from Kubernetes to Windows:");
        log.info("      kubectl port-forward service/neo4j 7474:7474 7687:7687");
        log.info("");

        log.info("   3. Or expose Neo4j with NodePort service:");
        log.info("      kubectl patch service neo4j -p '{\"spec\":{\"type\":\"NodePort\"}}'");
        log.info("");

        log.info("   4. Check WSL networking mode:");
        log.info("      cat /etc/wsl.conf");
        log.info("      # Should have [network] mode=mirrored for direct access");
        log.info("");

        log.info("   5. Alternative: Use 'localhost' if port-forwarding is active:");
        log.info("      URL: http://localhost:7474");
        log.info("");

        log.info("   6. Check Windows firewall isn't blocking connections");
        log.info("");

        log.info("   7. Verify Neo4j configuration allows external connections:");
        log.info("      # In neo4j.conf:");
        log.info("      dbms.default_listen_address=0.0.0.0");
        log.info("      dbms.connector.http.listen_address=:7474");
        log.info("");

        log.info("💡 RECOMMENDED QUICK FIXES:");
        log.info("   Option A: Use localhost with port-forward");
        log.info("   Option B: Use WSL IP address if mirrored networking");
        log.info("   Option C: Use Kubernetes NodePort service");
        log.info("");

        log.info("🔍 TO FIND YOUR NEO4J:");
        log.info("   kubectl get pods -A | grep neo4j");
        log.info("   kubectl get services -A | grep neo4j");
        log.info("   kubectl describe service neo4j");
    }

    /**
     * Test a specific Neo4j configuration
     */
    public static boolean testNeo4jConfig(String url, String database, String username, String password) {
        log.info("Testing specific Neo4j configuration:");
        log.info("  URL: {}", url);
        log.info("  Database: {}", database);
        log.info("  Username: {}", username);
        log.info("  Password: {}***", password.substring(0, Math.min(2, password.length())));

        // Extract host and port from URL
        try {
            URL neo4jUrl = new URL(url);
            String host = neo4jUrl.getHost();
            int port = neo4jUrl.getPort() != -1 ? neo4jUrl.getPort() : 7474;

            // Test basic connectivity
            testPortOnHost(host, port, "Neo4j HTTP");

            // Test HTTP endpoint
            testEndpointConnectivity(url);

            return true;

        } catch (Exception e) {
            log.error("Configuration test failed: {}", e.getMessage());
            return false;
        }
    }
}