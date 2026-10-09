// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.ai;

import de.provitex.erechnung.util.AtomicFiles;
import de.provitex.erechnung.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Liest und schreibt daten/ki.json. Ein nicht gespeicherter Key bleibt nur im Arbeitsspeicher. */
public final class AiSettingsStore {
    private final Path file;
    private String sessionKey = "";

    public AiSettingsStore(Path file) {
        this.file = file;
    }

    public synchronized AiSettings load() {
        AiSettings s = AiSettings.off();
        if (Files.exists(file)) {
            try {
                s = Json.mapper().readValue(file.toFile(), AiSettings.class);
            } catch (IOException e) {
                s = AiSettings.off();
            }
        }
        if (s.apiKey().isBlank() && !sessionKey.isBlank()) {
            s = new AiSettings(s.provider(), s.url(), s.model(), sessionKey, s.saveKey());
        }
        return s;
    }

    public synchronized void save(AiSettings s) throws IOException {
        sessionKey = s.apiKey();
        AiSettings onDisk = s.saveKey() ? s : new AiSettings(s.provider(), s.url(), s.model(), "", false);
        AtomicFiles.write(file, Json.mapper().writeValueAsBytes(onDisk));
    }
}
