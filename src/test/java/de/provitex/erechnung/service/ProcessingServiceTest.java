// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.provitex.erechnung.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import de.provitex.erechnung.archive.BelegStatus;
import de.provitex.erechnung.archive.Confirmation;
import de.provitex.erechnung.audit.AuditLog;
import de.provitex.erechnung.audit.AuditTrail;
import de.provitex.erechnung.util.Json;
import java.io.IOException;
import de.provitex.erechnung.config.AppConfig;
import de.provitex.erechnung.config.ConfigStore;
import de.provitex.erechnung.extract.RuleBasedExtractorTest;
import de.provitex.erechnung.mandant.Mandant;
import de.provitex.erechnung.mandant.MandantStore;
import de.provitex.erechnung.model.InvoiceData;
import de.provitex.erechnung.model.OutputFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessingServiceTest {
    @TempDir
    Path home;
    private ProcessingService service;
    private ConfigStore config;
    private Mandant mandant;
    private AuditLog audit;

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC);
        config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        var mandanten = new MandantStore(home.resolve("daten/mandanten.json"));
        audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "tester");
        service = new ProcessingService(home, config, mandanten, audit, clock);
        mandant = service.registerMandant(RuleBasedExtractorTest.mandant());
    }

    private Path pdf(String text) throws Exception {
        Path p = home.resolve("eingang").resolve("rechnung.pdf");
        Files.createDirectories(p.getParent());
        Files.write(p, TestData.embeddedFontPdf(text));
        return p;
    }

    private void setFormat(OutputFormat f) throws Exception {
        config.save(new AppConfig(home.resolve("archiv").toString(), "{Mandant}/{Jahr}/{Monat}/{Rechnungsnummer}", f, LocalDate.of(2027, 1, 1)));
    }

    private static InvoiceData withNumber(InvoiceData d, String number) {
        return new InvoiceData(d.type(), number, d.issueDate(), d.deliveryDate(), null, d.dueDate(), d.currency(),
            d.buyerReference(), d.paymentTerms(), d.seller(), d.buyer(), d.iban(), d.bic(), d.items());
    }

    @Test
    void prepareRecognizesMandantAndExtractsFromText() throws Exception {
        var p = service.prepare(pdf("Rechnungsnummer: RE-2027-0001 USt-IdNr.: DE123456789"));
        assertThat(p.mandant()).isPresent();
        assertThat(p.mandant().get().id()).isEqualTo(mandant.id());
        assertThat(p.extraction().draft().number()).isEqualTo("RE-2027-0001");
        assertThat(p.sha256()).hasSize(64);
        assertThat(p.duplicateByHash()).isEmpty();
    }

    @Test
    void xrechnungIsArchivedAndAuditedAndChainIsValid() throws Exception {
        setFormat(OutputFormat.XRECHNUNG);
        var p = service.prepare(pdf("Rechnung RE-2027-0001"));
        var out = service.generateAndArchive(p, mandant, TestData.invoice(), "tester");
        assertThat(out.problems()).isEmpty();
        assertThat(out.archived()).isTrue();
        Path dir = out.directory();
        assertThat(dir.resolve("ausgabe-xrechnung.xml")).exists();
        assertThat(dir.resolve("vorlage-original.pdf")).exists();
        assertThat(dir.resolve("pruefprotokoll.html")).exists();
        assertThat(dir.resolve("daten.json")).exists();
        assertThat(audit.verify().valid()).isTrue();
        assertThat(audit.verify().entries()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void zugferdIsArchived() throws Exception {
        setFormat(OutputFormat.ZUGFERD);
        var p = service.prepare(pdf("Rechnung RE-2027-0001"));
        var out = service.generateAndArchive(p, mandant, TestData.invoice(), "tester");
        assertThat(out.problems()).isEmpty();
        assertThat(out.archived()).isTrue();
        assertThat(out.directory().resolve("ausgabe-zugferd.pdf")).exists();
    }

    @Test
    void secondRunWithSameNumberIsBlockedAndNothingIsOverwritten() throws Exception {
        setFormat(OutputFormat.XRECHNUNG);
        var first = service.generateAndArchive(service.prepare(pdf("A")), mandant, TestData.invoice(), "tester");
        assertThat(first.archived()).isTrue();
        byte[] before = Files.readAllBytes(first.directory().resolve("ausgabe-xrechnung.xml"));
        Path again = home.resolve("eingang/zweite.pdf");
        Files.write(again, TestData.embeddedFontPdf("ganz anderer Inhalt"));
        var second = service.generateAndArchive(service.prepare(again), mandant, TestData.invoice(), "tester");
        assertThat(second.archived()).isFalse();
        assertThat(second.problems()).anyMatch(m -> m.contains("RE-2027-0001") && m.contains("bereits"));
        assertThat(Files.readAllBytes(first.directory().resolve("ausgabe-xrechnung.xml"))).isEqualTo(before);
    }

    @Test
    void samePdfTwiceIsDetectedByHash() throws Exception {
        setFormat(OutputFormat.XRECHNUNG);
        Path file = pdf("Rechnung identisch");
        assertThat(service.generateAndArchive(service.prepare(file), mandant, TestData.invoice(), "tester").archived()).isTrue();
        assertThat(service.prepare(file).duplicateByHash()).isPresent();
    }

    @Test
    void zugferdWithNotEmbeddedFontsIsRejectedWithMessageAndNothingArchived() throws Exception {
        setFormat(OutputFormat.ZUGFERD);
        Path file = home.resolve("eingang/helvetica.pdf");
        Files.createDirectories(file.getParent());
        Files.write(file, TestData.base14Pdf("Rechnung"));
        var out = service.generateAndArchive(service.prepare(file), mandant, TestData.invoice(), "tester");
        assertThat(out.archived()).isFalse();
        assertThat(out.problems()).anyMatch(m -> m.contains("Helvetica") && m.contains("eingebettet"));
        Path archiv = home.resolve("archiv");
        if (Files.exists(archiv)) {
            try (Stream<Path> s = Files.walk(archiv)) {
                assertThat(s.filter(x -> x.getFileName().toString().equals("daten.json"))).isEmpty();
            }
        }
    }

    @Test
    void incompleteDataIsNotGeneratedOrArchived() throws Exception {
        setFormat(OutputFormat.XRECHNUNG);
        var out = service.generateAndArchive(service.prepare(pdf("x")), mandant, withNumber(TestData.invoice(), ""), "tester");
        assertThat(out.archived()).isFalse();
        assertThat(out.problems()).anyMatch(m -> m.contains("Rechnungsnummer"));
    }

    @Test
    void pathHostileInvoiceNumberStaysInsideArchive() throws Exception {
        setFormat(OutputFormat.XRECHNUNG);
        var out = service.generateAndArchive(service.prepare(pdf("x")), mandant, withNumber(TestData.invoice(), "../../evil"), "tester");
        assertThat(out.archived()).isTrue();
        assertThat(out.directory().toAbsolutePath().normalize().startsWith(home.resolve("archiv").toAbsolutePath().normalize())).isTrue();
    }

    @Test
    void totalsMismatchBlocksUntilConfirmedAndIsDocumented() throws Exception {
        setFormat(OutputFormat.XRECHNUNG);
        var prep = service.prepare(pdf("Gesamtbetrag 358,00 €"));
        var blocked = service.generateAndArchive(prep, mandant, TestData.invoice(), "tester");
        assertThat(blocked.archived()).isFalse();
        assertThat(blocked.problems()).anyMatch(m -> m.contains("weichen ab") && m.contains("358,00"));
        var conf = new Confirmation(null, null, new java.math.BigDecimal("358.00"), true, null);
        var out = service.generateAndArchive(prep, mandant, TestData.invoice(), "tester", conf);
        assertThat(out.archived()).isTrue();
        JsonNode data = Json.mapper().readTree(out.directory().resolve("daten.json").toFile());
        assertThat(data.get("totalsMismatchConfirmed").asBoolean()).isTrue();
        assertThat(data.at("/printedTotals/gross").asText()).isEqualTo("358.00");
    }

    @Test
    void belegStatusOverrideIsRecorded() throws Exception {
        setFormat(OutputFormat.XRECHNUNG);
        var conf = new Confirmation(null, null, null, false, BelegStatus.PDF_IST_ORIGINAL);
        var out = service.generateAndArchive(service.prepare(pdf("x")), mandant, TestData.invoice(), "tester", conf);
        assertThat(out.archived()).isTrue();
        JsonNode data = Json.mapper().readTree(out.directory().resolve("daten.json").toFile());
        assertThat(data.get("belegStatus").asText()).isEqualTo("PDF_IST_ORIGINAL");
        assertThat(data.get("belegStatusAuto").asText()).isEqualTo("E_RECHNUNG_IST_ORIGINAL");
        assertThat(data.get("belegStatusOverridden").asBoolean()).isTrue();
    }

    private ProcessingService serviceWithAuditFailingOn(String action) throws Exception {
        AuditTrail failing = (a, d) -> {
            if (a.equals(action)) {
                throw new IOException("Platte voll");
            }
            return audit.append(a, d);
        };
        return new ProcessingService(home, config, new MandantStore(home.resolve("daten/mandanten.json")), failing,
            Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void auditFailureAfterArchiveWritingIsReportedAsArchivedWithWarning() throws Exception {
        setFormat(OutputFormat.XRECHNUNG);
        var svc = serviceWithAuditFailingOn("archiviert");
        var out = svc.generateAndArchive(svc.prepare(pdf("x")), mandant, TestData.invoice(), "tester");
        assertThat(out.archived()).isTrue();
        assertThat(out.directory().resolve("daten.json")).exists();
        assertThat(out.problems()).anyMatch(m -> m.contains("Protokoll") && m.contains("Platte voll"));
    }

    @Test
    void auditFailureBeforeArchiveWritingArchivesNothing() throws Exception {
        setFormat(OutputFormat.XRECHNUNG);
        var svc = serviceWithAuditFailingOn("validiert");
        var prep = svc.prepare(pdf("x"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> svc.generateAndArchive(prep, mandant, TestData.invoice(), "tester"))
            .isInstanceOf(IOException.class).hasMessageContaining("Platte voll");
        Path archiv = home.resolve("archiv");
        if (Files.exists(archiv)) {
            try (Stream<Path> s = Files.walk(archiv)) {
                assertThat(s.filter(x -> x.getFileName().toString().equals("daten.json"))).isEmpty();
            }
        }
    }
}
