// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.SampleInvoices;
import io.github.ebolansk.erechnung.archive.ArchiveIndex;
import io.github.ebolansk.erechnung.audit.AuditLog;
import io.github.ebolansk.erechnung.config.AppConfig;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import io.github.ebolansk.erechnung.model.OutputFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Erzeugen, ansehen, dann erst ins Archiv übernehmen: der Entwurf ist kein Archiveintrag. */
class DraftWorkflowTest {
    private ProcessingService service;
    private AuditLog audit;
    private ProcessingService.Prepared prepared;
    private Mandant mandant;
    private Path home;

    private void setUp(Path dir, OutputFormat format) throws Exception {
        home = dir;
        Files.createDirectories(home.resolve("daten"));
        Path pdf = home.resolve("rechnung.pdf");
        Files.write(pdf, SampleInvoices.classic());
        Clock clock = Clock.systemUTC();
        var config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        config.save(new AppConfig(home.resolve("archiv").toString(), "{Mandant}/{Jahr}/{Monat}/{Rechnungsnummer}", format, LocalDate.of(2027, 1, 1)));
        audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "test");
        service = new ProcessingService(home, config, new MandantStore(home.resolve("daten/mandanten.json")), audit, clock);
        mandant = service.registerMandant(new Mandant(null, "Nordlicht Werbetechnik GmbH", "Lindenweg 12", "70173", "Stuttgart", "DE",
            "DE811234567", "", "rechnung@nordlicht-werbung.example", "Erika Beispiel", "+49 711 5550123", "DE89370400440532013000",
            "COBADEFFXXX", ""));
        prepared = service.prepare(pdf);
    }

    private int archiveCount() throws Exception {
        return ArchiveIndex.scan(home.resolve("archiv")).size();
    }

    @Test
    void generateOnlyCreatesAViewableDraftAndNothingInTheArchive(@TempDir Path dir) throws Exception {
        setUp(dir, OutputFormat.XRECHNUNG);
        var g = service.generate(prepared, mandant, prepared.extraction().draft(), "test",
            new io.github.ebolansk.erechnung.archive.Confirmation(null, null, null, false, null));
        assertThat(g.problems()).isEmpty();
        var draft = g.draft();
        assertThat(draft).isNotNull();
        assertThat(draft.outputFile()).exists().hasFileName("ausgabe-xrechnung.xml");
        assertThat(draft.protocolFile()).exists();
        assertThat(draft.report().passed()).isTrue();
        assertThat(archiveCount()).as("noch nichts im Archiv").isZero();
        assertThat(audit.entries()).extracting(e -> e.action()).doesNotContain("archiviert");
    }

    @Test
    void adoptingArchivesTheDraftAndRemovesItsFiles(@TempDir Path dir) throws Exception {
        setUp(dir, OutputFormat.ZUGFERD);
        var draft = service.generate(prepared, mandant, prepared.extraction().draft(), "test",
            new io.github.ebolansk.erechnung.archive.Confirmation(null, null, null, false, null)).draft();
        assertThat(draft.outputFile()).hasFileName("ausgabe-zugferd.pdf");
        var out = service.archive(draft);
        assertThat(out.archived()).isTrue();
        assertThat(archiveCount()).isEqualTo(1);
        assertThat(out.directory().resolve("ausgabe-zugferd.pdf")).exists();
        assertThat(draft.stagingDir()).doesNotExist();
        assertThat(audit.entries()).extracting(e -> e.action()).contains("validiert", "archiviert");
        assertThat(audit.verify().valid()).isTrue();
    }

    @Test
    void discardingRemovesTheDraftAndIsLoggedWithoutArchiving(@TempDir Path dir) throws Exception {
        setUp(dir, OutputFormat.XRECHNUNG);
        var draft = service.generate(prepared, mandant, prepared.extraction().draft(), "test",
            new io.github.ebolansk.erechnung.archive.Confirmation(null, null, null, false, null)).draft();
        service.discard(draft);
        assertThat(draft.stagingDir()).doesNotExist();
        assertThat(archiveCount()).isZero();
        assertThat(audit.entries()).extracting(e -> e.action()).contains("entwurf-verworfen").doesNotContain("archiviert");
    }

    @Test
    void secondDraftOfTheSameInvoiceIsRefusedAtAdoptionAndStaysAnDraft(@TempDir Path dir) throws Exception {
        setUp(dir, OutputFormat.XRECHNUNG);
        var confirmation = new io.github.ebolansk.erechnung.archive.Confirmation(null, null, null, false, null);
        var first = service.generate(prepared, mandant, prepared.extraction().draft(), "test", confirmation).draft();
        var second = service.generate(prepared, mandant, prepared.extraction().draft(), "test", confirmation).draft();
        assertThat(service.archive(first).archived()).isTrue();
        var refused = service.archive(second);
        assertThat(refused.archived()).isFalse();
        assertThat(String.join(" ", refused.problems())).contains("bereits archiviert");
        assertThat(second.stagingDir()).as("der Entwurf bleibt zum Verwerfen bestehen").exists();
        assertThat(archiveCount()).isEqualTo(1);
    }

    @Test
    void cleanupRemovesLeftoverDraftsOfEarlierSessions(@TempDir Path dir) throws Exception {
        setUp(dir, OutputFormat.XRECHNUNG);
        var draft = service.generate(prepared, mandant, prepared.extraction().draft(), "test",
            new io.github.ebolansk.erechnung.archive.Confirmation(null, null, null, false, null)).draft();
        service.cleanupDrafts();
        assertThat(draft.stagingDir()).doesNotExist();
        assertThat(home.resolve("daten/entwurf")).doesNotExist();
    }

    @Test
    void updatedSellerDataIsSavedInTheMandantAndLogged(@TempDir Path dir) throws Exception {
        setUp(dir, OutputFormat.XRECHNUNG);
        var changed = new Mandant(mandant.id(), mandant.name(), mandant.street(), mandant.zip(), mandant.city(), mandant.country(),
            mandant.vatId(), mandant.taxNumber(), mandant.email(), "Neue Person", "+49 711 1", mandant.iban(), mandant.bic(), "");
        service.updateMandant(changed);
        var stored = new MandantStore(home.resolve("daten/mandanten.json")).all();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).contactName()).isEqualTo("Neue Person");
        assertThat(audit.entries()).extracting(e -> e.action()).contains("mandant-geaendert");
    }
}
