package dev.jax.skylyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** Spotify OAuth 2.0 Authorization Code with PKCE for a public desktop client. */
final class SpotifyOAuth {
    private static final String REDIRECT = "http://127.0.0.1:8888/callback";
    private static final String SCOPE = "user-read-playback-state user-read-currently-playing";
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<String> STATUS = new AtomicReference<>("Not connected");
    private static volatile String accessToken = "";
    private static volatile String refreshToken = "";
    private static volatile long expiresAt;
    private static volatile HttpServer server;

    private SpotifyOAuth() {}

    static String status() { return STATUS.get(); }
    static boolean connected() { return !accessToken.isBlank() && System.currentTimeMillis() < expiresAt; }

    static void load(Path file) {
        try {
            if (!Files.exists(file)) return;
            JsonObject j = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            accessToken = j.has("access_token") ? j.get("access_token").getAsString() : "";
            refreshToken = j.has("refresh_token") ? j.get("refresh_token").getAsString() : "";
            expiresAt = j.has("expires_at") ? j.get("expires_at").getAsLong() : 0;
            if (!refreshToken.isBlank()) STATUS.set("Saved Spotify session found");
        } catch (Exception e) { STATUS.set("Saved session unreadable"); }
    }

    static CompletableFuture<Void> authorize(String clientId, Path tokenFile) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("Enter your Spotify Client ID first");
                String verifier = randomUrl(64);
                String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                String state = randomUrl(24);
                CompletableFuture<String> codeFuture = new CompletableFuture<>();
                stopServer();
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8888), 0);
                server.createContext("/callback", exchange -> {
                    String query = exchange.getRequestURI().getRawQuery();
                    var params = parseQuery(query == null ? "" : query);
                    String response = "SkyLyrics Spotify login received. You can return to Minecraft.";
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                    if (!state.equals(params.get("state"))) {
                        codeFuture.completeExceptionally(new SecurityException("OAuth state did not match"));
                    } else if (params.containsKey("error")) {
                        codeFuture.completeExceptionally(new IOException("Spotify authorization: " + params.get("error")));
                    } else {
                        codeFuture.complete(params.get("code"));
                    }
                });
                server.setExecutor(Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "SkyLyrics-OAuth"); t.setDaemon(true); return t; }));
                server.start();
                String auth = "https://accounts.spotify.com/authorize?response_type=code"
                        + "&client_id=" + enc(clientId)
                        + "&scope=" + enc(SCOPE)
                        + "&redirect_uri=" + enc(REDIRECT)
                        + "&state=" + enc(state)
                        + "&code_challenge_method=S256&code_challenge=" + enc(challenge);
                if (!Desktop.isDesktopSupported()) throw new IOException("Desktop browser integration is unavailable");
                Desktop.getDesktop().browse(URI.create(auth));
                STATUS.set("Waiting for Spotify approval in browser...");
                String code = codeFuture.get();
                exchangeCode(clientId, code, verifier, tokenFile);
                STATUS.set("Connected to Spotify");
            } catch (Exception e) {
                STATUS.set("Login failed: " + rootMessage(e));
            } finally { stopServer(); }
        });
    }

    private static synchronized void exchangeCode(String clientId, String code, String verifier, Path file) throws Exception {
        String body = "grant_type=authorization_code&code=" + enc(code)
                + "&redirect_uri=" + enc(REDIRECT)
                + "&client_id=" + enc(clientId)
                + "&code_verifier=" + enc(verifier);
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        JsonObject json = JsonParser.parseString(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
        if (!json.has("access_token")) throw new IOException("Spotify token exchange failed: " + json);
        accessToken = json.get("access_token").getAsString();
        refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken;
        expiresAt = System.currentTimeMillis() + json.get("expires_in").getAsLong() * 1000L;
        save(file);
    }

    static synchronized void refreshIfNeeded(String clientId, Path file) {
        if (refreshToken.isBlank() || System.currentTimeMillis() < expiresAt - 60000) return;
        try {
            String body = "grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId);
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            JsonObject j = JsonParser.parseString(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
            if (!j.has("access_token")) throw new IOException("Refresh failed: " + j);
            accessToken = j.get("access_token").getAsString();
            if (j.has("refresh_token")) refreshToken = j.get("refresh_token").getAsString();
            expiresAt = System.currentTimeMillis() + j.get("expires_in").getAsLong() * 1000L;
            save(file);
            STATUS.set("Connected to Spotify");
        } catch (Exception e) { STATUS.set("Session refresh failed: " + rootMessage(e)); }
    }

    static String currentTrack() throws Exception {
        if (!connected()) return null;
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                .header("Authorization", "Bearer " + accessToken).GET().build();
        HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 204) return null;
        if (response.statusCode() == 401) { STATUS.set("Spotify session expired"); return null; }
        if (response.statusCode() != 200) throw new IOException("Spotify API returned HTTP " + response.statusCode());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("item") || root.get("item").isJsonNull()) return null;
        JsonObject item = root.getAsJsonObject("item");
        String title = item.get("name").getAsString();
        String artist = item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
        return title + "\n" + artist;
    }

    static void disconnect(Path file) {
        accessToken = ""; refreshToken = ""; expiresAt = 0;
        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
        STATUS.set("Disconnected");
    }

    private static void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        JsonObject j = new JsonObject();
        j.addProperty("access_token", accessToken);
        j.addProperty("refresh_token", refreshToken);
        j.addProperty("expires_at", expiresAt);
        Files.writeString(file, GSON.toJson(j));
    }

    private static void stopServer() {
        HttpServer s = server; server = null;
        if (s != null) s.stop(0);
    }
    private static String randomUrl(int bytes) {
        byte[] b = new byte[bytes]; new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static java.util.Map<String,String> parseQuery(String q) {
        java.util.Map<String,String> m = new java.util.HashMap<>();
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) m.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return m;
    }
    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
