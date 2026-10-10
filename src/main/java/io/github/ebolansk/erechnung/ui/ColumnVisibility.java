// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import io.github.ebolansk.erechnung.util.AtomicFiles;
import io.github.ebolansk.erechnung.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Welche Spalten der Übersicht ausgeblendet sind; wird in daten/ansicht.json gemerkt. */
final class ColumnVisibility {
    private record Stored(List<String> hidden) {
    }

    private final Path file;
    private final Set<String> hidden = new LinkedHashSet<>();

    /** Ohne Datei (null) wird nichts gemerkt. Gibt es noch keine gemerkte Auswahl, sind die Spalten aus defaultHidden ausgeblendet. */
    ColumnVisibility(Path file, Set<String> defaultHidden) {
        this.file = file;
        if (file == null || !Files.exists(file)) {
            hidden.addAll(defaultHidden);
        }
        if (file != null && Files.exists(file)) {
            try {
                Stored s = Json.mapper().readValue(file.toFile(), Stored.class);
                if (s != null && s.hidden() != null) {
                    hidden.addAll(s.hidden());
                }
            } catch (IOException e) {
                // unlesbar: alle Spalten anzeigen
            }
        }
    }

    ColumnVisibility(Path file) {
        this(file, Set.of());
    }

    boolean isHidden(String column) {
        return hidden.contains(column);
    }

    void setHidden(String column, boolean on) {
        if (on) {
            hidden.add(column);
        } else {
            hidden.remove(column);
        }
        save();
    }

    void showAll() {
        hidden.clear();
        save();
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            AtomicFiles.write(file, Json.mapper().writeValueAsBytes(new Stored(new ArrayList<>(hidden))));
        } catch (IOException e) {
            // nur eine Komfortangabe: ein Fehler beim Speichern darf nichts stören
        }
    }
}
