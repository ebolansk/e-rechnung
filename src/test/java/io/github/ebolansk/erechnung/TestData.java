// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 E-Rechnung-Tool contributors
package io.github.ebolansk.erechnung;

import io.github.ebolansk.erechnung.model.DocumentType;
import io.github.ebolansk.erechnung.model.InvoiceData;
import io.github.ebolansk.erechnung.model.LineItem;
import io.github.ebolansk.erechnung.model.Party;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class TestData {
    private TestData() {
    }

    public static Party seller() {
        return new Party("Muster GmbH", "Hauptstr. 1", "72760", "Reutlingen", "DE", "DE123456789", "",
            "rechnung@muster.example", "Max Muster", "+49 7121 123456");
    }

    public static Party buyer() {
        return new Party("Kunde AG", "Nebenstr. 2", "10115", "Berlin", "DE", "", "", "einkauf@kunde.example", "", "");
    }

    public static InvoiceData invoice() {
        return new InvoiceData(DocumentType.INVOICE, "RE-2027-0001",
            LocalDate.of(2027, 1, 15), LocalDate.of(2027, 1, 10), null, LocalDate.of(2027, 2, 14),
            "EUR", "04011000-12345-34", "Zahlbar innerhalb von 30 Tagen netto",
            seller(), buyer(), "DE02120300000000202051", "BYLADEM1001",
            List.of(new LineItem("Beratung", "Stundensatz", "HUR", new BigDecimal("3"),
                new BigDecimal("100.00"), new BigDecimal("19"), "S", "")));
    }

    public static byte[] embeddedFontPdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); InputStream ttf = TestData.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            return render(doc, PDType0Font.load(doc, ttf), text);
        }
    }

    public static byte[] base14Pdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            return render(doc, new PDType1Font(Standard14Fonts.FontName.HELVETICA), text);
        }
    }

    private static byte[] render(PDDocument doc, PDFont font, String text) throws Exception {
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(font, 12);
            cs.newLineAtOffset(72, 700);
            cs.showText(text);
            cs.endText();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }
}
