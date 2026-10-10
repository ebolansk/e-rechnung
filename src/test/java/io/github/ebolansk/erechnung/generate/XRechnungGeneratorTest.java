// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.generate;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.TestData;
import io.github.ebolansk.erechnung.model.DocumentType;
import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.LineItem;
import io.github.ebolansk.erechnung.model.Party;
import io.github.ebolansk.erechnung.validate.ValidationService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class XRechnungGeneratorTest {
    private final XRechnungGenerator generator = new XRechnungGenerator();
    private final ValidationService validator = new ValidationService();

    private static InvoiceData with(InvoiceData d, Party seller, List<LineItem> items, DocumentType type) {
        return new InvoiceData(type, d.number(), d.issueDate(), d.deliveryDate(), d.deliveryPeriodEnd(), d.dueDate(),
            d.currency(), d.buyerReference(), d.paymentTerms(), seller, d.buyer(), d.iban(), d.bic(), items);
    }

    private void assertValid(InvoiceData d) {
        byte[] xml = generator.generate(d);
        var report = validator.validate(xml, "xrechnung.xml");
        assertThat(report.errors()).as("Befunde: %s", report.errors()).isEmpty();
        assertThat(report.passed()).isTrue();
    }

    @Test
    void datesAreNotShiftedWhenJvmStartsInAnotherTimeZone() throws Exception {
        for (String zone : new String[] {"UTC", "America/New_York", "Asia/Tokyo", "Europe/Berlin"}) {
            Process proc = new ProcessBuilder(
                java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.timezone=" + zone, "-Djava.awt.headless=true",
                "-cp", System.getProperty("java.class.path"), TimeZoneProbe.class.getName())
                .redirectErrorStream(true).start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(proc.waitFor()).as("Kind-JVM %s: %s", zone, out).isZero();
            assertThat(out).as("Zeitzone %s", zone).contains(">20270115<").contains(">20270110<").contains(">20270214<");
        }
    }

    @Test
    void discountLineWithNegativeQuantityIsValid() {
        var d = TestData.invoice();
        var items = new ArrayList<>(d.items());
        items.add(new LineItem("Rabatt", "", "C62", new BigDecimal("-1"), new BigDecimal("50.00"), new BigDecimal("19"), "S", ""));
        assertValid(with(d, d.seller(), items, DocumentType.INVOICE));
    }

    @Test
    void sellerWithoutContactNameAndPhoneProducesNoEmptyContactElements() {
        var d = TestData.invoice();
        var s = d.seller();
        var seller = new Party(s.name(), s.street(), s.zip(), s.city(), s.country(), s.vatId(), "", s.email(), "", "");
        String xml = new String(generator.generate(with(d, seller, d.items(), DocumentType.INVOICE)), StandardCharsets.UTF_8);
        assertThat(xml).doesNotContain("<ram:PersonName").doesNotContain("<ram:TelephoneUniversalCommunication");
        assertThat(xml).contains("<ram:URIID");
    }

    @Test
    void standardInvoiceIsValid() {
        assertValid(TestData.invoice());
    }

    @Test
    void umlautsSurviveAsUtf8() {
        var d = TestData.invoice();
        var items = List.of(new LineItem("Beratung für Müller & Söhne", "Größe", "HUR", new BigDecimal("1"),
            new BigDecimal("100.00"), new BigDecimal("19"), "S", ""));
        byte[] xml = generator.generate(with(d, d.seller(), items, DocumentType.INVOICE));
        assertThat(new String(xml, StandardCharsets.UTF_8)).contains("Beratung für Müller &amp; Söhne");
        assertValid(with(d, d.seller(), items, DocumentType.INVOICE));
    }

    @Test
    void creditNoteHasTypeCode381AndIsValid() {
        var d = TestData.invoice();
        var credit = with(d, d.seller(), d.items(), DocumentType.CREDIT_NOTE);
        assertThat(new String(generator.generate(credit), StandardCharsets.UTF_8)).contains(">381<");
        assertValid(credit);
    }

    @Test
    void sellerWithTaxNumberOnlyIsValid() {
        var d = TestData.invoice();
        var s = d.seller();
        var taxOnly = new Party(s.name(), s.street(), s.zip(), s.city(), s.country(), "", "12345/67890",
            s.email(), s.contactName(), s.contactPhone());
        assertValid(with(d, taxOnly, d.items(), DocumentType.INVOICE));
    }

    @Test
    void mixedVatRatesAreValid() {
        var d = TestData.invoice();
        var items = new ArrayList<>(d.items());
        items.add(new LineItem("Buch", "", "C62", new BigDecimal("2"), new BigDecimal("10.00"),
            new BigDecimal("7"), "S", ""));
        assertValid(with(d, d.seller(), items, DocumentType.INVOICE));
    }

    @Test
    void exemptLineWithReasonIsValid() {
        var d = TestData.invoice();
        var items = List.of(new LineItem("Leistung", "", "C62", new BigDecimal("1"), new BigDecimal("200.00"),
            BigDecimal.ZERO, "E", "Steuerfrei nach § 4 Nr. 21 UStG"));
        assertValid(with(d, d.seller(), items, DocumentType.INVOICE));
    }

    @Test
    void deliveryPeriodIsValid() {
        var d = TestData.invoice();
        var period = new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), d.deliveryDate().plusDays(20),
            d.dueDate(), d.currency(), d.buyerReference(), d.paymentTerms(), d.seller(), d.buyer(), d.iban(), d.bic(), d.items());
        assertValid(period);
    }

    /** Läuft in einer Kind-JVM mit anderer Zeitzone und gibt das erzeugte XML aus. */
    public static final class TimeZoneProbe {
        public static void main(String[] args) {
            System.out.print(new String(new XRechnungGenerator().generate(TestData.invoice()), StandardCharsets.UTF_8));
        }
    }
}
