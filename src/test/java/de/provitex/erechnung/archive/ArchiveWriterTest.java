// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import de.provitex.erechnung.TestData;
import de.provitex.erechnung.model.OutputFormat;
import de.provitex.erechnung.util.Hashes;
import de.provitex.erechnung.util.Json;
import de.provitex.erechnung.validate.ValidationReportParser;
import de.provitex.erechnung.validate.ValidationReportParserTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveWriterTest {
    static ArchiveInput input() {
        var inv = TestData.invoice();
        return new ArchiveInput("m1", "Muster GmbH", inv, OutputFormat.XRECHNUNG,
            "PDF-ORIGINAL".getBytes(StandardCharsets.UTF_8), "rechnung.pdf",
            "<xml/>".getBytes(StandardCharsets.UTF_8), "ausgabe-xrechnung.xml", "<html/>",
            ValidationReportParser.parse(ValidationReportParserTest.XML_VALID),
            BelegStatus.assess(inv.issueDate(), inv.deliveryDate(), null, LocalDate.of(2027, 1, 1)),
            "tester", Instant.parse("2027-01-15T10:00:00Z"));
    }

    @Test
    void writesAllFilesWithMatchingHashes(@TempDir Path root) throws Exception {
        Path target = root.resolve("Muster GmbH/2027/01/RE-2027-0001");
        var result = new ArchiveWriter().write(target, input());
        for (String n : List.of("vorlage-original.pdf", "ausgabe-xrechnung.xml", "pruefprotokoll.html", "daten.json")) {
            assertThat(target.resolve(n)).exists();
        }
        JsonNode data = Json.mapper().readTree(target.resolve("daten.json").toFile());
        assertThat(data.at("/files/vorlage-original.pdf/sha256").asText())
            .isEqualTo(Hashes.sha256Hex(target.resolve("vorlage-original.pdf")))
            .isEqualTo(result.sha256ByFile().get("vorlage-original.pdf"));
        assertThat(data.at("/files/ausgabe-xrechnung.xml/sha256").asText())
            .isEqualTo(Hashes.sha256Hex(target.resolve("ausgabe-xrechnung.xml")));
        assertThat(data.get("retainUntil").asText()).isEqualTo("2035-12-31");
        assertThat(data.at("/invoice/number").asText()).isEqualTo("RE-2027-0001");
        assertThat(data.at("/mandant/id").asText()).isEqualTo("m1");
        assertThat(data.get("belegStatus").asText()).isEqualTo("E_RECHNUNG_IST_ORIGINAL");
        assertThat(data.at("/validation/passed").asBoolean()).isTrue();
    }

    @Test
    void recordsConfirmationAndBelegOverrideInDatenJson(@TempDir Path root) throws Exception {
        var ok = input();
        var conf = new Confirmation(new java.math.BigDecimal("300.00"), null, new java.math.BigDecimal("358.00"), true,
            BelegStatus.PDF_IST_ORIGINAL);
        var in = new ArchiveInput(ok.mandantId(), ok.mandantName(), ok.invoice(), ok.format(), ok.originalPdf(),
            ok.originalFileName(), ok.output(), ok.outputFileName(), ok.protocolHtml(), ok.report(), ok.beleg(),
            ok.confirmedBy(), ok.confirmedAt(), conf);
        Path target = root.resolve("x");
        new ArchiveWriter().write(target, in);
        JsonNode data = Json.mapper().readTree(target.resolve("daten.json").toFile());
        assertThat(data.get("belegStatus").asText()).isEqualTo("PDF_IST_ORIGINAL");
        assertThat(data.get("belegStatusAuto").asText()).isEqualTo("E_RECHNUNG_IST_ORIGINAL");
        assertThat(data.get("belegStatusOverridden").asBoolean()).isTrue();
        assertThat(data.get("totalsMismatchConfirmed").asBoolean()).isTrue();
        assertThat(data.at("/printedTotals/gross").asText()).isEqualTo("358.00");
        assertThat(data.at("/printedTotals/tax").isNull()).isTrue();
    }

    @Test
    void neverOverwritesAnExistingArchiveEntry(@TempDir Path root) throws Exception {
        Path target = root.resolve("a/b");
        new ArchiveWriter().write(target, input());
        byte[] before = Files.readAllBytes(target.resolve("vorlage-original.pdf"));
        assertThatThrownBy(() -> new ArchiveWriter().write(target, input())).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readAllBytes(target.resolve("vorlage-original.pdf"))).isEqualTo(before);
    }

    @Test
    void failureLeavesNoPartialDirectoryBehind(@TempDir Path root) throws Exception {
        var ok = input();
        var broken = new ArchiveInput(ok.mandantId(), ok.mandantName(), ok.invoice(), ok.format(), ok.originalPdf(),
            ok.originalFileName(), ok.output(), ok.outputFileName(), null, ok.report(), ok.beleg(), ok.confirmedBy(), ok.confirmedAt());
        assertThatThrownBy(() -> new ArchiveWriter().write(root.resolve("x/y"), broken)).isInstanceOf(RuntimeException.class);
        try (Stream<Path> s = Files.walk(root)) {
            assertThat(s.map(p -> p.getFileName().toString())).noneMatch(n -> n.contains(".tmp-"));
        }
        assertThat(root.resolve("x/y")).doesNotExist();
    }
}
