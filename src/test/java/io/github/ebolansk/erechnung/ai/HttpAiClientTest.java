// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpAiClientTest {
    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> bearer = new AtomicReference<>();

    private String start(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            path.set(ex.getRequestURI().getPath());
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKey.set(ex.getRequestHeaders().getFirst("x-api-key"));
            bearer.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void claudeSendsKeyAndSystemPromptAndReadsTextBlocks() throws Exception {
        String base = start(200, "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"a\\\":1}\"}]}");
        var client = new HttpAiClient(new AiSettings(AiProvider.CLAUDE, base + "/v1/messages", "claude-x", "geheim", false));
        assertThat(client.complete("SYS", "USER")).isEqualTo("{\"a\":1}");
        assertThat(path.get()).isEqualTo("/v1/messages");
        assertThat(apiKey.get()).isEqualTo("geheim");
        var sent = io.github.ebolansk.erechnung.util.Json.mapper().readTree(body.get());
        assertThat(sent.path("system").asText()).isEqualTo("SYS");
        assertThat(sent.path("model").asText()).isEqualTo("claude-x");
        assertThat(sent.path("messages").path(0).path("content").asText()).isEqualTo("USER");
    }

    @Test
    void openAiAppendsChatCompletionsAndUsesBearerOnlyWithKey() throws Exception {
        String base = start(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        assertThat(new HttpAiClient(new AiSettings(AiProvider.OPENAI, base + "/v1", "m", "k1", false)).complete("S", "U"))
            .isEqualTo("ok");
        assertThat(path.get()).isEqualTo("/v1/chat/completions");
        assertThat(bearer.get()).isEqualTo("Bearer k1");
        assertThat(io.github.ebolansk.erechnung.util.Json.mapper().readTree(body.get()).path("messages").path(0).path("role").asText())
            .isEqualTo("system");
        new HttpAiClient(new AiSettings(AiProvider.OPENAI, base + "/v1", "m", "", false)).complete("S", "U");
        assertThat(bearer.get()).isNull();
    }

    @Test
    void errorStatusBecomesReadableExceptionWithoutLeakingKey() throws Exception {
        String base = start(401, "{\"error\":\"invalid key\"}");
        var client = new HttpAiClient(new AiSettings(AiProvider.CLAUDE, base + "/v1/messages", "m", "geheim123", false));
        assertThatThrownBy(() -> client.complete("S", "U")).isInstanceOf(IOException.class)
            .hasMessageContaining("401").hasMessageNotContaining("geheim123");
    }

    @Test
    void unreachableServerIsAnIoException() {
        var client = new HttpAiClient(new AiSettings(AiProvider.OPENAI, "http://127.0.0.1:1", "m", "", false));
        assertThatThrownBy(() -> client.complete("S", "U")).isInstanceOf(IOException.class);
    }

    @Test
    void emptyAnswerIsAnError() throws Exception {
        String base = start(200, "{\"choices\":[]}");
        var client = new HttpAiClient(new AiSettings(AiProvider.OPENAI, base, "m", "", false));
        assertThatThrownBy(() -> client.complete("S", "U")).hasMessageContaining("keine verwertbare");
    }

    @Test
    void settingsStoreKeepsKeyOnlyInMemoryUnlessAskedToSave(@TempDir Path dir) throws Exception {
        var store = new AiSettingsStore(dir.resolve("ki.json"));
        store.save(new AiSettings(AiProvider.CLAUDE, "", "m", "streng-geheim", false));
        assertThat(java.nio.file.Files.readString(dir.resolve("ki.json"))).doesNotContain("streng-geheim");
        assertThat(store.load().apiKey()).isEqualTo("streng-geheim");
        assertThat(new AiSettingsStore(dir.resolve("ki.json")).load().apiKey()).isEmpty();
        assertThat(new AiSettingsStore(dir.resolve("ki.json")).load().usable()).isFalse();
        // Speichern geht nur verschlüsselt (hier ein Test-Protector; unter Windows ist es DPAPI).
        var withProtector = new AiSettingsStore(dir.resolve("ki.json"), AiSettingsStoreTest.FAKE);
        withProtector.save(new AiSettings(AiProvider.CLAUDE, "", "m", "streng-geheim", true));
        assertThat(new AiSettingsStore(dir.resolve("ki.json"), AiSettingsStoreTest.FAKE).load().usable()).isTrue();
    }

    @Test
    void offByDefault(@TempDir Path dir) {
        assertThat(new AiSettingsStore(dir.resolve("nix.json")).load().provider()).isEqualTo(AiProvider.OFF);
        assertThat(AiSettings.off().usable()).isFalse();
    }

    // ---- Abbrechen ------------------------------------------------------------------------------------------

    private String startSlow(long delayMillis, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", ex -> {
            ex.getRequestBody().readAllBytes();
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ignored) {
                // Client ist weg
            }
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            try {
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
            } catch (IOException ignored) {
                // Client hat abgebrochen
            }
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void slowAnswerIsWaitedForWithoutHurry() throws Exception {
        String base = startSlow(1500, "{\"choices\":[{\"message\":{\"content\":\"fertig\"}}]}");
        var client = new HttpAiClient(new AiSettings(AiProvider.OPENAI, base + "/v1", "m", "", false));
        assertThat(client.complete("S", "U", new AiCancel())).isEqualTo("fertig");
    }

    @Test
    void cancelEndsTheWaitImmediatelyInsteadOfAfterTheModelIsDone() throws Exception {
        String base = startSlow(10_000, "{\"choices\":[{\"message\":{\"content\":\"zu spät\"}}]}");
        var client = new HttpAiClient(new AiSettings(AiProvider.OPENAI, base + "/v1", "m", "", false));
        var cancel = new AiCancel();
        var outcome = new AtomicReference<Throwable>();
        Thread t = new Thread(() -> {
            try {
                client.complete("S", "U", cancel);
            } catch (Throwable e) {
                outcome.set(e);
            }
        });
        long start = System.currentTimeMillis();
        t.start();
        Thread.sleep(400);
        cancel.cancel();
        t.join(3000);
        assertThat(t.isAlive()).as("Abbruch muss sofort wirken, nicht erst nach 10 s").isFalse();
        assertThat(outcome.get()).isInstanceOf(AiCancelledException.class);
        assertThat(System.currentTimeMillis() - start).isLessThan(5000);
    }

    @Test
    void alreadyCancelledRequestIsNotSentAtAll() throws Exception {
        String base = start(200, "{}");
        var cancel = new AiCancel();
        cancel.cancel();
        var client = new HttpAiClient(new AiSettings(AiProvider.OPENAI, base + "/v1", "m", "", false));
        assertThatThrownBy(() -> client.complete("S", "U", cancel)).isInstanceOf(AiCancelledException.class);
        assertThat(path.get()).isNull();
    }
}
