// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.launcher;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Schaltet vor dem Programmstart auf eine vorbereitete neue Version um (oder zurück). Läuft in einer eigenen JVM aus
 * launcher/launcher.jar. Bewusst ohne weitere Abhängigkeiten. Das Ergebnis steht in daten/update/ergebnis.properties
 * und wird beim Start protokolliert.
 *
 * <p>Jede Version liegt in einem eigenen Ordner und wird nie umbenannt oder überschrieben; nur ein Zeiger entscheidet,
 * welche startet. Das Umschalten braucht deshalb weder Löschen noch Umbenennen von Ordnern mit Dateien, die ein
 * Virenscanner, der Explorer oder ein auslaufender Prozess gerade festhalten könnten.
 *
 * <pre>
 * daten/update/neu/app/        vorbereitete neue Version (von der Update-Funktion angelegt)
 * daten/update/neu/version.txt deren Versionsnummer (bestimmt den Ordnernamen)
 * daten/update/probation.flag  neue Version wurde eingespielt und hat sich noch nicht als startfähig gemeldet
 * versionen/&lt;version&gt;/         eingespielte Versionen
 * versionen/aktuell.txt        Ordner der laufenden Version, relativ zum Programmordner (fehlt: app, Altbestand)
 * versionen/vorher.txt         Ordner der vorherigen Version (Ziel des Rollbacks)
 * </pre>
 */
public final class Swap {
    public enum Action { NONE, UPDATE, ROLLBACK_AUTOMATIC, FAILED }

    /** Fortschritt des Tauschs für die Anzeige (Prozent 0 bis 100 und ein kurzer Text). */
    @FunctionalInterface
    public interface Progress {
        Progress NONE = (percent, text) -> { };

        void step(int percent, String text);
    }

    static final String VERSIONS = "versionen";
    static final String LEGACY = "app";
    private static final Pattern POINTER = Pattern.compile("app|versionen/[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private Swap() {
    }

    public static void main(String[] args) {
        Path home = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        boolean rollback = Files.exists(home.resolve("daten").resolve("update").resolve("probation.flag"));
        SwapWindow window = SwapWindow.open(rollback ? "Vorherige Version wird wiederhergestellt …" : "Update wird eingespielt …");
        try {
            run(home, window == null ? Progress.NONE : window);
        } finally {
            if (window != null) {
                window.close();
                System.exit(0);
            }
        }
    }

    /** Der Ordner der Version, die startet (Zeiger aktuell.txt, ohne Zeiger der Altbestand app/). */
    public static Path activeDir(Path home) {
        return home.resolve(pointer(home, "aktuell").orElse(LEGACY));
    }

    public static Action run(Path home) {
        return run(home, Progress.NONE);
    }

    public static Action run(Path home, Progress progress) {
        Path upd = home.resolve("daten").resolve("update");
        Path neu = upd.resolve("neu").resolve("app");
        Path probation = upd.resolve("probation.flag");
        try {
            if (Files.exists(probation)) {
                return rollback(home, upd, Action.ROLLBACK_AUTOMATIC,
                    "Die zuletzt eingespielte Version hat sich nicht als startfähig gemeldet. Die vorherige Version wurde wiederhergestellt.",
                    progress);
            }
            if (Files.isRegularFile(neu.resolve("lib").resolve("e-rechnung.jar"))) {
                return update(home, upd, neu, probation, progress);
            }
        } catch (IOException | RuntimeException e) {
            return result(upd, Action.FAILED, "Die Version konnte nicht umgeschaltet werden: " + e.getMessage());
        }
        return Action.NONE;
    }

    private static Action update(Path home, Path upd, Path neu, Path probation, Progress progress) throws IOException {
        String current = pointer(home, "aktuell").orElse(LEGACY);
        // Ein vorbereitetes Paket muss seine Version nennen (jedes Paket der heutigen Update-Funktion tut das) und neuer sein als die
        // laufende. Alles andere ist ein Rest aus einer älteren Programmversion, etwa ein schon eingespieltes oder nicht mehr
        // passendes Paket, und würde die Installation beschädigen: es wird verworfen, nicht eingespielt.
        String staged = stagedVersion(upd.resolve("neu"));
        if (staged == null) {
            discardStaged(upd);
            return result(upd, Action.FAILED, "Ein vorbereitetes Update ohne gültige Versionsangabe wurde verworfen. Bitte erneut nach Updates suchen.");
        }
        String running = versionOf(current);
        if (running != null && compareVersions(staged, running) <= 0) {
            discardStaged(upd);
            return result(upd, Action.FAILED, "Das vorbereitete Update (Version " + staged + ") ist nicht neuer als die installierte ("
                + running + ") und wurde verworfen.");
        }
        progress.step(5, "Update wird eingespielt …");
        String target = VERSIONS + "/" + staged;
        if (Files.exists(home.resolve(target))) {
            // Rest einer früheren Version gleichen Namens, der sich nicht entfernen ließ: nicht darauf warten, anderer Name
            target = target + "-" + System.currentTimeMillis();
        }
        Files.createDirectories(home.resolve(VERSIONS));
        // Der Zielname ist neu, der Ordner frisch entpackt: nichts Altes wird angefasst. Ein kurzer Wiederholungsversuch
        // fängt nur noch einen Virenscanner ab, der die frisch geschriebenen Dateien gerade prüft.
        progress.step(25, "Neue Version wird bereitgestellt …");
        moveWithRetry(neu, home.resolve(target));
        progress.step(60, "Neue Version wird aktiviert …");
        try {
            writePointer(home, "vorher", current);
            Files.writeString(probation, Instant.now().toString(), StandardCharsets.UTF_8);
            writePointer(home, "aktuell", target);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(probation);
            return result(upd, Action.FAILED, "Die neue Version konnte nicht aktiviert werden, die bisherige bleibt aktiv: " + e.getMessage());
        }
        progress.step(80, "Alte Versionen werden aufgeräumt …");
        cleanup(home);
        deleteTree(upd.resolve("neu"));
        progress.step(100, "Fertig.");
        return result(upd, Action.UPDATE, "Die neue Version wurde eingespielt. Die vorherige bleibt für einen Rollback erhalten.");
    }

    private static Action rollback(Path home, Path upd, Action action, String message, Progress progress) throws IOException {
        progress.step(10, "Vorherige Version wird wiederhergestellt …");
        Files.deleteIfExists(upd.resolve("probation.flag"));
        Optional<String> previous = pointer(home, "vorher").filter(p -> Files.isRegularFile(home.resolve(p).resolve("lib").resolve("e-rechnung.jar")));
        if (previous.isEmpty()) {
            return result(upd, Action.FAILED, "Es gibt keine vorherige Version zum Wiederherstellen.");
        }
        try {
            writePointer(home, "aktuell", previous.get());
        } catch (IOException | RuntimeException e) {
            return result(upd, Action.FAILED, "Die vorherige Version konnte nicht wiederhergestellt werden: " + e.getMessage());
        }
        progress.step(60, "Aufräumen …");
        Files.deleteIfExists(home.resolve(VERSIONS).resolve("vorher.txt"));
        deleteTree(upd.resolve("neu"));
        Files.deleteIfExists(upd.resolve("pending.json"));
        cleanup(home);
        progress.step(100, "Fertig.");
        return result(upd, action, message);
    }

    /**
     * Räumt auf, was nicht mehr gebraucht wird: alle Versionen außer aktueller und vorheriger sowie Reste des alten
     * Tauschverfahrens (app.alt, app.defekt). Reine Aufräumarbeit: was sich nicht löschen lässt, bleibt liegen und wird beim
     * nächsten Mal erneut versucht; ein Fehler hier verhindert nie ein Update. Ohne gültigen Zeiger geschieht nichts.
     */
    public static void cleanup(Path home) {
        try {
            Optional<String> active = pointer(home, "aktuell");
            String current = active.orElse(LEGACY);
            if (!Files.isRegularFile(home.resolve(current).resolve("lib").resolve("e-rechnung.jar"))) {
                return;
            }
            List<String> keep = List.of(current, pointer(home, "vorher").orElse(""));
            Path versions = home.resolve(VERSIONS);
            if (Files.isDirectory(versions)) {
                try (Stream<Path> list = Files.list(versions)) {
                    for (Path p : (Iterable<Path>) list.filter(Files::isDirectory)::iterator) {
                        if (!keep.contains(VERSIONS + "/" + p.getFileName())) {
                            deleteTree(p);
                        }
                    }
                }
            }
            if (!keep.contains(LEGACY) && active.isPresent()) {
                deleteTree(home.resolve(LEGACY));
            }
            deleteTree(home.resolve("app.alt"));
            deleteTree(home.resolve("app.defekt"));
        } catch (IOException | RuntimeException ignored) {
            // wird beim nächsten Mal erneut versucht
        }
    }

    /** Die Versionsnummer des vorbereiteten Pakets (neu/version.txt) oder null, wenn sie fehlt oder ungültig ist. */
    private static String stagedVersion(Path neuRoot) {
        try {
            String name = Files.readString(neuRoot.resolve("version.txt"), StandardCharsets.UTF_8).trim();
            if (NAME.matcher(name).matches() && name.matches("\\d+(\\.\\d+)*")) {
                return name;
            }
        } catch (IOException | RuntimeException ignored) {
            // fehlt oder nicht lesbar
        }
        return null;
    }

    /** Die Versionsnummer zu einem Zeigerwert (versionen/0.1.4, auch mit Zusatz -zeitstempel); null beim Altbestand app. */
    private static String versionOf(String pointer) {
        if (!pointer.startsWith(VERSIONS + "/")) {
            return null;
        }
        var m = Pattern.compile("^\\d+(\\.\\d+)*").matcher(pointer.substring(VERSIONS.length() + 1));
        return m.find() ? m.group() : null;
    }

    /** Vergleicht zwei Versionsnummern aus Zahlen mit Punkten (0.1.10 ist neuer als 0.1.9); fehlende Teile zählen als 0. */
    static int compareVersions(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? Long.parseLong(x[i]) : 0;
            long q = i < y.length ? Long.parseLong(y[i]) : 0;
            if (p != q) {
                return Long.compare(p, q);
            }
        }
        return 0;
    }

    private static void discardStaged(Path upd) throws IOException {
        deleteTree(upd.resolve("neu"));
        Files.deleteIfExists(upd.resolve("pending.json"));
    }

    private static Optional<String> pointer(Path home, String name) {
        try {
            String v = Files.readString(home.resolve(VERSIONS).resolve(name + ".txt"), StandardCharsets.UTF_8).trim();
            return POINTER.matcher(v).matches() ? Optional.of(v) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Schreibt den Zeiger über eine Zwischendatei und einen atomaren Rename: nach einem Absturz gilt der alte oder der neue Wert. */
    private static void writePointer(Path home, String name, String value) throws IOException {
        Path dir = home.resolve(VERSIONS);
        Files.createDirectories(dir);
        Path target = dir.resolve(name + ".txt");
        Path tmp = dir.resolve(name + ".txt.tmp");
        Files.writeString(tmp, value + "\n", StandardCharsets.UTF_8);
        IOException last = null;
        for (int i = 0; i < 5; i++) {
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                pause(200);
            }
        }
        try {
            Files.writeString(target, value + "\n", StandardCharsets.UTF_8);
            Files.deleteIfExists(tmp);
        } catch (IOException e) {
            throw last;
        }
    }

    private static void moveWithRetry(Path from, Path to) throws IOException {
        IOException last = null;
        for (int i = 0; i < 6; i++) {
            try {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (IOException e) {
                last = e;
                pause(500);
            }
        }
        throw last;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
