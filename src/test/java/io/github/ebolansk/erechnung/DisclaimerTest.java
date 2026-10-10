// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.audit.AuditLog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DisclaimerTest {
    @Test
    void neededUntilAcceptedAndRecordedInLog(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("daten/hinweis.json");
        assertThat(Disclaimer.needed(file)).isTrue();
        var audit = new AuditLog(dir.resolve("daten/protokoll.jsonl"), Clock.systemUTC(), "tester");
        Disclaimer.accept(file, audit, Clock.systemUTC(), "tester");
        assertThat(Disclaimer.needed(file)).isFalse();
        assertThat(Files.readString(dir.resolve("daten/protokoll.jsonl"))).contains("hinweis-bestaetigt");
    }

    @Test
    void olderVersionOrBrokenFileNeedsConfirmationAgain(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("hinweis.json");
        Files.writeString(file, "{\"version\":0}");
        assertThat(Disclaimer.needed(file)).isTrue();
        Files.writeString(file, "kaputt");
        assertThat(Disclaimer.needed(file)).isTrue();
    }
}
