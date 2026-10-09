// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.update;

import de.provitex.erechnung.util.AtomicFiles;
import de.provitex.erechnung.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** daten/update.json. Das Token liegt unverschlüsselt dort (Leserecht auf ein Repository). */
public final class UpdateSettingsStore {
    private final Path file;

    public UpdateSettingsStore(Path file) {
        this.file = file;
    }

    public UpdateSettings load() {
        if (Files.exists(file)) {
            try {
                return Json.mapper().readValue(file.toFile(), UpdateSettings.class);
            } catch (IOException e) {
                // fällt auf die Voreinstellung zurück
            }
        }
        return new UpdateSettings(null, null);
    }

    public void save(UpdateSettings s) throws IOException {
        AtomicFiles.write(file, Json.mapper().writeValueAsBytes(s));
    }
}
