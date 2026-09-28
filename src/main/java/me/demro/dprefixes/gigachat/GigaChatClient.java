package me.demro.dprefixes.gigachat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.demro.dprefixes.utils.GradientUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP-клиент GigaChat API на базе встроенного {@link HttpClient}.
 */
public final class GigaChatClient {

    private static final Gson GSON = new Gson();
    private static final String CT_FORM = "application/x-www-form-urlencoded";
    private static final String CT_JSON = "application/json; charset=utf-8";

    private final Plugin plugin;
    private final HttpClient http;
    private final String authorizationKey;
    private final String scope;
    private final String model;
    private final String baseUrl;
    private final String oauthUrl;
    private final int maxTokens;
    private final double temperature;

    private volatile String accessToken;
    private volatile long tokenExpiresAt;

    public GigaChatClient(@NotNull Plugin plugin,
                          @NotNull String authorizationKey,
                          @NotNull String scope,
                          @NotNull String model,
                          @NotNull String baseUrl,
                          @NotNull String oauthUrl,
                          int maxTokens,
                          double temperature,
                          int timeoutSeconds) {
        this.plugin = plugin;
        this.authorizationKey = authorizationKey;
        this.scope = scope;
        this.model = model;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.oauthUrl = oauthUrl;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .sslContext(trustAllSslContext())
                .build();
    }

    private static @NotNull SSLContext trustAllSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("SSL init failed", e);
        }
    }

    public boolean isConfigured() {
        return !authorizationKey.isEmpty() && !authorizationKey.equalsIgnoreCase("YOUR_AUTHORIZATION_KEY_HERE");
    }

    private synchronized @NotNull String getAccessToken() throws GigaChatException {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < tokenExpiresAt - 30_000L) return accessToken;

        String rqUid = UUID.randomUUID().toString();
        Map<String, String> headers = Map.of(
                "Content-Type", CT_FORM,
                "Accept", "application/json",
                "RqUID", rqUid,
                "Authorization", "Basic " + authorizationKey
        );
        String body = postForm(oauthUrl, "scope=" + scope, headers);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!json.has("access_token")) throw new GigaChatException("OAuth: нет access_token: " + body);

        this.accessToken = json.get("access_token").getAsString();
        long expSec = json.has("expires_at") ? json.get("expires_at").getAsLong() : (now / 1000L + 1800L);
        this.tokenExpiresAt = expSec * 1000L;
        return accessToken;
    }

    private @NotNull String postForm(@NotNull String url, @NotNull String body, @NotNull Map<String, String> headers) throws GigaChatException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        return send(b.build());
    }

    private @NotNull String postJson(@NotNull String url, @NotNull String json, @NotNull Map<String, String> headers) throws GigaChatException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        return send(b.build());
    }

    private @NotNull String send(@NotNull HttpRequest req) throws GigaChatException {
        try {
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() < 200 || r.statusCode() >= 300)
                throw new GigaChatException("HTTP " + r.statusCode() + ": " + r.body());
            return r.body();
        } catch (Exception e) {
            throw new GigaChatException("HTTP IO: " + e.getMessage(), e);
        }
    }

    /**
     * Асинхронная генерация текста.
     *
     * @param userPrompt промпт
     * @return future с ответом
     */
    public @NotNull CompletableFuture<String> generateAsync(@NotNull String userPrompt) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(doChatCompletion(getAccessToken(), userPrompt));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private @NotNull String doChatCompletion(@NotNull String token, @NotNull String userPrompt) throws GigaChatException {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", userPrompt);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.add("messages", messages);
        payload.addProperty("max_tokens", maxTokens);
        payload.addProperty("temperature", temperature);
        payload.addProperty("stream", false);

        Map<String, String> headers = Map.of(
                "Authorization", "Bearer " + token,
                "Accept", "application/json",
                "Content-Type", CT_JSON
        );

        String body = postJson(baseUrl + "/chat/completions", GSON.toJson(payload), headers);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!json.has("choices")) throw new GigaChatException("Нет choices: " + body);

        JsonArray choices = json.getAsJsonArray("choices");
        if (choices.isEmpty()) throw new GigaChatException("Пустой choices");
        return choices.get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
    }

    /**
     * Парсит ответ модели в список вариантов.
     *
     * @param raw ответ модели
     * @return список очищенных вариантов
     */
    public static @NotNull List<String> parseVariants(@Nullable String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;

        String cleaned = raw.replace("```", "")
                .replaceAll("(?m)^\\s*[-*•●▪◦‣⁃]\\s*", "")
                .replaceAll("(?m)^\\s*\\d+[.)]\\s*", "")
                .trim();

        for (String line : cleaned.split("\\r?\\n")) {
            String v = line.trim();
            if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))))
                v = v.substring(1, v.length() - 1).trim();

            v = GradientUtils.stripNonText(v);
            if (v.isEmpty()) continue;
            if (v.length() > 20) v = v.substring(0, 20).trim();
            if (v.isEmpty()) continue;
            out.add(v);
        }
        return out;
    }
}