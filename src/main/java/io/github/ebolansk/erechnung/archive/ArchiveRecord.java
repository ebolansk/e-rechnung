// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.archive;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ebolansk.erechnung.util.Json;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Eine archivierte Rechnung, gelesen aus ihrer daten.json. Das Archiv bleibt die Wahrheit, dieser Satz ist nur eine Sicht darauf. */
public record ArchiveRecord(Path dir, String mandantId, String mandantName, String number, LocalDate issueDate,
                            String buyerName, String buyerCity, BigDecimal net, BigDecimal gross, String format,
                            String belegStatus, boolean belegOverridden, String toolVersion, String rules,
                            LocalDate retainUntil, boolean validationPassed, String confirmedBy, String confirmedAt,
                            Map<String, String> sha256ByFile) {

    public static ArchiveRecord parse(Path dataJson) throws IOException {
        JsonNode n = Json.mapper().readTree(dataJson.toFile());
        String number = n.at("/invoice/number").asText("");
        if (number.isEmpty()) {
            throw new IOException("Rechnungsnummer fehlt in " + dataJson);
        }
        Map<String, String> hashes = new LinkedHashMap<>();
        n.path("files").fields().forEachRemaining(e -> hashes.put(e.getKey(), e.getValue().path("sha256").asText("")));
        return new ArchiveRecord(dataJson.getParent(), n.at("/mandant/id").asText(""), n.at("/mandant/name").asText(""), number,
            date(n.at("/invoice/issueDate").asText("")), n.at("/invoice/buyer/name").asText(""),
            n.at("/invoice/buyer/city").asText(""), amount(n.at("/totals/netTotal")), amount(n.at("/totals/grossTotal")),
            n.path("format").asText(""), n.path("belegStatus").asText(""), n.path("belegStatusOverridden").asBoolean(false),
            n.path("toolVersion").asText(""), n.path("rules").asText(""), date(n.path("retainUntil").asText("")),
            n.at("/validation/passed").asBoolean(false), n.path("confirmedBy").asText(""), n.path("confirmedAt").asText(""), hashes);
    }

    /** Nur diese beiden Namen schreibt der Archivierungsablauf; alles andere in daten.json wird nie als Ausgabe geöffnet. */
    private static final java.util.Set<String> OUTPUT_FILES = java.util.Set.of("ausgabe-xrechnung.xml", "ausgabe-zugferd.pdf");

    /** Name der erzeugten Datei (ausgabe-xrechnung.xml oder ausgabe-zugferd.pdf), sonst leer. */
    public String outputFile() {
        return sha256ByFile.keySet().stream().filter(OUTPUT_FILES::contains).findFirst().orElse("");
    }

    /** Ein einfacher Dateiname ohne Pfadanteile: Einträge aus daten.json werden nur dann gegen den Archivordner aufgelöst. */
    public static boolean plainFileName(String name) {
        return name != null && name.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }

    public Path originalPdf() {
        return dir.resolve("vorlage-original.pdf");
    }

    public Path output() {
        return dir.resolve(outputFile());
    }

    public Path protocol() {
        return dir.resolve("pruefprotokoll.html");
    }

    public ArchiveIndex.Entry toEntry() {
        return new ArchiveIndex.Entry(dir, mandantId, number, sha256ByFile.getOrDefault("vorlage-original.pdf", ""));
    }

    private static LocalDate date(String s) {
        try {
            return s.isEmpty() ? null : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal amount(JsonNode n) {
        return n.isMissingNode() || n.isNull() ? null : new BigDecimal(n.asText());
    }
}
