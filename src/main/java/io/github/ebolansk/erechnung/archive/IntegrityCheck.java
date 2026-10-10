// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.archive;

import io.github.ebolansk.erechnung.audit.AuditLog;
import io.github.ebolansk.erechnung.util.Hashes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Vergleicht jede archivierte Datei mit ihrem Hash in der daten.json und diese wiederum mit dem Protokoll. So fällt auch auf,
 * wenn Datei und daten.json gemeinsam geändert wurden. Das Tool verhindert Änderungen nicht, es macht sie nachweisbar.
 */
public final class IntegrityCheck {
    public record Finding(boolean error, Path dir, String message) {
    }

    public record Report(int invoices, List<Finding> findings, AuditLog.Verification log) {
        public long errors() {
            return findings.stream().filter(Finding::error).count();
        }

        public boolean ok() {
            return errors() == 0 && log.valid();
        }

        public String summary() {
            long warnings = findings.size() - errors();
            return (ok() ? "In Ordnung" : "Abweichungen gefunden") + ": " + invoices + " Rechnung(en) geprüft, " + errors()
                + " Fehler, " + warnings + " Hinweis(e); Protokoll: " + log.message();
        }
    }

    private IntegrityCheck() {
    }

    public static Report run(Path root, AuditLog audit) throws IOException {
        List<Finding> findings = new ArrayList<>();
        Map<String, AuditLog.Entry> logged = new HashMap<>();
        for (AuditLog.Entry e : audit.entries()) {
            if (e.action().equals("archiviert")) {
                logged.put(e.details().get("mandant") + "|" + e.details().get("rechnung"), e);
            }
        }
        Set<String> seen = new HashSet<>();
        int count = 0;
        List<Path> dataFiles = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> s = Files.walk(root, 10)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    String name = p.getFileName().toString();
                    if (Files.isDirectory(p) && name.startsWith(".") && name.contains(".tmp-")) {
                        findings.add(new Finding(false, p, "Übrig gebliebener Schreibordner (Abbruch beim Archivieren?). "
                            + "Er gehört zu keiner Rechnung und kann nach Prüfung entfernt werden."));
                    } else if (name.equals("daten.json") && !hidden(root, p)) {
                        dataFiles.add(p);
                    }
                }
            }
        }
        for (Path dataJson : dataFiles) {
            count++;
            Path dir = dataJson.getParent();
            ArchiveRecord r;
            try {
                r = ArchiveRecord.parse(dataJson);
            } catch (IOException | RuntimeException e) {
                findings.add(new Finding(true, dir, "daten.json ist nicht lesbar: " + e.getMessage()));
                continue;
            }
            seen.add(r.mandantId() + "|" + r.number());
            checkFiles(dir, r, findings);
            checkAgainstLog(dataJson, r, logged.get(r.mandantId() + "|" + r.number()), findings);
        }
        for (Map.Entry<String, AuditLog.Entry> e : logged.entrySet()) {
            if (!seen.contains(e.getKey())) {
                findings.add(new Finding(true, Path.of(e.getValue().details().getOrDefault("pfad", "")),
                    "Im Protokoll als archiviert eingetragen (Rechnung " + e.getValue().details().get("rechnung")
                        + "), aber der Archivordner fehlt oder ist unlesbar."));
            }
        }
        AuditLog.Verification v = audit.verify();
        return new Report(count, findings, v);
    }

    private static boolean hidden(Path root, Path p) {
        for (Path part : root.relativize(p)) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static void checkFiles(Path dir, ArchiveRecord r, List<Finding> findings) {
        for (Map.Entry<String, String> e : r.sha256ByFile().entrySet()) {
            if (!ArchiveRecord.plainFileName(e.getKey())) {
                // Nie gegen den Archivordner auflösen: ein Name mit Pfadanteilen könnte aus dem Ordner hinauszeigen.
                findings.add(new Finding(true, dir, "Ungültiger Dateiname im Eintrag (daten.json manipuliert?): " + e.getKey()));
                continue;
            }
            Path f = dir.resolve(e.getKey());
            if (!Files.isRegularFile(f)) {
                findings.add(new Finding(true, dir, "Datei fehlt: " + e.getKey()));
                continue;
            }
            try {
                if (!Hashes.sha256Hex(f).equals(e.getValue())) {
                    findings.add(new Finding(true, dir, "Datei wurde verändert: " + e.getKey()));
                }
            } catch (IOException ex) {
                findings.add(new Finding(true, dir, "Datei nicht lesbar: " + e.getKey() + " (" + ex.getMessage() + ")"));
            }
        }
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                .filter(n -> !n.equals("daten.json") && !r.sha256ByFile().containsKey(n))
                .forEach(n -> findings.add(new Finding(false, dir, "Nicht dokumentierte Datei im Archivordner: " + n)));
        } catch (IOException ex) {
            findings.add(new Finding(true, dir, "Ordner nicht lesbar: " + ex.getMessage()));
        }
    }

    private static void checkAgainstLog(Path dataJson, ArchiveRecord r, AuditLog.Entry entry, List<Finding> findings) {
        Path dir = dataJson.getParent();
        if (entry == null) {
            findings.add(new Finding(false, dir, "Die Rechnung " + r.number() + " ist nicht im Protokoll eingetragen."));
            return;
        }
        Map<String, String> expected = new HashMap<>(r.sha256ByFile());
        try {
            expected.put("daten.json", Hashes.sha256Hex(dataJson));
        } catch (IOException e) {
            findings.add(new Finding(true, dir, "daten.json nicht lesbar: " + e.getMessage()));
            return;
        }
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String logged = entry.details().get("sha256:" + e.getKey());
            if (logged == null) {
                findings.add(new Finding(true, dir, "Im Protokoll fehlt der Hash von " + e.getKey() + "."));
            } else if (!logged.equals(e.getValue())) {
                findings.add(new Finding(true, dir, e.getKey() + " weicht vom Protokoll ab (auch daten.json kann geändert worden sein)."));
            }
        }
    }
}
