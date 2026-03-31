package br.com.nicomaia.server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class UpdateChecker {

    private static final String CURRENT_VERSION = "1.0.0";
    private static final String GITHUB_REPO = "josenicomaia/socks-server";
    private static final String RELEASES_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    public static void checkAsync() {
        Thread.ofVirtual().name("update-checker").start(() -> {
            try {
                String latestVersion = fetchLatestVersion();
                if (latestVersion != null && isNewer(latestVersion, CURRENT_VERSION)) {
                    System.out.println();
                    System.out.println("╔════════════════════════════════════════════════════════════╗");
                    System.out.println("║  A new version of socks-server is available: " + padRight(latestVersion, 13) + "║");
                    System.out.println("║  You are running version: " + padRight(CURRENT_VERSION, 33) + "║");
                    System.out.println("║                                                            ║");
                    System.out.println("║  https://github.com/" + padRight(GITHUB_REPO + "/releases", 39) + "║");
                    System.out.println("╚════════════════════════════════════════════════════════════╝");
                    System.out.println();
                }
            } catch (Exception ignored) {
                // Silently fail — update check should never block or crash the app
            }
        });
    }

    private static String fetchLatestVersion() throws Exception {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RELEASES_URL))
                    .header("Accept", "application/vnd.github.v3+json")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return null;
            }

            // Lightweight JSON parsing — extract "tag_name" without a JSON library
            String body = response.body();
            int tagIndex = body.indexOf("\"tag_name\"");
            if (tagIndex == -1) return null;

            int valueStart = body.indexOf('"', tagIndex + 10) + 1;
            int valueEnd = body.indexOf('"', valueStart);
            String tagName = body.substring(valueStart, valueEnd);

            // Strip leading 'v' if present (e.g., "v1.2.0" → "1.2.0")
            return tagName.startsWith("v") ? tagName.substring(1) : tagName;
        }
    }

    static boolean isNewer(String remote, String local) {
        int[] remoteParts = parseVersion(remote);
        int[] localParts = parseVersion(local);

        for (int i = 0; i < Math.max(remoteParts.length, localParts.length); i++) {
            int r = i < remoteParts.length ? remoteParts[i] : 0;
            int l = i < localParts.length ? localParts[i] : 0;
            if (r > l) return true;
            if (r < l) return false;
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    private static String padRight(String text, int length) {
        if (text.length() >= length) return text;
        return text + " ".repeat(length - text.length());
    }
}
