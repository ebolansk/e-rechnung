// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.config;

import io.github.ebolansk.erechnung.archive.ArchiveLayout;
import io.github.ebolansk.erechnung.model.OutputFormat;
import java.nio.file.Path;
import java.time.LocalDate;

public record AppConfig(String archiveRoot, String folderTemplate, OutputFormat outputFormat, LocalDate cutoffDate) {
    public static AppConfig defaults(Path home) {
        return new AppConfig(home.resolve("archiv").toString(), ArchiveLayout.DEFAULT_TEMPLATE,
            OutputFormat.XRECHNUNG, LocalDate.of(2027, 1, 1));
    }
}
