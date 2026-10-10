// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveRecordTest {
    private static ArchiveRecord record(Path dir, String filesJson) throws Exception {
        Files.createDirectories(dir);
        Path json = dir.resolve("daten.json");
        Files.writeString(json, "{\"invoice\":{\"number\":\"R-1\"},\"files\":" + filesJson + "}");
        return ArchiveRecord.parse(json);
    }

    @Test
    void outputFileIsLimitedToTheTwoNamesTheArchiverWrites(@TempDir Path dir) throws Exception {
        assertThat(record(dir.resolve("a"), "{\"vorlage-original.pdf\":{\"sha256\":\"x\"},\"ausgabe-xrechnung.xml\":{\"sha256\":\"y\"}}").outputFile())
            .isEqualTo("ausgabe-xrechnung.xml");
        assertThat(record(dir.resolve("b"), "{\"ausgabe-zugferd.pdf\":{\"sha256\":\"y\"}}").outputFile()).isEqualTo("ausgabe-zugferd.pdf");
        var manipulated = record(dir.resolve("c"), "{\"ausgabe-/../../../x.exe\":{\"sha256\":\"y\"},\"ausgabe-x.exe\":{\"sha256\":\"y\"}}");
        assertThat(manipulated.outputFile()).as("kein Name außerhalb der Whitelist").isEmpty();
    }

    @Test
    void onlyPlainFileNamesAreResolvedAgainstTheArchiveFolder() {
        assertThat(ArchiveRecord.plainFileName("vorlage-original.pdf")).isTrue();
        assertThat(ArchiveRecord.plainFileName("pruefprotokoll.html")).isTrue();
        assertThat(ArchiveRecord.plainFileName("../daten.json")).isFalse();
        assertThat(ArchiveRecord.plainFileName("ausgabe-/../../x.exe")).isFalse();
        assertThat(ArchiveRecord.plainFileName("a\\b.txt")).isFalse();
        assertThat(ArchiveRecord.plainFileName("..")).isFalse();
        assertThat(ArchiveRecord.plainFileName("")).isFalse();
        assertThat(ArchiveRecord.plainFileName(null)).isFalse();
    }
}
