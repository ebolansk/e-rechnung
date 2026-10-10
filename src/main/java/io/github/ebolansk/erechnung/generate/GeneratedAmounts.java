// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung.generate;

import io.github.ebolansk.erechnung.model.InvoiceCalculator;
import io.github.ebolansk.erechnung.model.LineItem;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Vergleicht die Beträge im erzeugten XML mit dem, was der Nutzer bestätigt hat und das Archiv festhält (InvoiceCalculator).
 * Mustang rechnet die Summen aus den Positionen selbst nach und der Validator prüft nur, dass das XML mit sich selbst
 * übereinstimmt: Eine abweichende Rundung, zum Beispiel nach einem Bibliotheks-Update, bliebe sonst unbemerkt, und die
 * E-Rechnung trüge einen anderen Betrag als der bestätigte.
 */
public final class GeneratedAmounts {
    private GeneratedAmounts() {
    }

    /**
     * Abweichungen zwischen dem erzeugten XML (bei ZUGFeRD das eingebettete XML) und den berechneten Summen; leer, wenn alles
     * übereinstimmt. Lässt sich das XML nicht lesen, ist das selbst ein Befund.
     */
    public static List<String> differences(byte[] output, boolean zugferd, List<LineItem> items) {
        List<String> found = new ArrayList<>();
        try {
            Document xml = parse(zugferd ? embeddedXml(output) : output);
            var expected = InvoiceCalculator.compute(items);
            Element sum = first(xml, "SpecifiedTradeSettlementHeaderMonetarySummation");
            compare(found, "Nettosumme (TaxBasisTotalAmount)", amount(sum, "TaxBasisTotalAmount"), expected.netTotal());
            compare(found, "Steuer (TaxTotalAmount)", amount(sum, "TaxTotalAmount"), expected.taxTotal());
            compare(found, "Bruttosumme (GrandTotalAmount)", amount(sum, "GrandTotalAmount"), expected.grossTotal());
            compare(found, "Zahlbetrag (DuePayableAmount)", amount(sum, "DuePayableAmount"), expected.grossTotal());
            BigDecimal lines = items.stream().map(InvoiceCalculator::lineNet).reduce(BigDecimal.ZERO, BigDecimal::add);
            compare(found, "Positionssumme (LineTotalAmount)", amount(sum, "LineTotalAmount"), lines);
            var taxes = xml.getElementsByTagNameNS("*", "ApplicableTradeTax");
            int groups = 0;
            for (int i = 0; i < taxes.getLength(); i++) {
                Element t = (Element) taxes.item(i);
                if (t.getElementsByTagNameNS("*", "BasisAmount").getLength() == 0) {
                    continue; // Steuer an einer Position, nicht in der Kopfsumme
                }
                groups++;
                String category = text(t, "CategoryCode");
                BigDecimal rate = new BigDecimal(text(t, "RateApplicablePercent"));
                var line = expected.taxLines().stream()
                    .filter(l -> l.category().equals(category) && l.percent().compareTo(rate) == 0).findFirst().orElse(null);
                if (line == null) {
                    found.add("Steuergruppe " + category + " " + rate + " % steht im XML, aber nicht in der Berechnung.");
                    continue;
                }
                compare(found, "Steuerbasis " + category + " " + rate + " %", amount(t, "BasisAmount"), line.basis());
                compare(found, "Steuerbetrag " + category + " " + rate + " %", amount(t, "CalculatedAmount"), line.tax());
            }
            if (groups != expected.taxLines().size()) {
                found.add("Anzahl der Steuergruppen: " + groups + " im XML, " + expected.taxLines().size() + " berechnet.");
            }
        } catch (Exception e) {
            found.add("Die Beträge im erzeugten XML ließen sich nicht prüfen (" + e.getMessage() + ").");
        }
        return found;
    }

    private static void compare(List<String> found, String what, BigDecimal actual, BigDecimal expected) {
        if (actual.compareTo(expected) != 0) {
            found.add(what + ": " + actual.toPlainString() + " im XML, " + expected.toPlainString() + " berechnet.");
        }
    }

    private static byte[] embeddedXml(byte[] pdf) throws Exception {
        try (var doc = Loader.loadPDF(pdf)) {
            var names = doc.getDocumentCatalog().getNames();
            if (names == null || names.getEmbeddedFiles() == null) {
                throw new IllegalStateException("kein eingebettetes XML im PDF");
            }
            for (var spec : names.getEmbeddedFiles().getNames().values()) {
                if (spec instanceof PDComplexFileSpecification complex && complex.getEmbeddedFile() != null) {
                    return complex.getEmbeddedFile().toByteArray();
                }
            }
            throw new IllegalStateException("kein eingebettetes XML im PDF");
        }
    }

    private static Document parse(byte[] xml) throws Exception {
        var f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static Element first(Document xml, String name) {
        var nodes = xml.getElementsByTagNameNS("*", name);
        if (nodes.getLength() == 0) {
            throw new IllegalStateException("Element " + name + " fehlt");
        }
        return (Element) nodes.item(0);
    }

    private static String text(Element parent, String name) {
        var nodes = parent.getElementsByTagNameNS("*", name);
        if (nodes.getLength() == 0) {
            throw new IllegalStateException("Element " + name + " fehlt");
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static BigDecimal amount(Element parent, String name) {
        return new BigDecimal(text(parent, name));
    }
}
