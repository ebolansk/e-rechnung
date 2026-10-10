// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditLogTest {
    private final Clock clock = Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void emptyLogIsValid(@TempDir Path dir) throws Exception {
        var log = new AuditLog(dir.resolve("protokoll.jsonl"), clock, "tester");
        assertThat(log.verify().valid()).isTrue();
        assertThat(log.verify().entries()).isZero();
    }

    @Test
    void chainOfThreeEntriesVerifies(@TempDir Path dir) throws Exception {
        var log = new AuditLog(dir.resolve("protokoll.jsonl"), clock, "tester");
        var e1 = log.append("import", Map.of("sha256", "aa"));
        var e2 = log.append("validiert", Map.of("ergebnis", "bestanden"));
        var e3 = log.append("archiviert", Map.of("pfad", "x"));
        assertThat(e1.seq()).isEqualTo(1);
        assertThat(e2.prev()).isEqualTo(e1.hash());
        assertThat(e3.prev()).isEqualTo(e2.hash());
        var v = log.verify();
        assertThat(v.valid()).isTrue();
        assertThat(v.entries()).isEqualTo(3);
    }

    @Test
    void reopeningContinuesTheChain(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("protokoll.jsonl");
        var first = new AuditLog(f, clock, "a");
        var e1 = first.append("x", Map.of());
        var again = new AuditLog(f, clock, "b");
        var e2 = again.append("y", Map.of());
        assertThat(e2.seq()).isEqualTo(2);
        assertThat(e2.prev()).isEqualTo(e1.hash());
        assertThat(again.verify().valid()).isTrue();
    }

    @Test
    void truncatedTailDoesNotRestartTheChain(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("protokoll.jsonl");
        var log = new AuditLog(f, clock, "tester");
        log.append("a", Map.of());
        log.append("b", Map.of());
        // Absturz mitten im Schreiben: die letzte Zeile ist abgeschnitten.
        Files.writeString(f, "{\"seq\":3,\"ti", java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        String before = Files.readString(f);

        var after = new AuditLog(f, clock, "tester");
        for (int i = 0; i < 2; i++) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> after.append("c", Map.of()))
                .as("jeder Versuch scheitert, keiner beginnt bei Folgenummer 1")
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("beschädigt");
        }
        assertThat(Files.readString(f)).as("es wurde nichts angehängt").isEqualTo(before);
        var v = after.verify();
        assertThat(v.valid()).isFalse();
        assertThat(v.firstBadSeq()).isEqualTo(3);
    }

    @Test
    void changedEntryIsDetected(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("protokoll.jsonl");
        var log = new AuditLog(f, clock, "tester");
        log.append("a", Map.of("k", "v1"));
        log.append("b", Map.of("k", "v2"));
        log.append("c", Map.of("k", "v3"));
        List<String> lines = new ArrayList<>(Files.readAllLines(f));
        lines.set(1, lines.get(1).replace("v2", "VERFAELSCHT"));
        Files.write(f, lines);
        var v = log.verify();
        assertThat(v.valid()).isFalse();
        assertThat(v.firstBadSeq()).isEqualTo(2);
    }

    @Test
    void removedEntryIsDetected(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("protokoll.jsonl");
        var log = new AuditLog(f, clock, "tester");
        log.append("a", Map.of());
        log.append("b", Map.of());
        log.append("c", Map.of());
        List<String> lines = new ArrayList<>(Files.readAllLines(f));
        lines.remove(1);
        Files.write(f, lines);
        var v = log.verify();
        assertThat(v.valid()).isFalse();
        assertThat(v.firstBadSeq()).isEqualTo(3);
    }
}
