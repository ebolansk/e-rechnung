// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GithubReleaseClientTest {
    private HttpServer server;
    private String base;
    private final List<String> authByPath = new ArrayList<>();
    private int releaseStatus = 200;
    private boolean withHashAsset = true;
    private String body = "Neu: Fehlerbehebungen.";
    private final byte[] payload = "ZIP-INHALT".getBytes(StandardCharsets.UTF_8);
    private final String sha = de.provitex.erechnung.util.Hashes.sha256Hex(payload);

    private void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", ex -> {
            authByPath.add(ex.getRequestURI().getPath() + "=" + ex.getRequestHeaders().getFirst("Authorization"));
            String path = ex.getRequestURI().getPath();
            switch (path) {
                case "/repos/o/r/releases/latest" -> {
                    if (releaseStatus != 200) {
                        reply(ex, releaseStatus, "{}");
                        return;
                    }
                    String assets = "{\"name\":\"E-Rechnung-update-0.2.0.zip\",\"size\":" + payload.length + ",\"url\":\"" + base + "/assets/1\"}"
                        + (withHashAsset ? ",{\"name\":\"E-Rechnung-update-0.2.0.zip.sha256\",\"size\":90,\"url\":\"" + base + "/assets/2\"}" : "");
                    reply(ex, 200, "{\"tag_name\":\"v0.2.0\",\"body\":\"" + body + "\",\"assets\":[" + assets + "]}");
                }
                case "/assets/1" -> {
                    ex.getResponseHeaders().add("Location", base + "/storage/zip");
                    ex.sendResponseHeaders(302, -1);
                    ex.close();
                }
                case "/assets/2" -> reply(ex, 200, sha + "  E-Rechnung-update-0.2.0.zip\n");
                case "/storage/zip" -> {
                    ex.sendResponseHeaders(200, payload.length);
                    ex.getResponseBody().write(payload);
                    ex.close();
                }
                default -> reply(ex, 404, "{}");
            }
        });
        server.start();
    }

    private static void reply(HttpExchange ex, int status, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void findsReleaseAndDownloadsWithoutSendingTokenToStorage(@TempDir Path dir) throws Exception {
        start();
        var client = new GithubReleaseClient(base, new UpdateSettings("o/r", "TOKEN"));
        ReleaseInfo r = client.latest().orElseThrow();
        assertThat(r.version()).isEqualTo("0.2.0");
        assertThat(r.sha256()).isEqualTo(sha);
        assertThat(r.notes()).contains("Fehlerbehebungen");
        Path target = dir.resolve("p.zip");
        client.download(r, target);
        assertThat(Files.readAllBytes(target)).isEqualTo(payload);
        assertThat(authByPath).contains("/repos/o/r/releases/latest=Bearer TOKEN", "/assets/1=Bearer TOKEN", "/storage/zip=null");
        assertThat(dir.resolve("p.zip.part")).doesNotExist();
    }

    @Test
    void publicRepositoryWorksWithoutTokenAndSendsNoAuthorization(@TempDir Path dir) throws Exception {
        start();
        var client = new GithubReleaseClient(base, new UpdateSettings("o/r", ""));
        ReleaseInfo r = client.latest().orElseThrow();
        client.download(r, dir.resolve("p.zip"));
        assertThat(authByPath).contains("/repos/o/r/releases/latest=null", "/assets/1=null", "/storage/zip=null");
    }

    @Test
    void takesHashFromReleaseBodyWhenNoHashAsset() throws Exception {
        withHashAsset = false;
        body = "Hinweise\\nSHA-256: " + sha;
        start();
        assertThat(new GithubReleaseClient(base, new UpdateSettings("o/r", "T")).latest().orElseThrow().sha256()).isEqualTo(sha);
    }

    @Test
    void releaseWithoutAnyHashIsRefused() throws Exception {
        withHashAsset = false;
        start();
        assertThatThrownBy(() -> new GithubReleaseClient(base, new UpdateSettings("o/r", "T")).latest())
            .hasMessageContaining("keine SHA-256");
    }

    @Test
    void explainsNotFoundAndRejectedToken() throws Exception {
        releaseStatus = 404;
        start();
        assertThatThrownBy(() -> new GithubReleaseClient(base, new UpdateSettings("o/r", "")).latest())
            .hasMessageContaining("nicht gefunden").hasMessageContaining("Token");
        assertThatThrownBy(() -> new GithubReleaseClient(base, new UpdateSettings("o/r", "T")).latest())
            .hasMessageContaining("nicht gefunden");
        releaseStatus = 401;
        assertThatThrownBy(() -> new GithubReleaseClient(base, new UpdateSettings("o/r", "T")).latest())
            .hasMessageContaining("verweigert");
    }

    @Test
    void settingsNeedRepoFormatButNoToken() {
        assertThat(new UpdateSettings("o/r", "t").usable()).isTrue();
        assertThat(new UpdateSettings("o/r", "").usable()).isTrue();
        assertThat(new UpdateSettings(null, null).usable()).isTrue();
        assertThat(new UpdateSettings("kaputt", "t").usable()).isFalse();
        assertThat(new UpdateSettings(null, "t").repo()).isEqualTo(UpdateSettings.DEFAULT_REPO);
    }
}
