// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.archive;

import static org.assertj.core.api.Assertions.assertThat;

import de.provitex.erechnung.util.Hashes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveIndexTest {
    @Test
    void findsEntriesByNumberAndByHash(@TempDir Path root) throws Exception {
        new ArchiveWriter().write(root.resolve("M/2027/01/RE-2027-0001"), ArchiveWriterTest.input());
        var index = ArchiveIndex.scan(root);
        assertThat(index.size()).isEqualTo(1);
        assertThat(index.findByNumber("m1", "RE-2027-0001")).isPresent();
        assertThat(index.findByNumber("andere", "RE-2027-0001")).isEmpty();
        assertThat(index.findByNumber("m1", "RE-9999")).isEmpty();
        String hash = Hashes.sha256Hex("PDF-ORIGINAL".getBytes(StandardCharsets.UTF_8));
        assertThat(index.findByOriginalHash(hash)).isPresent();
        assertThat(index.findByOriginalHash("00")).isEmpty();
    }

    @Test
    void corruptEntriesAreSkipped(@TempDir Path root) throws Exception {
        Path bad = root.resolve("kaputt");
        Files.createDirectories(bad);
        Files.writeString(bad.resolve("daten.json"), "{ das ist kein json");
        assertThat(ArchiveIndex.scan(root).findByNumber("m1", "x")).isEmpty();
    }

    @Test
    void missingRootGivesEmptyIndex(@TempDir Path root) throws Exception {
        assertThat(ArchiveIndex.scan(root.resolve("gibt-es-nicht")).findByNumber("m", "n")).isEmpty();
    }
}
