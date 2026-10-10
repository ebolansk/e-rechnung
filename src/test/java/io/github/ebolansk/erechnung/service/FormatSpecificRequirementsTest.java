// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.SampleInvoices;
import io.github.ebolansk.erechnung.audit.AuditLog;
import io.github.ebolansk.erechnung.config.AppConfig;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.OutputFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ansprechpartner, Telefon und Käuferreferenz: Pflicht nur bei XRechnung, bei ZUGFeRD wird auch ohne sie archiviert. */
class FormatSpecificRequirementsTest {
    private record Run(ProcessingService.Outcome outcome) {
    }

    private Run run(Path home, OutputFormat format) throws Exception {
        Files.createDirectories(home.resolve("daten"));
        Path pdf = home.resolve("rechnung.pdf");
        Files.write(pdf, SampleInvoices.classic());
        Clock clock = Clock.systemUTC();
        var config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        config.save(new AppConfig(home.resolve("archiv").toString(), "{Mandant}/{Jahr}/{Monat}/{Rechnungsnummer}", format, LocalDate.of(2027, 1, 1)));
        var service = new ProcessingService(home, config, new MandantStore(home.resolve("daten/mandanten.json")),
            new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "test"), clock);
        Mandant m = service.registerMandant(new Mandant(null, "Nordlicht Werbetechnik GmbH", "Lindenweg 12", "70173", "Stuttgart", "DE",
            "DE811234567", "", "rechnung@nordlicht-werbung.example", "", "", "DE89370400440532013000", "COBADEFFXXX", ""));
        var prepared = service.prepare(pdf);
        InvoiceData d = prepared.extraction().draft();
        InvoiceData noRef = new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), d.deliveryPeriodEnd(), d.dueDate(),
            d.currency(), "", d.paymentTerms(), m.toParty(), d.buyer(), d.iban(), d.bic(), d.items());
        return new Run(service.generateAndArchive(prepared, m, noRef, "test"));
    }

    @Test
    void zugferdIsArchivedWithoutContactAndBuyerReference(@TempDir Path home) throws Exception {
        var out = run(home, OutputFormat.ZUGFERD).outcome();
        assertThat(out.problems()).isEmpty();
        assertThat(out.archived()).isTrue();
        assertThat(out.report().passed()).isTrue();
    }

    @Test
    void xrechnungNamesTheMissingFields(@TempDir Path home) throws Exception {
        var out = run(home, OutputFormat.XRECHNUNG).outcome();
        assertThat(out.archived()).isFalse();
        assertThat(String.join("\n", out.problems())).contains("BT-41").contains("BT-42").contains("BT-10");
    }
}
