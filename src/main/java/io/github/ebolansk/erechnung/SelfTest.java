// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung;

import io.github.ebolansk.erechnung.audit.AuditLog;
import io.github.ebolansk.erechnung.config.AppConfig;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import io.github.ebolansk.erechnung.model.OutputFormat;
import io.github.ebolansk.erechnung.service.ProcessingService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Selbsttest ohne Oberfläche: `Main --selftest`. Schickt eine mitgelieferte, erfundene Beispielrechnung in beiden
 * Ausgabeformaten durch den ganzen Ablauf (Einlesen, Prüfen, Erzeugen, Validieren, Archivieren). Er zeigt, ob das
 * ausgelieferte Programmpaket samt Java-Laufzeit auf dem Zielsystem funktioniert. Exit-Code 0 = in Ordnung.
 */
public final class SelfTest {
    private SelfTest() {
    }

    public static int run() {
        Path work = null;
        try {
            work = Files.createTempDirectory("erechnung-selftest");
            System.setProperty("pdfbox.fontcache", Files.createDirectories(work.resolve("cache")).toString());
            for (OutputFormat format : OutputFormat.values()) {
                check(work, format);
            }
            System.out.println("SELBSTTEST OK");
            return 0;
        } catch (Exception | LinkageError e) {
            System.out.println("SELBSTTEST FEHLGESCHLAGEN: " + e);
            e.printStackTrace(System.out);
            return 1;
        } finally {
            if (work != null) {
                try {
                    io.github.ebolansk.erechnung.launcher.Swap.deleteTree(work);
                } catch (IOException ignored) {
                    // Temp-Ordner bleibt liegen
                }
            }
        }
    }

    private static void check(Path work, OutputFormat format) throws IOException {
        Path home = work.resolve(format.name());
        Files.createDirectories(home.resolve("daten"));
        Path pdf = home.resolve("beispiel.pdf");
        try (InputStream in = SelfTest.class.getResourceAsStream("/selftest/beispiel.pdf")) {
            if (in == null) {
                throw new IOException("Beispielrechnung fehlt im Programmpaket.");
            }
            Files.write(pdf, in.readAllBytes());
        }
        Clock clock = Clock.fixed(Instant.parse("2027-02-10T10:00:00Z"), ZoneOffset.UTC);
        ConfigStore config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        config.save(new AppConfig(home.resolve("archiv").toString(), "{Mandant}/{Jahr}/{Monat}/{Rechnungsnummer}", format,
            LocalDate.of(2027, 1, 1)));
        var service = new ProcessingService(home, config, new MandantStore(home.resolve("daten/mandanten.json")),
            new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "selbsttest"), clock);
        Mandant mandant = service.registerMandant(new Mandant(null, "Nordlicht Werbetechnik GmbH", "Lindenweg 12", "70173", "Stuttgart",
            "DE", "DE811234567", "", "rechnung@aussteller.example", "Erika Beispiel", "+49 7121 000000", "DE89370400440532013000",
            "COBADEFFXXX", ""));
        var prepared = service.prepare(pdf);
        var outcome = service.generateAndArchive(prepared, mandant, prepared.extraction().draft(), "selbsttest");
        if (!outcome.problems().isEmpty()) {
            throw new IOException(format + ": offene Punkte " + outcome.problems());
        }
        if (!outcome.archived() || outcome.report() == null || !outcome.report().passed()) {
            throw new IOException(format + ": nicht archiviert oder Prüfung nicht bestanden.");
        }
        System.out.println(format + ": archiviert und formal geprüft (" + outcome.directory().getFileName() + ")");
    }
}
