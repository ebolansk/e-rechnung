// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.provitex.erechnung.model.OutputFormat;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {
    @Test
    void defaultsWhenFileMissing(@TempDir Path dir) throws Exception {
        var cfg = new ConfigStore(dir.resolve("konfiguration.json")).load(dir);
        assertThat(cfg.outputFormat()).isEqualTo(OutputFormat.XRECHNUNG);
        assertThat(cfg.cutoffDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(cfg.archiveRoot()).isEqualTo(dir.resolve("archiv").toString());
    }

    @Test
    void saveAndLoadRoundTrip(@TempDir Path dir) throws Exception {
        var store = new ConfigStore(dir.resolve("konfiguration.json"));
        store.save(new AppConfig("/x/archiv", "{Jahr}/{Rechnungsnummer}", OutputFormat.ZUGFERD, LocalDate.of(2027, 1, 1)));
        var cfg = store.load(dir);
        assertThat(cfg.outputFormat()).isEqualTo(OutputFormat.ZUGFERD);
        assertThat(cfg.folderTemplate()).isEqualTo("{Jahr}/{Rechnungsnummer}");
    }

    @Test
    void invalidTemplateIsRejectedOnSave(@TempDir Path dir) {
        var store = new ConfigStore(dir.resolve("konfiguration.json"));
        assertThatThrownBy(() -> store.save(new AppConfig("/x", "{Jahr}", OutputFormat.XRECHNUNG, LocalDate.of(2027, 1, 1))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
