// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ColumnVisibilityTest {
    @Test
    void hiddenColumnsAreRememberedAcrossStarts(@TempDir Path dir) {
        Path file = dir.resolve("daten/ansicht.json");
        var v = new ColumnVisibility(file);
        assertThat(v.isHidden("Format")).isFalse();
        v.setHidden("Format", true);
        v.setHidden("Ordner", true);
        v.setHidden("Ordner", false);
        var again = new ColumnVisibility(file);
        assertThat(again.isHidden("Format")).isTrue();
        assertThat(again.isHidden("Ordner")).isFalse();
        again.showAll();
        assertThat(new ColumnVisibility(file).isHidden("Format")).isFalse();
    }

    @Test
    void brokenFileMeansEverythingIsShown(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("ansicht.json");
        Files.writeString(file, "kaputt");
        assertThat(new ColumnVisibility(file).isHidden("Format")).isFalse();
    }
}
