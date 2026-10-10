// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ebolansk.erechnung.util.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fragt die verfügbaren Modelle ab (GET …/models). Beide Anbieter liefern {"data":[{"id":"…"}]}. Es werden nur der Key
 * und die Adresse benutzt, nie Rechnungsdaten.
 */
public final class ModelLister {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    /** Die Adresse der Modellliste zur eingestellten Chat-Adresse (…/messages bzw. …/chat/completions werden ersetzt). */
    static String modelsUrl(AiSettings s) {
        String u = s.endpoint();
        for (String tail : new String[] {"/chat/completions", "/messages"}) {
            if (u.endsWith(tail)) {
                u = u.substring(0, u.length() - tail.length());
            }
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u.endsWith("/models") ? u : u + "/models";
    }

    public List<String> list(AiSettings s) throws IOException {
        if (s.provider() == AiProvider.OFF || s.endpoint().isBlank()) {
            throw new IOException("Bitte zuerst Anbieter und Adresse angeben.");
        }
        HttpRequest.Builder req;
        try {
            req = HttpRequest.newBuilder(URI.create(modelsUrl(s))).timeout(Duration.ofSeconds(12)).header("Accept", "application/json");
        } catch (IllegalArgumentException e) {
            throw new IOException("Die Adresse ist ungültig: " + s.endpoint(), e);
        }
        if (s.provider() == AiProvider.CLAUDE) {
            if (s.apiKey().isBlank()) {
                throw new IOException("Für die Modellliste von Claude wird der API-Key gebraucht.");
            }
            req.header("x-api-key", s.apiKey()).header("anthropic-version", "2023-06-01");
        } else if (!s.apiKey().isBlank()) {
            req.header("Authorization", "Bearer " + s.apiKey());
        }
        HttpResponse<String> res;
        try {
            res = http.send(req.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Die Abfrage wurde unterbrochen.", e);
        }
        if (res.statusCode() == 401 || res.statusCode() == 403) {
            throw new IOException("Zugriff verweigert (Status " + res.statusCode() + "): Key prüfen.");
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException("Die Modellliste ist nicht verfügbar (Status " + res.statusCode() + ").");
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode m : Json.mapper().readTree(res.body()).path("data")) {
            String id = m.path("id").asText("");
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            throw new IOException("Der Server hat keine Modelle gemeldet.");
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }
}
