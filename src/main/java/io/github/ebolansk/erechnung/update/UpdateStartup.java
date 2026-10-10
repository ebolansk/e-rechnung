// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.update;

import io.github.ebolansk.erechnung.audit.AuditTrail;
import io.github.ebolansk.erechnung.launcher.Swap;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
                    : "Das Update auf Version " + runningVersion + " wurde erfolgreich eingespielt.", mismatch));
            }
            case "ROLLBACK_AUTOMATIC" -> {
                d.put("art", "automatisch");
                audit.append("rollback", d);
                return Optional.of(new Notice(action, message + " Laufende Version: " + runningVersion + ".", true));
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

    /**
     * Nach dem Start: räumt alte Versionen auf und übernimmt Launcher und start.cmd der laufenden Version in den
     * Programmordner. Beides ist Zubehör, das ein Update-Paket (nur der Versionsordner) sonst nie erneuern könnte. Fehler
     * bleiben still; die Stücke werden beim nächsten Start erneut versucht.
     */
    public static void housekeeping(Path home) {
        Swap.cleanup(home);
        Path version = Swap.activeDir(home);
        syncFile(version.resolve("launcher").resolve("launcher.jar"), home.resolve("launcher").resolve("launcher.jar"));
        syncFile(version.resolve("start.cmd"), home.resolve("start.cmd"));
    }

    private static void syncFile(Path source, Path target) {
        try {
            if (!Files.isRegularFile(source) || (Files.isRegularFile(target) && Files.mismatch(source, target) < 0)) {
                return;
            }
            Files.createDirectories(target.toAbsolutePath().getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".neu");
            Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            // beim nächsten Start erneut
        }
    }

    private static String pending(Path home) {
        Path p = UpdateInstaller.updateDir(home).resolve("pending.json");
        try {
            return Files.exists(p) ? io.github.ebolansk.erechnung.util.Json.mapper().readTree(p.toFile()).path("version").asText(null) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
