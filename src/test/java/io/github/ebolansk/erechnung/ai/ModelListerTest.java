// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ModelListerTest {
    @Test
    void modelsUrlReplacesChatEndpoints() {
        assertThat(ModelLister.modelsUrl(new AiSettings(AiProvider.CLAUDE, "", "", "k", false))).isEqualTo("https://api.anthropic.com/v1/models");
        assertThat(ModelLister.modelsUrl(new AiSettings(AiProvider.OPENAI, "http://x:1/v1/", "", "", false))).isEqualTo("http://x:1/v1/models");
        assertThat(ModelLister.modelsUrl(new AiSettings(AiProvider.OPENAI, "http://x/v1/chat/completions", "", "", false)))
            .isEqualTo("http://x/v1/models");
        assertThat(ModelLister.modelsUrl(new AiSettings(AiProvider.OPENAI, "", "", "", false))).isEqualTo("https://api.openai.com/v1/models");
    }

    @Test
    void listsSortedModelsAndSendsKeyOnlyAsHeader() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"data\":[{\"id\":\"zeta\"},{\"id\":\"Alpha\"},{\"id\":\"\"}]}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            List<String> models = new ModelLister().list(new AiSettings(AiProvider.OPENAI, base, "", "geheim", false));
            assertThat(models).containsExactly("Alpha", "zeta");
            assertThat(auth.get()).isEqualTo("Bearer geheim");
            assertThat(new ModelLister().list(new AiSettings(AiProvider.OPENAI, base, "", "", false))).hasSize(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void explainsMissingKeyForClaudeAndServerErrors() throws Exception {
        assertThatThrownBy(() -> new ModelLister().list(new AiSettings(AiProvider.CLAUDE, "", "", "", false))).hasMessageContaining("Key");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            ex.sendResponseHeaders(401, -1);
            ex.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThatThrownBy(() -> new ModelLister().list(new AiSettings(AiProvider.OPENAI, base, "", "x", false)))
                .hasMessageContaining("verweigert");
        } finally {
            server.stop(0);
        }
    }
}
