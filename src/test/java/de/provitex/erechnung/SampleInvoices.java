// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Stefan Schmitt
package de.provitex.erechnung;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Drei erfundene Beispielrechnungen mit unterschiedlichem Layout (alle Firmen, Personen und Kontodaten fiktiv).
 * Sie ersetzen echte Kunden-PDFs im Test: Seitenaufbau, Spalten und Schreibweisen weichen bewusst voneinander ab.
 */
public final class SampleInvoices {
    private SampleInvoices() {
    }

    /** Klassisch nach DIN 5008: Absenderzeile über dem Anschriftfeld, Infoblock rechts auf Höhe der Anschrift, Fußzeile in drei Spalten. */
    public static byte[] classic() throws IOException {
        try (Doc d = new Doc()) {
            Pg p = d.page();
            p.text(350, 790, 14, "Nordlicht Werbetechnik GmbH");
            p.text(350, 775, 9, "Lindenweg 12 · 70173 Stuttgart");
            p.text(350, 763, 9, "Tel.: +49 711 5550123");
            p.text(350, 751, 9, "info@nordlicht-werbung.example");

            p.text(60, 705, 7, "Nordlicht Werbetechnik GmbH · Lindenweg 12 · 70173 Stuttgart");
            p.text(60, 685, 11, "Bäckerei Sonnenschein OHG");
            p.text(60, 671, 11, "z. Hd. Frau Keller");
            p.text(60, 657, 11, "Marktplatz 5");
            p.text(60, 643, 11, "89073 Ulm");

            p.text(350, 685, 10, "Rechnungs-Nr.: 2027-00123");
            p.text(350, 671, 10, "Rechnungsdatum: 20.01.2027");
            p.text(350, 657, 10, "Leistungsdatum: 18.01.2027");
            p.text(350, 643, 10, "Kunden-Nr.: 10045");
            p.text(350, 629, 10, "Bestellnummer: B-7781");

            p.text(60, 590, 16, "Rechnung");
            p.text(60, 560, 9, "Pos");
            p.text(90, 560, 9, "Bezeichnung");
            p.text(300, 560, 9, "Menge");
            p.text(360, 560, 9, "Einzelpreis");
            p.text(440, 560, 9, "MwSt");
            p.right(535, 560, 9, "Gesamt");
            p.rule(60, 555, 535);

            float y = 538;
            for (String[] r : new String[][] {
                {"1", "Leuchtreklame Aluminium 120x40", "2 Stk", "389,90 €", "19 %", "779,80 €"},
                {"2", "Montage vor Ort", "3,5 Std", "85,00 €", "19 %", "297,50 €"},
                {"3", "Anfahrtspauschale", "1 psch", "45,00 €", "19 %", "45,00 €"}}) {
                p.text(60, y, 10, r[0]);
                p.text(90, y, 10, r[1]);
                p.text(300, y, 10, r[2]);
                p.text(360, y, 10, r[3]);
                p.text(440, y, 10, r[4]);
                p.right(535, y, 10, r[5]);
                y -= 18;
            }
            p.rule(60, y + 8, 535);
            p.text(330, y - 10, 10, "Nettobetrag");
            p.right(535, y - 10, 10, "1.122,30 €");
            p.text(330, y - 26, 10, "zzgl. 19 % MwSt.");
            p.right(535, y - 26, 10, "213,24 €");
            p.text(330, y - 46, 11, "Rechnungsbetrag");
            p.right(535, y - 46, 11, "1.335,54 €");
            p.text(60, y - 90, 10, "Zahlbar innerhalb von 14 Tagen ohne Abzug bis 03.02.2027.");

            p.text(60, 90, 8, "Nordlicht Werbetechnik GmbH");
            p.text(60, 79, 8, "Lindenweg 12");
            p.text(60, 68, 8, "70173 Stuttgart");
            p.text(210, 90, 8, "Amtsgericht Stuttgart HRB 123456");
            p.text(210, 79, 8, "Geschäftsführerin: Anna Beispiel");
            p.text(210, 68, 8, "USt-IdNr.: DE811234567");
            p.text(390, 90, 8, "Beispielbank Stuttgart");
            p.text(390, 79, 8, "IBAN: DE89 3704 0044 0532 0130 00");
            p.text(390, 68, 8, "BIC: COBADEFFXXX");
            return d.bytes();
        }
    }

    /** Modern: Kopf mit großem Namen, Rechnungsdaten als Beschriftungszeile über einer Wertezeile, Beträge mit „EUR“, ohne Positionsnummern. */
    public static byte[] modern() throws IOException {
        try (Doc d = new Doc()) {
            Pg p = d.page();
            p.text(50, 780, 22, "Studio Feldweg");
            p.text(50, 762, 10, "Webdesign & Beratung");
            p.right(545, 785, 9, "Studio Feldweg UG (haftungsbeschränkt)");
            p.right(545, 773, 9, "Gartenstraße 7");
            p.right(545, 761, 9, "72070 Tübingen");
            p.right(545, 749, 9, "hallo@studio-feldweg.example");
            p.right(545, 737, 9, "Tel. 07071 998877");

            p.text(50, 690, 11, "Rothaus Immobilien GmbH");
            p.text(50, 676, 11, "Frau Lena Roth");
            p.text(50, 662, 11, "Hirschgasse 3");
            p.text(50, 648, 11, "72074 Tübingen");

            p.text(50, 600, 20, "Rechnung");
            p.text(50, 570, 8, "Rechnungsnummer");
            p.text(180, 570, 8, "Rechnungsdatum");
            p.text(320, 570, 8, "Leistungsdatum");
            p.text(450, 570, 8, "Ihre Referenz");
            p.text(50, 556, 11, "R-2027-0815");
            p.text(180, 556, 11, "15. Januar 2027");
            p.text(320, 556, 11, "12.01.2027");
            p.text(450, 556, 11, "PRJ-2027-14");

            p.text(50, 520, 9, "Beschreibung");
            p.text(330, 520, 9, "Anzahl");
            p.right(450, 520, 9, "Preis");
            p.right(545, 520, 9, "Betrag");
            p.rule(50, 515, 545);
            float y = 498;
            for (String[] r : new String[][] {
                {"Webdesign Relaunch (Pauschale)", "1", "4.200,00 EUR", "4.200,00 EUR"},
                {"Hosting 12 Monate", "12", "29,90 EUR", "358,80 EUR"},
                {"Wartung", "6", "49,00 EUR", "294,00 EUR"}}) {
                p.text(50, y, 10, r[0]);
                p.text(330, y, 10, r[1]);
                p.right(450, y, 10, r[2]);
                p.right(545, y, 10, r[3]);
                y -= 20;
            }
            p.rule(50, y + 10, 545);
            p.text(330, y - 8, 10, "Summe netto");
            p.right(545, y - 8, 10, "4.852,80 EUR");
            p.text(330, y - 24, 10, "Umsatzsteuer 19 %");
            p.right(545, y - 24, 10, "922,03 EUR");
            p.text(330, y - 44, 11, "Gesamtsumme");
            p.right(545, y - 44, 11, "5.774,83 EUR");
            p.text(50, y - 90, 10, "Bitte überweisen Sie den Betrag bis zum 14.02.2027 auf das unten genannte Konto.");

            p.text(50, 70, 7, "Studio Feldweg UG (haftungsbeschränkt) · Sitz Tübingen · Amtsgericht Stuttgart HRB 765432 · Geschäftsführerin: Mara Feld");
            p.text(50, 60, 7, "Steuernummer 86/123/45678 · USt-IdNr. DE299876543");
            p.text(50, 50, 7, "Beispielsparkasse Tübingen · IBAN DE75 5121 0800 1245 1261 99 · BIC SSKMDEMMXXX");
            return d.bytes();
        }
    }

    /** Dienstleister: Rechnungsdaten links, Anschrift rechts auf gleicher Höhe, zwei Steuersätze, Leistungszeitraum, zweiseitig mit Tabellenkopf auf Seite 2. */
    public static byte[] service() throws IOException {
        try (Doc d = new Doc()) {
            Pg p = d.page();
            p.text(60, 800, 7, "Hartmann Ingenieurleistungen e.K. · Werkstraße 4a · 73728 Esslingen am Neckar");
            p.text(330, 760, 11, "Landratsamt Beispielkreis");
            p.text(330, 746, 11, "Fachbereich Bauen");
            p.text(330, 732, 11, "Amtsplatz 1");
            p.text(330, 718, 11, "73728 Esslingen am Neckar");

            p.text(60, 760, 14, "Rechnung Nr. 2027-0042");
            p.text(60, 744, 10, "Datum: 03.02.2027");
            p.text(60, 732, 10, "Leistungszeitraum: 01.01.2027 – 31.01.2027");
            p.text(60, 720, 10, "Leitweg-ID: 08116000-1234-21");
            p.text(60, 708, 10, "Bestellnummer: 4500012345");

            p.text(60, 660, 9, "Pos");
            p.text(85, 660, 9, "Beschreibung");
            p.text(270, 660, 9, "Menge");
            p.text(335, 660, 9, "Einzelpreis");
            p.text(410, 660, 9, "USt");
            p.right(535, 660, 9, "Netto");
            p.rule(60, 655, 535);
            p.text(85, 637, 10, "Bestandsaufnahme Heizungsanlage");
            p.text(270, 637, 10, "2,5 Std");
            p.text(335, 637, 10, "95,00 EUR");
            p.text(410, 637, 10, "19 %");
            p.text(60, 637, 10, "1");
            p.right(535, 637, 10, "237,50 EUR");
            p.text(85, 624, 8, "Begehung inklusive Dokumentation und Fotos");
            p.text(60, 600, 10, "2");
            p.text(85, 600, 10, "Planungsleistung Phase 2");
            p.text(270, 600, 10, "18 Std");
            p.text(335, 600, 10, "95,00 EUR");
            p.text(410, 600, 10, "19 %");
            p.right(535, 600, 10, "1.710,00 EUR");
            p.text(85, 587, 8, "Heizlastberechnung und Hydraulikschema");
            p.text(400, 60, 8, "Seite 1 von 2");

            Pg q = d.page();
            q.text(60, 800, 9, "Rechnung Nr. 2027-0042 – Seite 2 von 2");
            q.text(60, 770, 9, "Pos");
            q.text(85, 770, 9, "Beschreibung");
            q.text(270, 770, 9, "Menge");
            q.text(335, 770, 9, "Einzelpreis");
            q.text(410, 770, 9, "USt");
            q.right(535, 770, 9, "Netto");
            q.rule(60, 765, 535);
            q.text(60, 747, 10, "3");
            q.text(85, 747, 10, "Fachbuch Heizlastberechnung");
            q.text(270, 747, 10, "1 Stk");
            q.text(335, 747, 10, "39,00 EUR");
            q.text(410, 747, 10, "7 %");
            q.right(535, 747, 10, "39,00 EUR");
            q.text(60, 729, 10, "4");
            q.text(85, 729, 10, "Reisekosten pauschal");
            q.text(270, 729, 10, "1 psch");
            q.text(335, 729, 10, "120,00 EUR");
            q.text(410, 729, 10, "19 %");
            q.right(535, 729, 10, "120,00 EUR");
            q.rule(60, 715, 535);
            q.text(250, 700, 10, "Zwischensumme netto");
            q.right(535, 700, 10, "2.106,50 EUR");
            q.text(250, 686, 10, "Umsatzsteuer 7 % auf 39,00 EUR");
            q.right(535, 686, 10, "2,73 EUR");
            q.text(250, 672, 10, "Umsatzsteuer 19 % auf 2.067,50 EUR");
            q.right(535, 672, 10, "392,83 EUR");
            q.text(250, 652, 11, "Rechnungsbetrag brutto");
            q.right(535, 652, 11, "2.502,06 EUR");
            q.text(60, 610, 10, "Zahlungsziel: 17.02.2027");

            q.text(60, 80, 8, "Bankverbindung: Volksbank Beispiel eG, IBAN DE12 5001 0517 0648 4898 90, BIC INGDDEFFXXX");
            q.text(60, 68, 8, "St.-Nr. 59/123/45678 · USt-IdNr. DE312345678");
            q.text(60, 56, 8, "hartmann@ing-hartmann.example · Tel.: 0711 4455667");
            return d.bytes();
        }
    }

    public static final class Doc implements AutoCloseable {
        private final PDDocument doc = new PDDocument();
        private final PDFont font;

        public Doc() throws IOException {
            try (InputStream ttf = SampleInvoices.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
                font = PDType0Font.load(doc, ttf);
            }
        }

        public Pg page() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            return new Pg(new PDPageContentStream(doc, page), font);
        }

        public byte[] bytes() throws IOException {
            closeStreams();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }

        private void closeStreams() throws IOException {
            for (Pg p : Pg.OPEN) {
                p.cs.close();
            }
            Pg.OPEN.clear();
        }

        @Override
        public void close() throws IOException {
            Pg.OPEN.clear();
            doc.close();
        }
    }

    public static final class Pg {
        static final java.util.List<Pg> OPEN = new java.util.ArrayList<>();
        final PDPageContentStream cs;
        private final PDFont font;

        Pg(PDPageContentStream cs, PDFont font) {
            this.cs = cs;
            this.font = font;
            OPEN.add(this);
        }

        public void text(float x, float y, float size, String s) throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(x, y);
            cs.showText(s);
            cs.endText();
        }

        public void right(float xRight, float y, float size, String s) throws IOException {
            text(xRight - font.getStringWidth(s) / 1000f * size, y, size, s);
        }

        public void rule(float x1, float y, float x2) throws IOException {
            cs.moveTo(x1, y);
            cs.lineTo(x2, y);
            cs.stroke();
        }
    }
}
