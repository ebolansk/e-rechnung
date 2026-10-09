// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.archive;

import de.provitex.erechnung.util.FileNames;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArchiveLayout {
    public static final String DEFAULT_TEMPLATE = "{Mandant}/{Jahr}/{Monat}/{Rechnungsnummer}";
    private static final Set<String> PLACEHOLDERS = Set.of("Mandant", "Jahr", "Monat", "Rechnungsnummer");
    private static final Pattern TOKEN = Pattern.compile("\\{([^}]*)}");
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");

    private ArchiveLayout() {
    }

    public static void validateTemplate(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("Die Ordnervorlage darf nicht leer sein.");
        }
        String t = template.replace('\\', '/');
        if (t.startsWith("/") || DRIVE.matcher(t).matches()) {
            throw new IllegalArgumentException("Die Ordnervorlage muss relativ zum Archivordner sein.");
        }
        Matcher m = TOKEN.matcher(t);
        while (m.find()) {
            if (!PLACEHOLDERS.contains(m.group(1))) {
                throw new IllegalArgumentException("Unbekannter Platzhalter {" + m.group(1)
                    + "}. Erlaubt: {Mandant}, {Jahr}, {Monat}, {Rechnungsnummer}");
            }
        }
        if (!t.contains("{Rechnungsnummer}")) {
            throw new IllegalArgumentException("Die Vorlage muss {Rechnungsnummer} enthalten, damit jede Rechnung einen eigenen Ordner bekommt.");
        }
        for (String seg : t.split("/")) {
            if (seg.equals("..") || seg.equals(".")) {
                throw new IllegalArgumentException("Die Ordnervorlage darf kein '.' oder '..' enthalten.");
            }
        }
    }

    public static Path resolve(Path root, String template, String mandantName, LocalDate issueDate, String number) {
        validateTemplate(template);
        Path base = root.toAbsolutePath().normalize();
        Path result = base;
        for (String segment : template.replace('\\', '/').split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            String filled = segment
                .replace("{Mandant}", FileNames.sanitize(mandantName))
                .replace("{Jahr}", String.format("%04d", issueDate.getYear()))
                .replace("{Monat}", String.format("%02d", issueDate.getMonthValue()))
                .replace("{Rechnungsnummer}", FileNames.sanitize(number));
            result = result.resolve(FileNames.sanitize(filled));
        }
        result = result.normalize();
        if (!result.startsWith(base) || result.equals(base)) {
            throw new IllegalArgumentException("Der Archivpfad liegt außerhalb des Archivordners.");
        }
        return result;
    }
}
