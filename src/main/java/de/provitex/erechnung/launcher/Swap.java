// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.launcher;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Tauscht vor dem Programmstart den Ordner app/ gegen eine vorbereitete neue Version (oder zurück). Läuft in einer eigenen
 * JVM aus launcher/launcher.jar, weil ein laufendes Java-Programm seine eigenen Dateien unter Windows nicht ersetzen kann.
 * Bewusst ohne weitere Abhängigkeiten. Das Ergebnis steht in daten/update/ergebnis.properties und wird beim Start protokolliert.
 *
 * <pre>
 * daten/update/neu/app/        vorbereitete neue Version (von der Update-Funktion angelegt)
 * daten/update/rollback.flag   Nutzer hat „Vorherige Version wiederherstellen“ gewählt
 * daten/update/probation.flag  neue Version wurde eingespielt und hat sich noch nicht als startfähig gemeldet
 * app.alt/                     vorherige Version (genau eine bleibt liegen)
 * </pre>
 */
public final class Swap {
    public enum Action { NONE, UPDATE, ROLLBACK_MANUAL, ROLLBACK_AUTOMATIC, FAILED }

    private Swap() {
    }

    public static void main(String[] args) {
        Path home = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        run(home);
    }

    public static Action run(Path home) {
        Path upd = home.resolve("daten").resolve("update");
        Path app = home.resolve("app");
        Path alt = home.resolve("app.alt");
        Path neu = upd.resolve("neu").resolve("app");
        Path probation = upd.resolve("probation.flag");
        Path rollback = upd.resolve("rollback.flag");
        try {
            if (Files.exists(probation)) {
                return rollback(home, upd, app, alt, Action.ROLLBACK_AUTOMATIC,
                    "Die zuletzt eingespielte Version hat sich nicht als startfähig gemeldet. Die vorherige Version wurde wiederhergestellt.");
            }
            if (Files.exists(rollback)) {
                Files.deleteIfExists(rollback);
                return rollback(home, upd, app, alt, Action.ROLLBACK_MANUAL, "Die vorherige Version wurde auf Wunsch wiederhergestellt.");
            }
            if (Files.isRegularFile(neu.resolve("lib").resolve("e-rechnung.jar"))) {
                return update(home, upd, app, alt, neu, probation);
            }
        } catch (IOException | RuntimeException e) {
            return result(upd, Action.FAILED, "Der Programmordner konnte nicht getauscht werden: " + e.getMessage());
        }
        return Action.NONE;
    }

    private static Action update(Path home, Path upd, Path app, Path alt, Path neu, Path probation) throws IOException {
        deleteTree(alt);
        if (Files.exists(alt)) {
            return result(upd, Action.FAILED, "Die alte Sicherung (app.alt) konnte nicht entfernt werden. Das Update wurde nicht eingespielt.");
        }
        Files.move(app, alt, StandardCopyOption.ATOMIC_MOVE);
        try {
            Files.move(neu, app, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            Files.move(alt, app, StandardCopyOption.ATOMIC_MOVE);
            return result(upd, Action.FAILED, "Die neue Version konnte nicht eingesetzt werden, die bisherige bleibt aktiv: " + e.getMessage());
        }
        Files.writeString(probation, Instant.now().toString(), StandardCharsets.UTF_8);
        deleteTree(upd.resolve("neu"));
        return result(upd, Action.UPDATE, "Die neue Version wurde eingespielt. Die vorherige liegt als app.alt bereit.");
    }

    private static Action rollback(Path home, Path upd, Path app, Path alt, Action action, String message) throws IOException {
        Files.deleteIfExists(upd.resolve("probation.flag"));
        if (!Files.isDirectory(alt)) {
            return result(upd, Action.FAILED, "Es gibt keine vorherige Version (app.alt) zum Wiederherstellen.");
        }
        Path defekt = home.resolve("app.defekt");
        deleteTree(defekt);
        Files.move(app, defekt, StandardCopyOption.ATOMIC_MOVE);
        try {
            Files.move(alt, app, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            Files.move(defekt, app, StandardCopyOption.ATOMIC_MOVE);
            return result(upd, Action.FAILED, "Die vorherige Version konnte nicht wiederhergestellt werden: " + e.getMessage());
        }
        deleteTree(defekt);
        deleteTree(upd.resolve("neu"));
        Files.deleteIfExists(upd.resolve("pending.json"));
        return result(upd, action, message);
    }

    private static Action result(Path upd, Action action, String message) {
        try {
            Files.createDirectories(upd);
            Properties p = new Properties();
            p.setProperty("aktion", action.name());
            p.setProperty("meldung", message);
            p.setProperty("zeit", Instant.now().toString());
            try (Writer w = Files.newBufferedWriter(upd.resolve("ergebnis.properties"), StandardCharsets.UTF_8)) {
                p.store(w, null);
            }
        } catch (IOException ignored) {
            // Ohne Ergebnisdatei startet das Programm trotzdem; es protokolliert dann keinen Tausch.
        }
        return action;
    }

    /** Löscht rekursiv, so weit möglich (gesperrte Dateien bleiben liegen, der Aufrufer prüft Files.exists). */
    public static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    // gesperrte Datei: Verzeichnis bleibt bestehen, der Aufrufer prüft Files.exists
                }
            }
        }
    }
}
