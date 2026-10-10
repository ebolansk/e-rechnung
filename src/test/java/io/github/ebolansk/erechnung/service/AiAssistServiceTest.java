// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.ebolansk.erechnung.ai.AiProvider;
import io.github.ebolansk.erechnung.ai.AiSettings;
import io.github.ebolansk.erechnung.ai.AiSettingsStore;
import io.github.ebolansk.erechnung.audit.AuditLog;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiAssistServiceTest {
    private record Logged(String action, Map<String, String> details) {
    }

    @Test
    void successfulRequestIsAuditedWithoutKeyAndWithoutInvoiceText(@TempDir Path dir) throws Exception {
        var store = new AiSettingsStore(dir.resolve("ki.json"));
        store.save(new AiSettings(AiProvider.CLAUDE, "https://api.example/v1/messages", "claude-x", "TOPSECRET", false));
        List<Logged> log = new ArrayList<>();
        var svc = new AiAssistService(store, (a, d) -> {
            log.add(new Logged(a, d));
            return null;
        }, s -> (system, user) -> "{\"number\":\"X-1\",\"items\":[]}");
        var r = svc.ask("Rechnung X-1 für Kunde Geheim AG", "x.pdf", null);
        assertThat(r.fields()).containsEntry("number", "X-1");
        assertThat(log).hasSize(1);
        assertThat(log.get(0).action()).isEqualTo("ki-genutzt");
        assertThat(log.get(0).details()).containsEntry("modell", "claude-x").containsEntry("bestaetigt", "ja")
            .containsEntry("quelle", "https://api.example/v1/messages").containsEntry("ergebnis", "Vorschlag erhalten");
        assertThat(log.toString()).doesNotContain("TOPSECRET").doesNotContain("Geheim AG");
        svc.recordApplied("x.pdf", List.of("number", "items"));
        assertThat(log.get(1).action()).isEqualTo("ki-uebernommen");
        assertThat(log.get(1).details()).containsEntry("felder", "number,items");
    }

    @Test
    void failureIsAuditedAndRethrown(@TempDir Path dir) throws Exception {
        var store = new AiSettingsStore(dir.resolve("ki.json"));
        store.save(new AiSettings(AiProvider.OPENAI, "http://x", "m", "", false));
        List<Logged> log = new ArrayList<>();
        var svc = new AiAssistService(store, (a, d) -> {
            log.add(new Logged(a, d));
            return null;
        }, s -> (system, user) -> {
            throw new IOException("Netz weg");
        });
        assertThatThrownBy(() -> svc.ask("t", "x.pdf", null)).hasMessageContaining("Netz weg");
        assertThat(log.get(0).details().get("ergebnis")).contains("Netz weg");
    }

    @Test
    void notConfiguredNeverCallsTheClientOrTheAudit(@TempDir Path dir) {
        List<Logged> log = new ArrayList<>();
        var svc = new AiAssistService(new AiSettingsStore(dir.resolve("ki.json")), (a, d) -> {
            log.add(new Logged(a, d));
            return null;
        }, s -> (system, user) -> {
            throw new AssertionError("darf nicht aufgerufen werden");
        });
        assertThat(svc.available()).isFalse();
        assertThatThrownBy(() -> svc.ask("t", "x.pdf", null)).hasMessageContaining("nicht konfiguriert");
        assertThat(log).isEmpty();
    }

    @Test
    void realAuditLogKeepsItsChain(@TempDir Path dir) throws Exception {
        var store = new AiSettingsStore(dir.resolve("ki.json"));
        store.save(new AiSettings(AiProvider.OPENAI, "http://x", "m", "", false));
        var audit = new AuditLog(dir.resolve("p.jsonl"), Clock.systemUTC(), "t");
        new AiAssistService(store, audit, s -> (a, b) -> "{\"items\":[]}").ask("t", "x.pdf", null);
        assertThat(audit.verify().valid()).isTrue();
        assertThat(audit.entries()).extracting(e -> e.action()).containsExactly("ki-genutzt");
    }
}
