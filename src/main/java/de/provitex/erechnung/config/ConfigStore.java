// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.config;

import de.provitex.erechnung.archive.ArchiveLayout;
import de.provitex.erechnung.util.AtomicFiles;
import de.provitex.erechnung.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigStore {
    private final Path file;

    public ConfigStore(Path file) {
        this.file = file;
    }

    public AppConfig load(Path home) throws IOException {
        if (!Files.exists(file)) {
            return AppConfig.defaults(home);
        }
        return Json.mapper().readValue(file.toFile(), AppConfig.class);
    }

    public void save(AppConfig config) throws IOException {
        ArchiveLayout.validateTemplate(config.folderTemplate());
        AtomicFiles.write(file, Json.mapper().writeValueAsBytes(config));
    }
}
