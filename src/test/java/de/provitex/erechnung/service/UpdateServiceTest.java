// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import de.provitex.erechnung.audit.AuditLog;
import de.provitex.erechnung.launcher.Swap;
import de.provitex.erechnung.update.GithubReleaseClient;
import de.provitex.erechnung.update.UpdateInstallerTest;
import de.provitex.erechnung.update.UpdateSettings;
import de.provitex.erechnung.update.UpdateSettingsStore;
import de.provitex.erechnung.update.UpdateStartup;
import de.provitex.erechnung.util.Hashes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateServiceTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String serve(String version, byte[] zip, String sha) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String name = "E-Rechnung-update-" + version + ".zip";
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            byte[] out;
            if (path.equals("/repos/o/r/releases/latest")) {
                out = ("{\"tag_name\":\"v" + version + "\",\"body\":\"SHA-256: " + sha + "\",\"assets\":[{\"name\":\"" + name
                    + "\",\"size\":" + zip.length + ",\"url\":\"" + base + "/zip\"}]}").getBytes(StandardCharsets.UTF_8);
            } else if (path.equals("/zip")) {
                out = zip;
            } else {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return base;
    }

    private static void install(Path home, String content) throws Exception {
        Files.createDirectories(home.resolve("app/lib"));
        Files.writeString(home.resolve("app/lib/e-rechnung.jar"), content);
    }

    @Test
    void fullCycleCheckPrepareSwapStartAndRollback(@TempDir Path home) throws Exception {
        install(home, "v1");
        Path zip = UpdateInstallerTest.zip(home.resolve("src.zip"), "0.2.0", UpdateInstallerTest.good());
        byte[] bytes = Files.readAllBytes(zip);
        String base = serve("0.2.0", bytes, Hashes.sha256Hex(bytes));
        var store = new UpdateSettingsStore(home.resolve("daten/update.json"));
        store.save(new UpdateSettings("o/r", "T"));
        var audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), Clock.systemUTC(), "t");
        var svc = new UpdateService(home, store, audit, "0.1.0", s -> new GithubReleaseClient(base, s));

        var release = svc.check().orElseThrow();
        assertThat(release.version()).isEqualTo("0.2.0");
        svc.prepare(release);
        assertThat(svc.pendingVersion()).isEqualTo("0.2.0");
        assertThat(home.resolve("daten/update/download/E-Rechnung-update-0.2.0.zip")).doesNotExist();
        assertThat(home.resolve("app/lib/e-rechnung.jar")).hasContent("v1");

        assertThat(Swap.run(home)).isEqualTo(Swap.Action.UPDATE);
        assertThat(home.resolve("app/lib/e-rechnung.jar")).hasContent("JAR");
        var notice = UpdateStartup.consume(home, "0.2.0", audit).orElseThrow();
        assertThat(notice.problem()).isFalse();
        assertThat(notice.message()).contains("0.2.0");
        UpdateStartup.confirmStarted(home);
        assertThat(svc.rollbackPossible()).isTrue();
        assertThat(svc.pendingVersion()).isNull();

        svc.requestRollback();
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.ROLLBACK_MANUAL);
        assertThat(home.resolve("app/lib/e-rechnung.jar")).hasContent("v1");
        assertThat(UpdateStartup.consume(home, "0.1.0", audit).orElseThrow().message()).contains("wiederhergestellt");

        assertThat(audit.entries()).extracting(e -> e.action())
            .containsExactly("update-vorbereitet", "update-eingespielt", "rollback-vorgemerkt", "rollback");
        assertThat(audit.verify().valid()).isTrue();
    }

    @Test
    void startCheckIsOffByDefaultAndSendsNothing(@TempDir Path home) throws Exception {
        var store = new UpdateSettingsStore(home.resolve("u.json"));
        store.save(new UpdateSettings("o/r", ""));
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var svc = new UpdateService(home, store, (a, d) -> null, "0.1.0", s -> {
            requests.incrementAndGet();
            return null;
        });
        assertThat(svc.checkOnStart(java.time.Instant.now())).isEmpty();
        assertThat(requests).hasValue(0);
        assertThat(store.load().checkOnStart()).isFalse();
        Files.writeString(home.resolve("alt.json"), "{\"repo\":\"o/r\",\"token\":\"\"}");
        assertThat(new UpdateSettingsStore(home.resolve("alt.json")).load().checkOnStart()).isFalse();
    }

    @Test
    void startCheckFindsNewVersionAtMostOncePerDayAndLogs(@TempDir Path home) throws Exception {
        Path zip = UpdateInstallerTest.zip(home.resolve("src.zip"), "0.2.0", UpdateInstallerTest.good());
        byte[] bytes = Files.readAllBytes(zip);
        String base = serve("0.2.0", bytes, Hashes.sha256Hex(bytes));
        var store = new UpdateSettingsStore(home.resolve("daten/update.json"));
        store.save(new UpdateSettings("o/r", "", true));
        var audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), Clock.systemUTC(), "t");
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var svc = new UpdateService(home, store, audit, "0.1.0", s -> {
            requests.incrementAndGet();
            return new GithubReleaseClient(base, s);
        });
        var now = java.time.Instant.parse("2027-03-01T08:00:00Z");
        assertThat(svc.checkOnStart(now).orElseThrow().version()).isEqualTo("0.2.0");
        assertThat(svc.checkOnStart(now.plusSeconds(3600))).isEmpty();
        assertThat(requests).hasValue(1);
        assertThat(svc.checkOnStart(now.plus(java.time.Duration.ofHours(25)))).isPresent();
        assertThat(requests).hasValue(2);
        assertThat(audit.entries()).extracting(e -> e.action()).containsOnly("update-pruefung-start");
    }

    @Test
    void startCheckFailureStaysSilentAndIsNotRepeatedTheSameDay(@TempDir Path home) throws Exception {
        var store = new UpdateSettingsStore(home.resolve("daten/update.json"));
        store.save(new UpdateSettings("o/r", "", true));
        var audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), Clock.systemUTC(), "t");
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var svc = new UpdateService(home, store, audit, "0.1.0", s -> {
            requests.incrementAndGet();
            return new GithubReleaseClient("http://127.0.0.1:1", s);
        });
        var now = java.time.Instant.parse("2027-03-01T08:00:00Z");
        assertThat(svc.checkOnStart(now)).isEmpty();
        assertThat(svc.checkOnStart(now.plusSeconds(60))).isEmpty();
        assertThat(requests).hasValue(1);
        assertThat(audit.entries()).extracting(e -> e.details().get("ergebnis")).containsExactly("fehlgeschlagen");
    }

    @Test
    void olderOrEqualReleaseIsNoUpdate(@TempDir Path home) throws Exception {
        Path zip = UpdateInstallerTest.zip(home.resolve("src.zip"), "0.1.0", UpdateInstallerTest.good());
        byte[] bytes = Files.readAllBytes(zip);
        String base = serve("0.1.0", bytes, Hashes.sha256Hex(bytes));
        var store = new UpdateSettingsStore(home.resolve("u.json"));
        store.save(new UpdateSettings("o/r", "T"));
        var svc = new UpdateService(home, store, (a, d) -> null, "0.1.0", s -> new GithubReleaseClient(base, s));
        assertThat(svc.check()).isEmpty();
    }

    @Test
    void tamperedPackageIsNotStagedAndInstalledVersionStaysUntouched(@TempDir Path home) throws Exception {
        install(home, "v1");
        Path zip = UpdateInstallerTest.zip(home.resolve("src.zip"), "0.2.0", UpdateInstallerTest.good());
        byte[] bytes = Files.readAllBytes(zip);
        String base = serve("0.2.0", bytes, "a".repeat(64));
        var store = new UpdateSettingsStore(home.resolve("u.json"));
        store.save(new UpdateSettings("o/r", "T"));
        var svc = new UpdateService(home, store, (a, d) -> {
            throw new AssertionError("darf nichts protokollieren");
        }, "0.1.0", s -> new GithubReleaseClient(base, s));
        var release = svc.check().orElseThrow();
        assertThatThrownBy(() -> svc.prepare(release)).hasMessageContaining("Prüfsumme");
        assertThat(svc.pendingVersion()).isNull();
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.NONE);
        assertThat(home.resolve("app/lib/e-rechnung.jar")).hasContent("v1");
    }

    @Test
    void publicDefaultNeedsNoSetupButBrokenRepoExplainsWhere(@TempDir Path home) throws Exception {
        var store = new UpdateSettingsStore(home.resolve("u.json"));
        var svc = new UpdateService(home, store, (a, d) -> null, "0.1.0");
        assertThat(svc.configured()).isTrue();
        store.save(new UpdateSettings("kaputt", ""));
        assertThat(svc.configured()).isFalse();
        assertThatThrownBy(svc::check).hasMessageContaining("Konfiguration → Update");
    }

    @Test
    void automaticRollbackIsReportedAsProblem(@TempDir Path home) throws Exception {
        install(home, "v1");
        Files.createDirectories(home.resolve("daten/update/neu/app/lib"));
        Files.writeString(home.resolve("daten/update/neu/app/lib/e-rechnung.jar"), "v2");
        var audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), Clock.systemUTC(), "t");
        Swap.run(home);
        UpdateStartup.consume(home, "0.2.0", audit);
        assertThat(Swap.run(home)).isEqualTo(Swap.Action.ROLLBACK_AUTOMATIC);
        var n = UpdateStartup.consume(home, "0.1.0", audit).orElseThrow();
        assertThat(n.problem()).isTrue();
        assertThat(audit.entries()).extracting(e -> e.action()).contains("rollback");
    }
}
