// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ebolansk.erechnung.TestData;
import io.github.ebolansk.erechnung.archive.ArchiveIndex;
import io.github.ebolansk.erechnung.archive.ArchiveRecord;
import io.github.ebolansk.erechnung.archive.ArchiveSearch;
import io.github.ebolansk.erechnung.archive.ArchiveSearch.Query;
import io.github.ebolansk.erechnung.archive.IntegrityCheck;
import io.github.ebolansk.erechnung.audit.AuditLog;
import io.github.ebolansk.erechnung.config.AppConfig;
import io.github.ebolansk.erechnung.config.ConfigStore;
import io.github.ebolansk.erechnung.extract.RuleBasedExtractorTest;
import io.github.ebolansk.erechnung.mandant.Mandant;
import io.github.ebolansk.erechnung.mandant.MandantStore;
import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.LineItem;
import io.github.ebolansk.erechnung.model.OutputFormat;
import io.github.ebolansk.erechnung.model.Party;
import io.github.ebolansk.erechnung.util.Json;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveSearchAndIntegrityTest {
    @TempDir
    Path home;
    private Path root;
    private AuditLog audit;
    private ArchiveRecord first;
    private ArchiveRecord second;
    private ArchiveRecord third;

    @BeforeEach
    void archiveThreeInvoices() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2027-04-01T10:00:00Z"), ZoneOffset.UTC);
        ConfigStore config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        root = home.resolve("archiv");
        config.save(new AppConfig(root.toString(), "{Mandant}/{Jahr}/{Monat}/{Rechnungsnummer}", OutputFormat.XRECHNUNG, LocalDate.of(2027, 1, 1)));
        audit = new AuditLog(home.resolve("daten/protokoll.jsonl"), clock, "tester");
        var service = new ProcessingService(home, config, new MandantStore(home.resolve("daten/mandanten.json")), audit, clock);
        Mandant m = service.registerMandant(RuleBasedExtractorTest.mandant());
        archive(service, m, "RE-2027-0001", LocalDate.of(2027, 1, 15), "Kunde AG", "100.00", "3");
        archive(service, m, "RE-2027-0002", LocalDate.of(2027, 3, 10), "Müller Bau GmbH", "500.00", "2");
        archive(service, m, "RE-2026-0099", LocalDate.of(2026, 12, 1), "Kunde AG", "40.00", "1");
        List<ArchiveRecord> all = ArchiveIndex.scan(root).records();
        first = byNumber(all, "RE-2027-0001");
        second = byNumber(all, "RE-2027-0002");
        third = byNumber(all, "RE-2026-0099");
    }

    private void archive(ProcessingService service, Mandant m, String number, LocalDate issue, String buyer, String price, String qty)
        throws Exception {
        InvoiceData base = TestData.invoice();
        Party b = new Party(buyer, "Nebenstr. 2", "10115", "Berlin", "DE", "", "", "einkauf@kunde.example", "", "");
        InvoiceData d = new InvoiceData(base.type(), number, issue, issue, null, issue.plusDays(30), "EUR", base.buyerReference(),
            base.paymentTerms(), m.toParty(), b, base.iban(), base.bic(),
            List.of(new LineItem("Beratung", "Stundensatz", "HUR", new BigDecimal(qty), new BigDecimal(price), new BigDecimal("19"), "S", "")));
        Path pdf = home.resolve("eingang").resolve(number + ".pdf");
        Files.createDirectories(pdf.getParent());
        Files.write(pdf, TestData.embeddedFontPdf("Rechnung " + number));
        var out = service.generateAndArchive(service.prepare(pdf), m, d, "tester");
        assertThat(out.problems()).isEmpty();
        assertThat(out.archived()).isTrue();
    }

    private static ArchiveRecord byNumber(List<ArchiveRecord> all, String n) {
        return all.stream().filter(r -> r.number().equals(n)).findFirst().orElseThrow();
    }

    private List<String> numbers(Query q) throws Exception {
        return ArchiveSearch.filter(ArchiveIndex.scan(root).records(), q, LocalDate.of(2027, 4, 1)).stream().map(ArchiveRecord::number).toList();
    }

    // ---- Suche ------------------------------------------------------------------------------------------------

    @Test
    void recordCarriesTheArchivedFacts() {
        assertThat(second.buyerName()).isEqualTo("Müller Bau GmbH");
        assertThat(second.issueDate()).isEqualTo(LocalDate.of(2027, 3, 10));
        assertThat(second.gross()).isEqualByComparingTo("1190.00");
        assertThat(second.format()).isEqualTo("XRECHNUNG");
        assertThat(second.belegStatus()).isEqualTo("E_RECHNUNG_IST_ORIGINAL");
        assertThat(third.belegStatus()).isEqualTo("PDF_IST_ORIGINAL");
        assertThat(second.retainUntil()).isEqualTo(LocalDate.of(2035, 12, 31));
        assertThat(second.outputFile()).isEqualTo("ausgabe-xrechnung.xml");
        assertThat(second.output()).exists();
    }

    @Test
    void emptyQueryFindsAllNewestFirst() throws Exception {
        assertThat(numbers(Query.all())).containsExactly("RE-2027-0002", "RE-2027-0001", "RE-2026-0099");
    }

    @Test
    void textSearchMatchesNumberBuyerAndIgnoresCase() throws Exception {
        assertThat(numbers(new Query("müller", null, null, null, null, null, null, null, null))).containsExactly("RE-2027-0002");
        assertThat(numbers(new Query("re-2027 kunde", null, null, null, null, null, null, null, null))).containsExactly("RE-2027-0001");
        assertThat(numbers(new Query("gibtesnicht", null, null, null, null, null, null, null, null))).isEmpty();
    }

    @Test
    void dateAmountStatusVersionAndMandantFilters() throws Exception {
        assertThat(numbers(new Query("", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 28), null, null, null, null, null, null)))
            .containsExactly("RE-2027-0001");
        assertThat(numbers(new Query("", null, null, new BigDecimal("1000"), null, null, null, null, null))).containsExactly("RE-2027-0002");
        assertThat(numbers(new Query("", null, null, null, new BigDecimal("100"), null, null, null, null))).containsExactly("RE-2026-0099");
        assertThat(numbers(new Query("", null, null, null, null, null, "PDF_IST_ORIGINAL", null, null))).containsExactly("RE-2026-0099");
        assertThat(numbers(new Query("", null, null, null, null, null, null, "9.9.9", null))).isEmpty();
        assertThat(numbers(new Query("", null, null, null, null, first.mandantId(), null, io.github.ebolansk.erechnung.Version.TOOL, null))).hasSize(3);
        assertThat(numbers(new Query("", null, null, null, null, "anderer", null, null, null))).isEmpty();
    }

    @Test
    void retentionFilterUsesTheEightYearRule() throws Exception {
        var all = ArchiveIndex.scan(root).records();
        Query expired = new Query("", null, null, null, null, null, null, null, true);
        assertThat(ArchiveSearch.filter(all, expired, LocalDate.of(2034, 12, 31))).isEmpty();
        assertThat(ArchiveSearch.filter(all, expired, LocalDate.of(2035, 1, 1))).extracting(ArchiveRecord::number)
            .containsExactly("RE-2026-0099");
        assertThat(ArchiveSearch.filter(all, expired, LocalDate.of(2036, 1, 1))).hasSize(3);
    }

    // ---- Integrität -------------------------------------------------------------------------------------------

    @Test
    void untouchedArchiveIsOk() throws Exception {
        var r = IntegrityCheck.run(root, audit);
        assertThat(r.invoices()).isEqualTo(3);
        assertThat(r.findings()).isEmpty();
        assertThat(r.ok()).isTrue();
    }

    @Test
    void changedFileIsReported() throws Exception {
        Files.writeString(second.output(), "<x/>", StandardCharsets.UTF_8);
        var r = IntegrityCheck.run(root, audit);
        assertThat(r.ok()).isFalse();
        assertThat(r.findings()).anyMatch(f -> f.error() && f.dir().equals(second.dir()) && f.message().contains("verändert")
            && f.message().contains("ausgabe-xrechnung.xml"));
    }

    @Test
    void missingFileIsReported() throws Exception {
        Files.delete(first.originalPdf());
        var r = IntegrityCheck.run(root, audit);
        assertThat(r.findings()).anyMatch(f -> f.error() && f.message().contains("fehlt") && f.message().contains("vorlage-original.pdf"));
    }

    @Test
    void fileAndDataJsonChangedTogetherIsStillCaughtByTheLog() throws Exception {
        byte[] forged = "<forged/>".getBytes(StandardCharsets.UTF_8);
        Files.write(second.output(), forged);
        Path dataJson = second.dir().resolve("daten.json");
        ObjectNode n = (ObjectNode) Json.mapper().readTree(dataJson.toFile());
        ((ObjectNode) n.at("/files/ausgabe-xrechnung.xml")).put("sha256", io.github.ebolansk.erechnung.util.Hashes.sha256Hex(forged));
        Files.write(dataJson, Json.mapper().writeValueAsBytes(n));
        var r = IntegrityCheck.run(root, audit);
        assertThat(r.ok()).isFalse();
        assertThat(r.findings()).noneMatch(f -> f.message().contains("Datei wurde verändert"));
        assertThat(r.findings()).anyMatch(f -> f.error() && f.message().contains("weicht vom Protokoll ab"));
    }

    @Test
    void unlistedFileAndLeftoverWriteFolderAreHintsNotErrors() throws Exception {
        Files.writeString(first.dir().resolve("notiz.txt"), "x");
        Files.createDirectories(root.resolve(".RE-X.tmp-1234"));
        var r = IntegrityCheck.run(root, audit);
        assertThat(r.ok()).isTrue();
        assertThat(r.findings()).hasSize(2).noneMatch(IntegrityCheck.Finding::error);
        assertThat(r.findings()).anyMatch(f -> f.message().contains("notiz.txt"));
        assertThat(r.findings()).anyMatch(f -> f.message().contains("Schreibordner"));
    }

    @Test
    void invoiceInLogButFolderGoneIsReported() throws Exception {
        try (var s = Files.walk(third.dir())) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
        var r = IntegrityCheck.run(root, audit);
        assertThat(r.invoices()).isEqualTo(2);
        assertThat(r.findings()).anyMatch(f -> f.error() && f.message().contains("RE-2026-0099") && f.message().contains("fehlt"));
    }

    @Test
    void unreadableDataJsonIsReported() throws Exception {
        Files.writeString(first.dir().resolve("daten.json"), "{kaputt");
        var r = IntegrityCheck.run(root, audit);
        assertThat(r.findings()).anyMatch(f -> f.error() && f.message().contains("daten.json ist nicht lesbar"));
    }

    @Test
    void tamperedAuditLogMakesTheReportNotOk() throws Exception {
        Path log = home.resolve("daten/protokoll.jsonl");
        Files.writeString(log, Files.readString(log).replaceFirst("tester", "mallory"));
        var r = IntegrityCheck.run(root, audit);
        assertThat(r.log().valid()).isFalse();
        assertThat(r.ok()).isFalse();
    }

    // ---- Oberfläche -------------------------------------------------------------------------------------------

    @Test
    void archivePanelListsFiltersAndOpensFiles() throws Exception {
        ConfigStore config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        var archiveService = new ArchiveService(home, config, audit, Clock.fixed(Instant.parse("2027-04-01T10:00:00Z"), ZoneOffset.UTC));
        List<Path> opened = new java.util.ArrayList<>();
        var panel = new io.github.ebolansk.erechnung.ui.ArchivePanel(archiveService, opened::add, null);
        assertThat(panel.results()).hasSize(3);
        panel.setText("müller");
        panel.search();
        assertThat(panel.results()).extracting(ArchiveRecord::number).containsExactly("RE-2027-0002");
        panel.select(0);
        for (String tip : new String[] {"Vorlage öffnen", "E-Rechnung öffnen"}) {
            for (var b : allButtons(panel)) {
                if (tip.equals(b.getToolTipText())) {
                    b.doClick();
                }
            }
        }
        assertThat(opened).containsExactly(second.originalPdf(), second.output());

        panel.setText("");
        panel.search();
        panel.setSize(1100, 420);
        layout(panel);
        var img = new java.awt.image.BufferedImage(1100, 420, java.awt.image.BufferedImage.TYPE_INT_RGB);
        panel.paint(img.getGraphics());
        Path out = Path.of("target/ui-snapshots/archiv.png");
        Files.createDirectories(out.getParent());
        javax.imageio.ImageIO.write(img, "png", out.toFile());
    }

    @Test
    void formatIsShownWithItsProperSpelling() {
        assertThat(io.github.ebolansk.erechnung.ui.ArchivePanel.formatLabel("XRECHNUNG")).isEqualTo("XRechnung");
        assertThat(io.github.ebolansk.erechnung.ui.ArchivePanel.formatLabel("ZUGFERD")).isEqualTo("ZUGFeRD");
        assertThat(io.github.ebolansk.erechnung.ui.ArchivePanel.formatLabel("xrechnung")).isEqualTo("XRechnung");
        assertThat(io.github.ebolansk.erechnung.ui.ArchivePanel.formatLabel("")).isEmpty();
    }

    @Test
    void typingInTheSearchFieldNarrowsTheListWithoutPressingAnything() throws Exception {
        ConfigStore config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        var archiveService = new ArchiveService(home, config, audit, Clock.fixed(Instant.parse("2027-04-01T10:00:00Z"), ZoneOffset.UTC));
        var panel = new io.github.ebolansk.erechnung.ui.ArchivePanel(archiveService, p -> { }, null);
        assertThat(panel.results()).hasSize(3);
        javax.swing.SwingUtilities.invokeAndWait(() -> panel.setText("müller"));
        long end = System.currentTimeMillis() + 3000;
        while (panel.results().size() != 1 && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
        assertThat(panel.results()).extracting(ArchiveRecord::number).containsExactly("RE-2027-0002");
        javax.swing.SwingUtilities.invokeAndWait(() -> panel.setText(""));
        end = System.currentTimeMillis() + 3000;
        while (panel.results().size() != 3 && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
        assertThat(panel.results()).hasSize(3);
    }

    @Test
    void checkWritesAnAuditEntryAndReportsTampering() throws Exception {
        ConfigStore config = new ConfigStore(home.resolve("daten/konfiguration.json"));
        var archiveService = new ArchiveService(home, config, audit, Clock.fixed(Instant.parse("2027-04-01T10:00:00Z"), ZoneOffset.UTC));
        assertThat(archiveService.check().ok()).isTrue();
        Files.writeString(first.output(), "<x/>");
        assertThat(archiveService.check().ok()).isFalse();
        assertThat(audit.entries()).filteredOn(e -> e.action().equals("integritaetspruefung"))
            .extracting(e -> e.details().get("ergebnis")).containsExactly("in Ordnung", "Abweichungen");
        assertThat(audit.verify().valid()).isTrue();
    }

    private static List<javax.swing.JButton> allButtons(java.awt.Container c) {
        List<javax.swing.JButton> found = new java.util.ArrayList<>();
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof javax.swing.JButton b) {
                found.add(b);
            } else if (k instanceof java.awt.Container sub) {
                found.addAll(allButtons(sub));
            }
        }
        return found;
    }

    private static void layout(java.awt.Component c) {
        c.doLayout();
        if (c instanceof java.awt.Container k) {
            for (java.awt.Component child : k.getComponents()) {
                layout(child);
            }
        }
    }
}
