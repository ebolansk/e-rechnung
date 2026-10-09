// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.update;

import de.provitex.erechnung.audit.AuditTrail;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Wertet beim Programmstart aus, was der Launcher (Swap) getan hat, und protokolliert es. */
public final class UpdateStartup {
    public record Notice(String action, String message, boolean problem) {
    }

    private UpdateStartup() {
    }

    /** Liest und löscht das Ergebnis des letzten Tauschs, protokolliert und liefert einen Hinweis für den Nutzer. */
    public static Optional<Notice> consume(Path home, String runningVersion, AuditTrail audit) throws IOException {
        Path file = UpdateInstaller.updateDir(home).resolve("ergebnis.properties");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        Files.deleteIfExists(file);
        String action = p.getProperty("aktion", "");
        String message = p.getProperty("meldung", "");
        Map<String, String> d = new LinkedHashMap<>();
        d.put("version", runningVersion);
        d.put("meldung", message);
        switch (action) {
            case "UPDATE" -> {
                String expected = pending(home);
                d.put("erwartet", expected == null ? "" : expected);
                audit.append("update-eingespielt", d);
                Files.deleteIfExists(UpdateInstaller.updateDir(home).resolve("pending.json"));
                boolean mismatch = expected != null && !expected.equals(runningVersion);
                return Optional.of(new Notice(action, mismatch
                    ? "Das Update wurde eingespielt, die laufende Version (" + runningVersion + ") weicht von der erwarteten (" + expected + ") ab."
                    : "Das Update auf Version " + runningVersion + " wurde eingespielt.", mismatch));
            }
            case "ROLLBACK_MANUAL", "ROLLBACK_AUTOMATIC" -> {
                d.put("art", action.equals("ROLLBACK_MANUAL") ? "manuell" : "automatisch");
                audit.append("rollback", d);
                return Optional.of(new Notice(action, message + " Laufende Version: " + runningVersion + ".",
                    action.equals("ROLLBACK_AUTOMATIC")));
            }
            case "FAILED" -> {
                audit.append("update-fehlgeschlagen", d);
                return Optional.of(new Notice(action, message, true));
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    /** Die neue Version meldet sich als startfähig: die Bewährungsmarke entfällt, ein Rollback erfolgt nicht mehr automatisch. */
    public static void confirmStarted(Path home) {
        try {
            Files.deleteIfExists(UpdateInstaller.updateDir(home).resolve("probation.flag"));
        } catch (IOException ignored) {
            // bleibt die Marke liegen, stellt der nächste Start die vorherige Version wieder her
        }
    }

    private static String pending(Path home) {
        Path p = UpdateInstaller.updateDir(home).resolve("pending.json");
        try {
            return Files.exists(p) ? de.provitex.erechnung.util.Json.mapper().readTree(p.toFile()).path("version").asText(null) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
