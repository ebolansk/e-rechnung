// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sortiert abgelegte Dateien in annehmbare PDFs und Abgelehnte. Geprüft wird der Name und dass es kein Ordner ist, nicht
 * {@code isFile()}: Dateien aus OneDrive (nur online verfügbar) oder anderen Cloud-Ordnern gelten unter Windows als
 * „keine normale Datei“, obwohl sie sich öffnen lassen. Ob sie lesbar sind, zeigt erst das Einlesen („Nicht lesbar“).
 */
record FileIntake(List<File> accepted, List<String> rejected) {
    static FileIntake sort(List<File> files) {
        List<File> ok = new ArrayList<>();
        List<String> no = new ArrayList<>();
        for (File f : files) {
            if (!f.isDirectory() && f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                ok.add(f);
            } else {
                no.add(f.getName());
            }
        }
        return new FileIntake(ok, no);
    }
}
