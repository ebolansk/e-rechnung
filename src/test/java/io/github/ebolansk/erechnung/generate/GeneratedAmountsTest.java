// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.generate;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ebolansk.erechnung.TestData;
import io.github.ebolansk.erechnung.model.InvoiceCalculator;
import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.LineItem;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Die Beträge im erzeugten XML müssen genau dem entsprechen, was der Nutzer bestätigt hat und was das Archiv festhält
 * (InvoiceCalculator). Mustang rechnet die Summen aus den Positionen selbst nach, der Validator prüft nur, dass das XML mit sich
 * selbst übereinstimmt: Ohne diesen Test bliebe eine abweichende Rundung (zum Beispiel nach einem Bibliotheks-Update) unbemerkt.
 */
class GeneratedAmountsTest {
    private static LineItem item(String qty, String price, String vat, String category, String reason) {
        return new LineItem("Position", "", "C62", new BigDecimal(qty), new BigDecimal(price), new BigDecimal(vat), category, reason);
    }

    private static InvoiceData invoiceWith(List<LineItem> items) {
        var d = TestData.invoice();
        return new InvoiceData(d.type(), d.number(), d.issueDate(), d.deliveryDate(), d.deliveryPeriodEnd(), d.dueDate(),
            d.currency(), d.buyerReference(), d.paymentTerms(), d.seller(), d.buyer(), d.iban(), d.bic(), items);
    }

    private static List<List<LineItem>> cases() {
        List<LineItem> manySmall = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            manySmall.add(item("3", "0.10", "19", "S", ""));
        }
        return List.of(
            List.of(item("3", "100.00", "19", "S", "")),
            List.of(item("2.5", "13.3333", "19", "S", ""), item("0.333", "19.9999", "19", "S", ""), item("2", "10.00", "7", "S", "")),
            List.of(item("1", "200.00", "0", "E", "Steuerfrei nach § 4 Nr. 21 UStG"), item("4", "12.505", "19", "S", ""),
                item("-1", "50.00", "19", "S", "")),
            manySmall,
            List.of(item("0.5", "0.01", "7", "S", ""), item("1.5", "0.01", "7", "S", ""), item("1000", "3.3333", "19", "S", "")));
    }

    private static void assertAmounts(Document xml, List<LineItem> items) {
        var expected = InvoiceCalculator.compute(items);
        Element summation = (Element) xml.getElementsByTagNameNS("*", "SpecifiedTradeSettlementHeaderMonetarySummation").item(0);
        assertThat(amount(summation, "TaxBasisTotalAmount")).as("TaxBasisTotalAmount").isEqualByComparingTo(expected.netTotal());
        assertThat(amount(summation, "TaxTotalAmount")).as("TaxTotalAmount").isEqualByComparingTo(expected.taxTotal());
        assertThat(amount(summation, "GrandTotalAmount")).as("GrandTotalAmount").isEqualByComparingTo(expected.grossTotal());
        assertThat(amount(summation, "DuePayableAmount")).as("DuePayableAmount").isEqualByComparingTo(expected.grossTotal());
        BigDecimal lines = items.stream().map(InvoiceCalculator::lineNet).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(amount(summation, "LineTotalAmount")).as("LineTotalAmount").isEqualByComparingTo(lines);

        var taxes = xml.getElementsByTagNameNS("*", "ApplicableTradeTax");
        int headerTaxes = 0;
        for (int i = 0; i < taxes.getLength(); i++) {
            Element t = (Element) taxes.item(i);
            if (t.getElementsByTagNameNS("*", "BasisAmount").getLength() == 0) {
                continue; // Steuer an einer Position, nicht in der Kopfsumme
            }
            headerTaxes++;
            String category = text(t, "CategoryCode");
            BigDecimal rate = new BigDecimal(text(t, "RateApplicablePercent"));
            var line = expected.taxLines().stream()
                .filter(l -> l.category().equals(category) && l.percent().compareTo(rate) == 0).findFirst().orElseThrow();
            assertThat(amount(t, "BasisAmount")).as("BasisAmount %s %s", category, rate).isEqualByComparingTo(line.basis());
            assertThat(amount(t, "CalculatedAmount")).as("CalculatedAmount %s %s", category, rate).isEqualByComparingTo(line.tax());
        }
        assertThat(headerTaxes).as("Steuergruppen in der Kopfsumme").isEqualTo(expected.taxLines().size());
    }

    private static String text(Element parent, String name) {
        return parent.getElementsByTagNameNS("*", name).item(0).getTextContent().trim();
    }

    private static BigDecimal amount(Element parent, String name) {
        return new BigDecimal(text(parent, name));
    }

    private static Document parse(byte[] xml) throws Exception {
        var f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    @Test
    void xRechnungAmountsEqualTheCalculatorForRoundingHeavyInvoices() throws Exception {
        for (var items : cases()) {
            assertAmounts(parse(new XRechnungGenerator().generate(invoiceWith(items))), items);
        }
    }

    @Test
    void zugferdEmbeddedXmlAmountsEqualTheCalculator() throws Exception {
        byte[] pdf = TestData.embeddedFontPdf("Rechnung RE-2027-0001");
        for (var items : cases()) {
            byte[] out = new ZugferdGenerator().generate(invoiceWith(items), pdf);
            try (var doc = Loader.loadPDF(out)) {
                var files = doc.getDocumentCatalog().getNames().getEmbeddedFiles().getNames();
                byte[] xml = Stream.of(files.values().toArray(new org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification[0]))
                    .findFirst().orElseThrow().getEmbeddedFile().toByteArray();
                assertAmounts(parse(xml), items);
            }
        }
    }

    @Test
    void productionCheckAcceptsTheGeneratedXmlAndDetectsADeviatingTotal() throws Exception {
        for (var items : cases()) {
            byte[] xml = new XRechnungGenerator().generate(invoiceWith(items));
            assertThat(GeneratedAmounts.differences(xml, false, items)).as("unverändertes XML").isEmpty();
            byte[] pdf = new ZugferdGenerator().generate(invoiceWith(items), TestData.embeddedFontPdf("Rechnung"));
            assertThat(GeneratedAmounts.differences(pdf, true, items)).as("unverändertes ZUGFeRD").isEmpty();
        }
        var items = cases().get(0);
        String tampered = new String(new XRechnungGenerator().generate(invoiceWith(items)), java.nio.charset.StandardCharsets.UTF_8)
            .replaceAll("(<ram:GrandTotalAmount>)[0-9.]+(</ram:GrandTotalAmount>)", "$11.00$2");
        assertThat(GeneratedAmounts.differences(tampered.getBytes(java.nio.charset.StandardCharsets.UTF_8), false, items))
            .anyMatch(m -> m.contains("Bruttosumme"));
        assertThat(GeneratedAmounts.differences("kein xml".getBytes(), false, items)).anyMatch(m -> m.contains("ließen sich nicht prüfen"));
    }
}
