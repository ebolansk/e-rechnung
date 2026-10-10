// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ebolansk.erechnung.util.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Spricht die Claude Messages API oder einen OpenAI-kompatiblen Chat-Completions-Endpunkt an (eine Antwort, kein Streaming). */
public final class HttpAiClient implements AiClient {
    /** So lange wartet das Tool auf die fertige Antwort; langsame Modelle brauchen oft länger als eine Minute. */
    private static final Duration ANSWER_TIMEOUT = Duration.ofMinutes(5);

    private final AiSettings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public HttpAiClient(AiSettings settings) {
        this.settings = settings;
    }

    @Override
    public String complete(String system, String user) throws IOException {
        return complete(system, user, new AiCancel());
    }

    /** Wie {@link #complete(String, String)}, lässt sich aber über {@code cancel} sofort abbrechen (die Anfrage wird beendet). */
    @Override
    public String complete(String system, String user, AiCancel cancel) throws IOException {
        if (cancel.isCancelled()) {
            throw new AiCancelledException();
        }
        boolean claude = settings.provider() == AiProvider.CLAUDE;
        ObjectNode body = Json.mapper().createObjectNode();
        body.put("model", settings.model());
        ArrayNode messages = body.putArray("messages");
        if (claude) {
            body.put("max_tokens", 4096);
            body.put("system", system);
        } else {
            body.put("temperature", 0);
            messages.addObject().put("role", "system").put("content", system);
        }
        messages.addObject().put("role", "user").put("content", user);

        HttpRequest.Builder req;
        try {
            req = HttpRequest.newBuilder(URI.create(url(claude)))
                .timeout(ANSWER_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.mapper().writeValueAsString(body), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IOException("Die KI-Adresse ist ungültig: " + settings.endpoint(), e);
        }
        if (claude) {
            req.header("x-api-key", settings.apiKey()).header("anthropic-version", "2023-06-01");
        } else if (!settings.apiKey().isBlank()) {
            req.header("Authorization", "Bearer " + settings.apiKey());
        }
        CompletableFuture<HttpResponse<String>> future = http.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        cancel.onCancel(() -> future.cancel(true));
        HttpResponse<String> res;
        try {
            res = future.get();
        } catch (CancellationException e) {
            throw new AiCancelledException();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Die Anfrage wurde unterbrochen.", e);
        } catch (ExecutionException e) {
            if (cancel.isCancelled()) {
                throw new AiCancelledException();
            }
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof java.net.http.HttpTimeoutException) {
                throw new IOException("Die KI hat nach " + ANSWER_TIMEOUT.toMinutes() + " Minuten nicht geantwortet.", cause);
            }
            if (cause instanceof IllegalArgumentException) {
                throw new IOException("Die KI-Adresse ist ungültig: " + settings.endpoint(), cause);
            }
            throw cause instanceof IOException io ? io : new IOException(cause.getMessage(), cause);
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException("Die KI antwortete mit Status " + res.statusCode() + ": " + shorten(res.body()));
        }
        JsonNode root = Json.mapper().readTree(res.body());
        String text = claude ? claudeText(root) : root.path("choices").path(0).path("message").path("content").asText("");
        if (text.isBlank()) {
            throw new IOException("Die KI hat keine verwertbare Antwort geliefert.");
        }
        return text;
    }

    private String url(boolean claude) {
        String u = settings.endpoint();
        if (!claude && !u.endsWith("/chat/completions")) {
            u = (u.endsWith("/") ? u.substring(0, u.length() - 1) : u) + "/chat/completions";
        }
        return u;
    }

    private static String claudeText(JsonNode root) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : root.path("content")) {
            if ("text".equals(part.path("type").asText())) {
                sb.append(part.path("text").asText());
            }
        }
        return sb.toString();
    }

    private static String shorten(String s) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) + " …" : t;
    }
}
